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

        // Stage into a system temp dir (kept out of the managed project's
        // working tree); only clear and swap the real themes/ directory once
        // the staged copy has fully succeeded, so a mid-copy failure can never
        // destroy already-committed theme files.
        Path themeFolderPath = projectFolderPath.resolve("themes");
        Path stagingPath;
        try {
            stagingPath = Files.createTempDirectory("git-theme-snapshot");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create theme snapshot staging directory: "
                    + e.getMessage(), e);
        }
        try {
            if (Files.isDirectory(themeFolder)) {
                FileUtils.copyDirectoryToDirectory(themeFolder.toFile(), stagingPath.toFile());
            }
            if (Files.exists(themeFile)) {
                Files.copy(themeFile, stagingPath.resolve(themeFile.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            clearDirectory(themeFolderPath);
            Files.createDirectories(themeFolderPath);
            FileUtils.copyDirectory(stagingPath.toFile(), themeFolderPath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to snapshot themes: " + e.getMessage(), e);
        } finally {
            try {
                FileUtils.deleteDirectory(stagingPath.toFile());
            } catch (IOException ignored) {
                // best-effort cleanup of the staging dir
            }
        }
    }
}
