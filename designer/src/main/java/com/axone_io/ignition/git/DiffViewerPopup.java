package com.axone_io.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DiffViewerPopup extends JFrame {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Color ADDED_COLOR = new Color(220, 255, 220);
    private static final Color REMOVED_COLOR = new Color(255, 220, 220);
    private static final Color LINE_NUMBER_COLOR = new Color(128, 128, 128);

    public DiffViewerPopup(String resourcePath, String oldContent, String newContent, Component parent) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/com/axone_io/ignition/git/icons/ic_commit.svg");
            if (iconStream != null) {
                ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
                setIconImage(icon.getImage());
            }
        } catch (IOException e) {
            logger.trace(e.toString(), e);
        }

        setTitle("Diff: " + resourcePath);
        setSize(900, 600);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Compute diff
        String[] oldLines = oldContent.isEmpty() ? new String[0] : oldContent.split("\n", -1);
        String[] newLines = newContent.isEmpty() ? new String[0] : newContent.split("\n", -1);
        List<DiffLine> diff = computeDiff(oldLines, newLines);

        // Build left and right panes
        JTextPane leftPane = new JTextPane();
        JTextPane rightPane = new JTextPane();
        leftPane.setEditable(false);
        rightPane.setEditable(false);

        populateDiffPane(leftPane, diff, true);
        populateDiffPane(rightPane, diff, false);

        // Headers
        JLabel leftHeader = new JLabel("HEAD (committed)", SwingConstants.CENTER);
        leftHeader.setFont(leftHeader.getFont().deriveFont(Font.BOLD));
        leftHeader.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel rightHeader = new JLabel("Working Tree", SwingConstants.CENTER);
        rightHeader.setFont(rightHeader.getFont().deriveFont(Font.BOLD));
        rightHeader.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        // Scroll panes
        JScrollPane leftScroll = new JScrollPane(leftPane);
        JScrollPane rightScroll = new JScrollPane(rightPane);

        // Synchronized scrolling
        synchronizeScrolling(leftScroll, rightScroll);

        // Left panel with header
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(leftHeader, BorderLayout.NORTH);
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        // Right panel with header
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(rightHeader, BorderLayout.NORTH);
        rightPanel.add(rightScroll, BorderLayout.CENTER);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Close button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        CommonUI.centerComponent(this, parent);
        setVisible(true);
        toFront();
    }

    private void synchronizeScrolling(JScrollPane left, JScrollPane right) {
        AdjustmentListener listener = new AdjustmentListener() {
            private boolean adjusting = false;

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if (adjusting) return;
                adjusting = true;
                JScrollBar source = (JScrollBar) e.getSource();
                JScrollBar leftBar = left.getVerticalScrollBar();
                JScrollBar rightBar = right.getVerticalScrollBar();
                if (source == leftBar) {
                    rightBar.setValue(source.getValue());
                } else {
                    leftBar.setValue(source.getValue());
                }
                adjusting = false;
            }
        };
        left.getVerticalScrollBar().addAdjustmentListener(listener);
        right.getVerticalScrollBar().addAdjustmentListener(listener);
    }

    private void populateDiffPane(JTextPane pane, List<DiffLine> diff, boolean isLeft) {
        StyledDocument doc = pane.getStyledDocument();

        // Monospace font
        Style defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style base = doc.addStyle("base", defaultStyle);
        StyleConstants.setFontFamily(base, "Monospaced");
        StyleConstants.setFontSize(base, 12);

        Style addedStyle = doc.addStyle("added", base);
        StyleConstants.setBackground(addedStyle, ADDED_COLOR);

        Style removedStyle = doc.addStyle("removed", base);
        StyleConstants.setBackground(removedStyle, REMOVED_COLOR);

        Style lineNumStyle = doc.addStyle("lineNum", base);
        StyleConstants.setForeground(lineNumStyle, LINE_NUMBER_COLOR);

        int lineNum = 0;

        for (DiffLine line : diff) {
            try {
                Style style;
                String text;

                if (isLeft) {
                    switch (line.type) {
                        case REMOVED:
                            lineNum++;
                            style = removedStyle;
                            text = formatLine(lineNum, line.oldText);
                            break;
                        case ADDED:
                            // Show blank line on left for added lines
                            style = base;
                            text = "\n";
                            break;
                        case UNCHANGED:
                        default:
                            lineNum++;
                            style = base;
                            text = formatLine(lineNum, line.oldText);
                            break;
                    }
                } else {
                    switch (line.type) {
                        case ADDED:
                            lineNum++;
                            style = addedStyle;
                            text = formatLine(lineNum, line.newText);
                            break;
                        case REMOVED:
                            // Show blank line on right for removed lines
                            style = base;
                            text = "\n";
                            break;
                        case UNCHANGED:
                        default:
                            lineNum++;
                            style = base;
                            text = formatLine(lineNum, line.newText);
                            break;
                    }
                }

                doc.insertString(doc.getLength(), text, style);
            } catch (BadLocationException e) {
                logger.error("Error populating diff pane", e);
            }
        }
    }

    private String formatLine(int lineNum, String text) {
        return String.format("%4d  %s\n", lineNum, text);
    }

    /**
     * Simple LCS-based diff algorithm. Computes the longest common subsequence
     * and uses it to produce a list of diff lines (unchanged, added, removed).
     */
    private List<DiffLine> computeDiff(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;

        // Build LCS table
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack to produce diff
        List<DiffLine> result = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                result.add(0, new DiffLine(DiffType.UNCHANGED, oldLines[i - 1], newLines[j - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, new DiffLine(DiffType.ADDED, "", newLines[j - 1]));
                j--;
            } else {
                result.add(0, new DiffLine(DiffType.REMOVED, oldLines[i - 1], ""));
                i--;
            }
        }

        return result;
    }

    private enum DiffType {
        UNCHANGED, ADDED, REMOVED
    }

    private static class DiffLine {
        final DiffType type;
        final String oldText;
        final String newText;

        DiffLine(DiffType type, String oldText, String newText) {
            this.type = type;
            this.oldText = oldText;
            this.newText = newText;
        }
    }
}
