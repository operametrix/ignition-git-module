package com.operametrix.ignition.git.utils;

import com.inductiveautomation.ignition.client.icons.SvgIconUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;

/**
 * Loads the module's bundled SVG icons as resolution-independent (vector) Swing
 * icons via Ignition's platform {@code SvgIconUtil}, so they stay sharp on HiDPI /
 * scaled displays.
 * <p>
 * Callers pass a bare icon name (e.g. {@code "ic_git"}); {@code SvgIconUtil} resolves
 * it to {@code images/svgicons/<name>.svg}, which is where the module's icon SVGs are
 * bundled ({@code resources/images/svgicons/}). We deliberately go through this
 * {@code Icon}-returning API only — never referencing Batik / {@code SVGDocument}
 * directly — so no icon rendering class outside the module SDK is linked into the
 * module (which the Designer's isolated classloader would not resolve at runtime).
 */
public class IconUtils {

    private static final Logger logger = LoggerFactory.getLogger(IconUtils.class);

    private static final int WINDOW_ICON_SIZE = 32;

    /** Loads an icon by bare name (e.g. {@code "ic_git"}), rendered as a sharp vector. */
    public static Icon getIcon(String iconName) {
        try {
            return SvgIconUtil.getIcon(IconUtils.class, iconName);
        } catch (Exception e) {
            logger.warn(e.toString(), e);
            return null;
        }
    }

    /**
     * Sets the given window's title-bar icon from a bundled SVG (by bare name). The
     * vector is rendered to a bitmap (title-bar icons are inherently rasterized by the
     * OS) at a size large enough to look crisp. Silently does nothing on failure.
     */
    public static void setWindowIcon(Window window, String iconName) {
        try {
            Icon icon = SvgIconUtil.getIcon(IconUtils.class, iconName, WINDOW_ICON_SIZE, WINDOW_ICON_SIZE);
            if (icon == null) return;
            BufferedImage image = new BufferedImage(WINDOW_ICON_SIZE, WINDOW_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            icon.paintIcon(null, g, 0, 0);
            g.dispose();
            window.setIconImage(image);
        } catch (Exception e) {
            logger.trace(e.toString(), e);
        }
    }
}
