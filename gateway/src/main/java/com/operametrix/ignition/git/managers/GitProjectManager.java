package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.StringPath;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonSyntaxException;
import com.inductiveautomation.ignition.common.resourcecollection.Resource;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceBuilder;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionImmutableException;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionInvalidException;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollectionManifest;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceManifest;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;
import com.inductiveautomation.ignition.common.resourcecollection.json.ResourceManifestSerializer;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.operametrix.ignition.git.GatewayHook.getContext;
import static com.operametrix.ignition.git.managers.GitManager.getProjectFolderPath;

public class GitProjectManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitProjectManager.class);

    /** Gson configured to (de)serialize the per-resource {@code resource.json} manifest format. */
    private static final Gson RESOURCE_GSON = new GsonBuilder()
            .registerTypeAdapter(ResourceManifest.class, ResourceManifestSerializer.forProject())
            .create();

    public static void importProject(String projectName) {
        ProjectManager projectManager = getContext().getProjectManager();
        Path projectDir = getProjectFolderPath(projectName);

        try {
            Set<Resource> resources = importFromFolder(projectDir, projectName);
            ResourceCollectionManifest projectManifest = loadProjectManifest(projectDir);
            projectManager.createOrReplace(projectName, projectManifest, new ArrayList<>(resources));

        } catch (ResourceCollectionInvalidException | ResourceCollectionImmutableException | IOException e) {
            logger.error("An error occurred while importing '" + projectName + "' project.", e);
            throw new RuntimeException(e);
        }
    }

    public static Set<Map.Entry<String, byte[]>> listFiles(Path projectPath) {
        Set<Map.Entry<String, byte[]>> resources = new HashSet<>();
        Stack<File> stack = new Stack<>();
        File directory = projectPath.toFile();

        stack.push(directory);

        while (!stack.empty()) {
            File current = stack.pop();

            File[] files = current.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        stack.push(file);
                    } else {
                        try {
                            String path = file.getAbsolutePath().replace(projectPath.toFile().getAbsolutePath(), "");
                            path = path.substring(1);
                            path = path.replace("\\", "/");
                            resources.add(new AbstractMap.SimpleEntry<>(path, Files.readAllBytes(file.toPath())));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        return resources;
    }

    public static boolean isAnIgnitionResource(String resource) {
        return !resource.startsWith(".git")
                && !resource.startsWith("tags")
                && !resource.startsWith("images")
                && !resource.startsWith("themes")
                && !resource.equals("project.json");
    }

    public static Set<Resource> importFromFolder(Path projectPath, String projectName) throws ResourceCollectionInvalidException, IOException {
        Set<Resource> resources = new HashSet<>();
        Set<StringPath> createdFolders = new HashSet<>();

        Set<Map.Entry<String, byte[]>> files = listFiles(projectPath);
        files.stream().collect(Collectors.groupingBy(e -> StringUtils.substringBeforeLast(e.getKey(), "/")))
                .forEach((resourcePath, listOfFileNodes) -> {
                    StringPath stringPath = StringPath.parse(resourcePath);
                    resources.addAll(createParentFolderResources(projectName, stringPath, createdFolders));
                    String manifestPath = String.format("%s/%s", resourcePath, "resource.json");

                    ResourceManifest resourceManifest = removeResourceManifest(manifestPath, listOfFileNodes);

                    if (resourceManifest != null) {

                        Map<String, byte[]> dataMap = createDataMap(resourceManifest, listOfFileNodes);

                        resources.add(createResourceBuilder(projectName, stringPath, resourceManifest, dataMap).build());
                    } else if (!createdFolders.contains(stringPath)) {
                        resources.add(createResourceBuilder(projectName, stringPath, ResourceManifest.newBuilder().build(), new HashMap<>()).setFolder(true).build());
                        createdFolders.add(stringPath);
                    }
                });

        return resources;
    }

    private static ResourceManifest removeResourceManifest(String manifestPath, List<Map.Entry<String, byte[]>> listOfFileNodes) {
        return listOfFileNodes.stream()
                .filter(e -> manifestPath.equals(e.getKey()))
                .findFirst()
                .map(entry -> {
                    listOfFileNodes.remove(entry);
                    try {
                        return RESOURCE_GSON.fromJson(new String(entry.getValue(), StandardCharsets.UTF_8), ResourceManifest.class);
                    } catch (JsonSyntaxException e) {
                        logger.infof("Malformed resource.json at %s, unable to remove", entry.getKey(), e);
                        return null;
                    }
                }).orElse(null);
    }


    private static List<Resource> createParentFolderResources(String projectName, StringPath resourcePath, Set<StringPath> ignoreList) {
        List<Resource> folders = new ArrayList<>();
        StringPath currentPath = resourcePath.getParentPath();
        while (currentPath != null && currentPath.getPathLength() > 0 &&
                !ignoreList.contains(currentPath)) {
            folders.add(createResourceBuilder(projectName, currentPath,
                    ResourceManifest.newBuilder().build(), new HashMap<>())
                    .setFolder(true)
                    .build());
            ignoreList.add(currentPath);
            currentPath = currentPath.getParentPath();
        }

        return folders;
    }

    private static Map<String, byte[]> createDataMap(ResourceManifest resourceManifest, List<Map.Entry<String, byte[]>> listOfFileNodes) {
        List<String> allowedFiles = resourceManifest.getFiles();
        HashMap<String, byte[]> dataMap = new HashMap<>();
        listOfFileNodes.forEach(e -> {
            String filename = StringUtils.substringAfterLast(e.getKey(), "/");
            if (allowedFiles.contains(filename))
                dataMap.put(filename, e.getValue());
        });
        return dataMap;
    }

    private static ResourceBuilder createResourceBuilder(String projectName, StringPath resourcePath, ResourceManifest manifest, Map<String, byte[]> dataMap) {
        String moduleId = resourcePath.getPathComponent(0);
        String resourceType = (resourcePath.getPathLength() > 1) ? resourcePath.getPathComponent(1) : null;
        String subPath = (resourcePath.getPathLength() > 2) ? resourcePath.subPath().subPath().toString() : "";
        return Resource.newBuilder()
                .setResourceCollectionName(projectName)
                .setResourcePath(new ResourcePath(new ResourceType(moduleId, resourceType), subPath))
                .setDataBytes(dataMap)
                .setRestricted(manifest.isRestricted())
                .setAttributes(manifest.getAttributes())
                .setApplicationScope(manifest.getScope())
                .setDocumentation(manifest.getDocumentation())
                .setVersion(manifest.getVersion())
                .setOverridable(manifest.isOverridable());
    }

    public static ResourceCollectionManifest loadProjectManifest(Path projectPath) throws IOException {
        String json = new String(Files.readAllBytes(projectPath.resolve("project.json")), StandardCharsets.UTF_8);
        JsonObject o = RESOURCE_GSON.fromJson(json, JsonObject.class);

        String title = (o.has("title") && !o.get("title").isJsonNull())
                ? o.get("title").getAsString() : projectPath.getFileName().toString();
        String description = (o.has("description") && !o.get("description").isJsonNull())
                ? o.get("description").getAsString() : "";
        boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
        boolean inheritable = o.has("inheritable") && o.get("inheritable").getAsBoolean();
        String parent = (o.has("parent") && !o.get("parent").isJsonNull())
                ? o.get("parent").getAsString() : "";

        return new ResourceCollectionManifest(title, description, enabled, inheritable, parent);
    }

}
