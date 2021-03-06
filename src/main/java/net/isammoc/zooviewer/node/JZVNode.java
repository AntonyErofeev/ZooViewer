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
package net.isammoc.zooviewer.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.isammoc.zooviewer.model.ZVModel;
import net.isammoc.zooviewer.model.ZVModelListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ResourceBundle;

/**
 * Editor panel for a node.
 * 
 * @author franck
 */
public class JZVNode extends JPanel {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Border BEVEL_LOWERED_BORDER = BorderFactory.createBevelBorder(BevelBorder.LOWERED);

    private static final String ADD_CHILD_NODE_KEY = "btn.add.child";
    private static final String UPDATE_NODE_KEY = "btn.update";
    private static final String DELETE_NODE_KEY = "btn.delete";

    /** */
    private static final long serialVersionUID = 1L;

    private static final ResourceBundle bundle = ResourceBundle.getBundle(JZVNode.class.getCanonicalName());

    private final TitledBorder titleBorder = BorderFactory.createTitledBorder("-");

    private ZVNode[] nodes;
    private final ZVModel model;

    private final JButton jbNewChild = new JButton();
    private final JButton jbUpdate = new JButton();
    private final JButton jbDelete = new JButton();

    private final JTextArea taChildData = new JTextArea();
    private final JTextField jtfChildName = new JTextField();
    private final JZVStat jzvStat = new JZVStat();
    private final JTextArea taUpdate = new JTextArea();

    private Action addChildAction = null;
    private Action updateAction = null;
    private Action deleteAction = null;

    private JPanel nodePanel = null;
    private JPanel deletePanel = null;
    private JPanel statsPanel = null;
    private JPanel dataPanel = null;
    private JPanel newChildPanel = null;

    private final PropertyChangeListener propertyListener = evt -> updateView();

    /**
     * Constructs a new editor panel.
     * 
     * @param model
     *            the model
     */
    public JZVNode(ZVModel model) {
        super(new BorderLayout());

        this.model = model;
        this.model.addModelListener(new RefreshZVModelListener());

        // Components
        this.taChildData.setBorder(BEVEL_LOWERED_BORDER);
        this.taChildData.setRows(2);
        this.taChildData.setFont(new Font("Sans-Serif", Font.PLAIN, 14));
        this.taChildData.setAutoscrolls(true);

        this.taUpdate.setBorder(BEVEL_LOWERED_BORDER);
        this.taUpdate.setRows(2);
        this.taUpdate.setFont(new Font("Sans-Serif", Font.PLAIN, 14));

        // Actions
        this.jbDelete.setAction(getDeleteAction());
        this.jbNewChild.setAction(getAddChildAction());
        this.jbUpdate.setAction(getUpdateAction());

        // Main content
        this.add(getNodePanel());
        this.updateView();
        
        Dimension prefSize = this.jbNewChild.getPreferredSize();
        this.jbDelete.setPreferredSize( prefSize );
        this.jbNewChild.setPreferredSize( prefSize );
        this.jbUpdate.setPreferredSize( prefSize );
        
        initListeners();
    }

    /**
     * Returns the main node panel.
     * 
     * @return the panel
     */
    private JPanel getNodePanel() {
        if (nodePanel == null) {
            nodePanel = new JPanel(new GridBagLayout());
            
            // Sub-panels
            int row = 0;
            nodePanel.add( 
                    getDeletePanel(),
                    new GridBagConstraints(0, row, 1, 1, 1, 1,
                            GridBagConstraints.WEST, GridBagConstraints.BOTH,
                            new Insets(2, 2, 2, 2), 0, 0));
            nodePanel.add(
                    getStatsPanel(),
                    new GridBagConstraints(1, row++, 4, 1, 1, 1,
                            GridBagConstraints.WEST, GridBagConstraints.BOTH,
                            new Insets(2, 2, 2, 2), 0, 0));
    
            nodePanel.add(
                    getDataPanel(),
                    new GridBagConstraints(0, row++, 5, 1, 1, 1,
                            GridBagConstraints.WEST, GridBagConstraints.BOTH,
                            new Insets(2, 2, 2, 2), 0, 0));
    
            nodePanel.add(
                    getNewChildPanel(),
                    new GridBagConstraints(0, row, 5, 1, 1, 1,
                            GridBagConstraints.WEST, GridBagConstraints.BOTH,
                            new Insets(2, 2, 2, 2), 0, 0));
        }
        return nodePanel;
    }

