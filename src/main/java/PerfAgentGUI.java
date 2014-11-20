import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * User interface to see results from java agent.
 */
public class PerfAgentGUI extends JFrame implements ActionListener {

  public static final String OPEN = "open";
  private static final Object ROOT = new Object();
  public static final String SUBCALLS = "subcalls";
  public static final String LOCATION = "location";
  public static final String SIZE = "size";
  public static final String FILEPATH = "filepath";
  private static final String RECENTE_FILE = "recent_file";
  private final List<File> recentFiles;
  private DefaultTreeModel treeModel;
  private JTree tree;
  private String filePath = "&lt;none&gt;";
  private final DefaultMutableTreeNode rootNode;
  private JMenuItem recentFilesMenu;

  public PerfAgentGUI() {
    super("Java performance agent");
    Preferences preferences = Preferences.userNodeForPackage(getClass());
    String location = preferences.get(LOCATION, null);
    if(location != null) {
      String[] loc = location.split(",");
      try {
        setLocation(Integer.parseInt(loc[0]), Integer.parseInt(loc[1]));
      } catch(NumberFormatException e) {

      }
    } else {
      setLocationByPlatform(true);
    }
    this.recentFiles = loadRecentFiles(preferences);

    String[] size = preferences.get(SIZE, "800,600").split(",");
    setSize(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Test");
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    setResizable(true);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.put(LOCATION, getLocation().x+","+getLocation().y);
        preferences.put(SIZE, getWidth()+","+getHeight());
        for(int i=1;i<5;i++) {
          if(i<=recentFiles.size()) {
            preferences.put(RECENTE_FILE+"_"+i, recentFiles.get(i-1).getPath());
          } else {
            preferences.remove(RECENTE_FILE + "_" + i);
          }
        }
        System.out.println("pref saved");
      }
    });

    setJMenuBar(createMenu());
    rootNode = new DefaultMutableTreeNode(ROOT);
    treeModel = new DefaultTreeModel(rootNode);
    tree = new JTree(treeModel);
    tree.setCellRenderer(new TreeCellRenderer() {
      @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
          boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node != rootNode) {
          Measure m = (Measure) node.getUserObject();
          return new JLabel(
              "<html>"
                  + "[<font color='"+color(grade(m.timeInMs))+"'>" + m.timeInMs + "ms</font>"
                  + " - <font color='"+color(grade(m.percentage, m.timeInMs))+"'>"+m.percentage+"%</font>]"
                  + "&nbsp;"
                  + "<font color='"+methodcolor(m.timeInMs)+"'>" + m.methodName + "</font>"
                  + "</html>");
        } else {
          return new JLabel("<html><font color=gray>file " + filePath + "</font></html>");
        }
      }

      private String color(int grade) {
        switch(grade) {
        case 5:
          return "#FF0000";
        case 4:
          return "#FF9900";
        case 3:
          return "#FFCC00";
        case 2:
          return "#33CC00";
        case 1:
        default:
          return "#C0C0C0";
        }
      }

      private int grade(long timeInMs) {
        if(timeInMs>1000)
          return 5;
        if(timeInMs>500)
          return 4;
        if(timeInMs>150)
          return 3;
        if(timeInMs>40)
          return 2;
        return 1;
      }

      private int grade(double percent, long timeInMs) {
        if(percent>40)
          return Math.min(5, grade(timeInMs));
        if(percent>20)
          return Math.min(4, grade(timeInMs));
        if(percent>10)
          return Math.min(3, grade(timeInMs));
        if(percent>5)
          return Math.min(2, grade(timeInMs));
        return 1;
      }

      private String methodcolor(long timeInMs) {
        if(timeInMs>1000)
          return "#FF0000";
        if(timeInMs>40)
          return "#000000";
        return "#C0C0C0";
      }
    });
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    getContentPane().add(new JScrollPane(tree));
  }

  private List<File> loadRecentFiles(Preferences preferences) {
    List<File> files = new ArrayList<>();
    for(int i=1; i<=5; i++){
      String path = preferences.get(RECENTE_FILE + "_" + i, null);
      if(path!=null) {
        File f =new File(path);
        if(f.exists() && f.canRead()) {
          files.add(f);
        }
      } else {
        return files;
      }
    }
    return files;
  }

  private JMenuBar createMenu() {
    JMenuBar menu = new JMenuBar();
    JMenu fileMenu = menu.add(new JMenu("File"));
    JMenuItem openMenu = fileMenu.add(new JMenuItem("Open..."));
    openMenu.setActionCommand(OPEN);
    openMenu.addActionListener(this);
    this.recentFilesMenu = fileMenu.add(new JMenu("Recent files"));
    if(recentFiles.size()==0) {
      recentFilesMenu.setEnabled(false);
    } else {
      refreshRecentFileMenu();
    }
    return menu;
  }

  public static void main(String[] args) {
    new PerfAgentGUI().setVisible(true);
  }

  @Override public void actionPerformed(ActionEvent e) {
    switch (e.getActionCommand()) {
    case OPEN:
      Preferences preferences = Preferences.userNodeForPackage(getClass());
      String filePath = preferences.get(FILEPATH, null);
      JFileChooser f = filePath!=null?new JFileChooser(filePath):new JFileChooser();
      if (f.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
        f.setVisible(true);
        File selectedFile = f.getSelectedFile();
        if (selectedFile != null) {
          loadFile(selectedFile);
        }
      }
      break;
    }
  }

  private void loadFile(File selectedFile) {
    if(!recentFiles.contains(selectedFile)) {
      if (recentFiles.size() == 5) {
        recentFiles.remove(4);
      }
      recentFiles.add(0, selectedFile);
    } else {
      recentFiles.remove(selectedFile);
      recentFiles.add(0, selectedFile);
    }
    refreshRecentFileMenu();

    Preferences preferences = Preferences.userNodeForPackage(getClass());
    preferences.put(FILEPATH, selectedFile.getPath());

    try {
      getContentPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      loadContent(selectedFile);
      refreshUI();
    } finally {
      getContentPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
  }

  private void refreshRecentFileMenu() {
    recentFilesMenu.removeAll();
    recentFilesMenu.setEnabled(recentFiles.size() > 0);
    for(File recentFile : recentFiles) {
      final File f = recentFile;
      JMenuItem menuItem = (JMenuItem) recentFilesMenu.add(new JMenuItem(f.getAbsoluteFile().toString()));
      menuItem.addActionListener(new ActionListener() {
        @Override public void actionPerformed(ActionEvent e) {
          loadFile(f);
        }
      });
    }
    recentFilesMenu.updateUI();
  }

  private void refreshUI() {
    treeModel.nodeStructureChanged(rootNode);
    tree.updateUI();
  }

  private void loadContent(File selectedFile) {

      clearContent(false);
      filePath = selectedFile.getAbsolutePath();
      DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new FileReader(selectedFile));
        String line;
        do {
          line = reader.readLine();
          if (line != null) {
            root.add(loadCallTree(line));
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

  private MutableTreeNode loadCallTree(String json) {
    JSONArray jsonArray = JSONArray.fromObject(json);
    JSONObject jsonObject = jsonArray.getJSONObject(0);
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();
    Set<String> keys = jsonObject.keySet();
    for (String key : keys) {
      if (!key.equals(SUBCALLS)) {
        String value = jsonObject.getString((String) key);
        value = value.substring(0, value.length() - 2);
        long duration = Long.parseLong(value);
        Measure userObject = new Measure(key, duration, duration, 100);
        node.setUserObject(userObject);
      }
    }
    if (keys.contains(SUBCALLS)) {
      addCallTreeSubElements(node, jsonObject.getJSONArray(SUBCALLS));
    }
    return node;
  }

  public void resetExpandTree() {
    expandIfNecessary((DefaultMutableTreeNode) treeModel.getRoot());
  }

  private void expandIfNecessary(DefaultMutableTreeNode parent) {
    for (int c = 0, max = parent.getChildCount(); c < max; c++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(c);
      expandIfNecessary(child);
      Measure m = (Measure) child.getUserObject();
      if (m.shouldExpand) {
        tree.expandPath(new TreePath(child.getPath()));
      } else {
        tree.collapsePath(new TreePath(child.getPath()));
      }
    }
  }

  private void addCallTreeSubElements(DefaultMutableTreeNode parent, JSONArray children) {
    Measure parentUserObject = (Measure) parent.getUserObject();
    for (int i = 0, max = children.size(); i < max; i++) {
      JSONObject jsonObject = children.getJSONObject(i);
      DefaultMutableTreeNode node = new DefaultMutableTreeNode();
      Set<String> keys = jsonObject.keySet();
      for (String key : keys) {
        if (!key.equals(SUBCALLS)) {
          String value = jsonObject.getString(key);
          value = value.substring(0, value.length() - 2);
          long timeInMs = Long.parseLong(value);
          Measure userObject = new Measure(key, timeInMs, parentUserObject.rootTotalTimeInMs,
              parentUserObject.rootTotalTimeInMs>0?Math.round(timeInMs*1000d/parentUserObject.rootTotalTimeInMs)/10d:0d);
          node.setUserObject(userObject);
          if (userObject.timeInMs > 10 && userObject.timeInMs > parentUserObject.timeInMs * 0.1) {
            userObject.shouldExpand = true;
          }
        }
      }
      parent.add(node);
      if (keys.contains(SUBCALLS)) {
        addCallTreeSubElements(node, jsonObject.getJSONArray(SUBCALLS));
      }
    }
  }

  private static class Measure {
    double percentage;
    String methodName;
    long timeInMs;
    boolean shouldExpand=false;
    long rootTotalTimeInMs;

    Measure(String methodName, long timeInMs, long rootTotalTimeInMs, double percentage) {
      this.methodName = methodName;
      this.timeInMs = timeInMs;
      this.rootTotalTimeInMs = rootTotalTimeInMs;
      this.percentage = percentage;
    }
  }

  private void clearContent(boolean updateUI) {
    this.tree.clearSelection();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.tree.getModel().getRoot();
    root.removeAllChildren();
    if (updateUI) {
      refreshUI();
    }
  }
}
