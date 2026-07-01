package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.designer.gui.CommonUI;
import com.operametrix.ignition.git.utils.IconUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Designer popup for managing user-level credentials:
 *   - SSH Keys (host-independent, with default marking)
 *   - HTTPS Credentials (per-host, with provider hint text)
 */
public class UserCredentialsPopup extends JDialog {

    private static final Map<String, String> HTTPS_HINTS = new HashMap<>();
    static {
        HTTPS_HINTS.put("github.com", "Use a Personal Access Token as the password.");
        HTTPS_HINTS.put("gitlab.com", "Use a Personal Access Token as the password.");
        HTTPS_HINTS.put("dev.azure.com", "Use a Personal Access Token as the password. Leave username empty.");
        HTTPS_HINTS.put("bitbucket.org", "Use an App Password.");
    }

    // SSH Keys table
    private DefaultTableModel sshTableModel;
    private JTable sshTable;

    // HTTPS Credentials table
    private DefaultTableModel httpsTableModel;
    private JTable httpsTable;

    public UserCredentialsPopup(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
        IconUtils.setWindowIcon(this, "/com/operametrix/ignition/git/icons/ic_verified_user.svg");
        setTitle("User Credentials");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());

        pack();
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 10));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel sectionsPanel = new JPanel();
        sectionsPanel.setLayout(new BoxLayout(sectionsPanel, BoxLayout.Y_AXIS));
        sectionsPanel.add(buildSshSection());
        sectionsPanel.add(Box.createVerticalStrut(10));
        sectionsPanel.add(buildHttpsSection());

        main.add(sectionsPanel, BorderLayout.CENTER);

        return main;
    }

    // ── SSH Keys Section ──────────────────────────────────────────────

    private JPanel buildSshSection() {
        JPanel section = new JPanel(new BorderLayout(5, 5));

        JButton addBtn = buildIconButton(VectorIcons.get("add"), "Add SSH Key", e -> showAddSshKeyDialog());
        section.add(buildSectionHeader("SSH Keys", addBtn), BorderLayout.NORTH);

        sshTableModel = new DefaultTableModel(new String[]{"Key Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        sshTable = new JTable(sshTableModel);
        sshTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sshTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        attachRemoveContextMenu(sshTable, sshTableModel, row -> {
            String keyName = (String) sshTableModel.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove SSH key '" + keyName + "'?", "Confirm Remove",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                long keyId = getIdForSshRow(row);
                onDeleteSshKey(keyId);
            }
        });

        JScrollPane scrollPane = new JScrollPane(sshTable);
        scrollPane.setPreferredSize(new Dimension(400, 120));
        section.add(scrollPane, BorderLayout.CENTER);

        return section;
    }

    private void showAddSshKeyDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Key Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField keyNameField = new JTextField(20);
        panel.add(keyNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
        JTextArea sshKeyArea = new JTextArea(8, 30);
        sshKeyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel keyPanel = new JPanel(new BorderLayout());
        keyPanel.setBorder(BorderFactory.createTitledBorder("SSH Private Key"));
        keyPanel.add(new JScrollPane(sshKeyArea), BorderLayout.CENTER);
        panel.add(keyPanel, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add SSH Key",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String keyName = keyNameField.getText().trim();
            String sshKey = sshKeyArea.getText().trim();
            if (keyName.isEmpty() || sshKey.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Key name and SSH key are required.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            onSaveSshKey(keyName, sshKey);
        }
    }

    // ── HTTPS Credentials Section ─────────────────────────────────────

    private JPanel buildHttpsSection() {
        JPanel section = new JPanel(new BorderLayout(5, 5));

        JButton addBtn = buildIconButton(VectorIcons.get("add"), "Add HTTPS Credential", e -> showAddHttpsDialog());
        section.add(buildSectionHeader("HTTPS Credentials", addBtn), BorderLayout.NORTH);

        httpsTableModel = new DefaultTableModel(new String[]{"Host", "Username"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        httpsTable = new JTable(httpsTableModel);
        httpsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        httpsTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        httpsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        attachRemoveContextMenu(httpsTable, httpsTableModel, row -> {
            String host = (String) httpsTableModel.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove HTTPS credential for '" + host + "'?", "Confirm Remove",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                long credId = getIdForHttpsRow(row);
                onDeleteHttpsCredential(credId);
            }
        });

        JScrollPane scrollPane = new JScrollPane(httpsTable);
        scrollPane.setPreferredSize(new Dimension(400, 120));
        section.add(scrollPane, BorderLayout.CENTER);

        return section;
    }

    private void showAddHttpsDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 5, 4, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Host:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField hostField = new JTextField(25);
        panel.add(hostField, gbc);

        // Hint label (dynamic based on host)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel hintLabel = new JLabel(" ");
        hintLabel.setForeground(new Color(100, 100, 100));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC));
        panel.add(hintLabel, gbc);

        hostField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String host = hostField.getText().trim().toLowerCase();
                String hint = HTTPS_HINTS.get(host);
                hintLabel.setText(hint != null ? hint : " ");
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });

        gbc.gridy = 2; gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField userField = new JTextField(25);
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Password / Token:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPasswordField passField = new JPasswordField(25);
        panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add HTTPS Credential",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String host = hostField.getText().trim();
            String userName = userField.getText().trim();
            String password = new String(passField.getPassword());
            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Host is required.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            onSaveHttpsCredential(host, userName, password);
        }
    }

    // ── Section header + icon button helpers ──────────────────────────

    private JPanel buildSectionHeader(String title, JButton... trailingButtons) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

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

    private void attachRemoveContextMenu(JTable table, DefaultTableModel model,
                                          java.util.function.IntConsumer onRemove) {
        table.addMouseListener(new MouseAdapter() {
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
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);

                JPopupMenu menu = new JPopupMenu();
                JMenuItem removeItem = new JMenuItem("Remove");
                removeItem.setForeground(new Color(0xCC0000));
                removeItem.addActionListener(a -> onRemove.accept(row));
                menu.add(removeItem);
                menu.show(table, e.getX(), e.getY());
            }
        });
    }

    // ── Data population ───────────────────────────────────────────────

    // Hidden ID columns stored alongside visible data
    private long[] sshKeyIds = new long[0];
    private long[] httpsCredentialIds = new long[0];

    public void setSshKeyData(Dataset keys) {
        sshTableModel.setRowCount(0);
        if (keys != null && keys.getRowCount() > 0) {
            sshKeyIds = new long[keys.getRowCount()];
            for (int i = 0; i < keys.getRowCount(); i++) {
                sshKeyIds[i] = ((Number) keys.getValueAt(i, "id")).longValue();
                sshTableModel.addRow(new Object[]{
                        keys.getValueAt(i, "keyName")
                });
            }
        } else {
            sshKeyIds = new long[0];
        }
    }

    public void setHttpsCredentialData(Dataset creds) {
        httpsTableModel.setRowCount(0);
        if (creds != null && creds.getRowCount() > 0) {
            httpsCredentialIds = new long[creds.getRowCount()];
            for (int i = 0; i < creds.getRowCount(); i++) {
                httpsCredentialIds[i] = ((Number) creds.getValueAt(i, "id")).longValue();
                httpsTableModel.addRow(new Object[]{
                        creds.getValueAt(i, "hostPattern"),
                        creds.getValueAt(i, "userName")
                });
            }
        } else {
            httpsCredentialIds = new long[0];
        }
    }

    private long getIdForSshRow(int row) {
        return row >= 0 && row < sshKeyIds.length ? sshKeyIds[row] : -1;
    }

    private long getIdForHttpsRow(int row) {
        return row >= 0 && row < httpsCredentialIds.length ? httpsCredentialIds[row] : -1;
    }

    // ── Callbacks ─────────────────────────────────────────────────────

    public void onSaveSshKey(String keyName, String sshKey) {}
    public void onDeleteSshKey(long keyId) {}
    public void onSaveHttpsCredential(String hostPattern, String userName, String password) {}
    public void onDeleteHttpsCredential(long credentialId) {}
    public void onRefresh() {}
}
