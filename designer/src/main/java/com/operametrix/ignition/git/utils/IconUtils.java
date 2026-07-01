package com.operametrix.ignition.git.utils;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.Image;
import java.awt.Window;

public class IconUtils {

    private static final Logger logger = LoggerFactory.getLogger(IconUtils.class);

    /**
     * Loads a bundled SVG as a resolution-independent {@link FlatSVGIcon}. Unlike a
     * rasterized {@code ImageIO.read} bitmap, this renders the vector at the display's
     * scale, so it stays sharp on HiDPI / scaled displays.
     */
    public static Icon getIcon(String bundleKey) {
        return svgIcon(bundleKey);
    }

    /**
     * Sets the given window's title-bar icon from a bundled SVG resource. The vector is
     * rendered to a bitmap (title-bar icons are inherently rasterized by the OS) at a
     * size large enough to look crisp. Silently does nothing if the resource is missing.
     */
    public static void setWindowIcon(Window window, String bundleKey) {
        try {
            FlatSVGIcon icon = svgIcon(bundleKey);
            if (icon != null) {
                Image image = icon.derive(32, 32).getImage();
                if (image != null) {
                    window.setIconImage(image);
                }
            }
        } catch (Exception e) {
            logger.trace(e.toString(), e);
        }
    }

    private static FlatSVGIcon svgIcon(String bundleKey) {
        String name = bundleKey.startsWith("/") ? bundleKey.substring(1) : bundleKey;
        try {
            return new FlatSVGIcon(name, IconUtils.class.getClassLoader());
        } catch (Exception e) {
            logger.warn(e.toString(), e);
            return null;
        }
    }
}
