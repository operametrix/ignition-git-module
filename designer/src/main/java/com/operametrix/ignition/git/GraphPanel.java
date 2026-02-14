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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * VS Code-style commit graph panel showing a visual DAG with colored lanes,
 * commit dots, and connecting lines. Designed to be embedded as a dockable
 * tab alongside the Project Browser and Changes panel.
 */
public class GraphPanel extends JPanel {

    public static final int PAGE_SIZE = 50;

    private static final Color[] LANE_COLORS = {
            new Color(0x4E8EF7),  // blue
            new Color(0x28A745),  // green
            new Color(0xE36209),  // orange
            new Color(0x6F42C1),  // purple
            new Color(0xE74C3C),  // red
            new Color(0x17A2B8),  // teal
            new Color(0xD4A017),  // amber
            new Color(0xE91E8C),  // pink
    };

    private static final int LANE_WIDTH = 16;
    private static final int DOT_RADIUS = 4;
    private static final int DOT_X_OFFSET = 12;

    private final JTable graphTable;
    private final JButton loadMoreButton;
    private final JLabel statusLabel;
    private final List<CommitNode> nodes = new ArrayList<>();

    private Runnable onPushRequested;
    private Runnable onPullRequested;
    private Runnable onRefreshRequested;
    private Runnable onLoadMore;
    private Consumer<CommitNode> onCommitSelected;

