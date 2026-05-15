package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static com.operametrix.ignition.git.managers.GitManager.*;

public class GitThemeManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitThemeManager.class);

    public static void importTheme(String projectName) {
        Path dataDir = getDataFolderPath();
        Path projectDir = getProjectFolderPath(projectName);
        Path themesDir = dataDir.resolve("modules").resolve("com.inductiveautomation.perspective").resolve("themes");
        Path themesProjectDir = projectDir.resolve("themes");
        File themesProjectDirFile = themesProjectDir.toFile();

        if (themesProjectDirFile.exists()) {
            File[] files = themesProjectDirFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String themeName = FilenameUtils.removeExtension(file.getName());
                        try {
                            Path destinationDirectoryMain = themesDir.resolve(file.getName());
                            Path sourceDirectoryMain = themesProjectDir.resolve(file.getName());
                            Files.copy(sourceDirectoryMain, destinationDirectoryMain, StandardCopyOption.REPLACE_EXISTING);

                            File destinationDirectory = themesDir.resolve(themeName).toFile();
                            File sourceDirectory = themesProjectDir.resolve(themeName).toFile();
                            FileUtils.deleteDirectory(destinationDirectory);
                            FileUtils.copyDirectory(sourceDirectory, destinationDirectory);
                        } catch (IOException e) {
                            logger.warn("An error occurred while importing '" + themeName + "' theme.", e);
                        }
                    }
                }
            }
        }
    }

    public static void exportTheme(Path projectFolderPath) {
        Path sessionPropsPath = projectFolderPath.resolve("com.inductiveautomation.perspective")
                .resolve("session-props")
                .resolve("props.json");
        if (!Files.exists(sessionPropsPath)) {
            throw new RuntimeException("No Perspective session properties found for this project — "
                    + "nothing to snapshot for themes.");
        }

        String theme;
        try {
            String content = Files.readString(sessionPropsPath);
            JsonObject json = new Gson().fromJson(content, JsonObject.class);
            theme = JsonUtilities.readString(json, "props.theme", "light");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Perspective session properties: "
                    + e.getMessage(), e);
        }

        Path themesDir = getDataFolderPath()
                .resolve("modules")
                .resolve("com.inductiveautomation.perspective")
                .resolve("themes");
        Path themeFolder = themesDir.resolve(theme);
        Path themeFile = themesDir.resolve(theme + ".css");

        if (!Files.isDirectory(themeFolder) && !Files.exists(themeFile)) {
            throw new RuntimeException("Theme '" + theme + "' was not found on the gateway — "
                    + "nothing to snapshot.");
        }

        // Stage the new theme tree into a system temp dir first (a failed
        // gateway read can't touch the project). Before overwriting themes/,
        // rename the committed copy aside as an atomic same-filesystem move so
        // that a mid-copy failure rolls back instead of leaving the
        // already-committed theme files half-destroyed.
        Path themeFolderPath = projectFolderPath.resolve("themes");
        Path backupPath = projectFolderPath.resolve("themes.git-snapshot-bak");
        Path stagingPath;
        try {
            stagingPath = Files.createTempDirectory("git-theme-snapshot");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create theme snapshot staging directory: "
                    + e.getMessage(), e);
        }
        boolean backupCreated = false;
        boolean swapSucceeded = false;
        try {
            if (Files.isDirectory(themeFolder)) {
                FileUtils.copyDirectoryToDirectory(themeFolder.toFile(), stagingPath.toFile());
            }
            if (Files.exists(themeFile)) {
                Files.copy(themeFile, stagingPath.resolve(themeFile.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            if (Files.exists(themeFolderPath)) {
                if (Files.exists(backupPath)) {
                    FileUtils.deleteDirectory(backupPath.toFile());
                }
                Files.move(themeFolderPath, backupPath, StandardCopyOption.ATOMIC_MOVE);
                backupCreated = true;
            }
            Files.createDirectories(themeFolderPath);
            FileUtils.copyDirectory(stagingPath.toFile(), themeFolderPath.toFile());
            swapSucceeded = true;
        } catch (IOException e) {
            if (backupCreated) {
                try {
                    if (Files.exists(themeFolderPath)) {
                        FileUtils.deleteDirectory(themeFolderPath.toFile());
                    }
                    Files.move(backupPath, themeFolderPath, StandardCopyOption.ATOMIC_MOVE);
                    backupCreated = false; // restored; backup consumed by the move
                } catch (IOException restoreEx) {
                    logger.error("Theme snapshot failed and the committed themes/ could not be "
                            + "restored automatically; the previous contents are preserved at '"
                            + backupPath.getFileName() + "' inside the project folder.", restoreEx);
                    RuntimeException re = new RuntimeException("Failed to snapshot themes and could"
                            + " not restore the previous themes automatically; recover them from '"
                            + backupPath.getFileName() + "' in the project folder.", e);
                    re.addSuppressed(restoreEx);
                    throw re;
                }
            }
            throw new RuntimeException("Failed to snapshot themes: " + e.getMessage(), e);
        } finally {
            try {
                FileUtils.deleteDirectory(stagingPath.toFile());
            } catch (IOException ignored) {
                // best-effort cleanup of the staging dir
            }
            // Drop the backup only once the new themes/ is safely in place.
            // If a restore failed it is the sole surviving copy — keep it.
            if (swapSucceeded && backupCreated) {
                try {
                    FileUtils.deleteDirectory(backupPath.toFile());
                } catch (IOException ignored) {
                    // best-effort cleanup of the backup dir after a successful swap
                }
            }
        }
    }
}
