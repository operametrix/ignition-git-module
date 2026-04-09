package com.operametrix.ignition.git.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

public class SelectAllHeader extends JCheckBox implements TableCellRenderer {
    private final JTable table;
    private final TableModel tableModel;
    private final JTableHeader header;
    private final TableColumnModel tcm;
    private final int targetColumn;
    private int viewColumn;
    private boolean updating;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    public SelectAllHeader(JTable table, int targetColumn) {
        super();
        this.table = table;
        this.tableModel = table.getModel();
        if (tableModel.getColumnClass(targetColumn) != Boolean.class) {
            throw new IllegalArgumentException("Boolean column required.");
        }
        this.targetColumn = targetColumn;
        this.header = table.getTableHeader();
        setHorizontalAlignment(JCheckBox.CENTER);
        this.tcm = table.getColumnModel();
        this.applyUI();
        header.addMouseListener(new MouseHandler());
        setBorderPainted(true);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,new Color(-4143670)));
        tableModel.addTableModelListener(new ModelHandler());

        syncCheckboxToModel();
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        return this;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyUI();
    }

    private void applyUI() {
        this.setFont(UIManager.getFont("TableHeader.font"));
        this.setBorder(UIManager.getBorder("TableHeader.cellBorder"));
        this.setBackground(UIManager.getColor("TableHeader.background"));
        this.setForeground(UIManager.getColor("TableHeader.foreground"));
    }

    private class MouseHandler extends MouseAdapter {

        @Override
        public void mouseClicked(MouseEvent e) {
            viewColumn = header.columnAtPoint(e.getPoint());
            int modelColumn = tcm.getColumn(viewColumn).getModelIndex();
            if (modelColumn == targetColumn) {
                boolean newState = !isSelected();
                updating = true;
                try {
                    for (int r = 0; r < tableModel.getRowCount(); r++) {
                        tableModel.setValueAt(newState, r, targetColumn);
                    }
                    setSelected(newState);
                } finally {
                    updating = false;
                }
                header.repaint();
            }
        }
    }

    private class ModelHandler implements TableModelListener {

        @Override
        public void tableChanged(TableModelEvent e) {
            if (!updating) {
                syncCheckboxToModel();
            }
        }
    }

    private void syncCheckboxToModel() {
        if (tableModel.getRowCount() == 0) {
            setSelected(false);
            header.repaint();
            return;
        }
        boolean allTrue = true;
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            if (!(Boolean) tableModel.getValueAt(r, targetColumn)) {
                allTrue = false;
                break;
            }
        }
        if (allTrue != isSelected()) {
            setSelected(allTrue);
            header.repaint();
        }
    }
}

