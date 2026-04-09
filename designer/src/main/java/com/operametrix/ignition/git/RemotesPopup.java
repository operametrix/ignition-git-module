package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Designer popup dialog for managing git remotes.
 * CardLayout-based with two cards:
 *   Card 1 "List" — table of remotes + Add/Edit/Remove/Close buttons
 *   Card 2 "Form" — add/edit remote with URL and credential dropdown
 */
public class RemotesPopup extends JDialog {

    private static final String CARD_LIST = "List";
    private static final String CARD_FORM = "Form";

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // List card
    private DefaultTableModel tableModel;
    private JTable remotesTable;
    private JButton editButton;
    private JButton removeButton;

    // Form card
    private boolean formIsEdit;
    private JTextField nameField;
    private JTextField urlField;
    private JLabel authTypeLabel;

    // Credential dropdown
    private JPanel credentialPanel;
    private JComboBox<String> credentialDropdown;
    private JButton configureCredentialsButton;
    private List<Long> credentialIds = new ArrayList<>();
    private Dataset savedSshKeys;
    private Dataset savedHttpsCreds;

    public RemotesPopup(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
        setTitle("Manage Remotes");
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

        cardPanel.add(buildListCard(), CARD_LIST);
        cardPanel.add(buildFormCard(), CARD_FORM);

        main.add(cardPanel, BorderLayout.CENTER);

        return main;
    }

    // ── Card 1: List ───────────────────────────────────────────────────

    private JPanel buildListCard() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel titleLabel = new JLabel("Configured Remotes");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        panel.add(titleLabel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(new String[]{"Name", "URL"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        remotesTable = new JTable(tableModel);
        remotesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        remotesTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        remotesTable.getColumnModel().getColumn(1).setPreferredWidth(350);
        remotesTable.getSelectionModel().addListSelectionListener(e -> updateListButtons());

        JScrollPane scrollPane = new JScrollPane(remotesTable);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton addButton = new JButton("Add Remote");
        addButton.setBackground(new Color(71, 137, 199));
        addButton.setForeground(Color.WHITE);
        addButton.addActionListener(e -> showFormForAdd());

        editButton = new JButton("Edit");
        editButton.setEnabled(false);
        editButton.addActionListener(e -> showFormForEdit());

        removeButton = new JButton("Remove");
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> handleRemove());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(closeButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateListButtons() {
        boolean hasSelection = remotesTable.getSelectedRow() >= 0;
        editButton.setEnabled(hasSelection);
        removeButton.setEnabled(hasSelection);
    }

    // ── Card 2: Form ───────────────────────────────────────────────────

    private JPanel buildFormCard() {
        JPanel main = new JPanel(new BorderLayout(5, 5));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        nameField = new JTextField(15);
        formPanel.add(nameField, gbc);

        // URL
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("URL:"), gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        urlField = new JTextField(30);
        urlField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateFormAuthType(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateFormAuthType(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateFormAuthType(); }
        });
        formPanel.add(urlField, gbc);

        // Auth type label
        gbc.gridx = 0;
        gbc.gridy = 2;
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
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(credentialPanel, gbc);

        main.add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> showCard(CARD_LIST));

        JButton saveBtn = new JButton("Save");
        saveBtn.setBackground(new Color(71, 137, 199));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.addActionListener(e -> handleSave());

        buttonPanel.add(backBtn);
        buttonPanel.add(saveBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    private void updateFormAuthType() {
        String url = urlField.getText().trim().toLowerCase();
        boolean hasUrl = !url.isEmpty();
        boolean isHttps = url.startsWith("http");

        authTypeLabel.setVisible(hasUrl);
        authTypeLabel.setText(isHttps ? "HTTPS" : "SSH");

        credentialPanel.setVisible(hasUrl);
        if (hasUrl) {
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
        String url = urlField.getText().trim().toLowerCase();
        if (!url.isEmpty()) {
            populateCredentialDropdown(url.startsWith("http"));
        }
    }

    // ── Navigation ─────────────────────────────────────────────────────

    private void showCard(String name) {
        cardLayout.show(cardPanel, name);
        pack();
        revalidate();
        repaint();
    }

    private void showFormForAdd() {
        formIsEdit = false;
        nameField.setText("");
        nameField.setEnabled(true);
        urlField.setText("");
        authTypeLabel.setVisible(false);
        credentialPanel.setVisible(false);
        showCard(CARD_FORM);
    }

    private void showFormForEdit() {
        int row = remotesTable.getSelectedRow();
        if (row < 0) return;
        formIsEdit = true;
        String name = (String) tableModel.getValueAt(row, 0);
        String url = (String) tableModel.getValueAt(row, 1);
        nameField.setText(name);
        nameField.setEnabled(false);
        urlField.setText(url);
        updateFormAuthType();
        showCard(CARD_FORM);
    }

    // ── Actions ────────────────────────────────────────────────────────

    private void handleSave() {
        String name = nameField.getText().trim();
        String url = urlField.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Remote name is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Remote URL is required.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check for duplicate name on add
        if (!formIsEdit) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (name.equals(tableModel.getValueAt(i, 0))) {
                    JOptionPane.showMessageDialog(this, "A remote named '" + name + "' already exists.",
                            "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }

        int selectedIdx = credentialDropdown.getSelectedIndex();
        long selectedCredId = selectedIdx >= 0 && selectedIdx < credentialIds.size()
                ? credentialIds.get(selectedIdx) : 0;
        boolean isHttps = url.toLowerCase().startsWith("http");

        // Add/edit the remote with empty inline creds (credential is referenced by FK)
        if (formIsEdit) {
            onEditRemote(name, url, "", "", "");
        } else {
            onAddRemote(name, url, "", "", "");
        }

        // Associate the selected credential via FK
        if (selectedCredId > 0) {
            onSavedCredentialSelected(name, isHttps ? 0 : selectedCredId,
                    isHttps ? selectedCredId : 0);
        }
    }

    private void handleRemove() {
        int row = remotesTable.getSelectedRow();
        if (row < 0) return;
        String name = (String) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove remote '" + name + "'?", "Confirm Remove",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            onRemoveRemote(name);
        }
    }

    // ── Data ───────────────────────────────────────────────────────────

    /**
     * Populate the remotes table from a Dataset with columns [name, url].
     * Returns to the list card.
     */
    public void setData(Dataset remotes) {
        tableModel.setRowCount(0);
        if (remotes != null) {
            for (int i = 0; i < remotes.getRowCount(); i++) {
                tableModel.addRow(new Object[]{
                        remotes.getValueAt(i, "name"),
                        remotes.getValueAt(i, "url")
                });
            }
        }
        updateListButtons();
        showCard(CARD_LIST);
    }

    /**
     * Provide the user's saved credentials for populating the dropdown.
     * Call this before showing the form card.
     */
    public void setSavedCredentials(Dataset sshKeys, Dataset httpsCreds) {
        this.savedSshKeys = sshKeys;
        this.savedHttpsCreds = httpsCreds;
    }

    // ── Callbacks ──────────────────────────────────────────────────────

    public void onAddRemote(String name, String url, String gitUsername, String password, String sshKey) {
    }

    public void onEditRemote(String name, String newUrl, String gitUsername, String password, String sshKey) {
    }

    public void onRemoveRemote(String name) {
    }

    public void onRefresh() {
    }

    /** Called when the user selects a saved credential for a remote. */
    public void onSavedCredentialSelected(String remoteName, long sshKeyId, long httpsCredentialId) {
    }

    /** Called when the user clicks "Configure..." to open the User Credentials popup. */
    public void onConfigureCredentials() {
    }
}
