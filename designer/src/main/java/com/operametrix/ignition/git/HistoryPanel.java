package com.operametrix.ignition.git;

import com.operametrix.ignition.git.utils.IconUtils;
import com.inductiveautomation.ignition.common.Dataset;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Commit history log panel showing the current branch's commits with
 * message, author, date, and colored ref badges. Designed to be embedded
 * as a dockable tab alongside the Project Browser and Changes panel.
 */
public class HistoryPanel extends JPanel {

    public static final int PAGE_SIZE = 50;

    private static final Color[] LABEL_COLORS = {
            new Color(0x4E8EF7),  // blue
            new Color(0x28A745),  // green
            new Color(0xE36209),  // orange
            new Color(0x6F42C1),  // purple
            new Color(0xE74C3C),  // red
            new Color(0x17A2B8),  // teal
            new Color(0xD4A017),  // amber
            new Color(0xE91E8C),  // pink
    };

    private final JTable historyTable;
    private final JButton loadMoreButton;
    private final JLabel statusLabel;
    private final List<CommitNode> nodes = new ArrayList<>();

    private Runnable onPushRequested;
    private Runnable onPullRequested;
    private Runnable onRefreshRequested;
    private Runnable onLoadMore;
    private Consumer<CommitNode> onCommitSelected;

    public HistoryPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Top toolbar: Refresh, Push, Pull
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_history.svg", "Refresh", () -> {
            if (onRefreshRequested != null) onRefreshRequested.run();
        }));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_push.svg", "Push", () -> {
            if (onPushRequested != null) onPushRequested.run();
        }));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_pull.svg", "Pull", () -> {
            if (onPullRequested != null) onPullRequested.run();
        }));
        add(toolbar, BorderLayout.NORTH);

        // Center: history table
        historyTable = new JTable(new HistoryTableModel());
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setRowHeight(24);
        historyTable.setShowGrid(false);
        historyTable.setIntercellSpacing(new Dimension(0, 0));

        configureColumns();

        // Double-click to view commit detail
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = historyTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < nodes.size() && onCommitSelected != null) {
                        onCommitSelected.accept(nodes.get(row));
                    }
                }
            }
        });

        // Tooltip showing full hash on hover
        historyTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = historyTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < nodes.size()) {
                    CommitNode node = nodes.get(row);
                    historyTable.setToolTipText(node.shortHash + "  " + node.author + "  " + node.date);
                } else {
                    historyTable.setToolTipText(null);
                }
            }
        });

        add(new JScrollPane(historyTable), BorderLayout.CENTER);

        // Bottom: Load More + status
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        loadMoreButton = new JButton("Load More");
        loadMoreButton.addActionListener(e -> {
            if (onLoadMore != null) onLoadMore.run();
        });
        bottomPanel.add(loadMoreButton);

        statusLabel = new JLabel("");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        bottomPanel.add(statusLabel);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void configureColumns() {
        // Column 0: Message
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(300);

        // Column 1: Author
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        historyTable.getColumnModel().getColumn(1).setMaxWidth(180);
        DefaultTableCellRenderer authorRenderer = new DefaultTableCellRenderer();
        authorRenderer.setForeground(Color.GRAY);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(authorRenderer);

        // Column 2: Refs (colored badges)
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(2).setMaxWidth(250);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(new RefsRenderer());
    }

    /**
     * Update the history data. Safe to call from any thread.
     *
     * @param ds     Dataset with columns: hash, shortHash, author, date, message, refs
     * @param append true to append to existing data, false to replace
     */
    public void setData(Dataset ds, boolean append) {
        SwingUtilities.invokeLater(() -> {
            if (!append) {
                nodes.clear();
            }

            for (int i = 0; i < ds.getRowCount(); i++) {
                CommitNode node = new CommitNode();
                node.hash = (String) ds.getValueAt(i, "hash");
                node.shortHash = (String) ds.getValueAt(i, "shortHash");
                node.author = (String) ds.getValueAt(i, "author");
                node.date = (String) ds.getValueAt(i, "date");
                node.message = (String) ds.getValueAt(i, "message");
                String refsStr = (String) ds.getValueAt(i, "refs");
                node.refs = (refsStr != null && !refsStr.isEmpty())
                        ? refsStr.split(",") : new String[0];
                nodes.add(node);
            }

            HistoryTableModel model = (HistoryTableModel) historyTable.getModel();
            model.fireTableDataChanged();
            configureColumns();

            statusLabel.setText(nodes.size() + " commits");
        });
    }

    public int getCurrentOffset() {
        return nodes.size();
    }

    // --- Inner classes ---

    public static class CommitNode {
        public String hash;
        public String shortHash;
        public String author;
        public String date;
        public String message;
        public String[] refs;
    }

    private class HistoryTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Message", "Author", "Refs"};

        @Override
        public int getRowCount() {
            return nodes.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= nodes.size()) return "";
            CommitNode node = nodes.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return node.message;
                case 1:
                    return node.author;
                case 2:
                    return node;  // Rendered by RefsRenderer
                default:
                    return "";
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }

    /**
     * Custom renderer for the Refs column. Draws colored rounded-rect
     * badges for each branch/tag ref decoration.
     */
    private class RefsRenderer extends JPanel implements TableCellRenderer {
        private CommitNode currentNode;

        public RefsRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus,
                                                        int row, int column) {
            this.currentNode = (value instanceof CommitNode) ? (CommitNode) value : null;
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (currentNode == null || currentNode.refs == null || currentNode.refs.length == 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Font labelFont = getFont().deriveFont(Font.BOLD, 10f);
            g2.setFont(labelFont);
            FontMetrics fm = g2.getFontMetrics();
            int labelH = fm.getHeight();
            int h = getHeight();
            int labelY = (h - labelH) / 2;
            int padX = 4, padY = 2;
            int x = 4;

            for (int i = 0; i < currentNode.refs.length; i++) {
                String ref = currentNode.refs[i];
                int textW = fm.stringWidth(ref);
                int badgeW = textW + padX * 2;
                int badgeH = labelH + padY;

                Color bg = LABEL_COLORS[i % LABEL_COLORS.length];
                g2.setColor(bg);
                g2.fillRoundRect(x, labelY, badgeW, badgeH, 6, 6);
                g2.setColor(Color.WHITE);
                g2.drawString(ref, x + padX, labelY + fm.getAscent() + padY / 2);

                x += badgeW + 3;
            }

            g2.dispose();
        }
    }

    // --- Toolbar helper ---

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

    // --- Callback setters ---

    public void setOnPushRequested(Runnable onPushRequested) {
        this.onPushRequested = onPushRequested;
    }

    public void setOnPullRequested(Runnable onPullRequested) {
        this.onPullRequested = onPullRequested;
    }

    public void setOnRefreshRequested(Runnable onRefreshRequested) {
        this.onRefreshRequested = onRefreshRequested;
    }

    public void setOnLoadMore(Runnable onLoadMore) {
        this.onLoadMore = onLoadMore;
    }

    public void setOnCommitSelected(Consumer<CommitNode> onCommitSelected) {
        this.onCommitSelected = onCommitSelected;
    }
}
