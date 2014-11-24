import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
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
 * An agent to track performances. See {@link #printUsage()} for documentation.
 */
public class PerfAgent implements ClassFileTransformer {

  private static boolean trackParameters = false;
  private static boolean debugConfigFile = false;
  private Map<String, Pair<Map<String,Boolean>, Boolean>> trackedClass = new HashMap<>();
  private Map<String, Pair<Map<String,Boolean>, Boolean>> untrackedClass = new HashMap<>();
  private Set<String> debugClasses = new HashSet<>();

  public PerfAgent(String ... args) {
    config(args[0]);
  }

  public void config(String configFilePath) {
    BufferedReader reader = null;
    try {
      if(new File(configFilePath).exists()) {
        reader = new BufferedReader(new FileReader(configFilePath));
      } else {
        reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(configFilePath)));
      }
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
    line = line.trim();
    if(!line.isEmpty() && !line.startsWith("//")) {
      if (line.startsWith("+")) {
        if(debugConfigFile) {
          System.out.println("track "+line.substring(1));
        }
        addConfig(line.substring(1), trackedClass, false);
      } else if (line.startsWith("#")) {
        if(debugConfigFile) {
          System.out.println("track with parameter "+line.substring(1));
        }
        addConfig(line.substring(1), trackedClass, true);
      } else if (line.startsWith("-")) {
        if(debugConfigFile) {
          System.out.println("untrack "+line.substring(1));
        }
        addConfig(line.substring(1), untrackedClass, false);
      } else if (line.startsWith("!")) {
        if(debugConfigFile) {
          System.out.println("debug "+line.substring(1));
        }
        addDebugInfo(line.substring(1));
      } else if (line.startsWith("$")) {
        if(debugConfigFile) {
          System.out.println("command found "+line.substring(1));
        }
        addOption(line.substring(1));
      } else if (line.startsWith(":")) {
        if(debugConfigFile) {
          System.out.println("output to "+line.substring(1));
        }
        String filePath = line.substring(1);
        boolean appendFile = filePath.charAt(filePath.length() - 1) == '+';
        if (appendFile) {
          filePath = filePath.substring(0, filePath.length() - 1);
        } else {
          new File(filePath).delete();
        }
        PerfAgentMonitor.outputFile(filePath);
      }
    }
  }

  public static void addOption(String option) {
    String[] split = option.split("=");
    switch(split[0]) {
      case "minTimeToTrackInMicros":
        PerfAgentMonitor.minTimeToTrackInMicros = Long.parseLong(split[1]);
        break;
      case "minRootTimeToTrackInMicros":
        PerfAgentMonitor.minRootTimeToTrackInMicros = Long.parseLong(split[1]);
        break;
      case "debugConfigFile":
        debugConfigFile = split.length==1 || "true".equalsIgnoreCase(split[1]);
        break;
      case "trackParameters":
        trackParameters = split.length==1 || "true".equalsIgnoreCase(split[1]);
        break;
      }
  }

  private void addDebugInfo(String clazz) {
    this.debugClasses.add(clazz);
  }

  private void addConfig(String value, Map<String, Pair<Map<String,Boolean>, Boolean>> set, boolean trackParameter) {
    int i = value.indexOf("(");
    if(i >= 0) {
      int endClassIndex = value.substring(0, i).lastIndexOf('.');
      String className = value.substring(0, endClassIndex);
      String methodName = value.substring(endClassIndex+1);
      Pair<Map<String,Boolean>, Boolean> methodList = set.get(className);
      if(methodList == null) {
        methodList = Pair.of((Map<String,Boolean>)new HashMap<String,Boolean>(), false);
        set.put(className, methodList);
      }
      methodList.getLeft().put(methodName, trackParameter);
    } else {
      Pair<Map<String,Boolean>, Boolean> methodList = set.get(value);
      if (methodList == null) {
        methodList = Pair.of((Map<String,Boolean>)new HashMap<String,Boolean>(), trackParameter);
        set.put(value, methodList);
      }
    }
  }

  public Pair<String,String> findTrackedClassEntry(String className) {
    String entry = findEntry(className, trackedClass);
    if(entry!=null) {
      String untrackedEntry = findEntry(className, untrackedClass);
      if (untrackedEntry!=null) {
        if(untrackedClass.get(untrackedEntry) != null && untrackedClass.get(untrackedEntry).getLeft().size()>0) {
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
    return checkMethodEntry(methodName, trackedClass.get(entryClassName).getLeft())
        && (entryClassNameInUntracked==null || !checkMethodEntry(methodName, untrackedClass.get(entryClassNameInUntracked).getLeft()));
  }

  public boolean checkTrackParam(String methodName, String entryClassName) {
    Pair<Map<String, Boolean>, Boolean> map = trackedClass.get(entryClassName);
    boolean trackParam = map.getRight().booleanValue();
    Map<String, Boolean> methods = map.getLeft();
    Boolean aBoolean = methods.get(methodName);
    if(aBoolean!=null){
      trackParam = aBoolean.booleanValue();
    } else {
      String keyFound = null;
      for (String entry : methods.keySet()) {
        if (entry.lastIndexOf("*") >= 0) {
          if (methodName.startsWith(entry.substring(0, entry.length() - 1))) {
            keyFound = entry;
          }
        }
      }
      if (keyFound != null) {
        Boolean val = methods.get(keyFound);
        if (val != null)
          return val.booleanValue();
      }
    }
    return trackParam;
  }

  private boolean checkMethodEntry(String methodName, Map<String,Boolean> set) {
    if (set==null)
      return false;
    if(set.size()==0)
      return true;
    if(set.containsKey(methodName)) {
      return true;
    } else {
      for(String entry : set.keySet()) {
        if(entry.lastIndexOf("*")>=0) {
          if(methodName.startsWith(entry.substring(0, entry.length()-1))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private String findEntry(String className, Map<String, Pair<Map<String,Boolean>, Boolean>> set) {
    if(set.containsKey(className)) {
      return className;
    } else {
      String keyFound = null;
      for(String key : set.keySet()) {
        if (key.endsWith("*")) {
          if (className.startsWith(key.substring(0, key.length()-2))) {
            if(keyFound==null || keyFound.length() < key.length())
              keyFound = key;
          }
        }
      }
      return keyFound;
    }
  }

  @Override public byte[] transform(ClassLoader loader, String classNameWithSlashes, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
      byte[] classfileBuffer) throws IllegalClassFormatException {

    byte[] byteCode;
    String className = classNameWithSlashes.replaceAll("[/]", ".");
    Pair<String, String> trackedClassEntry = findTrackedClassEntry(className);
    if (trackedClassEntry!=null && trackedClassEntry.getLeft()!=null) {
      try {
        ClassPool cp = ClassPool.getDefault();
        CtClass cc = cp.get(className);
        boolean debug = debugClasses.contains(className);
        if (!cc.isFrozen()) {
          boolean isModified = false;
          Set<String> methodsModified = new HashSet<>();
          for (CtMethod m : cc.getDeclaredMethods()) {
            if(checkMethod(m.getName(), trackedClassEntry.getLeft(), trackedClassEntry.getRight())) {
              if (!m.isEmpty() && m.getMethodInfo().getCodeAttribute()!=null) {
                methodsModified.add(m.getLongName());
                m.addLocalVariable("monitorsIndex", CtClass.intType);
                if(checkTrackParam(m.getName(), trackedClassEntry.getLeft()) || trackParameters) {
                  m.insertBefore("monitorsIndex = PerfAgentMonitor.beforeMethod(\"" + m.getLongName() + "\", " + debug + ", $args);");
                } else {
                  m.insertBefore("monitorsIndex = PerfAgentMonitor.beforeMethod(\"" + m.getLongName() + "\", " + debug + ", null);");
                }
                m.insertAfter("{PerfAgentMonitor.afterMethod(monitorsIndex, "+debug+");}");
                isModified = true;
              }
            }
          }
          if(isModified) {
            if(debug) {
              System.out.println(format("Class %s was modified. Methods tracked: %s.", className, methodsModified.toString()));
            }
            byteCode = cc.toBytecode();
            cc.detach();
            return byteCode;
          } else {
            if(debug) {
              System.out.println(format("Class %s is not modified because no method was marked as tracked", className));
            }
          }
        } else {
          if(debug) {
            System.out.println(format("Class %s is frozen. Cant modified it with agent code%n", className));
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
    System.out.println("Activate perf agent");
    if( agentArgs==null || agentArgs.trim().length()==0) {
      System.err.println("You must specify the path to configuration file for the agent.");
      System.exit(9);
    }
    String[] param = agentArgs.split(",");
    System.out.println("Perf agent config file: "+param[0]);
    PerfAgent perfAgent = new PerfAgent(param);
    inst.addTransformer(perfAgent);
  }

  public static void main(String[] args) {
    printUsage();
  }

  private static void printUsage() {
    System.out.println(
        "To plug the agent, add to JVM option -javaagent:<PATH_TO_JAR>=<PATH_TO_CONFIG_FILE>.\n"
            + "Configuration file is a simple text file.\n"
            + "If line starts with '//' then this is a comment line\n"
            + "You should specify where to write results with a line starting with ':' followed by the path to the config file. Example:\n"
            + "\t:/tmp/stats.json\n"
            + "You can add some options starting with the character '$'. Options available are:\n"
            + "\t$minRootTimeToTrackInMicros=<TIME IN MS>\n"
            + "\t  specifies the minimum time for the root call to match in order to log results from this method\n"
            + "\t$minTimeToTrackInMicros=<TIME IN MS>\n"
            + "\t  specifies the minimum time on a call to match in order to log results from this method\n"
            + "\t$trackParameters\n"
            + "\t  specifies that parameters should be tracked\n"
            + "\t$debugConfigFile\n"
            + "\t  debug configuration analysis\n"
            + "You should add some classes or methods to track with: \n"
            + "\t* A full class name (means package with class name) starting with '+'. for example:\n"
            + "\t\t+java.util.ArrayList\n"
            + "\t* You can also specify an ending pattern for the package terminating the line with the character '*'. For example:\n"
            + "\t\t+com.test.Test*\n"
            + "\t  will activate tracking for all classes from package 'com.test' with a name starting with 'Test'.\n"
            + "\t* You can also specify a method name like\n"
            + "\t\t+java.util.ArrayList.add()\n"
            + "\t  to track a particular method. Notice that '*' suffix do not work with method names\n"
            + "\t* For each of this configuration, you can start the line with the character '-' in order to specify that you don't want to track this entry. Example:\n"
            + "\t\t-com.test.Test.remove(int)\n"
            + "\t  will not track the method 'remove(int)' from class 'com.test.Test'\n"
            + "\t* For each of this configuration, you can start the line with the character '#' in order to specify that you want to track parameters for this entry. Example:\n"
            + "\t\t#com.test.Test.remove(int)\n"
            + "\t  will track the method 'remove(int)' from class 'com.test.Test' and the parameter value from type 'int' will be tracked also\n"
    );
  }

}

