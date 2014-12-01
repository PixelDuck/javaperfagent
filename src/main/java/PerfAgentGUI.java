import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * User interface to see results from java agent.
 * @author olmartin
 */
public class PerfAgentGUI extends JFrame implements ActionListener, MouseListener {
  public static final String OPEN = "open";
  private static final Object ROOT = new Object();
  public static final String SUBCALLS = "subcalls";
  public static final String LOCATION = "location";
  public static final String SIZE = "size";
  public static final String FILEPATH = "filepath";
  private static final String RECENT_FILE = "recent_file";
  private List<File> recentFiles;
  private DefaultTreeModel treeModel;
  private JTree tree;
  private String filePath = "&lt;none&gt;";
  private DefaultMutableTreeNode rootNode;
  private JMenuItem recentFilesMenu;
  private JPopupMenu popupMenu;

  public PerfAgentGUI() {
    super("Java performance agent");

    setResizable(true);
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    loadAndApplyPreferences();
    fixLookAndFeel();
    initiatWindowListener();

    createPopupMenu();
    createMenuBar();
    createTree();

    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    getContentPane().add(new JScrollPane(tree));
  }

  private void createTree() {

    rootNode = new DefaultMutableTreeNode(ROOT);
    treeModel = new DefaultTreeModel(rootNode);
    tree = new JTree(treeModel);
    tree.setCellRenderer(new MyTreeCellRenderer());

    tree.addMouseListener(this);
  }

