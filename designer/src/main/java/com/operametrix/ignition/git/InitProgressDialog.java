package com.operametrix.ignition.git;

import javax.swing.*;
import java.awt.*;

/**
 * Modal progress dialog shown during git operations (init, push, pull, fetch).
 * Displays an indeterminate progress bar with a status label.
 */
public class InitProgressDialog extends JDialog {

    private final JLabel statusLabel;

    public InitProgressDialog(Component parent, String title) {
        super(SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        content.add(statusLabel, BorderLayout.NORTH);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(350, 22));
        content.add(progressBar, BorderLayout.CENTER);

        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Update the status label. Safe to call from any thread.
     */
    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /**
     * Dismiss the dialog. Safe to call from any thread.
     */
    public void complete() {
        SwingUtilities.invokeLater(this::dispose);
    }
}
