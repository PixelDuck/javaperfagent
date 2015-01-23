import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.kevoree.library.nanohttp.NanoHTTPD;

/**
 * A web gui to analyse results.
 *
 * @author olivier martin
 */
public class WebGUI extends DefaultWebController {

  public static final int PORT = 5000;

  private static final String RECENT_FILE = "recent_file";

  public static final String SUBCALLS = ",\"subcalls\":";
  public static final int MAX_RECENT_FILES = 5;

  protected List<String> recentFiles;
  private String currentFilePath;
  private Preferences preferences;

  public WebGUI() throws IOException {
    super(PORT);
    this.preferences = Preferences.userNodeForPackage(getClass());
    recentFiles = loadRecentFiles();
  }

  @Override
  public NanoHTTPD.Response serve(String uri, String method, Properties header, Properties params, Properties files, InputStream body) {
    String url = uri.substring(1);
    if(url.equals("files/recent")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_JSON, formatRecentFilesAsJson());
    } else if(url.equals("server/shutdown")) {
      System.out.println("Shutdown server");
      System.exit(0);
      return null;
    } else if (url.equals("file/current/path")) {
      if (currentFilePath != null) {
        return new NanoHTTPD.Response(HTTP_OK, MIME_JSON, "{\"path\":\""+currentFilePath+"\"}");
      }
      return new NanoHTTPD.Response(HTTP_BADREQUEST, MIME_PLAINTEXT, "No file already loaded");
    } else if (url.equals("file/current/line")) {
      return new NanoHTTPD.Response(HTTP_OK, MIME_JSON, readLine(Integer.valueOf(params.getProperty("lineNumber"))));
    } else if (url.startsWith("file/")) {
      String filePath = url.substring("file/".length());
      return loadFileAndReturnItAsJson(filePath);
    } else {
      return super.serve(uri, method, header, params, files, body);
    }
  }

  private Response loadFileAndReturnItAsJson(String filePath) {
    File currentFile = new File(filePath);
    if (currentFile.exists() && currentFile.canRead()) {
      currentFilePath = filePath;
      saveRecentFile(filePath);
      return new Response(HTTP_OK, MIME_JSON, readFirstContentAsJSON(currentFile));
    } else {
      return new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT, "Can't read "+filePath);
    }
  }

  private String formatRecentFilesAsJson() {
    StringBuilder buffer = new StringBuilder("[");
    for (int i=0; i<recentFiles.size(); i++) {
      String recentFile = recentFiles.get(i);
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append("{\"path\":\"").append(recentFile).append("\"}");
    }
    buffer.append("]");
    return buffer.toString();
  }

  private String readLine(int lineNumber) {
    if (supportShellCommand()) {
      return lineContentWithShellCommand(lineNumber);
    } else {
      return lineContentWithJava(lineNumber);
    }
  }

  private String lineContentWithJava(int lineNumber) {
    int count = 0;
    FileInputStream fileInputStream = null;
    Scanner sc = null;
    String line = null;
    try {
      fileInputStream = new FileInputStream(new File(currentFilePath));
      sc = new Scanner(fileInputStream, "UTF-8");
      while (sc.hasNextLine() && count!=lineNumber) {
        line = sc.nextLine();
        count++;
      }
    } catch(Exception e) {
      e.printStackTrace();
    } finally {
      if (fileInputStream != null) {
        try {
          fileInputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sc != null) {
        sc.close();
      }
    }
    if (line != null) {
      if(line.startsWith("["))
        return line.substring(1, line.length()-1);
      return line;
    }
    return "{}";
  }

  private String readFirstContentAsJSON(File file) {
    int count = 0;
    int nbLines = -1;
    if (supportShellCommand()) {
      nbLines = nbLinesWithShellCommand(file);
    }
    FileInputStream fileInputStream = null;
    Scanner sc = null;
    StringBuilder buffer = new StringBuilder("[");
    try {
      fileInputStream = new FileInputStream(file);
      sc = new Scanner(fileInputStream, "UTF-8");
//      int max = 100;
      while (sc.hasNextLine()) {
        String line = sc.nextLine();
        count++;
//        if(count>max) break;
        if (buffer.length()>1)
          buffer.append(",");
        if (nbLines >= 0) {
          System.out.print("\rRead line " + count+ " / "+nbLines);
        } else {
          System.out.print("\rRead line " + count);
        }
        if (line != null) {
          String rootCall;
          if (line.contains(SUBCALLS)) {
            rootCall = line.substring(0, line.indexOf(SUBCALLS));
          } else {
            rootCall = line.substring(0, line.indexOf('}'));
          }

          if (rootCall.indexOf(':') >= 0) {
            String[] split = rootCall.split(":");
            buffer.append("{\"lineNumber\":").append(count);
            buffer.append(",\"hasChild\":").append(line.contains(SUBCALLS));
            buffer.append(",\"name\":").append(split[0].startsWith("[") ? split[0].substring(2) : split[0].substring(1));
            buffer.append(",\"duration\":").append(split[1]).append("}");

          } else {
            System.err.println("Failed to analyzed: " + rootCall);
          }
        }
      }
      System.out.println("\nFile is read!");
    } catch(Exception e) {
      e.printStackTrace();
    } finally {
      if (fileInputStream != null) {
        try {
          fileInputStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (sc != null) {
        sc.close();
      }
    }
    buffer.append("]");
    return buffer.toString();
  }

  private boolean supportShellCommand() {
    return System.getProperty("os.name").startsWith("Mac OS")
        || System.getProperty("os.name").startsWith("Linux");
  }

  private int nbLinesWithShellCommand(File file) {
    String output = runShellCommand("cat \"" + file.getAbsolutePath() + "\" | wc -l");
    if (output != null) {
      return  Integer.parseInt(output);
    }
    return -1;
  }

  private String runShellCommand(String command) {
    InputStream commndOutput = null;
    Process cmd;
    try {
      cmd = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});
      commndOutput = cmd.getInputStream();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    try {
      try (Scanner sc = new Scanner(commndOutput, "utf-8")) {
        sc.useDelimiter("\\A");
        if (sc.hasNextLine())
          return sc.nextLine().trim();
        else {
          try (Scanner sc2 = new Scanner(cmd.getErrorStream(), "utf-8")) {
            sc2.useDelimiter("\\A");
            if (sc2.hasNextLine())
              System.out.println("Error :" + sc2.nextLine());
            else {
              System.out.println("No line returned by command");
            }
          }
          System.out.println("No line returned by command");
        }
      }
    } finally {
      try {
        commndOutput.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  private String lineContentWithShellCommand(int lineNumber) {
    return runShellCommand("sed -n '"+lineNumber+"p' "+currentFilePath);
  }

  public static void main(String[] args) {
    try {
      new WebGUI();
//      showGUI();
      while (true) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          e.printStackTrace();
          System.exit(0);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void showGUI() {
    if(java.awt.Desktop.isDesktopSupported() ) {
      java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

      if(desktop.isSupported(java.awt.Desktop.Action.BROWSE) ) {
        try {
          java.net.URI uri = new java.net.URI("http://localhost:"+PORT);
          desktop.browse(uri);

        } catch (URISyntaxException | IOException e) {
          e.printStackTrace();
        }
      }
    }
  }


  private List<String> loadRecentFiles() {
    List<String> files = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      String path = preferences.get(RECENT_FILE + "_" + i, null);
      if (path != null) {
        File f = new File(path);
        if (f.exists() && f.canRead()) {
          files.add(path);
        }
      } else {
        return files;
      }
    }
    return files;
  }



  protected void saveRecentFile(String selectedFile) {
    if (!recentFiles.contains(selectedFile)) {
      if (recentFiles.size() == MAX_RECENT_FILES) {
        recentFiles.remove(MAX_RECENT_FILES - 1);
      }
      recentFiles.add(0, selectedFile);
    } else {
      recentFiles.remove(selectedFile);
      recentFiles.add(0, selectedFile);
    }
    for (int i=0; i<recentFiles.size(); i++) {
      String recentFile = recentFiles.get(i);
      preferences.put(RECENT_FILE + "_" + (i+1), recentFile);
    }
    try {
      preferences.flush();
    } catch (BackingStoreException e) {
      e.printStackTrace();
    }
  }
}