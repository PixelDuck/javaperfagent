import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Monitor for classes instrumented.
 * @author olmartin
 */
public class PerfAgentMonitor {

  private static String outputFile = "/tmp/stats.json";

  public static ThreadLocal<List<TrackInfo>> monitorsTL = new ThreadLocal<List<TrackInfo>>() {
    @Override protected List<TrackInfo> initialValue() {
      return new ArrayList<>();
    }
  };
  public static ThreadLocal<Integer> deepTL = new ThreadLocal<Integer>() {
    @Override protected Integer initialValue() {
      return 0;
    }
  };
  public static long minTimeToTrackInMicros = 0;
  public static long minRootTimeToTrackInMicros = 0;

  private static class TrackInfo{
    String methodName;
    int deep;
    long startTime;
    long durationInMicros;
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

  public static int beforeMethod(String methodName, boolean debug, Object ... paramValues) {
    int deep = incrDeep();
    int monitorsIndex = monitorsTL.get().size();
    if(debug) {
      System.out.println("Method "+methodName+" is called (deep: "+deep+" called from "+findParent(monitorsIndex, deep)+")");
    }
    monitorsTL.get().add(new TrackInfo(methodName, deep, System.nanoTime()/1000, paramValues));
    return monitorsIndex;
  }

  public static void afterMethod(int monitorsIndex, boolean debug) {
    long now = System.nanoTime()/1000;
    TrackInfo trackInfo = monitorsTL.get().get(monitorsIndex);
    trackInfo.durationInMicros = now - trackInfo.startTime;
    if (trackInfo.durationInMicros < minTimeToTrackInMicros) {
      if (debug) {
        System.out.println("Time spent on " + trackInfo.methodName + ": " + (trackInfo.durationInMicros/1000) + "˜ms. (deep: " + trackInfo.deep
            + " called from " + findParent(monitorsIndex, trackInfo.deep) + "). Ignored because below " + (minTimeToTrackInMicros/1000) + "ms");
      }
      monitorsTL.get().remove(monitorsIndex);
    }
    if (debug) {
      System.out.println("Time spent on " + trackInfo.methodName + ": " + (trackInfo.durationInMicros/1000) + "ms. (deep: " + trackInfo.deep
          + " called from " + findParent(monitorsIndex, trackInfo.deep) + ")");
    }
    if (trackInfo.deep == 1) {
      String content = null;
      if (trackInfo.durationInMicros >= minRootTimeToTrackInMicros) {
        content = createJsonFromStack(monitorsTL.get());
      }
      monitorsTL.get().clear();
      deepTL.set(0);
      try {
        if (content != null) {
          synchronized (outputFile) {
            new FileWriter(outputFile, true).append(content).close();
          }
        } else {
          if (debug) {
            System.out.println("Content for " + trackInfo.methodName + " null");
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
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
      boolean isNextCallSubCall = nextElementDeep==(currentDeep+1);
      boolean isNextCallSequentialCall = nextElementDeep==currentDeep;
      boolean isLastCall = nextTrackInfo==null;
      double totalTime = (double)trackInfo.durationInMicros/1000;
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
    if(buffer.length()==0) {
      return null;
    }
    else {
      return "[{"+buffer.toString()+"}]\n";
    }
  }

  private static String toMethodName(String methodName, Object[] parameterValues) {
    if (parameterValues == null || parameterValues.length==0)
      return methodName;
    else {
      int start = methodName.indexOf("(");
      String[] parameters = methodName.substring(start, methodName.indexOf(")")).split(",");
      StringBuilder sb = new StringBuilder();
      sb.append(methodName.substring(0, start)).append("(");
      for(int i=0; i<parameters.length; i++) {
        if(i!=0)
          sb.append(",");
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
    for(int i=monitorsIndex-1; i>=0; i--) {
      TrackInfo trackInfo = monitorsTL.get().get(i);
      if(trackInfo.deep==deep-1) {
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

  public static void outputFile(String arg) {
    outputFile = arg;
  }

}
