package com.operametrix.ignition.git.utils;

import com.inductiveautomation.ignition.client.icons.SvgIconUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;

public class IconUtils {

    private static final Logger logger = LoggerFactory.getLogger(IconUtils.class);

    private static final int WINDOW_ICON_SIZE = 32;

    /**
     * Loads a bundled SVG as a resolution-independent {@link Icon} using Ignition's
     * platform SVG renderer. Unlike a rasterized {@code ImageIO.read} bitmap, this
     * renders the vector at the display's scale, so it stays sharp on HiDPI / scaled
     * displays.
     */
    public static Icon getIcon(String bundleKey) {
        try {
            return SvgIconUtil.getIcon(IconUtils.class, bundleKey);
        } catch (Exception e) {
            logger.warn(e.toString(), e);
            return null;
        }
    }

    /**
     * Sets the given window's title-bar icon from a bundled SVG resource. The vector is
     * rendered to a bitmap (title-bar icons are inherently rasterized by the OS) at a
     * size large enough to look crisp. Silently does nothing if the resource is missing.
     */
    public static void setWindowIcon(Window window, String bundleKey) {
        try {
            Icon icon = SvgIconUtil.getIcon(IconUtils.class, bundleKey, WINDOW_ICON_SIZE, WINDOW_ICON_SIZE);
            if (icon != null) {
                BufferedImage image = new BufferedImage(WINDOW_ICON_SIZE, WINDOW_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                icon.paintIcon(null, g, 0, 0);
                g.dispose();
                window.setIconImage(image);
            }
        } catch (Exception e) {
            logger.trace(e.toString(), e);
        }
    }
}
