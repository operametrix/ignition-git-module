package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import java.awt.*;

/**
 * Designer popup dialog for managing commit author identity (email).
 * Remote credentials are now managed per-remote via {@link RemotesPopup}.
 */
public class CredentialsPopup extends JFrame {

    private JTextField emailField;

    public CredentialsPopup(String currentEmail, Component parent) {
        setTitle("Git Credentials");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        setData(currentEmail);

        pack();
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: label
        JLabel titleLabel = new JLabel("Commit Author");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0, 128, 0));
        main.add(titleLabel, BorderLayout.NORTH);

        // Center: form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        emailField = new JTextField(25);
        formPanel.add(emailField, gbc);

        main.add(formPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton saveBtn = new JButton("Save");
        saveBtn.setBackground(new Color(71, 137, 199));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.addActionListener(e -> {
            if (validateFields()) {
                onSave(emailField.getText().trim());
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    public void setData(String email) {
        emailField.setText(email != null ? email : "");
        pack();
        revalidate();
        repaint();
    }

    private boolean validateFields() {
        String email = emailField.getText().trim();
        if (email.isEmpty() || !email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Called when the user clicks Save and validation passes.
     * Override in an anonymous subclass to handle persistence.
     *
     * @param email the email address entered by the user
     */
    public void onSave(String email) {
    }
}