    private JPanel getDeletePanel() {
        if (deletePanel == null) {
            deletePanel = new JPanel(new GridBagLayout());
            deletePanel.setBorder(this.titleBorder);
            deletePanel.add(this.jbDelete, new GridBagConstraints(0, 0, 1, 1,
                    1, 1, GridBagConstraints.SOUTHWEST,
                    GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));
        }
        return deletePanel;
    }

    /**
     * Returns the panel displaying the node stats.
     * 
     * @return the panel
     */
    private JPanel getStatsPanel() {
        if (statsPanel == null) {
            statsPanel = new JPanel(new BorderLayout());
            statsPanel.setBorder(BorderFactory.createTitledBorder(bundle
                    .getString("pnl.stat")));
            statsPanel.add(this.jzvStat);
        }
        return statsPanel;
    }

    private JPanel getDataPanel() {
        if (dataPanel == null) {
            dataPanel = new JPanel(new GridBagLayout());
            dataPanel.setBorder(BorderFactory.createTitledBorder(bundle
                    .getString("pnl.data")));
            dataPanel.add(this.jbUpdate, new GridBagConstraints(0, 0, 1, 1,
                    0, 0, GridBagConstraints.SOUTHWEST,
                    GridBagConstraints.NONE, new Insets(2, 2, 2, 2), 0, 0));
            dataPanel.add(this.taUpdate, new GridBagConstraints(1, 0, 1, 1,
                    1, .5, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                    new Insets(2, 2, 2, 2), 0, 0));
        }
        return dataPanel;
    }

