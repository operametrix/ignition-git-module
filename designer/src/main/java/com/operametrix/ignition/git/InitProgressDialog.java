package com.operametrix.ignition.git;

import javax.swing.*;
import java.awt.*;

/**
 * Non-modal progress dialog shown during repository initialization.
 * Displays an indeterminate progress bar with a phase label that updates
 * as the init flow progresses through its stages.
 */
public class InitProgressDialog extends JDialog {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final int totalSteps;

    public InitProgressDialog(Component parent, int totalSteps, String title) {
        super(SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        this.totalSteps = totalSteps;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        statusLabel = new JLabel("Initializing...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        content.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, totalSteps);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(350, 22));
        content.add(progressBar, BorderLayout.CENTER);

        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Update the progress bar and status label. Safe to call from any thread.
     */
    public void updateProgress(int step, String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(step);
            statusLabel.setText(message);
        });
    }

    /**
     * Dismiss the dialog. Safe to call from any thread.
     */
    public void complete() {
        SwingUtilities.invokeLater(this::dispose);
    }
}
