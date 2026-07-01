package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;
import com.operametrix.ignition.git.utils.IconUtils;

import javax.swing.*;
import java.awt.*;

/**
 * Lightweight popup for creating a new branch from the current HEAD.
 * Just a name field + Create/Cancel buttons — no start point, no advanced options.
 */
public class CreateBranchPopup extends JDialog {

    private final JTextField branchNameField;

    public CreateBranchPopup(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
        IconUtils.setWindowIcon(this, "/com/operametrix/ignition/git/icons/ic_branch.svg");

        setTitle("Create New Branch");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        main.add(new JLabel("Branch Name:"), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 10, 0);
        branchNameField = new JTextField(24);
        main.add(branchNameField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton createBtn = new JButton("Create");
        createBtn.setBackground(new Color(71, 137, 199));
        createBtn.setForeground(Color.WHITE);
        createBtn.addActionListener(e -> handleCreate());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(createBtn);
        buttonPanel.add(cancelBtn);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        main.add(buttonPanel, gbc);

        // Enter key triggers Create
        getRootPane().setDefaultButton(createBtn);

        setContentPane(main);
        pack();
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
        branchNameField.requestFocusInWindow();
    }

    private void handleCreate() {
        String name = branchNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Branch name is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        onCreateBranch(name);
        dispose();
    }

    /** Called when the user clicks Create with a valid branch name. */
    public void onCreateBranch(String branchName) {
    }
}
