package com.operametrix.ignition.git;

import com.operametrix.ignition.git.utils.IconUtils;
import com.inductiveautomation.ignition.client.icons.VectorIcons;
import com.inductiveautomation.ignition.common.Dataset;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Commit history log panel showing the current branch's commits with a
 * vscode-git-graph-style commit graph, message, author, date, and colored
 * ref badges. Designed to be embedded as a dockable tab alongside the Project
 * Browser and Changes panel.
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

    // Graph geometry
    private static final int LANE_WIDTH = 14;
    private static final int GRAPH_PAD = 6;
    private static final int DOT_RADIUS = 4;

    private final JTable historyTable;
    private final JButton loadMoreButton;
    private final JLabel statusLabel;
    private final List<CommitNode> nodes = new ArrayList<>();
    private final List<GraphRow> graphRows = new ArrayList<>();
    private int laneCount = 1;

    private Runnable onPushRequested;
    private Runnable onFetchRequested;
    private Runnable onPullRequested;
    private Runnable onRefreshRequested;
    private Runnable onLoadMore;
    private Consumer<CommitNode> onCommitSelected;
    private Consumer<CommitNode> onRevertRequested;
    private Consumer<CommitNode> onCheckoutRequested;

    public HistoryPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Top toolbar: Refresh, Push, Fetch, Pull
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        toolbar.add(createToolbarButton(VectorIcons.get("refresh"), "Refresh", () -> {
            if (onRefreshRequested != null) onRefreshRequested.run();
        }));
        toolbar.add(createToolbarButton(IconUtils.getIcon("ic_push"), "Push", () -> {
            if (onPushRequested != null) onPushRequested.run();
        }));
        toolbar.add(createToolbarButton(IconUtils.getIcon("ic_fetch"), "Fetch", () -> {
            if (onFetchRequested != null) onFetchRequested.run();
        }));
        toolbar.add(createToolbarButton(IconUtils.getIcon("ic_pull"), "Pull", () -> {
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

        // Double-click to view commit detail; right-click for context menu
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

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = historyTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < nodes.size()) {
                        historyTable.setRowSelectionInterval(row, row);
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem checkoutItem = new JMenuItem("Checkout Commit");
                        checkoutItem.addActionListener(ev -> {
                            if (onCheckoutRequested != null) onCheckoutRequested.accept(nodes.get(row));
                        });
                        menu.add(checkoutItem);
                        JMenuItem revertItem = new JMenuItem("Revert Commit");
                        revertItem.addActionListener(ev -> {
                            if (onRevertRequested != null) onRevertRequested.accept(nodes.get(row));
                        });
                        menu.add(revertItem);
                        menu.show(historyTable, e.getX(), e.getY());
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
        // Column 0: Graph (fixed width derived from lane count)
        int graphWidth = GRAPH_PAD * 2 + laneCount * LANE_WIDTH;
        TableColumn graphCol = historyTable.getColumnModel().getColumn(0);
        graphCol.setPreferredWidth(graphWidth);
        graphCol.setMinWidth(graphWidth);
        graphCol.setMaxWidth(graphWidth);
        graphCol.setCellRenderer(new GraphRenderer());

        // Column 1: Message
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        // Column 2: Author
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        historyTable.getColumnModel().getColumn(2).setMaxWidth(180);
        DefaultTableCellRenderer authorRenderer = new DefaultTableCellRenderer();
        authorRenderer.setForeground(Color.GRAY);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(authorRenderer);

        // Column 3: Refs (colored badges)
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(3).setMaxWidth(250);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(new RefsRenderer());
    }

    /**
     * Update the history data. Safe to call from any thread.
     *
     * @param ds     Dataset with columns: hash, shortHash, author, date, message, refs, parents
     * @param append true to append to existing data, false to replace
     */
    public void setData(Dataset ds, boolean append) {
        SwingUtilities.invokeLater(() -> {
            if (!append) {
                nodes.clear();
            }

            boolean hasParents = datasetHasColumn(ds, "parents");
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
                String parentsStr = hasParents ? (String) ds.getValueAt(i, "parents") : null;
                node.parents = (parentsStr != null && !parentsStr.trim().isEmpty())
                        ? parentsStr.trim().split("\\s+") : new String[0];
                nodes.add(node);
            }

            computeGraph();

            HistoryTableModel model = (HistoryTableModel) historyTable.getModel();
            model.fireTableDataChanged();
            configureColumns();

            statusLabel.setText(nodes.size() + " commits");
        });
    }

    private static boolean datasetHasColumn(Dataset ds, String name) {
        for (int c = 0; c < ds.getColumnCount(); c++) {
            if (name.equals(ds.getColumnName(c))) return true;
        }
        return false;
    }

    public int getCurrentOffset() {
        return nodes.size();
    }

    // --- Graph layout ---

    /**
     * Assigns each commit (row) to a swimlane and records the line segments that
     * cross its row, using the standard incremental lane-tracking algorithm:
     * lanes flow top-to-bottom, each "waiting" for a specific commit hash. When a
     * commit is reached, its first parent continues in the same lane and any
     * additional (merge) parents branch into new lanes.
     */
    private void computeGraph() {
        graphRows.clear();
        int maxLanes = 1;

        // lanes.get(k) = hash the lane at column k is currently routing toward
        // (null = free slot). Positions are stable across rows so passthrough
        // lanes render as straight vertical lines.
        List<String> lanes = new ArrayList<>();

        for (CommitNode node : nodes) {
            String h = node.hash;
            List<String> top = new ArrayList<>(lanes);

            // Locate (or open) this commit's lane.
            int commitLane = top.indexOf(h);
            if (commitLane < 0) {
                commitLane = firstFreeLane(lanes);
                if (commitLane == lanes.size()) {
                    lanes.add(h);
                } else {
                    lanes.set(commitLane, h);
                }
            }

            // First parent continues in the commit's lane; a root commit closes it.
            if (node.parents.length >= 1) {
                lanes.set(commitLane, node.parents[0]);
            } else {
                lanes.set(commitLane, null);
            }

            // Duplicate incoming children (other lanes awaiting this commit) end here.
            for (int k = 0; k < top.size(); k++) {
                if (k != commitLane && h.equals(top.get(k))) {
                    lanes.set(k, null);
                }
            }

            // Additional (merge) parents branch into their own lanes, reusing an
            // existing lane already awaiting that parent when possible.
            List<Integer> outgoing = new ArrayList<>();
            if (node.parents.length >= 1) {
                outgoing.add(commitLane);
            }
            for (int p = 1; p < node.parents.length; p++) {
                String pp = node.parents[p];
                int idx = lanes.indexOf(pp);
                if (idx < 0) {
                    idx = firstFreeLane(lanes);
                    if (idx == lanes.size()) {
                        lanes.add(pp);
                    } else {
                        lanes.set(idx, pp);
                    }
                }
                outgoing.add(idx);
            }

            // Incoming lines: every top lane that was awaiting this commit.
            List<Integer> incoming = new ArrayList<>();
            for (int k = 0; k < top.size(); k++) {
                if (h.equals(top.get(k))) incoming.add(k);
            }

            // Passthrough lines: top lanes flowing past this row untouched.
            List<Integer> passthrough = new ArrayList<>();
            for (int k = 0; k < top.size(); k++) {
                String t = top.get(k);
                if (t != null && !t.equals(h) && k != commitLane) {
                    passthrough.add(k);
                }
            }

            trimTrailingFree(lanes);

            GraphRow gr = new GraphRow();
            gr.nodeLane = commitLane;
            gr.incoming = toIntArray(incoming);
            gr.passthrough = toIntArray(passthrough);
            gr.outgoing = toIntArray(outgoing);
            graphRows.add(gr);

            maxLanes = Math.max(maxLanes, Math.max(lanes.size(), commitLane + 1));
            for (int idx : outgoing) maxLanes = Math.max(maxLanes, idx + 1);
        }

        laneCount = Math.max(1, maxLanes);
    }

    private static int firstFreeLane(List<String> lanes) {
        for (int k = 0; k < lanes.size(); k++) {
            if (lanes.get(k) == null) return k;
        }
        return lanes.size();
    }

    private static void trimTrailingFree(List<String> lanes) {
        for (int k = lanes.size() - 1; k >= 0 && lanes.get(k) == null; k--) {
            lanes.remove(k);
        }
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static Color laneColor(int lane) {
        return LABEL_COLORS[lane % LABEL_COLORS.length];
    }

    // --- Inner classes ---

    public static class CommitNode {
        public String hash;
        public String shortHash;
        public String author;
        public String date;
        public String message;
        public String[] refs;
        public String[] parents = new String[0];
    }

    /** Per-row graph geometry computed by {@link #computeGraph()}. */
    private static class GraphRow {
        int nodeLane;
        int[] incoming;      // top lane indices feeding into the node
        int[] passthrough;   // top lane indices flowing straight through the row
        int[] outgoing;      // bottom lane indices the node's parents flow into
    }

    private class HistoryTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Graph", "Message", "Author", "Refs"};

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
                    return rowIndex;  // Rendered by GraphRenderer
                case 1:
                    return node.message;
                case 2:
                    return node.author;
                case 3:
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
     * Renders the commit-graph column: colored swimlane lines connecting each
     * commit to its parents, with a filled node dot on the commit's lane.
     */
    private class GraphRenderer extends JPanel implements TableCellRenderer {
        private int rowIndex = -1;

        public GraphRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus,
                                                        int row, int column) {
            this.rowIndex = (value instanceof Integer) ? (Integer) value : -1;
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }

        private int laneX(int lane) {
            return GRAPH_PAD + lane * LANE_WIDTH + LANE_WIDTH / 2;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (rowIndex < 0 || rowIndex >= graphRows.size()) return;

            GraphRow gr = graphRows.get(rowIndex);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int h = getHeight();
            int mid = h / 2;
            int nodeX = laneX(gr.nodeLane);

            // Passthrough lanes: straight vertical lines spanning the full row.
            for (int lane : gr.passthrough) {
                int x = laneX(lane);
                g2.setColor(laneColor(lane));
                g2.draw(new Line2D.Float(x, 0, x, h));
            }

            // Incoming: from each feeding lane at the top edge to the node center.
            for (int lane : gr.incoming) {
                int x = laneX(lane);
                g2.setColor(laneColor(lane));
                g2.draw(new Line2D.Float(x, 0, nodeX, mid));
            }

            // Outgoing: from the node center to each parent lane at the bottom edge.
            for (int lane : gr.outgoing) {
                int x = laneX(lane);
                g2.setColor(laneColor(lane));
                g2.draw(new Line2D.Float(nodeX, mid, x, h));
            }

            // Node dot.
            g2.setColor(laneColor(gr.nodeLane));
            g2.fillOval(nodeX - DOT_RADIUS, mid - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
            g2.setColor(getBackground());
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval(nodeX - DOT_RADIUS, mid - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);

            g2.dispose();
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

    private JButton createToolbarButton(Icon icon, String tooltip, Runnable action) {
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
        button.addActionListener(e -> action.run());
        return button;
    }

    // --- Callback setters ---

    public void setOnPushRequested(Runnable onPushRequested) {
        this.onPushRequested = onPushRequested;
    }

    public void setOnFetchRequested(Runnable onFetchRequested) {
        this.onFetchRequested = onFetchRequested;
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

    public void setOnRevertRequested(Consumer<CommitNode> onRevertRequested) {
        this.onRevertRequested = onRevertRequested;
    }

    public void setOnCheckoutRequested(Consumer<CommitNode> onCheckoutRequested) {
        this.onCheckoutRequested = onCheckoutRequested;
    }
}
