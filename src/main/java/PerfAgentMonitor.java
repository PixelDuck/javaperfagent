import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Monitor for classes instrumented.
 *
 * @author olmartin
 */
public class PerfAgentMonitor {

  private static ThreadLocal<List<TrackInfo>> monitorsTL                       = new ThreadLocal<List<TrackInfo>>() {
    @Override protected List<TrackInfo> initialValue() {
      return new ArrayList<>();
    }
  };
  private static ThreadLocal<Integer>         deepTL                           = new ThreadLocal<Integer>() {
    @Override protected Integer initialValue() {
      return 0;
    }
  };
  private static long                         minTimeToTrackInMicros           = 0;
  private static long                         minRootTimeToTrackInMicros       = 0;

  private static final Semaphore ACCESS_FILE_SEMAPHORE = new Semaphore(1, false);

  private static String                   outputFilePath;
  private static File                     outputFile;
  private static ScheduledExecutorService scheduler;

  private static boolean logOutputEnabled = true;

  private static class TrackInfo {
    String   methodName;
    int      deep;
    long     startTime;
    long     durationInMicros;
    Object[] paramValues;

    TrackInfo(String methodName,
        int deep,
        long startTime,
        Object[] paramValues) {
      this.methodName = methodName;
      this.deep = deep;
      this.startTime = startTime;
      this.paramValues = paramValues;
    }
  }

  /**
   * Call by weaved method before calling the real code
   *
   * @param methodName  the method name
   * @param debug       specify if debugging is activated for this method call
   * @param paramValues the parameter value
   * @return the index of the monitor in the stack.
   */
  public static int beforeMethod(String methodName, boolean debug, Object... paramValues) {
    int deep = incrDeep();
    int monitorsIndex = monitorsTL.get().size();
    if (debug) {
      System.out.println("Method " + methodName + " is called (deep: " + deep + " called from " + findParent(monitorsIndex, deep) + ")");
    }
    monitorsTL.get().add(new TrackInfo(methodName, deep, System.nanoTime() / 1000, paramValues));
    return monitorsIndex;
  }

  /**
   * Call by weaved method after calling the real code
   *
   * @param monitorsIndex the index of the monitor for this call in the stack
   * @param debug         specify if debugging is activated for this method call
   */
  public static void afterMethod(int monitorsIndex, boolean debug) {
    long now = System.nanoTime() / 1000;
    TrackInfo trackInfo = monitorsTL.get().get(monitorsIndex);
    trackInfo.durationInMicros = now - trackInfo.startTime;
    if (trackInfo.durationInMicros < minTimeToTrackInMicros) {
      if (debug) {
        System.out.println("Time spent on " + trackInfo.methodName + ": " + (trackInfo.durationInMicros / 1000) + "˜ms. (deep: " + trackInfo.deep
            + " called from " + findParent(monitorsIndex, trackInfo.deep) + "). Ignored because below " + (minTimeToTrackInMicros / 1000) + "ms");
      }
      monitorsTL.get().remove(monitorsIndex);
    }
    if (debug) {
      System.out.println("Time spent on " + trackInfo.methodName + ": " + (trackInfo.durationInMicros / 1000) + "ms. (deep: " + trackInfo.deep
          + " called from " + findParent(monitorsIndex, trackInfo.deep) + ")");
    }
    if (trackInfo.deep == 1) {
      String content = null;
      if (logOutputEnabled) {
        if (trackInfo.durationInMicros >= minRootTimeToTrackInMicros) {
          content = createJsonFromStack(monitorsTL.get());
        }
      }
      monitorsTL.get().clear();
      deepTL.set(0);
      if (content != null) {
        try {
          if (debug) {
            System.out.println("Trying to acquire semeaphore to write content");
          }
          ACCESS_FILE_SEMAPHORE.acquire();
          try (Writer writer = new FileWriter(outputFilePath, true)) {
            writer.append(content);
          }
        } catch (IOException | InterruptedException e) {
          //nothing to do
        } finally {
          ACCESS_FILE_SEMAPHORE.release();
        }
        if (debug) {
          System.out.println("Release semaphore");
        }
      } else {
        if (debug) {
          System.out.println("Content for " + trackInfo.methodName + " null");
        }
      }
    } else {
      decrDeep();
    }
  }

