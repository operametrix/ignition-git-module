package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Paginated commit history popup. Displays a table of commits (short hash, author,
 * date, message) with Load More pagination, Refresh, and Close buttons.
 * Double-clicking a commit row triggers {@link #onCommitSelected} which is
 * overridden by {@link com.operametrix.ignition.git.managers.GitActionManager}
 * to open the {@link CommitDetailPopup}.
 */
public class HistoryPopup extends JFrame {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static final int PAGE_SIZE = 50;

    private DefaultTableModel tableModel;
    private JTable table;
    private JButton loadMoreBtn;
    private JLabel statusLabel;
    private List<String> fullHashes = new ArrayList<>();

    public HistoryPopup(Dataset data, Component parent) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/operametrix/ignition/git/icons/ic_history.svg");
            if (iconStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
                setIconImage(icon.getImage());
            }
        } catch (IOException e) {
            logger.trace(e.toString(), e);
        }

        setTitle("Commit History");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());
        setData(data, false);

        setSize(800, 600);
        setMinimumSize(new Dimension(600, 400));
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Table
        tableModel = new DefaultTableModel(new String[]{"Hash", "Author", "Date", "Message"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(400);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < fullHashes.size()) {
                        String fullHash = fullHashes.get(row);
                        String shortHash = (String) tableModel.getValueAt(row, 0);
                        String author = (String) tableModel.getValueAt(row, 1);
                        String date = (String) tableModel.getValueAt(row, 2);
                        String message = (String) tableModel.getValueAt(row, 3);
                        onCommitSelected(fullHash, shortHash, message, author, date);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        main.add(scrollPane, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        statusLabel = new JLabel("Showing 0 commits");
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        loadMoreBtn = new JButton("Load More");
        loadMoreBtn.setBackground(new Color(71, 137, 199));
        loadMoreBtn.setForeground(Color.WHITE);
        loadMoreBtn.addActionListener(e -> onLoadMore());

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> onRefresh());

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(loadMoreBtn);
        buttonPanel.add(refreshBtn);
        buttonPanel.add(closeBtn);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        main.add(bottomPanel, BorderLayout.SOUTH);

        return main;
    }

    /**
     * Populate (or append to) the commit table from a Dataset.
     *
     * @param data   Dataset with columns: hash, shortHash, author, date, message
     * @param append if true, rows are appended; if false, the table is cleared first
     */
    public void setData(Dataset data, boolean append) {
        if (!append) {
            tableModel.setRowCount(0);
            fullHashes.clear();
        }

        if (data != null) {
            for (int i = 0; i < data.getRowCount(); i++) {
                String fullHash = (String) data.getValueAt(i, "hash");
                String shortHash = (String) data.getValueAt(i, "shortHash");
                String author = (String) data.getValueAt(i, "author");
                String date = (String) data.getValueAt(i, "date");
                String message = (String) data.getValueAt(i, "message");

                fullHashes.add(fullHash);
                tableModel.addRow(new Object[]{shortHash, author, date, message});
            }

            loadMoreBtn.setEnabled(data.getRowCount() >= PAGE_SIZE);
        }

        statusLabel.setText("Showing " + tableModel.getRowCount() + " commits");
    }

    public int getCurrentOffset() {
        return tableModel.getRowCount();
    }

    public int getPageSize() {
        return PAGE_SIZE;
    }

    public void onCommitSelected(String fullHash, String shortHash, String message,
                                  String author, String date) {
    }

    public void onLoadMore() {
    }

    public void onRefresh() {
    }
}
