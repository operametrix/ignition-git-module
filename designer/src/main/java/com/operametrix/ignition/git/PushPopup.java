package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Lightweight popup for selecting which remote to push to.
 * Only shown when the project has multiple remotes; single-remote
 * projects skip this popup entirely and push immediately.
 */
public class PushPopup extends JFrame {

    private JComboBox<String> remoteCombo;

    public PushPopup(Component parent) {
        setTitle("Push");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());

        pack();
        setMinimumSize(getPreferredSize());
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center: remote selector
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        formPanel.add(new JLabel("Remote:"));
        remoteCombo = new JComboBox<>();
        remoteCombo.setPreferredSize(new Dimension(180, 24));
        formPanel.add(remoteCombo);

        main.add(formPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton pushBtn = new JButton("Push");
        pushBtn.setBackground(new Color(71, 137, 199));
        pushBtn.setForeground(Color.WHITE);
        pushBtn.addActionListener(e -> {
            String selectedRemote = (String) remoteCombo.getSelectedItem();
            onPush(selectedRemote != null ? selectedRemote : "origin");
            dispose();
        });

        buttonPanel.add(cancelBtn);
        buttonPanel.add(pushBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    public void setRemotes(List<String> remoteNames) {
        remoteCombo.removeAllItems();
        for (String name : remoteNames) {
            remoteCombo.addItem(name);
        }
        // Pre-select "origin" if present
        for (int i = 0; i < remoteCombo.getItemCount(); i++) {
            if ("origin".equals(remoteCombo.getItemAt(i))) {
                remoteCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Called when the user clicks "Push".
     * Override in an anonymous subclass to handle the push operation.
     */
    public void onPush(String remoteName) {
    }
}
