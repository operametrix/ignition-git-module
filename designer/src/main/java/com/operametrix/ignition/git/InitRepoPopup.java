package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Designer popup dialog for initializing a git repository for an unregistered project.
 * Wizard-style with three cards:
 *   Card 1 "Choose"  — asks whether the user has a remote repository
 *   Card 2a "Remote" — URI + email + credential dropdown
 *   Card 2b "Local"  — local-only init (email only)
 */
public class InitRepoPopup extends JDialog {

    private static final String CARD_CHOOSE = "Choose";
    private static final String CARD_REMOTE = "Remote";
    private static final String CARD_LOCAL = "Local";

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Remote card fields
    private JTextField repoUriField;
    private JLabel authTypeLabel;

    // Credential dropdown
    private JPanel credentialPanel;
    private JComboBox<String> credentialDropdown;
    private JButton configureCredentialsButton;
    private List<Long> credentialIds = new ArrayList<>();
    private Dataset savedSshKeys;
    private Dataset savedHttpsCreds;

    // Local card (no fields needed — email comes from Ignition user profile)

    public InitRepoPopup(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
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

    // ── Card 2a: Remote ─────────────────────────────────────────────────

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
        gbc.gridwidth = 2;
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

        // Auth type label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        authTypeLabel = new JLabel();
        authTypeLabel.setFont(authTypeLabel.getFont().deriveFont(Font.BOLD));
        authTypeLabel.setForeground(new Color(0, 128, 0));
        authTypeLabel.setVisible(false);
        formPanel.add(authTypeLabel, gbc);

        // Credential dropdown + Configure button
        credentialPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        credentialPanel.add(new JLabel("Credential:"));
        credentialDropdown = new JComboBox<>();
        credentialDropdown.setPreferredSize(new Dimension(280, 25));
        credentialPanel.add(credentialDropdown);
        configureCredentialsButton = new JButton("Configure...");
        configureCredentialsButton.addActionListener(e -> onConfigureCredentials());
        credentialPanel.add(configureCredentialsButton);
        credentialPanel.setVisible(false);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(credentialPanel, gbc);

        main.add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> showCard(CARD_CHOOSE));

        JButton initBtn = new JButton("Initialize");
        initBtn.setBackground(new Color(71, 137, 199));
        initBtn.setForeground(Color.WHITE);
        initBtn.addActionListener(e -> handleInitialize());

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

        JLabel infoLabel = new JLabel(
                "<html>A local Git repository will be created for this project.<br>"
                        + "You can add a remote repository later.<br><br>"
                        + "Commit author email will be taken from your Ignition user profile.</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        main.add(infoLabel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> showCard(CARD_CHOOSE));

        JButton initBtn = new JButton("Initialize");
        initBtn.setBackground(new Color(71, 137, 199));
        initBtn.setForeground(Color.WHITE);
        initBtn.addActionListener(e -> onLocalInitialize());

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

        credentialPanel.setVisible(hasUri);
        if (hasUri) {
            populateCredentialDropdown(isHttps);
        }

        pack();
        revalidate();
        repaint();
    }

    private void populateCredentialDropdown(boolean isHttps) {
        credentialDropdown.removeAllItems();
        credentialIds.clear();

        if (isHttps && savedHttpsCreds != null) {
            for (int i = 0; i < savedHttpsCreds.getRowCount(); i++) {
                String host = (String) savedHttpsCreds.getValueAt(i, "hostPattern");
                String user = (String) savedHttpsCreds.getValueAt(i, "userName");
                credentialDropdown.addItem(host + " (" + user + ")");
                credentialIds.add(((Number) savedHttpsCreds.getValueAt(i, "id")).longValue());
            }
        } else if (!isHttps && savedSshKeys != null) {
            for (int i = 0; i < savedSshKeys.getRowCount(); i++) {
                String keyName = (String) savedSshKeys.getValueAt(i, "keyName");
                credentialDropdown.addItem(keyName);
                credentialIds.add(((Number) savedSshKeys.getValueAt(i, "id")).longValue());
            }
        }

        if (credentialIds.isEmpty()) {
            credentialDropdown.addItem("(no credentials configured)");
        }
    }

    /**
     * Re-populate the credential dropdown from the current saved credentials.
     * Call after updating credentials via UserCredentialsPopup.
     */
    public void refreshCredentialDropdown() {
        String uri = repoUriField.getText().trim().toLowerCase();
        if (!uri.isEmpty()) {
            populateCredentialDropdown(uri.startsWith("http"));
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private void handleInitialize() {
        if (!validateRemoteFields()) return;

        String repoUri = repoUriField.getText().trim();
        boolean isHttps = repoUri.toLowerCase().startsWith("http");

        int selectedIdx = credentialDropdown.getSelectedIndex();
        long selectedCredId = selectedIdx >= 0 && selectedIdx < credentialIds.size()
                ? credentialIds.get(selectedIdx) : 0;

        long sshKeyId = (!isHttps && selectedCredId > 0) ? selectedCredId : 0;
        long httpsCredentialId = (isHttps && selectedCredId > 0) ? selectedCredId : 0;

        onInitialize(repoUri, sshKeyId, httpsCredentialId);
    }

    // ── Validation ──────────────────────────────────────────────────────

    private boolean validateRemoteFields() {
        String uri = repoUriField.getText().trim();
        if (uri.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Repository URI is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (credentialIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No credentials configured. Click 'Configure...' to add credentials first.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        return true;
    }

    // ── Data ────────────────────────────────────────────────────────────

    /**
     * Provide the user's saved credentials for populating the dropdown.
     * Call this before showing the popup.
     */
    public void setSavedCredentials(Dataset sshKeys, Dataset httpsCreds) {
        this.savedSshKeys = sshKeys;
        this.savedHttpsCreds = httpsCreds;
    }

    // ── Callbacks ───────────────────────────────────────────────────────

    public void onInitialize(String repoUri, long sshKeyId, long httpsCredentialId) {
    }

    public void onLocalInitialize() {
    }

    /** Called when the user clicks "Configure..." to open the User Credentials popup. */
    public void onConfigureCredentials() {
    }
}
