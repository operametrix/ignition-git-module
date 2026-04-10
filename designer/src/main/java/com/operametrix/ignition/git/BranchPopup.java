package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class BranchPopup extends JDialog {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private JLabel currentBranchLabel;
    private JList<String> localBranchList;
    private JList<String> remoteBranchList;
    private DefaultListModel<String> localModel;
    private DefaultListModel<String> remoteModel;

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

        // Top: current branch
        currentBranchLabel = new JLabel("Current Branch: ");
        currentBranchLabel.setFont(currentBranchLabel.getFont().deriveFont(Font.BOLD));
        currentBranchLabel.setForeground(new Color(0, 128, 0));
        main.add(currentBranchLabel, BorderLayout.NORTH);

        // Center: branch lists side by side
        JPanel listsPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        localModel = new DefaultListModel<>();
        localBranchList = new JList<>(localModel);
        localBranchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel localPanel = new JPanel(new BorderLayout());
        localPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Local Branches",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                null, null));
        localPanel.add(new JScrollPane(localBranchList), BorderLayout.CENTER);

        remoteModel = new DefaultListModel<>();
        remoteBranchList = new JList<>(remoteModel);
        remoteBranchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel remotePanel = new JPanel(new BorderLayout());
        remotePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Remote Branches",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                null, null));
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

        // Bottom: button row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton checkoutBtn = new JButton("Checkout");
        checkoutBtn.setBackground(new Color(71, 137, 199));
        checkoutBtn.setForeground(Color.WHITE);
        checkoutBtn.addActionListener(e -> {
            String selected = getSelectedBranch();
            if (selected != null) {
                onCheckoutBranch(selected);
            }
        });

        JButton createBtn = new JButton("Create Branch...");
        createBtn.setBackground(new Color(71, 137, 199));
        createBtn.setForeground(Color.WHITE);
        createBtn.addActionListener(e -> onCreateBranchRequested());

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setBackground(new Color(199, 71, 71));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.addActionListener(e -> {
            String selected = localBranchList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to delete branch '" + selected + "'?",
                        "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm == JOptionPane.YES_OPTION) {
                    onDeleteBranch(selected);
                }
            }
        });

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> onRefreshFromRemote());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(checkoutBtn);
        buttonPanel.add(createBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    private String getSelectedBranch() {
        String local = localBranchList.getSelectedValue();
        if (local != null) return local;
        String remote = remoteBranchList.getSelectedValue();
        if (remote != null) {
            // Strip "origin/" prefix for checkout
            if (remote.startsWith("origin/")) {
                return remote.substring("origin/".length());
            }
            return remote;
        }
        return null;
    }

    public void setData(String currentBranch, List<String> localBranches, List<String> remoteBranches) {
        currentBranchLabel.setText("Current Branch: " + currentBranch);

        localModel.clear();
        for (String b : localBranches) {
            localModel.addElement(b);
        }

        remoteModel.clear();
        for (String b : remoteBranches) {
            remoteModel.addElement(b);
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
