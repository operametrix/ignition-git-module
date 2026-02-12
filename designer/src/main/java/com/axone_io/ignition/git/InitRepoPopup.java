package com.axone_io.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Designer popup dialog for initializing a git repository for an unregistered project.
 * Allows the user to enter the repository URI and credentials without touching the Gateway web UI.
 * Dynamically switches between HTTPS and SSH credential fields based on the URI prefix.
 */
public class InitRepoPopup extends JFrame {

    private JTextField repoUriField;
    private JLabel authTypeLabel;
    private JTextField emailField;
    private JTextField gitUsernameField;
    private JPasswordField passwordField;
    private JTextArea sshKeyArea;
    private JPanel httpsPanel;
    private JPanel sshPanel;

    public InitRepoPopup(Component parent) {
        setTitle("Initialize Git Repository");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());

        pack();
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center: form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Repository URI
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Repository URI:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        repoUriField = new JTextField(30);
        repoUriField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateAuthType(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateAuthType(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateAuthType(); }
        });
        formPanel.add(repoUriField, gbc);

        // Email
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        emailField = new JTextField(30);
        formPanel.add(emailField, gbc);

        // Auth type label (after email, hidden until URI is entered)
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        authTypeLabel = new JLabel();
        authTypeLabel.setFont(authTypeLabel.getFont().deriveFont(Font.BOLD));
        authTypeLabel.setForeground(new Color(0, 128, 0));
        authTypeLabel.setVisible(false);
        formPanel.add(authTypeLabel, gbc);

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
        gitUsernameField = new JTextField(30);
        httpsPanel.add(gitUsernameField, hgbc);

        hgbc.gridx = 0;
        hgbc.gridy = 1;
        hgbc.fill = GridBagConstraints.NONE;
        hgbc.weightx = 0;
        httpsPanel.add(new JLabel("Password:"), hgbc);
        hgbc.gridx = 1;
        hgbc.fill = GridBagConstraints.HORIZONTAL;
        hgbc.weightx = 1.0;
        passwordField = new JPasswordField(30);
        httpsPanel.add(passwordField, hgbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(httpsPanel, gbc);

        // SSH panel
        sshPanel = new JPanel(new BorderLayout(5, 5));
        sshPanel.setBorder(BorderFactory.createTitledBorder("SSH Private Key"));
        sshKeyArea = new JTextArea(8, 30);
        sshKeyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sshPanel.add(new JScrollPane(sshKeyArea), BorderLayout.CENTER);

        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(sshPanel, gbc);

        // Default: hide auth panels until URI is entered
        httpsPanel.setVisible(false);
        sshPanel.setVisible(false);

        main.add(formPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton initBtn = new JButton("Initialize");
        initBtn.setBackground(new Color(71, 137, 199));
        initBtn.setForeground(Color.WHITE);
        initBtn.addActionListener(e -> {
            if (validateFields()) {
                String password = new String(passwordField.getPassword());
                onInitialize(repoUriField.getText().trim(), emailField.getText().trim(),
                        gitUsernameField.getText().trim(), password, sshKeyArea.getText().trim());
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(initBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    private void updateAuthType() {
        String uri = repoUriField.getText().trim().toLowerCase();
        boolean hasUri = !uri.isEmpty();
        boolean isHttps = uri.startsWith("http");

        authTypeLabel.setVisible(hasUri);
        authTypeLabel.setText(isHttps ? "HTTPS" : "SSH");
        httpsPanel.setVisible(hasUri && isHttps);
        sshPanel.setVisible(hasUri && !isHttps);
        pack();
        revalidate();
        repaint();
    }

    private boolean validateFields() {
        String uri = repoUriField.getText().trim();
        if (uri.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Repository URI is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

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
            String password = new String(passwordField.getPassword());
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Password is required for HTTPS authentication.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } else {
            String sshKey = sshKeyArea.getText().trim();
            if (sshKey.isEmpty()) {
                JOptionPane.showMessageDialog(this, "SSH private key is required for SSH authentication.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }

        return true;
    }

    /**
     * Called when the user clicks Initialize and validation passes.
     * Override in an anonymous subclass to handle the initialization.
     */
    public void onInitialize(String repoUri, String email, String gitUsername, String password, String sshKey) {
    }
}
