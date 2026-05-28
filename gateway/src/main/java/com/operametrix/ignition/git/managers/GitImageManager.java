package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.images.ImageFormat;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.images.ImageManager;
import com.inductiveautomation.ignition.gateway.images.ImageResource;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.operametrix.ignition.git.GatewayHook.getContext;
import static com.operametrix.ignition.git.managers.GitManager.clearDirectory;
import static com.operametrix.ignition.git.managers.GitManager.getProjectFolderPath;

public class GitImageManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitImageManager.class);

    public static void importImages(String projectName) {
        Path projectDir = getProjectFolderPath(projectName);
        File directory = projectDir.resolve("images").toFile();

        // DELETION — clear the gateway image store before re-importing the snapshot.
        ImageManager imageManager = getContext().getImageManager();
        for (ImageResource image : imageManager.getImages("")) {
            String imagePath = image.path().getPath().toString();
            try {
                imageManager.deleteImage(imagePath);
            } catch (Exception ex) {
                logger.error("Unable to delete image '" + imagePath + "'", ex);
            }
        }

        // INSERTION
        File[] files = directory.listFiles();
        uploadFiles(files != null ? files : new File[0]);
    }

    protected static void uploadFiles(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                uploadFolder(file, "");
            } else {
                uploadFile(file, "");
            }
        }
    }


    protected static void uploadFile(File f, String path) {
        String lName = f.getName().toLowerCase();
        if (lName.endsWith(".png") || lName
                .endsWith(".gif") || lName
                .endsWith(".jpg") || lName
                .endsWith(".jpeg") || lName
                .endsWith(".svg"))
            try {
                String ext = lName.substring(lName.lastIndexOf(".") + 1);
                ImageFormat format = ImageFormat.forExtension(ext).orElse(null);
                if (format == null) {
                    logger.warn("Unsupported image extension '" + ext + "' for file: '" + f.getName() + "'");
                    return;
                }
                byte[] bytes = Files.readAllBytes(f.toPath());
                int width = 0;
                int height = 0;
                Image img = Toolkit.getDefaultToolkit().createImage(bytes);
                if (PathIcon.waitForImage(img)) {
                    width = img.getWidth(null);
                    height = img.getHeight(null);
                }

                try {
                    getContext().getImageManager().insertImage(f.getName(), "", format, path, bytes, width, height, bytes.length);
                } catch (Exception ex) {
                    logger.error(ex.getMessage(), ex);
                }
            } catch (FileNotFoundException e) {
                logger.error("FileNotFound exception for file: '" + f.getPath() + "'");
            } catch (IOException e) {
                logger.error("IOException exception for file: '" + f.getPath() + "'");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
    }


    protected static void uploadFolder(File dir, String path) {
        try {
            getContext().getImageManager().insertImageFolder(dir.getName(), path.equals("") ? null : path);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        uploadFolder(file, path + dir.getName() + "/");
                    } else {
                        uploadFile(file, path + dir.getName() + "/");
                    }
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
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