    public GraphPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Top toolbar: Push, Pull, Refresh
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_push.svg", "Push", () -> {
            if (onPushRequested != null) onPushRequested.run();
        }));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_pull.svg", "Pull", () -> {
            if (onPullRequested != null) onPullRequested.run();
        }));
        toolbar.add(createToolbarButton("/com/operametrix/ignition/git/icons/ic_history.svg", "Refresh", () -> {
            if (onRefreshRequested != null) onRefreshRequested.run();
        }));
        add(toolbar, BorderLayout.NORTH);

        // Center: graph table
        graphTable = new JTable(new GraphTableModel());
        graphTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        graphTable.getTableHeader().setReorderingAllowed(false);
        graphTable.setRowHeight(24);
        graphTable.setShowGrid(false);
        graphTable.setIntercellSpacing(new Dimension(0, 0));

        configureColumns();

        // Double-click to view commit detail
        graphTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = graphTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < nodes.size() && onCommitSelected != null) {
                        onCommitSelected.accept(nodes.get(row));
                    }
                }
            }
        });

        // Tooltip showing author and date on hover
        graphTable.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = graphTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < nodes.size()) {
                    CommitNode node = nodes.get(row);
                    graphTable.setToolTipText(node.author + "  " + node.date);
                } else {
                    graphTable.setToolTipText(null);
                }
            }
        });

        add(new JScrollPane(graphTable), BorderLayout.CENTER);

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
        graphTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        graphTable.getColumnModel().getColumn(0).setMinWidth(60);
        graphTable.getColumnModel().getColumn(0).setMaxWidth(300);
        graphTable.getColumnModel().getColumn(0).setCellRenderer(new GraphCellRenderer());
        graphTable.getColumnModel().getColumn(1).setPreferredWidth(300);
        graphTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        graphTable.getColumnModel().getColumn(2).setMaxWidth(150);
        DefaultTableCellRenderer authorRenderer = new DefaultTableCellRenderer();
        authorRenderer.setForeground(Color.GRAY);
        graphTable.getColumnModel().getColumn(2).setCellRenderer(authorRenderer);
    }

    /**
     * Update the graph data. Safe to call from any thread.
     *
     * @param ds     Dataset with columns: hash, shortHash, author, date, message, parents
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
                String parentsStr = (String) ds.getValueAt(i, "parents");
                node.parentHashes = (parentsStr != null && !parentsStr.isEmpty())
                        ? parentsStr.split(",") : new String[0];
                String refsStr = (String) ds.getValueAt(i, "refs");
                node.refs = (refsStr != null && !refsStr.isEmpty())
                        ? refsStr.split(",") : new String[0];
                nodes.add(node);
            }

            computeLanes();

            GraphTableModel model = (GraphTableModel) graphTable.getModel();
            model.fireTableDataChanged();
            configureColumns();

            statusLabel.setText(nodes.size() + " commits");
        });
    }

    public int getCurrentOffset() {
        return nodes.size();
    }

    // --- Lane computation ---

    private void computeLanes() {
        // activeLanes[i] = hash expected at that lane slot, or null if free
        List<String> activeLanes = new ArrayList<>();

        // Build a hash->index lookup for nodes in this list
        Map<String, Integer> hashToIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            hashToIndex.put(nodes.get(i).hash, i);
        }

        for (int row = 0; row < nodes.size(); row++) {
            CommitNode node = nodes.get(row);

            // Find which lane expects this commit
            int assignedLane = activeLanes.indexOf(node.hash);
            if (assignedLane < 0) {
                // New lane — find first free slot or append
                assignedLane = activeLanes.indexOf(null);
                if (assignedLane < 0) {
                    assignedLane = activeLanes.size();
                    activeLanes.add(null);
                }
            }

            node.lane = assignedLane;
            node.color = LANE_COLORS[assignedLane % LANE_COLORS.length];

            // Find other lanes that also converge to this commit (fork points)
            List<Integer> converging = new ArrayList<>();
            for (int i = 0; i < activeLanes.size(); i++) {
                if (i != assignedLane && node.hash.equals(activeLanes.get(i))) {
                    converging.add(i);
                }
            }
            node.convergingLanes = converging.stream().mapToInt(Integer::intValue).toArray();

            // Record the active lanes *before* updating for child rendering
            node.activeLanesBefore = new ArrayList<>(activeLanes);

            // Close converging lanes (they've reached their target)
            for (int cl : node.convergingLanes) {
                activeLanes.set(cl, null);
            }

            // Update this lane to expect first parent (continuation)
            if (node.parentHashes.length > 0) {
                activeLanes.set(assignedLane, node.parentHashes[0]);
            } else {
                // No parents (root commit) — close the lane
                activeLanes.set(assignedLane, null);
            }

            // Additional parents (merge) — allocate lanes
            node.mergeTargetLanes = new int[Math.max(0, node.parentHashes.length - 1)];
            for (int p = 1; p < node.parentHashes.length; p++) {
                String parentHash = node.parentHashes[p];
                int parentLane = activeLanes.indexOf(parentHash);
                if (parentLane < 0) {
                    // Allocate a new lane for this merge parent
                    parentLane = activeLanes.indexOf(null);
                    if (parentLane < 0) {
                        parentLane = activeLanes.size();
                        activeLanes.add(null);
                    }
                    activeLanes.set(parentLane, parentHash);
                }
                node.mergeTargetLanes[p - 1] = parentLane;
            }

            // Record active lanes after update
            node.activeLanesAfter = new ArrayList<>(activeLanes);
        }
    }

    // --- Inner classes ---

    public static class CommitNode {
        public String hash;
        public String shortHash;
        public String author;
        public String date;
        public String message;
        public String[] parentHashes;
        public String[] refs;
        int lane;
        Color color;
        int[] mergeTargetLanes;
        int[] convergingLanes;
        List<String> activeLanesBefore;
        List<String> activeLanesAfter;
    }

    private class GraphTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"", "Message", "Author"};

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
                    return "";  // Rendered by GraphCellRenderer
                case 1:
                    return node.message;
                case 2:
                    return node.author;
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
     * Custom renderer for the Graph column. Draws lane lines, commit dots,
     * and merge/branch diagonal connections.
     */
    private class GraphCellRenderer extends JPanel implements TableCellRenderer {
        private int currentRow = -1;
        private boolean isSelected;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus,
                                                        int row, int column) {
            this.currentRow = row;
            this.isSelected = isSelected;
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
            if (currentRow < 0 || currentRow >= nodes.size()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));

            CommitNode node = nodes.get(currentRow);
            int h = getHeight();
            int midY = h / 2;

            // Draw vertical lane lines for all active lanes (before state)
            List<String> lanesBefore = node.activeLanesBefore;
            List<String> lanesAfter = node.activeLanesAfter;
            int maxLanes = Math.max(lanesBefore.size(), lanesAfter.size());

            for (int lane = 0; lane < maxLanes; lane++) {
                int x = lane * LANE_WIDTH + DOT_X_OFFSET;
                Color laneColor = LANE_COLORS[lane % LANE_COLORS.length];
                g2.setColor(laneColor);

                boolean activeBefore = lane < lanesBefore.size() && lanesBefore.get(lane) != null;
                boolean activeAfter = lane < lanesAfter.size() && lanesAfter.get(lane) != null;

                if (lane == node.lane) {
                    // This is the commit's lane
                    if (activeBefore) {
                        // Line from top to dot
                        g2.drawLine(x, 0, x, midY);
                    }
                    if (activeAfter) {
                        // Line from dot to bottom
                        g2.drawLine(x, midY, x, h);
                    }
                } else {
                    // Pass-through lane
                    if (activeBefore && activeAfter) {
                        g2.drawLine(x, 0, x, h);
                    } else if (activeBefore && !activeAfter) {
                        // Lane is being closed — draw to midpoint
                        g2.drawLine(x, 0, x, midY);
                    } else if (!activeBefore && activeAfter) {
                        // Lane is being opened — draw from midpoint
                        g2.drawLine(x, midY, x, h);
                    }
                }
            }

            // Draw converging diagonals (other branches joining this commit)
            if (node.convergingLanes != null) {
                for (int srcLane : node.convergingLanes) {
                    int fromX = srcLane * LANE_WIDTH + DOT_X_OFFSET;
                    int toX = node.lane * LANE_WIDTH + DOT_X_OFFSET;
                    Color convColor = LANE_COLORS[srcLane % LANE_COLORS.length];
                    g2.setColor(convColor);
                    g2.drawLine(fromX, 0, toX, midY);
                }
            }

            // Draw merge diagonals from commit dot to merge parent lanes
            if (node.mergeTargetLanes != null) {
                for (int targetLane : node.mergeTargetLanes) {
                    int fromX = node.lane * LANE_WIDTH + DOT_X_OFFSET;
                    int toX = targetLane * LANE_WIDTH + DOT_X_OFFSET;
                    Color mergeColor = LANE_COLORS[targetLane % LANE_COLORS.length];
                    g2.setColor(mergeColor);
                    g2.drawLine(fromX, midY, toX, h);
                }
            }

            // Draw commit dot
            int dotX = node.lane * LANE_WIDTH + DOT_X_OFFSET - DOT_RADIUS;
            int dotY = midY - DOT_RADIUS;
            g2.setColor(node.color);
            g2.fillOval(dotX, dotY, DOT_RADIUS * 2, DOT_RADIUS * 2);
            // White border for visibility
            g2.setColor(isSelected ? graphTable.getSelectionBackground() : getBackground());
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(dotX, dotY, DOT_RADIUS * 2, DOT_RADIUS * 2);

            // Draw branch ref labels to the right of the dot
            if (node.refs != null && node.refs.length > 0) {
                int labelX = (maxLanes) * LANE_WIDTH + DOT_X_OFFSET + 4;
                Font labelFont = getFont().deriveFont(Font.BOLD, 10f);
                g2.setFont(labelFont);
                FontMetrics fm = g2.getFontMetrics();
                int labelH = fm.getHeight();
                int labelY = midY - labelH / 2;

                for (int i = 0; i < node.refs.length; i++) {
                    String ref = node.refs[i];
                    int textW = fm.stringWidth(ref);
                    int padX = 4, padY = 2;
                    int badgeW = textW + padX * 2;
                    int badgeH = labelH + padY;

                    Color bg = LANE_COLORS[i % LANE_COLORS.length];
                    g2.setColor(bg);
                    g2.fillRoundRect(labelX, labelY, badgeW, badgeH, 6, 6);
                    g2.setColor(Color.WHITE);
                    g2.drawString(ref, labelX + padX, labelY + fm.getAscent() + padY / 2);

                    labelX += badgeW + 3;
                }
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
