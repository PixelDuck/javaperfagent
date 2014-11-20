import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Perf agent helper class.
 */
public class PerfAgentHelper {

  private static String outputFile = "/tmp/stats.json";

  public static ThreadLocal<ArrayList<Object[]>> monitorsTL = new ThreadLocal<ArrayList<Object[]>>() {
    @Override protected ArrayList<Object[]> initialValue() {
      return new ArrayList<>();
    }
  };
  public static ThreadLocal<Integer> deepTL = new ThreadLocal<Integer>() {
    @Override protected Integer initialValue() {
      return 0;
    }
  };
  public static long minTimeToTrackInMs = 0;
  public static boolean trackParameters = false;

  public static int beforeMethod(String methodName, boolean debug) {
    int deep = incrDeep();
    int monitorsIndex = monitorsTL.get().size();
    if(debug) {
      System.out.println("Method "+methodName+" is called (deep: "+deep+" called from "+findParent(monitorsIndex, deep)+")");
    }
    monitorsTL.get().add(new Object[] { methodName, deep, System.currentTimeMillis() });
    return monitorsIndex;
  }

  public static void afterMethod(int monitorsIndex, boolean debug) {
    java.lang.Object[] ar = monitorsTL.get().get(monitorsIndex);
    long duration = System.currentTimeMillis() - (Long) ar[2];
    ar[2] = duration;
    if (duration < minTimeToTrackInMs) {
      monitorsTL.get().remove(monitorsIndex);
    }
    int deep = (Integer)ar[1];
    if(debug) {
      System.out.println("Time spent on " + ar[0] + ": " + ar[2] + "ms. (deep: "+ deep
          +" called from "+findParent(monitorsIndex, deep)+")");
    }
    if (deep == 1) {
      ArrayList<Object[]> objects = monitorsTL.get();
      StringBuilder monitorsSb = new StringBuilder();
      monitorsSb.append("[{");
      for (int i1 = 0; i1 < objects.size(); i1++) {
        Object[] monitor = objects.get(i1);
        Object[] nextMonitor = (i1 < objects.size() - 1) ? objects.get(i1 + 1) : null;
        int currentDeep = (Integer) monitor[1];
        int nextElementDeep = nextMonitor != null ? (Integer) nextMonitor[1] : -1;
        boolean isNextCallSubCall = nextElementDeep==(currentDeep+1);
        boolean isNextCallSequentialCall = nextElementDeep==currentDeep;
        boolean isLastCall = nextMonitor==null;
        long totalTime = (Long) monitor[2];
        monitorsSb.append("\"").append(monitor[0]).append("\":\"").append(totalTime).append("ms\"");
        if (isLastCall) {
          for (int d = currentDeep; d > 1; d--) {
            monitorsSb.append("}]");
          }
        } else {
          if (isNextCallSubCall) {
            monitorsSb.append(",\"subcalls\":[{");
          } else if (isNextCallSequentialCall) {
            monitorsSb.append("},{");
          } else {
            for (int d = nextElementDeep; d < currentDeep; d++) {
              monitorsSb.append("}]");
            }
            monitorsSb.append("},{");
          }
        }
      }
      monitorsSb.append("}]\n");
      monitorsTL.get().clear();
      deepTL.set(0);
      try {
        String content = monitorsSb.toString();
        if(!content.equals("[{}]\n")) {
          new FileWriter(outputFile, true).append(content).close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      decrDeep();
    }
  }

  private static String findParent(int monitorsIndex, int deep) {
    for(int i=monitorsIndex-1; i>=0; i--) {
      java.lang.Object[] ar = monitorsTL.get().get(i);
      if(ar[1]==deep-1) {
        return (String) ar[0];
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
