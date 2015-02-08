/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.isammoc.zooviewer;

import net.isammoc.zooviewer.model.ZVModel;
import net.isammoc.zooviewer.model.ZVModelImpl;
import net.isammoc.zooviewer.node.JZVNode;
import net.isammoc.zooviewer.node.ZVNode;
import net.isammoc.zooviewer.tree.JZVTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class App {

    private static final String DEFAULT_CONNECTION_STRING = "127.0.0.1:2181";

    private static ResourceBundle bundle = ResourceBundle.getBundle(App.class.getCanonicalName());

    private static Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws IOException {
        String zkHost;
        Set<String> savedHosts = getSavedHosts();
        if (args.length > 0) {
            zkHost = args[0];
        } else {
            zkHost = inputConnectionString(savedHosts.toArray(new String[savedHosts.size()]));

            if (zkHost == null) {
                System.err.println(bundle.getString("start.connection.aborted.message"));
                System.exit(2);
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.error("=====> Cannot set platform default look and feel.", e);
        }

        final ZVModel model = new ZVModelImpl(zkHost);
        final JZVNode nodeView = new JZVNode(model);
        final JZVTree tree = new JZVTree(model);

        //If we could connect to zk and host is not in set of saved - update it
        if (!savedHosts.contains(zkHost)) {
            savedHosts.add(zkHost);
            updateSavedHosts(savedHosts);
        }

        String editorViewtitle = String.format("%s - Editor View - ZooViewer", zkHost);

        final JFrame jfEditor = new JFrame(editorViewtitle);
        jfEditor.setName("zv_editor");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), nodeView);
        split.setDividerLocation(0.4);

        jfEditor.getContentPane().add(split);
        jfEditor.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        jfEditor.setSize(1024, 768);
        jfEditor.setLocationRelativeTo(null);

        tree.addTreeSelectionListener(e -> {
            // Create the array of selections
            TreePath[] selPaths = e.getPaths();
            if (selPaths == null) {
                return;
            }
            ZVNode[] nodes = new ZVNode[selPaths.length];
            for (int i = 0; i < selPaths.length; i++) {
                nodes[i] = (ZVNode) selPaths[i].getLastPathComponent();
            }
            nodeView.setNodes(nodes);
        });

        jfEditor.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                jfEditor.dispose();
            }
        });

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            /** */
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTreeCellRendererComponent(JTree tree,
                                                          Object value,
                                                          boolean sel,
                                                          boolean expanded,
                                                          boolean leaf,
                                                          int row,
                                                          boolean hasFocus) {

                Component comp = super.getTreeCellRendererComponent(tree,
                        value, sel, expanded, leaf, row, hasFocus);
                if ((comp instanceof JLabel) && (value instanceof ZVNode)) {
                    ZVNode node = (ZVNode) value;
                    String text = node.getName();
                    byte[] data = node.getData();
                    if ((data != null) && (data.length > 0)) {
                        text += "=" + new String(data);
                    }
                    ((JLabel) comp).setText(text);
                    ((JLabel) comp).validate();
                }
                return comp;
            }
        };
        tree.setCellRenderer(renderer);

        jfEditor.setVisible(true);
    }

    private static String inputConnectionString(String[] possibilities) {
//        String[] possibilities = {"192.168.1.200:2181", "127.0.0.1:2181"};

        JOptionPane pane = new JOptionPane(
            bundle.getString("start.connection.message"),
            JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

        JComboBox<String> comboBox = new JComboBox<>(possibilities);
        comboBox.setEditable(true);
        pane.setMessage(new Object[]{bundle.getString("start.connection.message"),  comboBox});

        pane.createDialog(null, bundle.getString("start.connection.title")).setVisible(true);

        Object inputValue = comboBox.getSelectedItem();
        if (inputValue == null || "".equals(inputValue)) {
            return DEFAULT_CONNECTION_STRING;
        }

        return (String) inputValue;
    }

    protected static Set<String> getSavedHosts() {
        Set<String> result = new HashSet<>();
        try {
            File file = new File("hosts.lst");
            if (!file.exists()) {
                boolean newFile = file.createNewFile();
                if (newFile) {
                    FileWriter defaultValueWriter = new FileWriter(file);
                    defaultValueWriter.write(DEFAULT_CONNECTION_STRING + "\n");
                    defaultValueWriter.flush();
                    defaultValueWriter.close();
                } else {
                    log.warn("=====> Cannot create default file with hosts list.");
                    result.add(DEFAULT_CONNECTION_STRING);
                    return result;
                }
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));
            reader.lines().forEach(result::add);
            reader.close();

        } catch (Throwable t) {
            log.warn("=====> Error reding saved hosts. {}", t.getMessage());
        }
        return result;
    }

    protected static void updateSavedHosts(Set<String> hosts) {
        try {
            File file = new File("hosts.lst");

            if (file.exists()) {
                if (!file.delete()) {
                    log.warn("=====> Cannot delete file to save new, updated.");
                    return;
                }
            }

            FileWriter defaultValueWriter = new FileWriter(file);
            for (String val : hosts) {
                defaultValueWriter.write(val + "\n");
            }
            defaultValueWriter.flush();
            defaultValueWriter.close();


        } catch (Throwable t) {
            log.warn("=====> Error reding saved hosts. {}", t.getMessage());
        }
    }

}
