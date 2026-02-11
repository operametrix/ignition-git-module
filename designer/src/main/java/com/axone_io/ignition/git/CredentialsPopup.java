package com.axone_io.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import java.awt.*;

/**
 * Designer popup dialog for managing git user credentials (email, username, password, SSH key).
 * Displays fields appropriate to the project's authentication type (HTTPS or SSH).
 * Follows the same pattern as {@link BranchPopup} — subclass and override {@link #onSave} to
 * handle the save action.
 */
public class CredentialsPopup extends JFrame {

    private JLabel authTypeLabel;
    private JTextField emailField;
    private JTextField gitUsernameField;
    private JPasswordField passwordField;
    private JTextArea sshKeyArea;
    private JPanel httpsPanel;
    private JPanel sshPanel;

    public CredentialsPopup(String authType, String currentEmail, String currentGitUsername, Component parent) {
        setTitle("Git Credentials");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        setData(authType, currentEmail, currentGitUsername);

        pack();
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: auth type label
        authTypeLabel = new JLabel();
        authTypeLabel.setFont(authTypeLabel.getFont().deriveFont(Font.BOLD));
        authTypeLabel.setForeground(new Color(0, 128, 0));
        main.add(authTypeLabel, BorderLayout.NORTH);

        // Center: form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Email (always visible)
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        emailField = new JTextField(25);
        formPanel.add(emailField, gbc);

        // HTTPS panel
        httpsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints hgbc = new GridBagConstraints();
        hgbc.insets = new Insets(4, 5, 4, 5);
        hgbc.anchor = GridBagConstraints.WEST;

        hgbc.gridx = 0;
        hgbc.gridy = 0;
        httpsPanel.add(new JLabel("Git Username:"), hgbc);
        hgbc.gridx = 1;
        hgbc.fill = GridBagConstraints.HORIZONTAL;
        hgbc.weightx = 1.0;
        gitUsernameField = new JTextField(25);
        httpsPanel.add(gitUsernameField, hgbc);

        hgbc.gridx = 0;
        hgbc.gridy = 1;
        hgbc.fill = GridBagConstraints.NONE;
        hgbc.weightx = 0;
        httpsPanel.add(new JLabel("Password:"), hgbc);
        hgbc.gridx = 1;
        hgbc.fill = GridBagConstraints.HORIZONTAL;
        hgbc.weightx = 1.0;
        passwordField = new JPasswordField(25);
        httpsPanel.add(passwordField, hgbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(httpsPanel, gbc);

        // SSH panel
        sshPanel = new JPanel(new BorderLayout(5, 5));
        sshPanel.setBorder(BorderFactory.createTitledBorder("SSH Private Key"));
        sshKeyArea = new JTextArea(8, 25);
        sshKeyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sshPanel.add(new JScrollPane(sshKeyArea), BorderLayout.CENTER);

        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(sshPanel, gbc);

        main.add(formPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton saveBtn = new JButton("Save");
        saveBtn.setBackground(new Color(71, 137, 199));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.addActionListener(e -> {
            if (validateFields()) {
                String password = new String(passwordField.getPassword());
                onSave(emailField.getText().trim(), gitUsernameField.getText().trim(),
                        password, sshKeyArea.getText().trim());
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    public void setData(String authType, String email, String gitUsername) {
        authTypeLabel.setText("Authentication: " + authType);
        emailField.setText(email != null ? email : "");
        gitUsernameField.setText(gitUsername != null ? gitUsername : "");
        passwordField.setText("");
        sshKeyArea.setText("");

        boolean isHttps = "HTTPS".equals(authType);
        httpsPanel.setVisible(isHttps);
        sshPanel.setVisible(!isHttps);

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
        if (httpsPanel.isVisible()) {
            String username = gitUsernameField.getText().trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Git username is required for HTTPS authentication.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return true;
    }

    /**
     * Called when the user clicks Save and validation passes.
     * Override in an anonymous subclass to handle persistence.
     *
     * @param email       the email address entered by the user
     * @param gitUsername  the git username (relevant for HTTPS)
     * @param password     the password (HTTPS); empty if unchanged
     * @param sshKey       the SSH private key (SSH); empty if unchanged
     */
    public void onSave(String email, String gitUsername, String password, String sshKey) {
    }
}
