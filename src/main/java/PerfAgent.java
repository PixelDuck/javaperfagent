import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * An agent to track performances.
 */
public class PerfAgent implements ClassFileTransformer {

  private Map<String,Set<String>> trackedClass = new HashMap<>();
  private Map<String,Set<String>> untrackedClass = new HashMap<>();

  public PerfAgent(String ... args) {
    config(args[0]);
    if(args.length > 1)
      PerfAgentHelper.outputFile(args[1]);
  }

  public static class PerfAgentHelper {

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

    public static int beforeMethod(String methodName) {
      int iDeep = incrDeep();
      int monitorsIndex = monitorsTL.get().size();
      monitorsTL.get().add(new Object[] { methodName, iDeep, System.currentTimeMillis() });
      return monitorsIndex;
    }

    public static void afterMethod(int monitorsIndex) {
      java.lang.Object[] ar = monitorsTL.get().get(monitorsIndex);
      ar[2] = System.currentTimeMillis() - (Long) ar[2];
      if (deepTL.get() == 1) {
        ArrayList<Object[]> objects = monitorsTL.get();
        StringBuilder monitorsSb = new StringBuilder();
        monitorsSb.append("[{");
        for (int i1 = 0; i1 < objects.size(); i1++) {
          Object[] monitor = objects.get(i1);
          Object[] nextMonitor = (i1 < objects.size() - 1) ? objects.get(i1 + 1) : null;
          int currentDeep = (Integer) monitor[1];
          int nextElementDeep = nextMonitor != null ? (Integer) nextMonitor[1] : -1;
          long totalTime = (Long) monitor[2];
          long spentTime = totalTime;
          monitorsSb.append("\"").append(monitor[0]).append("\":\"").append(spentTime).append("/").append(totalTime).append("ms\"");
          if (nextElementDeep != -1) {
            if (currentDeep < nextElementDeep) {
              monitorsSb.append(",\"subcalls\":[{");
            } else if (currentDeep == nextElementDeep) {
              monitorsSb.append("},{");
            } else {
              for (int d = nextElementDeep; d < currentDeep; d++) {
                monitorsSb.append("}]");
              }
              monitorsSb.append("},{");
            }
          } else {
              for (int d=currentDeep; d>1; d--) {
                monitorsSb.append("}]");
              }
          }
        }
        monitorsSb.append("}]\n");
        monitorsTL.get().clear();
        try {
          new FileWriter(outputFile, true).append(monitorsSb.toString()).close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      decrDeep();
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

  public void config(String configFilePath) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(configFilePath));
      String line;
      do {
        line = reader.readLine();
        if (line != null) {
          addConfig(line);
        }
      } while (line != null);
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(8);
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
          System.exit(8);
        }
      }
    }
  }

  private void addConfig(String line) {
    if(line.startsWith("-")) {
      addConfig(line.substring(1), untrackedClass);
    } else {
      addConfig(line, trackedClass);
    }
  }

  private void addConfig(String value, Map<String,Set<String>> set) {
    int i = value.indexOf("(");
    if(i >= 0) {
      int endClassIndex = value.substring(0, i).lastIndexOf('.');
      String className = value.substring(0, endClassIndex);
      String methodName = value.substring(endClassIndex+1);
      Set<String> methodList = set.get(className);
      if(methodList == null) {
        methodList = new HashSet<>();
        set.put(className, methodList);
      }
      methodList.add(methodName);
    } else {
      Set<String> methodList = set.get(value);
      if (methodList == null) {
        methodList = new HashSet<>();
        set.put(value, methodList);
      }
    }
  }

  public Pair<String,String> findTrackedClassEntry(String className) {
    String entry = findEntry(className, trackedClass);
    if(entry!=null) {
      String untrackedEntry = findEntry(className, untrackedClass);
      if (untrackedEntry!=null) {
        if(untrackedClass.get(untrackedEntry) != null && untrackedClass.get(untrackedEntry).size()>0) {
          return Pair.of(entry, untrackedEntry);
        } else {
          return null;
        }
      } else {
        return Pair.of(entry, null);
      }
    } else {
      return null;
    }
  }

  public boolean checkMethod(String methodName, String entryClassName, String entryClassNameInUntracked) {
    return checkMethodEntry(methodName, trackedClass.get(entryClassName))
        && (entryClassNameInUntracked==null || !checkMethodEntry(methodName, untrackedClass.get(entryClassNameInUntracked)));
  }

  private boolean checkMethodEntry(String methodName, Set<String> set) {
    if (set==null)
      return false;
    if(set.size()==0)
      return true;
    if(set.contains(methodName)) {
      return true;
    } else {
      for(String entry : set) {
        if(entry.lastIndexOf("*")>=0) {
          if(methodName.startsWith(entry.substring(0, entry.length()-1))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private String findEntry(String className, Map<String, Set<String>> set) {
    if(set.containsKey(className)) {
      return className;
    } else {
      for(String key : set.keySet()) {
        if (key.endsWith("*")) {
          if (className.startsWith(key.substring(0, key.length()-2))) {
            return key;
          }
        }
      }
    }
    return null;
  }

  @Override public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
      byte[] classfileBuffer) throws IllegalClassFormatException {

    byte[] byteCode;
    String classname = className.replaceAll("[/]", ".");
    Pair<String, String> trackedClassEntry = findTrackedClassEntry(classname);
//    System.out.println("Check "+classname+"=>"+trackedClassEntry);
    if (trackedClassEntry!=null && trackedClassEntry.getLeft()!=null) {
      try {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(classname);
        if (!cc.isFrozen()) {
          boolean isModified = false;
          for (CtMethod m : cc.getDeclaredMethods()) {
            if(checkMethod(m.getName(), trackedClassEntry.getLeft(), trackedClassEntry.getRight())) {
              if (!m.isEmpty()) {
                m.addLocalVariable("monitorsIndex", CtClass.intType);
                m.insertBefore("monitorsIndex = PerfAgent.PerfAgentHelper.beforeMethod(\"" + m.getLongName() + "\");");
                m.insertAfter("{PerfAgent.PerfAgentHelper.afterMethod(monitorsIndex);}");
                isModified = true;
              }
            }
          }
          if(isModified) {
            byteCode = cc.toBytecode();
            cc.detach();
            return byteCode;
          }
        }
      } catch (NotFoundException e) {
        // nothing to do
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    return null;
  }

  public static void premain(String agentArgs, Instrumentation inst) {
    if( agentArgs==null || agentArgs.trim().length()==0) {
      System.err.println("You must specify the path to configuration file for the agent. This file should have the name of class/method to track " +
          "one entry per line. You can also specify wild card * or prefix the entry with - sign to explicitely not tracking this class or pattern. " +
          "By default, stat file will be generated on /tmp/stats.log but you can override this value passing a second " +
          "argument to the java agent with the file path where to put statistics separated by a comma from the config file path");
      System.exit(9);
    }
    PerfAgent perfAgent = new PerfAgent(agentArgs.split(","));
    inst.addTransformer(perfAgent);
  }

  public static void main(String[] args) {
    PerfAgent perfAgent = new PerfAgent("/Users/olmartin/Documents/statsagent.cfg,/tmp/stats.log".split(","));
    Pair<String, String> trackedClassEntry = perfAgent.findTrackedClassEntry("com.test.Remove");
    System.out.println(trackedClassEntry);
    if (trackedClassEntry != null) {
      System.out.println(perfAgent.checkMethod("test3()", trackedClassEntry.getLeft(), trackedClassEntry.getRight()));
    }
  }
}

