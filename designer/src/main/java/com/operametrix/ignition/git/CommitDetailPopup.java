package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Shows the files changed in a single commit. Displays commit hash and message at the
 * top, with a table of changed files (change type + path) in the center.
 * Double-clicking a file row triggers {@link #onFileDiffRequested} which is overridden
 * by {@link com.operametrix.ignition.git.managers.GitActionManager} to open the
 * existing {@link DiffViewerPopup} with the file's old/new content at that commit.
 * <p>
 * Not cached — a new instance is created each time so multiple commit details
 * can be viewed side by side.
 */
public class CommitDetailPopup extends JFrame {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String commitHash;
    private final String shortHash;
    private final String message;
    private DefaultTableModel tableModel;
    private JTable table;

    public CommitDetailPopup(String commitHash, String shortHash, String message,
                             String author, String date,
                             List<String> files, Component parent) {
        this.commitHash = commitHash;
        this.shortHash = shortHash;
        this.message = message;

        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/operametrix/ignition/git/icons/ic_history.svg");
            if (iconStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
                setIconImage(icon.getImage());
            }
        } catch (IOException e) {
            logger.trace(e.toString(), e);
        }

        setTitle("Commit: " + shortHash);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI(shortHash, message, author, date, files));

        setSize(700, 500);
        setMinimumSize(new Dimension(500, 350));
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI(String shortHash, String message, String author, String date,
                           List<String> files) {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: commit details
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "Commit Details",
                TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
                null, null));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        detailsPanel.add(new JLabel("Hash:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JLabel hashLabel = new JLabel(shortHash);
        hashLabel.setFont(hashLabel.getFont().deriveFont(Font.BOLD));
        detailsPanel.add(hashLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailsPanel.add(new JLabel("Author:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailsPanel.add(new JLabel(author != null ? author : ""), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailsPanel.add(new JLabel("Date:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailsPanel.add(new JLabel(date != null ? date : ""), gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailsPanel.add(new JLabel("Message:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JLabel messageLabel = new JLabel("<html>" + escapeHtml(message) + "</html>");
        detailsPanel.add(messageLabel, gbc);

        main.add(detailsPanel, BorderLayout.NORTH);

        // Center: file table
        tableModel = new DefaultTableModel(new String[]{"Change Type", "File Path"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(500);

        for (String file : files) {
            int colonIndex = file.indexOf(':');
            if (colonIndex >= 0) {
                String changeType = file.substring(0, colonIndex);
                String path = file.substring(colonIndex + 1);
                tableModel.addRow(new Object[]{changeType, path});
            }
        }

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String changeType = (String) tableModel.getValueAt(row, 0);
                        String filePath = (String) tableModel.getValueAt(row, 1);
                        onFileDiffRequested(commitHash, filePath, changeType);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        main.add(scrollPane, BorderLayout.CENTER);

        // Bottom: revert + close buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton revertBtn = new JButton("Revert Commit");
        revertBtn.addActionListener(e -> onRevertRequested(commitHash, shortHash, message));
        buttonPanel.add(revertBtn);
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);
        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void onFileDiffRequested(String commitHash, String filePath, String changeType) {
    }

    public void onRevertRequested(String commitHash, String shortHash, String message) {
    }
}
