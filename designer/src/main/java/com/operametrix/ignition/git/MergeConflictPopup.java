package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Popup shown when a pull results in merge conflicts. Lists conflicting files
 * with per-file "Accept Ours" / "Accept Theirs" resolution and global actions
 * (accept all ours, accept all theirs, abort merge, complete merge).
 * <p>
 * Follows the {@link CommitDetailPopup} pattern: extends JDialog, BorderLayout,
 * empty callback methods overridden by {@link com.operametrix.ignition.git.managers.GitActionManager}.
 * Not cached — created fresh for each conflict occurrence.
 */
public class MergeConflictPopup extends JDialog {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String STATUS_UNRESOLVED = "Unresolved";
    private static final String STATUS_OURS = "Accepted Ours";
    private static final String STATUS_THEIRS = "Accepted Theirs";

    private final Map<String, String> resolutionState = new LinkedHashMap<>();
    private DefaultTableModel tableModel;
    private JTable table;
    private JButton completeMergeBtn;

    public MergeConflictPopup(List<String> conflictingFiles, Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/operametrix/ignition/git/icons/ic_pull.svg");
            if (iconStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
                setIconImage(icon.getImage());
            }
        } catch (IOException e) {
            logger.trace(e.toString(), e);
        }

        for (String file : conflictingFiles) {
            resolutionState.put(file, STATUS_UNRESOLVED);
        }

        setTitle("Merge Conflicts");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onAbortMerge();
            }
        });

        setContentPane(buildUI(conflictingFiles));
        setSize(700, 450);
        setMinimumSize(new Dimension(550, 350));
        setVisible(true);
        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI(List<String> conflictingFiles) {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // North: warning panel
        JPanel warningPanel = new JPanel(new BorderLayout(8, 0));
        warningPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        JLabel warningIcon = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
        warningPanel.add(warningIcon, BorderLayout.WEST);
        JPanel warningText = new JPanel(new GridLayout(2, 1));
        JLabel titleLabel = new JLabel("Merge Conflicts Detected");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        warningText.add(titleLabel);
        warningText.add(new JLabel(conflictingFiles.size() + " file(s) have conflicts that must be resolved."));
        warningPanel.add(warningText, BorderLayout.CENTER);
        main.add(warningPanel, BorderLayout.NORTH);

        // Center: file table
        tableModel = new DefaultTableModel(new String[]{"File Path", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(450);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);

        // Custom renderer for the Status column
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    String status = (String) value;
                    if (STATUS_UNRESOLVED.equals(status)) {
                        c.setForeground(new Color(180, 0, 0));
                    } else if (STATUS_OURS.equals(status)) {
                        c.setForeground(new Color(0, 0, 180));
                    } else if (STATUS_THEIRS.equals(status)) {
                        c.setForeground(new Color(0, 130, 0));
                    }
                }
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });

        for (String file : conflictingFiles) {
            tableModel.addRow(new Object[]{file, STATUS_UNRESOLVED});
        }

        // Double-click to view diff
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String filePath = (String) tableModel.getValueAt(row, 0);
                        onViewDiff(filePath);
                    }
                }
            }
        });

        main.add(new JScrollPane(table), BorderLayout.CENTER);

        // South: button rows
        JPanel buttonArea = new JPanel();
        buttonArea.setLayout(new BoxLayout(buttonArea, BoxLayout.Y_AXIS));

        // Row 1: per-file actions
        JPanel perFileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        JButton viewDiffBtn = new JButton("View Diff");
        viewDiffBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                onViewDiff((String) tableModel.getValueAt(row, 0));
            }
        });
        perFileRow.add(viewDiffBtn);

        JButton acceptOursBtn = new JButton("Accept Ours");
        acceptOursBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                onResolveConflict((String) tableModel.getValueAt(row, 0), "OURS");
            }
        });
        perFileRow.add(acceptOursBtn);

        JButton acceptTheirsBtn = new JButton("Accept Theirs");
        acceptTheirsBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                onResolveConflict((String) tableModel.getValueAt(row, 0), "THEIRS");
            }
        });
        perFileRow.add(acceptTheirsBtn);
        buttonArea.add(perFileRow);

        // Row 2: global actions
        JPanel globalRow = new JPanel(new BorderLayout());
        JPanel leftGlobal = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        JButton allOursBtn = new JButton("Accept All Ours");
        allOursBtn.addActionListener(e -> onResolveAllConflicts("OURS"));
        leftGlobal.add(allOursBtn);

        JButton allTheirsBtn = new JButton("Accept All Theirs");
        allTheirsBtn.addActionListener(e -> onResolveAllConflicts("THEIRS"));
        leftGlobal.add(allTheirsBtn);
        globalRow.add(leftGlobal, BorderLayout.WEST);

        JPanel rightGlobal = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 3));
        JButton abortBtn = new JButton("Abort Merge");
        abortBtn.addActionListener(e -> onAbortMerge());
        rightGlobal.add(abortBtn);

        completeMergeBtn = new JButton("Complete Merge");
        completeMergeBtn.setEnabled(false);
        completeMergeBtn.addActionListener(e -> onCompleteMerge());
        rightGlobal.add(completeMergeBtn);
        globalRow.add(rightGlobal, BorderLayout.EAST);

        buttonArea.add(globalRow);
        main.add(buttonArea, BorderLayout.SOUTH);

        return main;
    }

    /** Update the resolution status of a file in the table. */
    public void markResolved(String filePath, String stage) {
        String statusLabel = "OURS".equals(stage) ? STATUS_OURS : STATUS_THEIRS;
        resolutionState.put(filePath, statusLabel);
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (filePath.equals(tableModel.getValueAt(i, 0))) {
                tableModel.setValueAt(statusLabel, i, 1);
                break;
            }
        }
        updateCompleteMergeButton();
    }

    /** Mark all files as resolved with the given stage. */
    public void markAllResolved(String stage) {
        String statusLabel = "OURS".equals(stage) ? STATUS_OURS : STATUS_THEIRS;
        for (Map.Entry<String, String> entry : resolutionState.entrySet()) {
            entry.setValue(statusLabel);
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(statusLabel, i, 1);
        }
        updateCompleteMergeButton();
    }

    private void updateCompleteMergeButton() {
        boolean allResolved = resolutionState.values().stream()
                .noneMatch(STATUS_UNRESOLVED::equals);
        completeMergeBtn.setEnabled(allResolved);
    }

    // ── Callbacks (overridden by GitActionManager) ─────────────────────

    public void onResolveConflict(String filePath, String stage) {
    }

    public void onResolveAllConflicts(String stage) {
    }

    public void onAbortMerge() {
    }

    public void onCompleteMerge() {
    }

    public void onViewDiff(String filePath) {
    }
}
