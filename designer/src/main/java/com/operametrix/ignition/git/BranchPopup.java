package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BranchPopup extends JDialog {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private JList<String> localBranchList;
    private JList<String> remoteBranchList;
    private DefaultListModel<String> localModel;
    private DefaultListModel<String> remoteModel;
    private String currentBranch = "";

    public BranchPopup(String currentBranch, List<String> localBranches, List<String> remoteBranches, Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/operametrix/ignition/git/icons/ic_branch.svg");
            if (iconStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
                setIconImage(icon.getImage());
            }
        } catch (IOException e) {
            logger.trace(e.toString(), e);
        }

        setTitle("Branch Management");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        setData(currentBranch, localBranches, remoteBranches);

        setSize(600, 420);
        setMinimumSize(new Dimension(500, 350));
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center: branch lists side by side
        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        localModel = new DefaultListModel<>();
        localBranchList = new JList<>(localModel);
        localBranchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        localBranchList.setCellRenderer(new CurrentBranchRenderer());
        attachLocalListHandlers();
        JPanel localPanel = new JPanel(new BorderLayout());
        JButton createBtn = buildIconButton(VectorIcons.get("add"), "Create Branch...", e -> onCreateBranchRequested());
        JButton localRefreshBtn = buildIconButton(VectorIcons.get("refresh"), "Refresh", e -> onRefresh());
        localPanel.add(buildListHeader("Local Branches", createBtn, localRefreshBtn), BorderLayout.NORTH);
        localPanel.add(new JScrollPane(localBranchList), BorderLayout.CENTER);

        remoteModel = new DefaultListModel<>();
        remoteBranchList = new JList<>(remoteModel);
        remoteBranchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        attachRemoteListHandlers();
        JPanel remotePanel = new JPanel(new BorderLayout());
        JButton remoteRefreshBtn = buildIconButton(VectorIcons.get("refresh"), "Refresh", e -> onRefreshFromRemote());
        remotePanel.add(buildListHeader("Remote Branches", remoteRefreshBtn), BorderLayout.NORTH);
        remotePanel.add(new JScrollPane(remoteBranchList), BorderLayout.CENTER);

        // Mutual exclusivity on selection
        localBranchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && localBranchList.getSelectedIndex() >= 0) {
                remoteBranchList.clearSelection();
            }
        });
        remoteBranchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && remoteBranchList.getSelectedIndex() >= 0) {
                localBranchList.clearSelection();
            }
        });

        listsPanel.add(localPanel);
        listsPanel.add(remotePanel);
        main.add(listsPanel, BorderLayout.CENTER);

        return main;
    }

    private void attachLocalListHandlers() {
        localBranchList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    String selected = localBranchList.getSelectedValue();
                    if (selected != null && !selected.equals(currentBranch)) {
                        onCheckoutBranch(selected);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = localBranchList.locationToIndex(e.getPoint());
                if (index < 0) return;
                localBranchList.setSelectedIndex(index);
                String branch = localModel.getElementAt(index);

                JPopupMenu menu = new JPopupMenu();

                JMenuItem checkoutItem = new JMenuItem("Checkout");
                checkoutItem.setEnabled(!branch.equals(currentBranch));
                checkoutItem.addActionListener(a -> onCheckoutBranch(branch));
                menu.add(checkoutItem);

                menu.addSeparator();

                JMenuItem deleteItem = new JMenuItem("Delete");
                deleteItem.setForeground(new Color(0xCC0000));
                deleteItem.setEnabled(!branch.equals(currentBranch));
                deleteItem.addActionListener(a -> {
                    int confirm = JOptionPane.showConfirmDialog(BranchPopup.this,
                            "Are you sure you want to delete branch '" + branch + "'?",
                            "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm == JOptionPane.YES_OPTION) {
                        onDeleteBranch(branch);
                    }
                });
                menu.add(deleteItem);

                menu.show(localBranchList, e.getX(), e.getY());
            }
        });
    }

    private void attachRemoteListHandlers() {
        remoteBranchList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    String branch = stripRemotePrefix(remoteBranchList.getSelectedValue());
                    if (branch != null && !branch.equals(currentBranch)) {
                        onCheckoutBranch(branch);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = remoteBranchList.locationToIndex(e.getPoint());
                if (index < 0) return;
                remoteBranchList.setSelectedIndex(index);
                String branch = stripRemotePrefix(remoteModel.getElementAt(index));
                if (branch == null) return;

                JPopupMenu menu = new JPopupMenu();
                JMenuItem checkoutItem = new JMenuItem("Checkout");
                checkoutItem.setEnabled(!branch.equals(currentBranch));
                checkoutItem.addActionListener(a -> onCheckoutBranch(branch));
                menu.add(checkoutItem);

                menu.show(remoteBranchList, e.getX(), e.getY());
            }
        });
    }

    private String stripRemotePrefix(String remote) {
        if (remote == null) return null;
        if (remote.startsWith("origin/")) return remote.substring("origin/".length());
        return remote;
    }

    private JPanel buildListHeader(String title, JButton... trailingButtons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        header.setBackground(UIManager.getColor("Panel.background"));

        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        header.add(label, BorderLayout.WEST);

        JPanel buttonBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonBox.setOpaque(false);
        for (JButton btn : trailingButtons) {
            buttonBox.add(btn);
        }
        header.add(buttonBox, BorderLayout.EAST);

        return header;
    }

    private JButton buildIconButton(Icon icon, String tooltip, java.awt.event.ActionListener action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(2, 2, 2, 2));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBorderPainted(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
            }
        });
        button.addActionListener(action);
        return button;
    }

    public void setData(String currentBranch, List<String> localBranches, List<String> remoteBranches) {
        this.currentBranch = currentBranch != null ? currentBranch : "";

        localModel.clear();
        for (String b : localBranches) {
            localModel.addElement(b);
        }

        remoteModel.clear();
        for (String b : remoteBranches) {
            remoteModel.addElement(b);
        }

        localBranchList.repaint();
    }

    /**
     * Renders the current branch with a bold "* " prefix in green.
     */
    private class CurrentBranchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String branch = value != null ? value.toString() : "";
            if (branch.equals(currentBranch)) {
                setFont(getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    setBackground(new Color(0xE8F0FE));
                }
            }
            return this;
        }
    }

    public void onCheckoutBranch(String branchName) {
    }

    /** Called when the user clicks "Create Branch..." — caller should open CreateBranchPopup. */
    public void onCreateBranchRequested() {
    }

    public void onDeleteBranch(String branchName) {
    }

    public void onRefresh() {
    }

    /** Called when the user clicks the Refresh button — fetches from remote first. */
    public void onRefreshFromRemote() {
    }
}
