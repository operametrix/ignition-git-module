package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.images.ImageFormat;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.images.ImageManager;
import com.inductiveautomation.ignition.gateway.images.ImageResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.operametrix.ignition.git.GatewayHook.getContext;
import static com.operametrix.ignition.git.managers.GitManager.clearDirectory;
import static com.operametrix.ignition.git.managers.GitManager.getProjectFolderPath;

public class GitImageManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitImageManager.class);

    /** An image folder to recreate, in parent-before-child order. */
    private record PendingFolder(String name, String parent) {}

    /** One snapshot image, already read and validated, ready to insert. */
    private record PendingImage(String name, ImageFormat format, String path, byte[] bytes,
                                int width, int height) {}

    /**
     * Replace the gateway's image store with the project's {@code images/} snapshot.
     *
     * <p>The whole snapshot is read and validated first, and the store is only cleared once every
     * file is known to be readable. Previously the store was emptied up front, so a file that could
     * not be read part-way through left the gateway with no images at all. (A failure during the
     * inserts themselves is still not rolled back: the images already written stay, and the log
     * names the one that failed. An empty or absent snapshot still empties the store, as before —
     * that is what a snapshot with no images means.)
     */
    public static void importImages(String projectName) {
        Path projectDir = getProjectFolderPath(projectName);
        File directory = projectDir.resolve("images").toFile();

        List<PendingFolder> folders = new ArrayList<>();
        List<PendingImage> images = new ArrayList<>();
        File[] files = directory.listFiles();
        collect(files != null ? files : new File[0], "", folders, images);

        ImageManager imageManager = getContext().getImageManager();
        for (ImageResource image : imageManager.getImages("")) {
            String imagePath = image.path().getPath().toString();
            try {
                imageManager.deleteImage(imagePath);
            } catch (Exception ex) {
                logger.error("Unable to delete image '" + imagePath + "'", ex);
            }
        }

        for (PendingFolder folder : folders) {
            try {
                imageManager.insertImageFolder(folder.name(), folder.parent());
            } catch (Exception ex) {
                logger.error("Unable to create image folder '" + folder.name() + "'", ex);
            }
        }
        for (PendingImage image : images) {
            try {
                imageManager.insertImage(image.name(), "", image.format(), image.path(),
                        image.bytes(), image.width(), image.height(), image.bytes().length);
            } catch (Exception ex) {
                logger.error("Unable to import image '" + image.path() + image.name() + "'", ex);
            }
        }
    }

    /** Walk the snapshot, reading every image into memory; throws before anything is deleted. */
    private static void collect(File[] files, String path, List<PendingFolder> folders,
                                List<PendingImage> images) {
        for (File file : files) {
            if (file.isDirectory()) {
                folders.add(new PendingFolder(file.getName(), path.isEmpty() ? null : path));
                File[] children = file.listFiles();
                collect(children != null ? children : new File[0], path + file.getName() + "/",
                        folders, images);
            } else {
                PendingImage image = read(file, path);
                if (image != null) {
                    images.add(image);
                }
            }
        }
    }

    /** Read one file as an image, or null when it isn't one we import. */
    private static PendingImage read(File f, String path) {
        String lName = f.getName().toLowerCase();
        if (!(lName.endsWith(".png") || lName.endsWith(".gif") || lName.endsWith(".jpg")
                || lName.endsWith(".jpeg") || lName.endsWith(".svg"))) {
            return null;
        }
        String ext = lName.substring(lName.lastIndexOf(".") + 1);
        ImageFormat format = ImageFormat.forExtension(ext).orElse(null);
        if (format == null) {
            logger.warn("Unsupported image extension '" + ext + "' for file: '" + f.getName() + "'");
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(f.toPath());
            int width = 0;
            int height = 0;
            Image img = Toolkit.getDefaultToolkit().createImage(bytes);
            if (PathIcon.waitForImage(img)) {
                width = img.getWidth(null);
                height = img.getHeight(null);
            }
            return new PendingImage(f.getName(), format, path, bytes, width, height);
        } catch (IOException e) {
            // Abort while the gateway still has its images.
            throw new RuntimeException("Could not read image '" + f.getPath() + "': " + e.getMessage(), e);
        }
    }

    public static void exportImages(Path projectFolderPath) {
        Path imageFolderPath = projectFolderPath.resolve("images");
        clearDirectory(imageFolderPath);
        try {
            Files.createDirectories(imageFolderPath);
        } catch (IOException e) {
            logger.error(e.toString(), e);
        }

        ImageManager imageManager = getContext().getImageManager();
        for (ImageResource image : imageManager.getImages("")) {
            String relPath = image.path().getPath().toString();
            Path target = imageFolderPath.resolve(relPath);
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.write(target, image.data().getBytes());
            } catch (IOException e) {
                logger.error("Unable to export image '" + relPath + "'", e);
            }
        }
    }
}

class PathIcon extends ImageIcon {
    protected static final Component COMP = new Component() {
    };
    private static MediaTracker tracker;
    private static int nextId;
    public static boolean waitForImage(Image image) {
        if (image == null) {
            return false;
        } else if (image instanceof BufferedImage) {
            return true;
        } else {
            int id;
            synchronized(COMP) {
                id = nextId++;
            }

            tracker.addImage(image, id);

            try {
                tracker.waitForID(id);
            } catch (InterruptedException var4) {
                System.err.println("Image loading interrupted!");
                return false;
            }

            boolean success = !tracker.isErrorID(id);
            tracker.removeImage(image, id);
            return success;
        }
    }

    static {
        tracker = new MediaTracker(COMP);
        nextId = 0;
    }
}