  private void initiatWindowListener() {
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.put(LOCATION, getLocation().x + "," + getLocation().y);
        preferences.put(SIZE, getWidth() + "," + getHeight());
        for (int i = 1; i < 5; i++) {
          if (i <= recentFiles.size()) {
            preferences.put(RECENT_FILE + "_" + i, recentFiles.get(i - 1).getPath());
          } else {
            preferences.remove(RECENT_FILE + "_" + i);
          }
        }
        System.out.println("pref saved");
      }
    });
  }

  private void fixLookAndFeel() {
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Java performance agent");
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void loadAndApplyPreferences() {
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
  }

  private void createPopupMenu() {
    popupMenu = new JPopupMenu();
    popupMenu.add(new JMenuItem("Compute stats for selected rows...")).addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        TreePath[] selectionPaths = tree.getSelectionPaths();
        double duration = 0d;
        if(selectionPaths != null) {
          for (TreePath treePath : selectionPaths) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) treePath.getLastPathComponent();
            Measure measure = (Measure) selectedNode.getUserObject();
            duration += measure.duration;
          }
          showInfo(duration + "ms", "Stats for selection");
        }
      }
    });
    popupMenu.add(new JMenuItem("Copy method name to clipboard")).addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        Measure measure = (Measure) selectedNode.getUserObject();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(measure.methodName), null);
      }
    });
    popupMenu.add(new JPopupMenu.Separator());
    popupMenu.add(new JMenuItem("Copy call stack to clipboard")).addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        Measure measure = (Measure) selectedNode.getUserObject();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("["+measure.toJson()+"]"), null);
      }
    });
    popupMenu.add(new JMenuItem("Copy call stack to file...")).addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        Measure measure = (Measure) selectedNode.getUserObject();
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String filePath = preferences.get(FILEPATH, null);
        JFileChooser f = filePath!=null?new JFileChooser(filePath):new JFileChooser();
        if (f.showSaveDialog(PerfAgentGUI.this) == JFileChooser.APPROVE_OPTION) {
          f.setVisible(true);
          File selectedFile = f.getSelectedFile();
          if (selectedFile != null) {
            if(selectedFile.exists() && selectedFile.canWrite()) {
              if(selectedFile.delete()) {
                showAlert("Failed to delete "+selectedFile, "Error");
                return ;
              }
            }
            try (FileWriter fileWriter = new FileWriter(selectedFile)) {
              fileWriter.write("["+measure.toJson()+"]");
              showInfo("Stack saved to " + selectedFile, "");
            } catch (IOException e1) {
              e1.printStackTrace();
            }
          }
        }
      }
    });
  }

  private void showInfo(String message, String title) {
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
  }

  private void showAlert(String message, String title) {
    JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (SwingUtilities.isRightMouseButton(e)) {
//      int row = tree.getClosestRowForLocation(e.getX(), e.getY());
//      tree.setSelectionRow(row);
      popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }
  }

  @Override public void mousePressed(MouseEvent e) {

  }

  @Override public void mouseReleased(MouseEvent e) {

  }

  @Override public void mouseEntered(MouseEvent e) {

  }

  @Override public void mouseExited(MouseEvent e) {

  }

  private void expandAll(JTree tree, TreePath parent) {
    TreeNode node = (TreeNode) parent.getLastPathComponent();
    if (node.getChildCount() >= 0) {
      for (Enumeration e = node.children(); e.hasMoreElements();) {
        TreeNode n = (TreeNode) e.nextElement();
        TreePath path = parent.pathByAddingChild(n);
        expandAll(tree, path);
      }
    }
    tree.expandPath(parent);
    // tree.collapsePath(parent);
  }

  private List<File> loadRecentFiles(Preferences preferences) {
    List<File> files = new ArrayList<>();
    for(int i=1; i<=5; i++){
      String path = preferences.get(RECENT_FILE + "_" + i, null);
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

  private void createMenuBar() {
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
    setJMenuBar(menu);
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
      } catch (IOException | ParseException e) {
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

  private MutableTreeNode loadCallTree(String json) throws ParseException {
    JSONParser p = new JSONParser(JSONParser.MODE_JSON_SIMPLE);
    JSONArray jsonArray = (JSONArray)p.parse(json);
//    JSONArray jsonArray = new JSONArray(json);
    JSONObject jsonObject = (JSONObject) jsonArray.get(0);
    DefaultMutableTreeNode node = new DefaultMutableTreeNode();

    Set<String> keys = jsonObject.keySet();
    for (String key : keys)
      if (!key.equals(SUBCALLS)) {
        String value = (String) jsonObject.get(key);
        value = value.substring(0, value.length() - 2);
        double duration = Double.parseDouble(value);
        Measure userObject = new Measure(key, duration, duration, 100, jsonObject);
        node.setUserObject(userObject);
      }
    if (keys.contains(SUBCALLS)) {
      addCallTreeSubElements(node, (JSONArray) jsonObject.get(SUBCALLS));
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
      JSONObject jsonObject = (JSONObject) children.get(i);
      DefaultMutableTreeNode node = new DefaultMutableTreeNode();
      Set<String> keys = jsonObject.keySet();
      for (String key : keys) {
        if (!key.equals(SUBCALLS)) {
          String value = (String) jsonObject.get(key);
          value = value.substring(0, value.length() - 2);
          double duration = Double.parseDouble(value);
          Measure userObject = new Measure(key, duration, parentUserObject.totalDuration,
              parentUserObject.totalDuration >0?Math.round(duration*1000d/parentUserObject.totalDuration)/10d:0d, jsonObject);
          node.setUserObject(userObject);
          if (userObject.duration > 10 && userObject.duration > parentUserObject.duration * 0.1) {
            userObject.shouldExpand = true;
          }
        }
      }
      parent.add(node);
      if (keys.contains(SUBCALLS)) {
        addCallTreeSubElements(node, (JSONArray) jsonObject.get(SUBCALLS));
      }
    }
  }

  private static class Measure {
    private final JSONObject jsonObject;
    double percentage;
    String methodName;
    double duration;
    boolean shouldExpand=false;
    double totalDuration;

    Measure(String methodName, double duration, double totalDuration, double percentage, JSONObject jsonObject) {
      this.methodName = methodName;
      this.duration = duration;
      this.totalDuration = totalDuration;
      this.percentage = percentage;
      this.jsonObject = jsonObject;
    }

    public String toJson() {
      return jsonObject.toString();
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

  private class MyTreeCellRenderer implements TreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row,
        boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
      if (node != rootNode) {
        Measure m = (Measure) node.getUserObject();
        JLabel jLabel = new JLabel(
            "<html><body style='background-color:"+(selected? "#FFFFBB":"#FFFFFF")+"'>"
                + "[<font style='color:" + color(grade(m.duration)) + "'>" + m.duration + "ms</font>"
                + " - <font style=';color:" + color(grade(m.percentage, m.duration)) + "'>" + m.percentage + "</font>]"
                + "&nbsp;"
                + "<font style='color:" + methodcolor(m.duration) + "'>" + m.methodName + "</font>"
                + "</body></html>");
        jLabel.setOpaque(true);
        return jLabel;
      } else {
        return new JLabel("<html><font color='gray'>file " + filePath + "</font></html>");
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

    private int grade(double duration) {
      if(duration>1000d)
        return 5;
      if(duration>500d)
        return 4;
      if(duration>150d)
        return 3;
      if(duration>40d)
        return 2;
      return 1;
    }

    private int grade(double percent, double duration) {
      if(percent>40d)
        return Math.min(5, grade(duration));
      if(percent>20d)
        return Math.min(4, grade(duration));
      if(percent>10d)
        return Math.min(3, grade(duration));
      if(percent>5d)
        return Math.min(2, grade(duration));
      return 1;
    }

    private String methodcolor(double duration) {
      if(duration>1000d)
        return "#FF0000";
      if(duration>40d)
        return "#000000";
      return "#C0C0C0";
    }
  }
}
