import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
 * An agent to track performances.
 */
public class PerfAgent implements ClassFileTransformer {

  private Map<String,Set<String>> trackedClass = new HashMap<>();
  private Map<String,Set<String>> untrackedClass = new HashMap<>();
  private Set<String> debugClasses = new HashSet<>();

  public PerfAgent(String ... args) {
    config(args[0]);
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
    } else if(line.startsWith("!")) {
      addDebugInfo(line.substring(1));
    } else if(line.startsWith(":")) {
      String filePath = line.substring(1);
      boolean appendFile = filePath.charAt(filePath.length()-1) == '+';
      if(appendFile) {
        filePath = filePath.substring(0, filePath.length()-1);
      } else {
        new File(filePath).delete();
      }
      PerfAgentHelper.outputFile(filePath);
    } else {
      addConfig(line, trackedClass);
    }
  }

  private void addDebugInfo(String clazz) {
    this.debugClasses.add(clazz);
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
              if (!m.isEmpty()) {
                methodsModified.add(m.getLongName());
                m.addLocalVariable("monitorsIndex", CtClass.intType);
                m.insertBefore("monitorsIndex = PerfAgentHelper.beforeMethod(\"" + m.getLongName() + "\", "+debug+");");
                m.insertAfter("{PerfAgentHelper.afterMethod(monitorsIndex, "+debug+");}");
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
    if( agentArgs==null || agentArgs.trim().length()==0) {
      System.err.println("You must specify the path to configuration file for the agent. This file should have the name of class/method to track " +
          "one entry per line. You can also specify wild card * or prefix the entry with - sign to explicitly not tracking this class or pattern. " +
          "By default, stat file will be generated on /tmp/stats.log but you can add a line starting with : to specify the file to use as output. If "
          + "the file path end with + character, the output will be append at the end of the file if it already exist.");
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