    private JPanel getNewChildPanel() {
        if (newChildPanel == null) {
            newChildPanel = new JPanel(new GridBagLayout());
            newChildPanel.setBorder(BorderFactory.createTitledBorder(bundle
                    .getString("pnl.new.child")));
            newChildPanel.add(
                    new JLabel(bundle.getString("pnl.new.child.lbl.name")),
                    new GridBagConstraints(0, 0, 1, 1, 0, 0,
                            GridBagConstraints.WEST,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2,
                                    2), 0, 0));
            newChildPanel.add(this.jtfChildName,
                    new GridBagConstraints(1, 0, 1, 1, 1, .2,
                            GridBagConstraints.NORTHWEST,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2,
                                    2), 0, 0));
            newChildPanel.add(
                    new JLabel(bundle.getString("pnl.new.child.lbl.data")),
                    new GridBagConstraints(0, 1, 1, 1, 0, 0,
                            GridBagConstraints.NORTHWEST,
                            GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2,
                                    2), 0, 0));
            newChildPanel.add(this.taChildData, new GridBagConstraints(1, 1, 1,
                    2, 1, 1, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                    new Insets(2, 2, 2, 2), 0, 0));
            
            newChildPanel.add(this.jbNewChild, new GridBagConstraints(0, 1, 1,
                    2, 0, 0, GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE,
                    new Insets(2, 2, 2, 2), 0, 0));
        }
        return newChildPanel;
    }

    /**
     * Returns the 'Add child' action.
     * 
     * @return the action
     */
    @SuppressWarnings("serial")
    private Action getAddChildAction() {
        if (addChildAction == null) {
            String actionCommand = bundle.getString(ADD_CHILD_NODE_KEY);
            String actionKey = bundle.getString(ADD_CHILD_NODE_KEY + ".action");
            addChildAction = new AbstractAction(actionCommand) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("actionPerformed(): action = "
                        + e.getActionCommand());
                    if (checkAction()) {
                        model.addNode(nodes[0].getPath() + "/" + jtfChildName.getText(), taChildData.getText().getBytes());
                    }
                }

                private boolean checkAction() {
                    // No node or several nodes selected
                    if (nodes == null || nodes.length > 1) {
                        return false;
                    }
                    // Emptry node name
                    if (jtfChildName.getText().isEmpty()) {
                        JOptionPane.showMessageDialog(JZVNode.this,
                                bundle.getString("dlg.error.addWithoutName"),
                                bundle.getString("dlg.error.title"),
                                JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    // No parent
                    if (nodes.length != 1) {
                        JOptionPane.showMessageDialog(JZVNode.this,
                                bundle.getString("dlg.error.addWithoutParent"),
                                bundle.getString("dlg.error.title"),
                                JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    return true;
                }
            };
            addChildAction.putValue(Action.ACTION_COMMAND_KEY, actionKey);
        }
        return this.addChildAction;
    }

    /**
     * Returns the 'Update' action.
     * 
     * @return the action
     */
    @SuppressWarnings("serial")
    private Action getUpdateAction() {
        if (updateAction == null) {
            String actionCommand = bundle.getString(UPDATE_NODE_KEY);
            String actionKey = bundle.getString(UPDATE_NODE_KEY + ".action");
            updateAction = new AbstractAction(actionCommand) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("actionPerformed(): action = {}", e.getActionCommand());
                    if (checkAction()) {
                        model.updateData(nodes[0].getPath(), taUpdate.getText().getBytes());
                    }
                }

                private boolean checkAction() {
                    // No node or several nodes selected
                    if (nodes == null || nodes.length > 1) {
                        return false;
                    }
                    // No parent
                    if (nodes.length != 1) {
                        JOptionPane.showMessageDialog(JZVNode.this, bundle
                                .getString("dlg.error.updateWithoutParent"),
                                bundle.getString("dlg.error.title"),
                                JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    return true;
                }
            };
            updateAction.putValue(Action.ACTION_COMMAND_KEY, actionKey);
        }
        return updateAction;
    }

    /**
     * Returns the 'Delete node(s)' action.
     * <p>
     * The action is created and mapped to the [Delete] key stroke
     * </p>
     * 
     * @return the action
     */
    @SuppressWarnings("serial")
    private Action getDeleteAction() {
        if (this.deleteAction == null) {
            String actionCommand = bundle.getString(DELETE_NODE_KEY);
            String actionKey = bundle.getString(DELETE_NODE_KEY + ".action");
            this.deleteAction = new AbstractAction(actionCommand) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    log.info("actionPerformed(): action = "
                        + e.getActionCommand());
                    if (checkAction()) {
                        // Checks if several nodes will be deleted
                        if (nodes.length > 1) {
                            model.deleteNodes(nodes);
                        } else {
                            model.deleteNode(nodes[0]);
                        }
                    }
                }

                private boolean checkAction() {
                    // No node selected
                    if (nodes == null) {
                        JOptionPane.showMessageDialog(JZVNode.this, bundle
                                .getString("dlg.error.deleteWithoutSelection"),
                                bundle.getString("dlg.error.title"),
                                JOptionPane.ERROR_MESSAGE);
                        return false;
                    }
                    return true;
                }
            };
            this.deleteAction.putValue(Action.ACTION_COMMAND_KEY, actionKey);

            this.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), actionKey);
            this.getActionMap().put(actionKey, this.deleteAction);
        }
        return this.deleteAction;
    }

    /**
     * Defines the list of selected nodes.
     * 
     * @param nodes
     *            the selected nodes
     */
    public void setNodes(ZVNode[] nodes) {
        if (this.nodes != null) {
            for (ZVNode node : this.nodes) {
                node.removePropertyChangeListener(ZVNode.PROPERTY_EXISTS, this.propertyListener);
            }
        }
        this.nodes = nodes;
        if (this.nodes != null) {
            for (ZVNode node : this.nodes) {
                node.addPropertyChangeListener(ZVNode.PROPERTY_EXISTS, this.propertyListener);
            }
        }
        this.updateView();
    }

    private void initListeners() {
        taUpdate.getDocument().addDocumentListener( new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                enableAction(e);
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                enableAction(e);
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                enableAction(e);
            }
            private void enableAction(DocumentEvent e) {
                boolean enabled = e.getDocument().getLength() > 0;
                getUpdateAction().setEnabled( enabled  );
            }
        });
        jtfChildName.getDocument().addDocumentListener( new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                log.info(".removeUpdate()");
                enableAction(e);
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                log.info(".insertUpdate()");
                enableAction(e);
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                log.info(".changedUpdate()");
                enableAction(e);
            }
            private void enableAction(DocumentEvent e) {
                int docLength = e.getDocument().getLength();
                boolean enabled;
                try {
                    enabled = ( docLength > 0 ) && !e.getDocument().getText(0, docLength).trim().equals("");
                    getAddChildAction().setEnabled( enabled  );
                } catch (BadLocationException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Updates the view.
     * <p>
     * If a node is selected, its data & stats are displayed. If no node is
     * selected (or several nodes are selected), the view is cleared.
     * </p>
     */
    private void updateView() {
        if (this.nodes == null || this.nodes.length > 1 || !this.nodes[0].exists()) {
            this.titleBorder.setTitle("-");
            this.jzvStat.setStat(null);
            this.taUpdate.setText("");
            this.taChildData.setText("");
            this.jbUpdate.setEnabled(false);
            this.jbNewChild.setEnabled(false);
            this.jbDelete.setEnabled(this.nodes != null);
        } else {
            this.titleBorder.setTitle(this.nodes[0].getPath());
            this.jzvStat.setStat(this.nodes[0].getStat());
            byte[] data = this.nodes[0].getData();
            if (data == null || data.length == 0) {
                this.taUpdate.setText("No data in node.");
            } else {
                String text = new String(data).trim();
                if (text.startsWith("{")) { //probably node data is json. Lets format it
                    try {
                        JsonNode tree = mapper.readTree(text);
                        this.taUpdate.setText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
                    } catch (Exception ex) {
                        log.warn("=====> String starts with { symbol but cannot be parsed as json. Using plain. Error: {}", ex.getMessage());
                        this.taUpdate.setText(text);
                    }
                } else {
                    this.taUpdate.setText(text);
                }
            }

            this.taChildData.setText("");
            this.jbUpdate.setEnabled( !this.taUpdate.getText().trim().equals("") );
            this.jbNewChild.setEnabled( !this.jtfChildName.getText().trim().equals("") );
            this.jbDelete.setEnabled(true);
        }
        this.repaint();
    }

    /**
     * Class managing events in order to update the view.
     */
    private final class RefreshZVModelListener implements ZVModelListener {
        @Override
        public void nodeDeleted(ZVNode oldNode, int oldIndex) {
            if (nodes != null) {
                for (ZVNode node : nodes) {
                    if ((node == oldNode) || (node == model.getParent(oldNode))) {
                        updateView();
                        break;
                    }
                }
            }
        }

        @Override
        public void nodeDataChanged(ZVNode node) {
            boolean updateView = false;
            if (nodes != null) {
                for (ZVNode node1 : nodes) {
                    if (node1 == node) {
                        updateView = true;
                    }
                }
            }
            if (updateView) {
                updateView();
            }
        }

        @Override
        public void nodeCreated(ZVNode newNode) {
            boolean updateView = false;
            if (nodes != null) {
                for (ZVNode node : nodes) {
                    if (node == newNode) {
                        updateView = true;
                    }
                }
            }
            if (updateView) {
                updateView();
            }
        }
    }
}