  private static String createJsonFromStack(List<TrackInfo> trackInfos) {
    StringBuilder buffer = new StringBuilder();
    for (int i1 = 0; i1 < trackInfos.size(); i1++) {
      TrackInfo trackInfo = trackInfos.get(i1);
      TrackInfo nextTrackInfo = (i1 < trackInfos.size() - 1) ? trackInfos.get(i1 + 1) : null;
      int currentDeep = trackInfo.deep;
      int nextElementDeep = nextTrackInfo != null ? nextTrackInfo.deep : -1;
      boolean isNextCallSubCall = nextElementDeep == (currentDeep + 1);
      boolean isNextCallSequentialCall = nextElementDeep == currentDeep;
      boolean isLastCall = nextTrackInfo == null;
      double totalTime = (double) trackInfo.durationInMicros / 1000;
      buffer.append("\"").append(toMethodName(trackInfo.methodName, trackInfo.paramValues)).append("\":\"").append(totalTime).append("ms\"");
      if (isLastCall) {
        for (int d = currentDeep; d > 1; d--) {
          buffer.append("}]");
        }
      } else {
        if (isNextCallSubCall) {
          buffer.append(",\"subcalls\":[{");
        } else if (isNextCallSequentialCall) {
          buffer.append("},{");
        } else {
          for (int d = nextElementDeep; d < currentDeep; d++) {
            buffer.append("}]");
          }
          buffer.append("},{");
        }
      }
    }
    if (buffer.length() == 0) {
      return null;
    } else {
      return "{" + buffer.toString() + "}\n";
    }
  }

  private static String toMethodName(String methodName, Object[] parameterValues) {
    if (parameterValues == null || parameterValues.length == 0) {
      return methodName;
    } else {
      int start = methodName.indexOf("(");
      String[] parameters = methodName.substring(start, methodName.indexOf(")")).split(",");
      StringBuilder sb = new StringBuilder();
      sb.append(methodName.substring(0, start)).append("(");
      for (int i = 0; i < parameters.length; i++) {
        if (i != 0) {
          sb.append(",");
        }
        sb.append(parameterValues[i]);
      }
      sb.append(")");
      return sb.toString()
          .replace("'", "\'")
          .replace("\\", "\\\\")
          .replace("\n", "")
          .replace("\t", "")
          .replace("\"", "\\\"");
    }
  }

  private static String findParent(int monitorsIndex, int deep) {
    for (int i = monitorsIndex - 1; i >= 0; i--) {
      TrackInfo trackInfo = monitorsTL.get().get(i);
      if (trackInfo.deep == deep - 1) {
        return trackInfo.methodName;
      }
    }
    return null;
  }

  private static int incrDeep() {
    int value = deepTL.get();
    value += 1;
    deepTL.set(value);
    return value;
  }

  private static int decrDeep() {
    int value = deepTL.get();
    value -= 1;
    deepTL.set(value);
    return value;
  }

  public static String outputFilePath() {
    return outputFilePath;
  }

  public static void outputFilePath(String arg) {
    outputFilePath = arg;
    outputFile = new File(arg);
    System.setProperty("JavaPerfAgent.output.filepath", arg);
    System.setProperty("JavaPerfAgent.output.enabled", "true");
  }

  public static void minTimeToTrackInMicros(long value) {
    minTimeToTrackInMicros = value;
  }

  public static void minRootTimeToTrackInMicros(long value) {
    minRootTimeToTrackInMicros = value;
  }

  public static void stopLoggingResultsOnLowDiskSpace(final long value) {
    if (value > 0) {
      scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(new Runnable() {
        @Override
        public void run() {
          if (outputFile != null) {
            double freeSpace = outputFile.getParentFile().getUsableSpace() / 1000000.00;
            if (logOutputEnabled) {
              if (freeSpace < value) {
                System.setProperty("JavaPerfAgent.output.enabled", "false");
                System.setProperty("JavaPerfAgent.output.disabled.reason", "Free space < "+value+"Mb. Actual free space: "+outputFile.getFreeSpace()+" bytes");
                logOutputEnabled = false;
              }
            } else {
              if (freeSpace > value) {
                System.setProperty("JavaPerfAgent.output.enabled", "true");
                System.clearProperty("JavaPerfAgent.output.disabled.reason");
                logOutputEnabled = true;
              }
            }
          }
        }
      }, 0, 1, TimeUnit.MINUTES);
    }
  }
}
