package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Designer popup dialog for initializing a git repository for an unregistered project.
 * Wizard-style with three cards:
 *   Card 1 "Choose"  — asks whether the user has a remote repository
 *   Card 2a "Remote" — existing clone flow (URI + credentials)
 *   Card 2b "Local"  — local-only init (email only)
 */
public class InitRepoPopup extends JFrame {

    private static final String CARD_CHOOSE = "Choose";
    private static final String CARD_REMOTE = "Remote";
    private static final String CARD_LOCAL = "Local";

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Remote card fields
    private JTextField repoUriField;
    private JLabel authTypeLabel;
    private JTextField emailField;
    private JTextField gitUsernameField;
    private JPasswordField passwordField;
    private JTextArea sshKeyArea;
    private JPanel httpsPanel;
    private JPanel sshPanel;

    // Local card fields
    private JTextField localEmailField;

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

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(buildChooseCard(), CARD_CHOOSE);
        cardPanel.add(buildRemoteCard(), CARD_REMOTE);
        cardPanel.add(buildLocalCard(), CARD_LOCAL);

        main.add(cardPanel, BorderLayout.CENTER);

        return main;
    }

    // ── Card 1: Choose ──────────────────────────────────────────────────

    private JPanel buildChooseCard() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel question = new JLabel(
                "<html><b>Does this project have a remote Git repository</b><br>"
                        + "(GitHub, GitLab, Bitbucket, etc.)?</html>");
        question.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(question, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));

        JButton yesBtn = new JButton("Yes, clone from remote");
        yesBtn.setBackground(new Color(71, 137, 199));
        yesBtn.setForeground(Color.WHITE);
        yesBtn.addActionListener(e -> showCard(CARD_REMOTE));

        JButton noBtn = new JButton("No, initialize locally");
        noBtn.addActionListener(e -> showCard(CARD_LOCAL));

        buttonPanel.add(yesBtn);
        buttonPanel.add(noBtn);

        panel.add(buttonPanel, BorderLayout.CENTER);

        // Cancel at bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        bottomPanel.add(cancelBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ── Card 2a: Remote (existing form) ─────────────────────────────────

    private JPanel buildRemoteCard() {
        JPanel main = new JPanel(new BorderLayout(5, 5));

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

        // Auth type label
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

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> showCard(CARD_CHOOSE));

        JButton initBtn = new JButton("Initialize");
        initBtn.setBackground(new Color(71, 137, 199));
        initBtn.setForeground(Color.WHITE);
        initBtn.addActionListener(e -> {
            if (validateRemoteFields()) {
                String password = new String(passwordField.getPassword());
                onInitialize(repoUriField.getText().trim(), emailField.getText().trim(),
                        gitUsernameField.getText().trim(), password, sshKeyArea.getText().trim());
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(backBtn);
        buttonPanel.add(initBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    // ── Card 2b: Local ──────────────────────────────────────────────────

    private JPanel buildLocalCard() {
        JPanel main = new JPanel(new BorderLayout(5, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Info text
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel infoLabel = new JLabel(
                "<html>A local Git repository will be created for this project.<br>"
                        + "You can add a remote repository later.</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        formPanel.add(infoLabel, gbc);

        // Email
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        localEmailField = new JTextField(30);
        formPanel.add(localEmailField, gbc);

        main.add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> showCard(CARD_CHOOSE));

        JButton initBtn = new JButton("Initialize");
        initBtn.setBackground(new Color(71, 137, 199));
        initBtn.setForeground(Color.WHITE);
        initBtn.addActionListener(e -> {
            if (validateLocalFields()) {
                onLocalInitialize(localEmailField.getText().trim());
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(backBtn);
        buttonPanel.add(initBtn);
        buttonPanel.add(cancelBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    // ── Navigation ──────────────────────────────────────────────────────

    private void showCard(String name) {
        cardLayout.show(cardPanel, name);
        pack();
        revalidate();
        repaint();
    }

    // ── Auth type switching (Remote card) ───────────────────────────────

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

    // ── Validation ──────────────────────────────────────────────────────

    private boolean validateRemoteFields() {
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

    private boolean validateLocalFields() {
        String email = localEmailField.getText().trim();
        if (email.isEmpty() || !email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email address.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    // ── Callbacks ───────────────────────────────────────────────────────

    /**
     * Called when the user clicks Initialize on the Remote card and validation passes.
     * Override in an anonymous subclass to handle the initialization.
     */
    public void onInitialize(String repoUri, String email, String gitUsername, String password, String sshKey) {
    }

    /**
     * Called when the user clicks Initialize on the Local card and validation passes.
     * Override in an anonymous subclass to handle local-only initialization.
     */
    public void onLocalInitialize(String email) {
    }
}
