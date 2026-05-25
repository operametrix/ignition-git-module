package com.operametrix.ignition.git;

import com.operametrix.ignition.git.components.SelectAllHeader;
import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.common.Dataset;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CommitPanel extends JPanel {

    private final JTextArea commitMessageArea;
    private final JCheckBox amendCheckBox;
    private final JButton commitButton;
    private final JTable changesTable;
    private final JLabel changesCountLabel;

    private Runnable onRefreshRequested;
    private Runnable onSnapshotTagsRequested;
    private Runnable onSnapshotThemesRequested;
    private Runnable onSnapshotImagesRequested;
    private BiConsumer<String, String> onDiffRequested;
    private Consumer<List<String>> onDiscardRequested;
    private CommitRequestHandler onCommitRequested;
    private Consumer<Boolean> onAmendToggled;
    private boolean amendSelected;

    @FunctionalInterface
    public interface CommitRequestHandler {
        void accept(List<String> changes, String message, boolean amend);
    }

    public CommitPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Center: commit section + changes table
        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));

        // Commit message area
        JPanel commitSection = new JPanel(new BorderLayout(0, 2));
        commitMessageArea = new JTextArea(3, 20);
        commitMessageArea.setLineWrap(true);
        commitMessageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(commitMessageArea);
        messageScroll.setPreferredSize(new Dimension(0, 60));
        commitSection.add(messageScroll, BorderLayout.CENTER);

        // Bottom controls: amend checkbox + commit button
        JPanel bottomControls = new JPanel(new BorderLayout(4, 0));
        amendCheckBox = new JCheckBox("Amend last commit");
        amendCheckBox.addActionListener(e -> {
            amendSelected = amendCheckBox.isSelected();
            if (onAmendToggled != null) {
                onAmendToggled.accept(amendSelected);
            }
        });
        bottomControls.add(amendCheckBox, BorderLayout.WEST);

        commitButton = new JButton("Commit");
        commitButton.setBackground(new Color(0x4E8EF7));
        commitButton.setForeground(Color.WHITE);
        commitButton.setFocusPainted(false);
        commitButton.addActionListener(e -> {
            if (onCommitRequested != null) {
                List<String> selected = getSelectedResources();
                String message = commitMessageArea.getText().trim();
                if (message.isEmpty()) {
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(CommitPanel.this),
                            "Please enter a commit message.", "Commit", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!amendSelected && selected.isEmpty()) {
                    JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(CommitPanel.this),
                            "Please select at least one file to commit.", "Commit", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                boolean amend = amendSelected;
                onCommitRequested.accept(selected, message, amend);
                commitMessageArea.setText("");
                amendCheckBox.setSelected(false);
                amendSelected = false;
            }
        });
        bottomControls.add(commitButton, BorderLayout.EAST);
        commitSection.add(bottomControls, BorderLayout.SOUTH);
        centerPanel.add(commitSection, BorderLayout.NORTH);

        // Changes header: label + right-aligned refresh button
        changesCountLabel = new JLabel("Changes (0)");
        changesCountLabel.setFont(changesCountLabel.getFont().deriveFont(Font.BOLD));

        Icon snapshotIcon = VectorIcons.get("project-update");
        JButton snapshotTagsButton = createHeaderTextButton(snapshotIcon, "Tags",
                "Snapshot gateway tag-provider state into project files so changes appear in the list below",
                () -> { if (onSnapshotTagsRequested != null) onSnapshotTagsRequested.run(); });
        JButton snapshotThemesButton = createHeaderTextButton(snapshotIcon, "Themes",
                "Snapshot gateway Perspective theme files into the project",
                () -> { if (onSnapshotThemesRequested != null) onSnapshotThemesRequested.run(); });
        JButton snapshotImagesButton = createHeaderTextButton(snapshotIcon, "Images",
                "Snapshot gateway image manager state into the project",
                () -> { if (onSnapshotImagesRequested != null) onSnapshotImagesRequested.run(); });
        JButton refreshButton = createHeaderIconButton(VectorIcons.get("refresh"), "Refresh",
                () -> { if (onRefreshRequested != null) onRefreshRequested.run(); });

        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        headerButtons.add(snapshotTagsButton);
        headerButtons.add(snapshotThemesButton);
        headerButtons.add(snapshotImagesButton);
        headerButtons.add(refreshButton);

        JPanel changesHeader = new JPanel(new BorderLayout());
        changesHeader.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        changesHeader.add(changesCountLabel, BorderLayout.WEST);
        changesHeader.add(headerButtons, BorderLayout.EAST);

        // Changes table
        String[] columnNames = {"", "Resource", "Type"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        changesTable = new JTable(model);
        changesTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        changesTable.getTableHeader().setReorderingAllowed(false);
        changesTable.setRowHeight(22);
        changesTable.setShowGrid(false);
        changesTable.setIntercellSpacing(new Dimension(0, 0));

        changesTable.getColumn("").setPreferredWidth(30);
        changesTable.getColumn("").setMaxWidth(30);
        changesTable.getColumn("Resource").setPreferredWidth(250);
        changesTable.getColumn("Type").setPreferredWidth(60);
        changesTable.getColumn("Type").setMinWidth(45);

        TableColumn tc = changesTable.getColumnModel().getColumn(0);
        tc.setHeaderRenderer(new SelectAllHeader(changesTable, 0));

        changesTable.getColumn("Type").setCellRenderer(new ChangeTypeCellRenderer());

        // Double-click for diff
        changesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = changesTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && onDiffRequested != null) {
                        String resource = (String) changesTable.getValueAt(row, 1);
                        String type = (String) changesTable.getValueAt(row, 2);
                        onDiffRequested.accept(resource, type);
                    }
                }
            }
        });

        // Right-click context menu
        changesTable.addMouseListener(new MouseAdapter() {
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
                int row = changesTable.rowAtPoint(e.getPoint());
                if (row < 0) return;
                changesTable.setRowSelectionInterval(row, row);

                String resource = (String) changesTable.getValueAt(row, 1);
                String type = (String) changesTable.getValueAt(row, 2);

                JPopupMenu menu = new JPopupMenu();

                JMenuItem viewDiff = new JMenuItem("View Diff");
                viewDiff.addActionListener(a -> {
                    if (onDiffRequested != null) onDiffRequested.accept(resource, type);
                });
                menu.add(viewDiff);
                menu.addSeparator();

                JMenuItem discard = new JMenuItem("Discard Changes");
                discard.setForeground(new Color(0xCC0000));
                discard.addActionListener(a -> {
                    int confirm = JOptionPane.showConfirmDialog(
                            CommitPanel.this,
                            "Discard changes to '" + resource + "'?\nThis cannot be undone.",
                            "Discard Changes",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm == JOptionPane.YES_OPTION && onDiscardRequested != null) {
                        List<String> paths = new ArrayList<>();
                        paths.add(resource);
                        onDiscardRequested.accept(paths);
                    }
                });
                menu.add(discard);

                menu.show(changesTable, e.getX(), e.getY());
            }
        });

        JPanel tableSection = new JPanel(new BorderLayout());
        tableSection.add(changesHeader, BorderLayout.NORTH);
        tableSection.add(new JScrollPane(changesTable), BorderLayout.CENTER);
        centerPanel.add(tableSection, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private List<String> getSelectedResources() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < changesTable.getModel().getRowCount(); i++) {
            if ((Boolean) changesTable.getValueAt(i, 0)) {
                selected.add((String) changesTable.getValueAt(i, 1));
            }
        }
        return selected;
    }

    /**
     * Update the changes table data. Safe to call from any thread.
     */
    public void setChangesData(Dataset ds) {
        SwingUtilities.invokeLater(() -> {
            // Preserve currently checked resources across refresh
            java.util.Set<String> previouslyChecked = new java.util.HashSet<>();
            for (int i = 0; i < changesTable.getModel().getRowCount(); i++) {
                if (Boolean.TRUE.equals(changesTable.getModel().getValueAt(i, 0))) {
                    previouslyChecked.add((String) changesTable.getModel().getValueAt(i, 1));
                }
            }

            String[] columnNames = {"", "Resource", "Type"};
            Object[][] data = new Object[ds.getRowCount()][];
            for (int i = 0; i < ds.getRowCount(); i++) {
                String resource = (String) ds.getValueAt(i, "resource");
                String type = (String) ds.getValueAt(i, "type");
                data[i] = new Object[]{previouslyChecked.contains(resource), resource, type};
            }

            DefaultTableModel model = new DefaultTableModel(data, columnNames) {
                @Override
                public Class<?> getColumnClass(int column) {
                    return column == 0 ? Boolean.class : String.class;
                }

                @Override
                public boolean isCellEditable(int row, int column) {
                    return column == 0;
                }
            };

            changesTable.setModel(model);
            changesTable.getColumn("").setPreferredWidth(30);
            changesTable.getColumn("").setMaxWidth(30);
            changesTable.getColumn("Resource").setPreferredWidth(250);
            changesTable.getColumn("Type").setPreferredWidth(60);
            changesTable.getColumn("Type").setMinWidth(45);

            TableColumn tc = changesTable.getColumnModel().getColumn(0);
            tc.setHeaderRenderer(new SelectAllHeader(changesTable, 0));
            changesTable.getColumn("Type").setCellRenderer(new ChangeTypeCellRenderer());

            changesCountLabel.setText("Changes (" + ds.getRowCount() + ")");
        });
    }

    public void setOnRefreshRequested(Runnable onRefreshRequested) {
        this.onRefreshRequested = onRefreshRequested;
    }

    public void setOnSnapshotTagsRequested(Runnable r) {
        this.onSnapshotTagsRequested = r;
    }

    public void setOnSnapshotThemesRequested(Runnable r) {
        this.onSnapshotThemesRequested = r;
    }

    public void setOnSnapshotImagesRequested(Runnable r) {
        this.onSnapshotImagesRequested = r;
    }

    private JButton createHeaderIconButton(Icon icon, String tooltip, Runnable action) {
        JButton b = new JButton(icon);
        styleHeaderButton(b, tooltip, action);
        return b;
    }

    private JButton createHeaderTextButton(Icon icon, String label, String tooltip, Runnable action) {
        JButton b = new JButton(label, icon);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        b.setIconTextGap(3);
        b.setMargin(new Insets(2, 6, 2, 6));
        styleHeaderButton(b, tooltip, action);
        return b;
    }

    private void styleHeaderButton(JButton b, String tooltip, Runnable action) {
        b.setToolTipText(tooltip);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setContentAreaFilled(true);
                b.setBorderPainted(true);
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
            }
        });
        b.addActionListener(e -> action.run());
    }

    public void setOnDiffRequested(BiConsumer<String, String> onDiffRequested) {
        this.onDiffRequested = onDiffRequested;
    }

    public void setOnDiscardRequested(Consumer<List<String>> onDiscardRequested) {
        this.onDiscardRequested = onDiscardRequested;
    }

    public void setOnCommitRequested(CommitRequestHandler onCommitRequested) {
        this.onCommitRequested = onCommitRequested;
    }

    public void setOnAmendToggled(Consumer<Boolean> onAmendToggled) {
        this.onAmendToggled = onAmendToggled;
    }

    public boolean isAmendSelected() {
        return amendSelected;
    }

    public void setCommitMessage(String message) {
        SwingUtilities.invokeLater(() -> commitMessageArea.setText(message));
    }

    /**
     * Renders the Type column as a color-coded single-letter badge.
     */
    private static class ChangeTypeCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));

            String type = value != null ? value.toString() : "";
            switch (type) {
                case "Created":
                    label.setText("A");
                    if (!isSelected) {
                        label.setForeground(new Color(0x28A745));
                    }
                    break;
                case "Modified":
                    label.setText("M");
                    if (!isSelected) {
                        label.setForeground(new Color(0xD4A017));
                    }
                    break;
                case "Deleted":
                    label.setText("D");
                    if (!isSelected) {
                        label.setForeground(new Color(0xCC0000));
                    }
                    break;
                case "Uncommitted":
                    label.setText("U");
                    if (!isSelected) {
                        label.setForeground(new Color(0xE67E22));
                    }
                    break;
                default:
                    label.setText(type);
                    if (!isSelected) {
                        label.setForeground(table.getForeground());
                    }
                    break;
            }
            return label;
        }
    }
}
