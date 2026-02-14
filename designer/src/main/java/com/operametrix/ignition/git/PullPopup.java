package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.designer.gui.CommonUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PullPopup extends JFrame {

    private JComboBox<String> remoteCombo;
    private JLabel remoteLabel;
    private JCheckBox imagesCheckBox;
    private JCheckBox themesCheckBox;
    private JCheckBox tagsCheckBox;

    public PullPopup(Component parent) {
        setTitle("Pull Settings");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildUI());

        pack();
        setMinimumSize(getPreferredSize());
        setVisible(true);

        CommonUI.centerComponent(this, parent);
        toFront();
    }

    private JPanel buildUI() {
        JPanel main = new JPanel(new BorderLayout(5, 5));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Center: form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        // Remote selector row
        JPanel remoteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        remoteLabel = new JLabel("Remote:");
        remoteCombo = new JComboBox<>();
        remoteCombo.setPreferredSize(new Dimension(180, 24));
        remoteRow.add(remoteLabel);
        remoteRow.add(remoteCombo);
        formPanel.add(remoteRow);

        // Import label
        JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        labelRow.add(new JLabel("Do you also wish to import the following:"));
        formPanel.add(labelRow);

        // Checkboxes
        imagesCheckBox = new JCheckBox("Images");
        themesCheckBox = new JCheckBox("Themes");
        tagsCheckBox = new JCheckBox("Tags");

        JPanel cbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cbPanel.add(imagesCheckBox);
        formPanel.add(cbPanel);

        JPanel cbPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cbPanel2.add(themesCheckBox);
        formPanel.add(cbPanel2);

        JPanel cbPanel3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        cbPanel3.add(tagsCheckBox);
        formPanel.add(cbPanel3);

        main.add(formPanel, BorderLayout.CENTER);

        // Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JButton pullBtn = new JButton("Pull and Import");
        pullBtn.setBackground(new Color(71, 137, 199));
        pullBtn.setForeground(Color.WHITE);
        pullBtn.addActionListener(e -> {
            String selectedRemote = (String) remoteCombo.getSelectedItem();
            onPullAction(selectedRemote != null ? selectedRemote : "origin",
                    tagsCheckBox.isSelected(),
                    themesCheckBox.isSelected(),
                    imagesCheckBox.isSelected());
            dispose();
        });

        buttonPanel.add(cancelBtn);
        buttonPanel.add(pullBtn);

        main.add(buttonPanel, BorderLayout.SOUTH);

        return main;
    }

    public void setRemotes(List<String> remoteNames) {
        remoteCombo.removeAllItems();
        for (String name : remoteNames) {
            remoteCombo.addItem(name);
        }
        // Pre-select "origin" if present
        for (int i = 0; i < remoteCombo.getItemCount(); i++) {
            if ("origin".equals(remoteCombo.getItemAt(i))) {
                remoteCombo.setSelectedIndex(i);
                break;
            }
        }
        setRemoteVisible(remoteNames.size() > 1);
    }

    public void setRemoteVisible(boolean visible) {
        remoteLabel.setVisible(visible);
        remoteCombo.setVisible(visible);
        pack();
        setMinimumSize(getPreferredSize());
    }

    public void resetCheckboxes() {
        tagsCheckBox.setSelected(false);
        themesCheckBox.setSelected(false);
        imagesCheckBox.setSelected(false);
    }

    /**
     * Called when the user clicks "Pull and Import".
     * Override in an anonymous subclass to handle the pull operation.
     */
    public void onPullAction(String remoteName, boolean importTags, boolean importTheme, boolean importImages) {
    }
}
