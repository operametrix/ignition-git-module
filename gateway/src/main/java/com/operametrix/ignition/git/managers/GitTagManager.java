package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.tags.TagUtilities;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.BasicTagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.operametrix.ignition.git.GatewayHook.getContext;
import static com.operametrix.ignition.git.managers.GitManager.clearDirectory;
import static com.operametrix.ignition.git.managers.GitManager.getProjectFolderPath;
import static com.inductiveautomation.ignition.common.tags.TagUtilities.TAG_GSON;

public class GitTagManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitTagManager.class);

    public static void importTagManager(String projectName) {
        Path projectDir = getProjectFolderPath(projectName);
        File tagsProjectDir = projectDir.resolve("tags").toFile();

        GatewayTagManager gatewayTagManager = getContext().getTagManager();
        if (tagsProjectDir.exists()) {
            File[] files = tagsProjectDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String providerName = FilenameUtils.removeExtension(file.getName());
                    TagProvider tagProvider = gatewayTagManager.getTagProvider(providerName);
                    if (tagProvider != null) {
                        try {
                            tagProvider.importTagsAsync(new BasicTagPath(""), FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString()), "JSON", CollisionPolicy.Overwrite, null);
                        } catch (IOException e) {
                            logger.warn("An error occurred while importing '" + providerName + "' tags.", e);
                        }
                    }
                }
            }
        }
    }

    public static void exportTag(Path projectFolderPath) {
        Path tagFolderPath = projectFolderPath.resolve("tags");
        clearDirectory(tagFolderPath);

        try {
            Files.createDirectories(tagFolderPath);

            for (TagProvider tagProvider : getContext().getTagManager().getTagProviders()) {
                TagPath typesPath = TagPathParser.parse("");
                List<TagPath> tagPaths = new ArrayList<>();
                tagPaths.add(typesPath);

                CompletableFuture<List<TagConfigurationModel>> cfTagModels =
                        tagProvider.getTagConfigsAsync(tagPaths, true, true);
                List<TagConfigurationModel> tModels;
                try {
                    tModels = cfTagModels.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException te) {
                    throw new RuntimeException("Timed out reading tag configuration from provider '"
                            + tagProvider.getName() + "' after 30s.", te);
                }

                JsonObject json = TagUtilities.toJsonObject(tModels.get(0));
                JsonElement sortedJson = JsonUtilities.createDeterministicCopy(json);

                Path newFile = tagFolderPath.resolve(tagProvider.getName() + ".json");

                Files.writeString(newFile, TAG_GSON.toJson(sortedJson));
            }
        } catch (Exception e) {
            logger.error(e.toString(), e);
            throw new RuntimeException(e);
        }
    }
}
