package com.operametrix.ignition.git;

import com.operametrix.ignition.git.components.SelectAllHeader;
import com.operametrix.ignition.git.utils.IconUtils;
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

public class SourceControlPanel extends JPanel {

    private final JTextArea commitMessageArea;
    private final JButton commitButton;
    private final JTable changesTable;
    private final JLabel changesCountLabel;

    private Runnable onRefreshRequested;
    private BiConsumer<String, String> onDiffRequested;
    private Consumer<List<String>> onDiscardRequested;
    private BiConsumer<List<String>, String> onCommitRequested;

    public SourceControlPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Top toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_history.svg", "Refresh", () -> {
            if (onRefreshRequested != null) onRefreshRequested.run();
        }));
        add(toolbar, BorderLayout.NORTH);

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

        commitButton = new JButton("Commit");
        commitButton.setBackground(new Color(0x4E8EF7));
        commitButton.setForeground(Color.WHITE);
        commitButton.setFocusPainted(false);
        commitButton.addActionListener(e -> {
            if (onCommitRequested != null) {
                List<String> selected = getSelectedResources();
                String message = commitMessageArea.getText().trim();
                if (!selected.isEmpty() && !message.isEmpty()) {
                    onCommitRequested.accept(selected, message);
                    commitMessageArea.setText("");
                }
            }
        });
        commitSection.add(commitButton, BorderLayout.SOUTH);
        centerPanel.add(commitSection, BorderLayout.NORTH);

        // Changes count label
        changesCountLabel = new JLabel("Changes (0)");
        changesCountLabel.setFont(changesCountLabel.getFont().deriveFont(Font.BOLD));
        changesCountLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));

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
                            SourceControlPanel.this,
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
        tableSection.add(changesCountLabel, BorderLayout.NORTH);
        tableSection.add(new JScrollPane(changesTable), BorderLayout.CENTER);
        centerPanel.add(tableSection, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    private JButton createToolbarButton(String iconPath, String tooltip, Runnable action) {
        JButton button = new JButton(IconUtils.getIcon(iconPath));
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(2, 2, 2, 2));
        button.addActionListener(e -> action.run());
        return button;
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
            String[] columnNames = {"", "Resource", "Type"};
            Object[][] data = new Object[ds.getRowCount()][];
            for (int i = 0; i < ds.getRowCount(); i++) {
                String resource = (String) ds.getValueAt(i, "resource");
                String type = (String) ds.getValueAt(i, "type");
                data[i] = new Object[]{Boolean.FALSE, resource, type};
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

    public void setOnDiffRequested(BiConsumer<String, String> onDiffRequested) {
        this.onDiffRequested = onDiffRequested;
    }

    public void setOnDiscardRequested(Consumer<List<String>> onDiscardRequested) {
        this.onDiscardRequested = onDiscardRequested;
    }

    public void setOnCommitRequested(BiConsumer<List<String>, String> onCommitRequested) {
        this.onCommitRequested = onCommitRequested;
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
