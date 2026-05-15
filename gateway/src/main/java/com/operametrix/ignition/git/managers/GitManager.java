package com.operametrix.ignition.git.managers;

import com.operametrix.ignition.git.SshTransportConfigCallback;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitRemoteCredentialsRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.operametrix.ignition.git.records.GitUserHttpsCredentialRecord;
import com.operametrix.ignition.git.records.GitUserSshKeyRecord;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.GsonBuilder;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.project.RuntimeProject;
import com.inductiveautomation.ignition.common.project.resource.LastModification;
import com.inductiveautomation.ignition.common.project.resource.ProjectResource;
import com.inductiveautomation.ignition.common.project.resource.ResourcePath;
import com.inductiveautomation.ignition.common.project.resource.ResourceType;
import com.inductiveautomation.ignition.common.util.DatasetBuilder;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.project.ProjectManager;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import simpleorm.dataset.SQuery;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;


import static com.operametrix.ignition.git.GatewayHook.getContext;

public class GitManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitManager.class);

    static public Git getGit(Path projectFolderPath) {
        try {
            return Git.open(projectFolderPath.resolve(".git").toFile());
        } catch (IOException e) {
            logger.error("Unable to retrieve Git repository", e);
            throw new RuntimeException(e);
        }
    }

    public static Path getProjectFolderPath(String projectName) {
        Path dataDir = getDataFolderPath();
        return dataDir.resolve("projects").resolve(projectName);
    }

    public static Path getDataFolderPath() {
        return getContext().getSystemManager().getDataDir().toPath();
    }


    public static void clearDirectory(Path folderPath) {
        try {
            if (folderPath.toFile().exists()) {
                FileUtils.cleanDirectory(folderPath.toFile());
            }
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }
    }

    /**
     * Set authentication on a transport command using a two-tier credential lookup:
     * 1. FK reference from GitRemoteCredentialsRecord to a user-level credential record
     * 2. User-level fallback (SSH: lone default key; HTTPS: match by host)
     *
     * Auth type (SSH vs HTTPS) is determined from the remote's URL in .git/config.
     */
    public static void setAuthentication(TransportCommand<?, ?> command, String projectName,
                                          String userName, String remoteName) throws Exception {
        GitRemoteCredentialsRecord creds = getRemoteCredentialsRecord(projectName, userName, remoteName);
        String url = getRemoteUrl(getProjectFolderPath(projectName), remoteName);
        boolean isSsh = url != null && !url.toLowerCase().startsWith("http");

        if (isSsh) {
            String sshKey = resolveSshKey(creds, userName);
            if (sshKey == null || sshKey.isEmpty()) {
                throw new Exception("No SSH credentials configured for remote '" + remoteName + "'.");
            }
            command.setTransportConfigCallback(new SshTransportConfigCallback(sshKey));
        } else {
            String[] httpsCreds = resolveHttpsCredentials(creds, userName, url);
            if (httpsCreds == null) {
                throw new Exception("No HTTPS credentials configured for remote '" + remoteName + "'.");
            }
            command.setCredentialsProvider(
                    new UsernamePasswordCredentialsProvider(httpsCreds[0], httpsCreds[1]));
        }
    }

    private static String resolveSshKey(GitRemoteCredentialsRecord creds, String userName) {
        // Tier 1: FK reference to user-level SSH key
        if (creds != null && creds.getSshKeyId() > 0) {
            GitUserSshKeyRecord keyRecord = getContext().getPersistenceInterface().queryOne(
                    new SQuery<>(GitUserSshKeyRecord.META)
                            .eq(GitUserSshKeyRecord.Id, creds.getSshKeyId()));
            if (keyRecord != null) {
                return keyRecord.getSSHKey();
            }
        }
        // Tier 2: User-level SSH key — use it if exactly one exists
        List<GitUserSshKeyRecord> userKeys = getContext().getPersistenceInterface().query(
                new SQuery<>(GitUserSshKeyRecord.META)
                        .eq(GitUserSshKeyRecord.IgnitionUser, userName));
        if (userKeys.size() == 1) {
            return userKeys.get(0).getSSHKey();
        }
        return null;
    }

    /** @return [username, password] or null if no credentials found */
    private static String[] resolveHttpsCredentials(GitRemoteCredentialsRecord creds,
                                                     String userName, String url) {
        // Tier 1: FK reference to user-level HTTPS credential
        if (creds != null && creds.getHttpsCredentialId() > 0) {
            GitUserHttpsCredentialRecord httpRecord = getContext().getPersistenceInterface().queryOne(
                    new SQuery<>(GitUserHttpsCredentialRecord.META)
                            .eq(GitUserHttpsCredentialRecord.Id, creds.getHttpsCredentialId()));
            if (httpRecord != null) {
                return new String[]{httpRecord.getUserName(), httpRecord.getPassword()};
            }
        }
        // Tier 2: User-level HTTPS credential matched by host
        String host = extractHost(url);
        if (host != null) {
            GitUserHttpsCredentialRecord hostRecord = getContext().getPersistenceInterface().queryOne(
                    new SQuery<>(GitUserHttpsCredentialRecord.META)
                            .eq(GitUserHttpsCredentialRecord.IgnitionUser, userName)
                            .eq(GitUserHttpsCredentialRecord.HostPattern, host));
            if (hostRecord != null) {
                return new String[]{hostRecord.getUserName(), hostRecord.getPassword()};
            }
        }
        return null;
    }

    /**
     * Extract the hostname from a git remote URL.
     * Handles both HTTPS (https://github.com/...) and SSH (git@github.com:...) formats.
     */
    static String extractHost(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            return new URIish(url).getHost();
        } catch (Exception e) {
            logger.error("Failed to extract host from URL: " + url, e);
            return null;
        }
    }


    public static void setCommitAuthor(CommitCommand command, String projectName, String userName) {
        try {
            String email = resolveUserEmail(userName);
            command.setAuthor(userName, email);
        } catch (Exception e) {
            logger.error("An error occurred while setting up commit author.", e);
        }
    }

    /**
     * Resolve a user's email address from Ignition's user source system.
     * Searches all configured user sources for the given username and returns
     * the first email found in the user's contact info.
     *
     * @return the email address, or empty string if not found
     */
    public static String resolveUserEmail(String userName) {
        try {
            List<com.inductiveautomation.ignition.gateway.user.UserSourceProfileRecord> profiles =
                    getContext().getPersistenceInterface().query(
                            new SQuery<>(com.inductiveautomation.ignition.gateway.user.UserSourceProfileRecord.META));
            for (com.inductiveautomation.ignition.gateway.user.UserSourceProfileRecord profileRecord : profiles) {
                try {
                    com.inductiveautomation.ignition.gateway.user.UserSourceProfile profile =
                            getContext().getUserSourceManager().getProfile(profileRecord.getName());
                    if (profile == null) continue;
                    com.inductiveautomation.ignition.common.user.User user =
                            profile.getUser(userName).orElse(null);
                    if (user != null) {
                        for (com.inductiveautomation.ignition.common.user.ContactInfo contact
                                : user.getContactInfo()) {
                            if ("email".equalsIgnoreCase(contact.getContactType())) {
                                String email = contact.getValue();
                                if (email != null && !email.isEmpty()) {
                                    return email;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip this user source, try the next one
                }
            }
        } catch (Exception e) {
            logger.error("Error resolving email for user: " + userName, e);
        }
        logger.warnf("No email found in user contact info for '%s'", userName);
        return "";
    }

    public static GitProjectsConfigRecord getGitProjectConfigRecord(String projectName) throws Exception {
        SQuery<GitProjectsConfigRecord> projectQuery = new SQuery<>(GitProjectsConfigRecord.META)
                .eq(GitProjectsConfigRecord.ProjectName, projectName);
        GitProjectsConfigRecord gitProjectsConfigRecord = getContext().getPersistenceInterface().queryOne(projectQuery);

        if (gitProjectsConfigRecord == null) {
            throw new Exception("Git Project not configured.");
        }

        return gitProjectsConfigRecord;
    }

    public static GitReposUsersRecord getGitReposUserRecord(GitProjectsConfigRecord gitProjectsConfigRecord,
                                                            String userName) throws Exception {
        SQuery<GitReposUsersRecord> userQuery = new SQuery<>(GitReposUsersRecord.META)
                .eq(GitReposUsersRecord.ProjectId, gitProjectsConfigRecord.getId())
                .eq(GitReposUsersRecord.IgnitionUser, userName);
        GitReposUsersRecord user = getContext().getPersistenceInterface().queryOne(userQuery);

        if (user == null) {
            throw new Exception("Git User not configured.");
        }

        return user;
    }

    public static int countOccurrences(Set<String> list, String prefix) {
        int count = 0;
        for (String str : list) {
            if (str.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    public static void uncommittedChangesBuilder(String projectName,
                                                 Set<String> updates,
                                                 String type,
                                                 List<String> changes,
                                                 DatasetBuilder builder) {
        for (String update : updates) {
            String[] rowData = new String[4];
            String actor = "unknown";
            String timestamp = "";
            String path = update;

            if (hasActor(path)) {
                String[] pathSplitted = update.split("/");
                path = String.join("/", Arrays.copyOf(pathSplitted, pathSplitted.length - 1));

                actor = getActor(projectName, path);
                timestamp = getTimestamp(projectName, path);
            }

            rowData[0] = path;
            rowData[1] = type;
            if (!changes.contains(path)) {
                rowData[2] = actor;
                rowData[3] = timestamp;
                changes.add(path);
                builder.addRow((Object[]) rowData);
            }
        }
    }

    public static boolean hasActor(String resource) {
        boolean hasActor = false;

        if (resource.startsWith("ignition")) {
            hasActor = Boolean.TRUE;
        }

        if (resource.startsWith("com.inductiveautomation.")) {
            hasActor = Boolean.TRUE;
        }

        return hasActor;
    }

    public static String getTimestamp(String projectName, String path) {
        ProjectManager projectManager = getContext().getProjectManager();
        Optional<RuntimeProject> projectOpt = projectManager.getProject(projectName);

        if (projectOpt.isPresent()) {
            RuntimeProject project = projectOpt.get();
            Optional<ProjectResource> resourceOpt = project.getResource(getResourcePath(path));

            if (resourceOpt.isPresent()) {
                ProjectResource projectResource = resourceOpt.get();
                return LastModification.of(projectResource)
                        .map(LastModification::getTimestamp)
                        .map(date -> new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date))
                        .orElse("");
            }
        }

        return "";
    }

    public static String getActor(String projectName, String path) {
        ProjectManager projectManager = getContext().getProjectManager();
        Optional<RuntimeProject> projectOpt = projectManager.getProject(projectName);

        if (projectOpt.isPresent()) {
            RuntimeProject project = projectOpt.get();
            Optional<ProjectResource> resourceOpt = project.getResource(getResourcePath(path));

            if (resourceOpt.isPresent()) {
                ProjectResource projectResource = resourceOpt.get();
                return LastModification.of(projectResource).map(LastModification::getActor).orElse("unknown");
            }
        }

        return "unknown";
    }

    public static List getAddedFiles(String projectName) {
        List<String> fileList = new ArrayList<>();
        Git git = getGit(getProjectFolderPath(projectName));
        try {
            Status status = git.status().call();
            fileList.addAll(status.getAdded());
            git.close();
        } catch (Exception e) {
            logger.info(e.toString(), e);
            throw new RuntimeException(e);
        }
        return fileList;
    }

    public static void cloneRepo(String projectName, String userName, String URI, String branchName) {
        File projectDirFile = getProjectFolderPath(projectName).toFile();
        if (projectDirFile.exists()) {
            try (Git git = Git.init().setDirectory(projectDirFile).call()) {
                disableSsl(git);

                // GIT REMOTE ADD
                URIish urIish = new URIish(URI);
                git.remoteAdd()
                        .setName(urIish.getHumanishName())
                        .setUri(urIish).call();

                //GIT FETCH
                String remoteName = urIish.getHumanishName();
                FetchCommand fetch = git.fetch()
                        .setRemote(remoteName)
                        .setRefSpecs(new RefSpec("refs/heads/" + branchName + ":refs/remotes/" + remoteName + "/" + branchName));

                setAuthentication(fetch, projectName, userName, "origin");
                fetch.call();

                //GIT CHECKOUT
                CheckoutCommand checkout = git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint(urIish.getHumanishName() + "/" + branchName);
                checkout.call();
            } catch (Exception e) {
                logger.error(e.toString());
                throw new RuntimeException(e);
            }
        }
    }


    public static ResourcePath getResourcePath(String resourcePath) {
        String moduleId = "";
        String typeId = "";
        String resource = "";
        String[] paths = resourcePath.split("/");

        if (paths.length > 0) moduleId = paths[0];
        if (paths.length > 1) typeId = paths[1];
        if (paths.length > 2) resource = resourcePath.replace(moduleId + "/" + typeId + "/", "");

        return new ResourcePath(new ResourceType(moduleId, typeId), resource);
    }

    public static void disableSsl(Git git) throws IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.setBoolean("http", null, "sslVerify", false);
        config.save();
    }

    public static boolean isUpdatedResource(String projectName, String resourcePath){
        boolean isUpdatedResource;
        Path projectPath = getProjectFolderPath(projectName);
        String filePath = projectPath.toAbsolutePath() + "\\" +resourcePath.replace("/", "\\");

        try (Repository repository = getGit(projectPath).getRepository()) {

            // Get the ObjectId of the latest commit
            ObjectId headId = repository.resolve("HEAD");

            // Use RevWalk to traverse the commit history
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(headId);

                // Get the tree of the commit
                RevTree tree = commit.getTree();

                // Use TreeWalk to traverse the files in the tree
                try (TreeWalk treeWalk = new TreeWalk(repository)) {
                    treeWalk.addTree(tree);
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(resourcePath));

                    // Get the ObjectId of the file in the commit
                    if (!treeWalk.next()) {
                        throw new IllegalStateException("Did not find expected file " + resourcePath);
                    }
                    ObjectId objectId = treeWalk.getObjectId(0);

                    // Get the contents of the file in the commit
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (ObjectReader reader = repository.newObjectReader()) {
                        reader.open(objectId).copyTo(out);
                    }
                    Gson g = new Gson();

                    String contentBefore = out.toString();
                    JsonObject jsonBefore = (JsonObject) g.fromJson(contentBefore, JsonElement.class);
                    jsonBefore.remove("files");
                    jsonBefore.remove("attributes");


                    String contentAfter = new String(Files.readAllBytes(Paths.get(filePath)));
                    JsonObject jsonAfter = (JsonObject) g.fromJson(contentAfter, JsonElement.class);
                    jsonAfter.remove("files");
                    jsonAfter.remove("attributes");

                    isUpdatedResource = !jsonBefore.equals(jsonAfter);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return isUpdatedResource;
    }

    /**
     * Filter out JSON files whose only differences are key ordering.
     * Compares HEAD content with working tree content using Gson's semantic equality.
     */
    public static Set<String> filterJsonOrderingChanges(Repository repository, Path projectPath, Set<String> files) {
        Set<String> filtered = new LinkedHashSet<>();
        for (String filePath : files) {
            if (!filePath.endsWith(".json")) {
                filtered.add(filePath);
                continue;
            }
            try {
                String oldContent = "";
                ObjectId headId = repository.resolve("HEAD");
                if (headId != null) {
                    try (RevWalk revWalk = new RevWalk(repository)) {
                        RevCommit commit = revWalk.parseCommit(headId);
                        try (TreeWalk treeWalk = new TreeWalk(repository)) {
                            treeWalk.addTree(commit.getTree());
                            treeWalk.setRecursive(true);
                            treeWalk.setFilter(PathFilter.create(filePath));
                            if (treeWalk.next()) {
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                try (ObjectReader reader = repository.newObjectReader()) {
                                    reader.open(treeWalk.getObjectId(0)).copyTo(out);
                                }
                                oldContent = out.toString();
                            }
                        }
                    }
                }

                Path workingFile = projectPath.resolve(filePath);
                String newContent = "";
                if (Files.exists(workingFile)) {
                    newContent = new String(Files.readAllBytes(workingFile));
                }

                Gson gson = new Gson();
                JsonElement oldJson = gson.fromJson(oldContent, JsonElement.class);
                JsonElement newJson = gson.fromJson(newContent, JsonElement.class);
                if (oldJson != null && oldJson.equals(newJson)) {
                    continue;
                }
            } catch (Exception e) {
                // If anything fails, include the file (safe default)
            }
            filtered.add(filePath);
        }
        return filtered;
    }

    private static final Set<String> METADATA_FILENAMES = new HashSet<>(Arrays.asList("resource.json", "thumbnail.png"));

    /**
     * Filter out metadata-only changes from a status set. A metadata file (resource.json,
     * thumbnail.png) is suppressed when no sibling source file in the same Ignition resource
     * directory also changed across any status category.
     *
     * @param allChangedFiles union of all status sets (for cross-category sibling detection)
     * @param targetSet       the specific status set to filter
     * @return a new set with metadata-only entries removed
     */
    public static Set<String> filterMetadataOnlyChanges(Set<String> allChangedFiles, Set<String> targetSet) {
        // Group all changed files by parent directory (only for Ignition resource paths)
        Map<String, List<String>> dirToFiles = new HashMap<>();
        for (String file : allChangedFiles) {
            if (!hasActor(file)) continue;
            int lastSlash = file.lastIndexOf('/');
            if (lastSlash < 0) continue;
            String dir = file.substring(0, lastSlash);
            String filename = file.substring(lastSlash + 1);
            dirToFiles.computeIfAbsent(dir, k -> new ArrayList<>()).add(filename);
        }

        // Identify directories that have ONLY metadata files (no source files changed)
        Set<String> metadataOnlyDirs = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : dirToFiles.entrySet()) {
            boolean hasSourceFile = false;
            for (String filename : entry.getValue()) {
                if (!METADATA_FILENAMES.contains(filename)) {
                    hasSourceFile = true;
                    break;
                }
            }
            if (!hasSourceFile) {
                metadataOnlyDirs.add(entry.getKey());
            }
        }

        // Remove metadata file paths from targetSet for metadata-only directories
        Set<String> filtered = new LinkedHashSet<>();
        for (String file : targetSet) {
            if (hasActor(file)) {
                int lastSlash = file.lastIndexOf('/');
                if (lastSlash >= 0) {
                    String dir = file.substring(0, lastSlash);
                    String filename = file.substring(lastSlash + 1);
                    if (metadataOnlyDirs.contains(dir) && METADATA_FILENAMES.contains(filename)) {
                        continue;
                    }
                }
            }
            filtered.add(file);
        }
        return filtered;
    }

    /**
     * Filter out metadata-only entries from a commit file list (format "CHANGE_TYPE:path").
     * Suppresses resource.json/thumbnail.png entries when no sibling source file in the same
     * resource directory also changed in the commit.
     */
    public static List<String> filterMetadataOnlyCommitFiles(List<String> files) {
        // Parse paths and group by parent directory
        Map<String, List<String>> dirToFilenames = new HashMap<>();
        for (String entry : files) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx < 0) continue;
            String path = entry.substring(colonIdx + 1);
            if (!hasActor(path)) continue;
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash < 0) continue;
            String dir = path.substring(0, lastSlash);
            String filename = path.substring(lastSlash + 1);
            dirToFilenames.computeIfAbsent(dir, k -> new ArrayList<>()).add(filename);
        }

        // Identify metadata-only directories
        Set<String> metadataOnlyDirs = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : dirToFilenames.entrySet()) {
            boolean hasSourceFile = false;
            for (String filename : entry.getValue()) {
                if (!METADATA_FILENAMES.contains(filename)) {
                    hasSourceFile = true;
                    break;
                }
            }
            if (!hasSourceFile) {
                metadataOnlyDirs.add(entry.getKey());
            }
        }

        // Filter out metadata entries from metadata-only directories
        List<String> filtered = new ArrayList<>();
        for (String entry : files) {
            int colonIdx = entry.indexOf(':');
            if (colonIdx >= 0) {
                String path = entry.substring(colonIdx + 1);
                if (hasActor(path)) {
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        String dir = path.substring(0, lastSlash);
                        String filename = path.substring(lastSlash + 1);
                        if (metadataOnlyDirs.contains(dir) && METADATA_FILENAMES.contains(filename)) {
                            continue;
                        }
                    }
                }
            }
            filtered.add(entry);
        }
        return filtered;
    }

    /**
     * Normalize JSON content by sorting keys recursively for consistent diffing.
     * Returns the original content unchanged if it is not valid JSON.
     */
    public static String normalizeJson(String content) {
        if (content == null || content.isEmpty()) return content;
        try {
            Gson gson = new Gson();
            JsonElement element = gson.fromJson(content, JsonElement.class);
            if (element != null) {
                JsonElement sorted = sortJsonKeys(element);
                return new GsonBuilder().setPrettyPrinting().create().toJson(sorted);
            }
        } catch (Exception e) {
            // Not valid JSON, return as-is
        }
        return content;
    }

    private static JsonElement sortJsonKeys(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject original = element.getAsJsonObject();
            JsonObject sorted = new JsonObject();
            List<String> keys = new ArrayList<>(original.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                sorted.add(key, sortJsonKeys(original.get(key)));
            }
            return sorted;
        } else if (element.isJsonArray()) {
            JsonArray sortedArray = new JsonArray();
            for (JsonElement item : element.getAsJsonArray()) {
                sortedArray.add(sortJsonKeys(item));
            }
            return sortedArray;
        }
        return element;
    }

    public static List<String> listLocalBranches(Path projectFolderPath) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            List<Ref> refs = git.branchList().call();
            List<String> branches = new ArrayList<>();
            for (Ref ref : refs) {
                branches.add(Repository.shortenRefName(ref.getName()));
            }
            return branches;
        }
    }

    public static List<String> listRemoteBranches(Path projectFolderPath) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            List<String> branches = new ArrayList<>();
            for (Ref ref : refs) {
                branches.add(Repository.shortenRefName(ref.getName()));
            }
            return branches;
        }
    }

    public static String getCurrentBranch(Path projectFolderPath) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            // In detached HEAD, getBranch() returns the full commit hash
            String fullBranch = repo.getFullBranch();
            if (fullBranch != null && !fullBranch.startsWith("refs/heads/")) {
                return branch.substring(0, 7) + " (detached)";
            }
            return branch;
        }
    }

    public static boolean createBranch(Path projectFolderPath, String branchName) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            git.branchCreate().setName(branchName).call();
            return true;
        }
    }

    private static final String STASH_PREFIX = "auto-stash: ";

    public static boolean checkoutBranch(Path projectFolderPath, String branchName) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            String currentBranch = git.getRepository().getBranch();

            // Stash uncommitted changes on the current branch
            stashChanges(git, currentBranch);

            List<Ref> localRefs = git.branchList().call();
            boolean localExists = false;
            for (Ref ref : localRefs) {
                if (Repository.shortenRefName(ref.getName()).equals(branchName)) {
                    localExists = true;
                    break;
                }
            }

            if (localExists) {
                git.checkout().setName(branchName).call();
            } else {
                git.checkout()
                        .setCreateBranch(true)
                        .setName(branchName)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                        .setStartPoint("origin/" + branchName)
                        .call();
            }

            // Apply stashed changes for the target branch if any exist
            applyStash(git, branchName);

            return true;
        }
    }

    public static boolean checkoutCommit(Path projectFolderPath, String commitHash) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            String currentRef = git.getRepository().getFullBranch();

            // Only stash if currently on a branch (not already detached)
            if (currentRef != null && currentRef.startsWith("refs/heads/")) {
                String currentBranch = Repository.shortenRefName(currentRef);
                stashChanges(git, currentBranch);
            }

            git.checkout().setName(commitHash).call();
            // No applyStash — detached HEAD has no branch-scoped stash
            return true;
        }
    }

    private static void stashChanges(Git git, String branchName) throws Exception {
        Status status = git.status().call();
        boolean hasChanges = !status.getUncommittedChanges().isEmpty()
                || !status.getUntracked().isEmpty()
                || !status.getModified().isEmpty()
                || !status.getMissing().isEmpty();

        if (hasChanges) {
            git.stashCreate()
                    .setIncludeUntracked(true)
                    .setWorkingDirectoryMessage(STASH_PREFIX + branchName)
                    .call();
        }
    }

    private static void applyStash(Git git, String branchName) throws Exception {
        String targetMessage = STASH_PREFIX + branchName;
        Collection<RevCommit> stashes = git.stashList().call();
        int index = 0;
        for (RevCommit stash : stashes) {
            if (stash.getFullMessage().contains(targetMessage)) {
                try {
                    git.stashApply().setStashRef("stash@{" + index + "}").call();
                } catch (org.eclipse.jgit.api.errors.StashApplyFailureException e) {
                    // Stash conflicts with the current branch state — reset the
                    // failed merge and discard the stash so checkout can proceed.
                    logger.warn("Stash for branch '" + branchName + "' could not be applied cleanly; discarding stashed changes.", e);
                    git.reset().setMode(ResetCommand.ResetType.HARD).call();
                }
                git.stashDrop().setStashRef(index).call();
                return;
            }
            index++;
        }
    }

    public static List<String> getResourceDiffContent(String projectName, String resourcePath) {
        Path projectPath = getProjectFolderPath(projectName);
        String oldContent = "";
        String newContent = "";

        // For Ignition resources, the directory contains resource.json (metadata) plus
        // one or more data files (view.json, data.bin, code.py, etc.).
        // We want the data files, not the metadata.
        String filePath = resourcePath;
        if (hasActor(resourcePath)) {
            filePath = findDataFile(projectPath, resourcePath);
        }

        // Read old content from HEAD
        try (Repository repository = getGit(projectPath).getRepository()) {
            ObjectId headId = repository.resolve("HEAD");
            if (headId != null) {
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit commit = revWalk.parseCommit(headId);
                    RevTree tree = commit.getTree();

                    try (TreeWalk treeWalk = new TreeWalk(repository)) {
                        treeWalk.addTree(tree);
                        treeWalk.setRecursive(true);
                        treeWalk.setFilter(PathFilter.create(filePath));

                        if (treeWalk.next()) {
                            ObjectId objectId = treeWalk.getObjectId(0);
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try (ObjectReader reader = repository.newObjectReader()) {
                                reader.open(objectId).copyTo(out);
                            }
                            oldContent = out.toString();
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading HEAD content for diff", e);
        }

        // Read new content from working tree
        Path workingTreeFile = projectPath.resolve(filePath.replace("/", File.separator));
        if (Files.exists(workingTreeFile)) {
            try {
                newContent = new String(Files.readAllBytes(workingTreeFile));
            } catch (IOException e) {
                logger.error("Error reading working tree content for diff", e);
            }
        }

        // Normalize JSON to eliminate key-ordering noise in diffs
        if (filePath.endsWith(".json")) {
            oldContent = normalizeJson(oldContent);
            newContent = normalizeJson(newContent);
        }

        return Arrays.asList(oldContent, newContent);
    }

    /**
     * Find the primary data file inside an Ignition resource directory,
     * skipping resource.json (which is metadata).
     * Falls back to resource.json if no other files exist.
     */
    private static String findDataFile(Path projectPath, String resourcePath) {
        Path resourceDir = projectPath.resolve(resourcePath.replace("/", File.separator));
        if (Files.isDirectory(resourceDir)) {
            try {
                java.util.Optional<Path> dataFile = Files.list(resourceDir)
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().equals("resource.json"))
                        .filter(p -> !p.getFileName().toString().equals("thumbnail.png"))
                        .findFirst();
                if (dataFile.isPresent()) {
                    return resourcePath + "/" + dataFile.get().getFileName().toString();
                }
            } catch (IOException e) {
                logger.error("Error listing resource directory", e);
            }
        }
        // Fallback to resource.json if no data file found
        return resourcePath + "/resource.json";
    }

    public static boolean deleteBranch(Path projectFolderPath, String branchName) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            String currentBranch = git.getRepository().getBranch();
            if (branchName.equals(currentBranch)) {
                throw new IllegalStateException("Cannot delete the currently checked out branch: " + branchName);
            }
            git.branchDelete().setBranchNames(branchName).setForce(true).call();
            return true;
        }
    }

    /**
     * Get paginated commit history from the repository log.
     *
     * @param projectFolderPath path to the git working directory
     * @param skip              number of commits to skip (for pagination)
     * @param limit             maximum number of commits to return
     * @return list of String arrays: [fullHash, shortHash, author, date, message, refs]
     */
    public static List<String[]> getCommitLog(Path projectFolderPath, int skip, int limit) {
        List<String[]> commits = new ArrayList<>();
        try (Git git = getGit(projectFolderPath)) {
            Repository repo = git.getRepository();

            // Build a map of commit hash → branch names for ref decorations
            Map<String, List<String>> refMap = new HashMap<>();
            for (Ref ref : repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)) {
                ObjectId id = ref.getPeeledObjectId() != null ? ref.getPeeledObjectId() : ref.getObjectId();
                String name = ref.getName().substring(Constants.R_HEADS.length());
                refMap.computeIfAbsent(id.getName(), k -> new ArrayList<>()).add(name);
            }
            for (Ref ref : repo.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES)) {
                ObjectId id = ref.getPeeledObjectId() != null ? ref.getPeeledObjectId() : ref.getObjectId();
                String name = ref.getName().substring(Constants.R_REMOTES.length());
                refMap.computeIfAbsent(id.getName(), k -> new ArrayList<>()).add(name);
            }

            LogCommand logCmd = git.log().setSkip(skip).setMaxCount(limit);

            // Include the upstream remote-tracking branch so fetched commits
            // appear in the history even before merging/pulling.
            String branch = repo.getBranch();
            if (branch != null) {
                BranchConfig branchConfig = new BranchConfig(repo.getConfig(), branch);
                String trackingBranch = branchConfig.getTrackingBranch();
                if (trackingBranch != null) {
                    Ref trackingRef = repo.exactRef(trackingBranch);
                    if (trackingRef != null) {
                        // Adding any ref disables the default HEAD start, so add both
                        ObjectId headId = repo.resolve(Constants.HEAD);
                        if (headId != null) {
                            logCmd.add(headId);
                        }
                        logCmd.add(trackingRef.getObjectId());
                    }
                }
            }

            Iterable<RevCommit> log = logCmd.call();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (RevCommit commit : log) {
                String fullHash = commit.getName();
                String shortHash = fullHash.substring(0, 7);
                String authorName = commit.getAuthorIdent().getName();
                String authorEmail = commit.getAuthorIdent().getEmailAddress();
                String author = (authorName == null || authorName.isEmpty()) ? authorEmail : authorName;
                String date = dateFormat.format(commit.getAuthorIdent().getWhen());
                String message = commit.getShortMessage();
                List<String> refs = refMap.getOrDefault(fullHash, java.util.Collections.emptyList());
                String refsStr = String.join(",", refs);
                commits.add(new String[]{fullHash, shortHash, author, date, message, refsStr});
            }
        } catch (Exception e) {
            logger.error("Error getting commit log", e);
        }
        return commits;
    }

    /**
     * List files changed in a specific commit by diffing against its parent tree.
     * Handles initial commits (no parent) via {@link EmptyTreeIterator}.
     *
     * @param projectFolderPath path to the git working directory
     * @param commitHash        full SHA-1 hash of the commit
     * @return list of strings in format "CHANGE_TYPE:path" (e.g. "ADD:src/Foo.java")
     */
    public static List<String> getCommitFileList(Path projectFolderPath, String commitHash) {
        List<String> files = new ArrayList<>();
        try (Git git = getGit(projectFolderPath);
             Repository repository = git.getRepository()) {

            ObjectId commitId = repository.resolve(commitHash);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);

                AbstractTreeIterator parentTreeIter;
                if (commit.getParentCount() > 0) {
                    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                    parentTreeIter = prepareTreeParser(repository, parent);
                } else {
                    parentTreeIter = new EmptyTreeIterator();
                }

                AbstractTreeIterator commitTreeIter = prepareTreeParser(repository, commit);

                try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    diffFormatter.setRepository(repository);
                    List<DiffEntry> diffs = diffFormatter.scan(parentTreeIter, commitTreeIter);
                    for (DiffEntry entry : diffs) {
                        String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                                ? entry.getOldPath()
                                : entry.getNewPath();
                        files.add(entry.getChangeType().name() + ":" + path);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error getting commit file list", e);
        }
        return files;
    }

    /**
     * Get the old (parent) and new (commit) content for a specific file at a given commit.
     *
     * @param projectFolderPath path to the git working directory
     * @param commitHash        full SHA-1 hash of the commit
     * @param filePath          repository-relative file path
     * @return two-element list: [oldContent, newContent]; empty strings for missing content
     */
    public static List<String> getCommitFileDiffContent(Path projectFolderPath, String commitHash, String filePath) {
        String oldContent = "";
        String newContent = "";

        try (Git git = getGit(projectFolderPath);
             Repository repository = git.getRepository()) {

            ObjectId commitId = repository.resolve(commitHash);
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);

                // Get new content from the commit
                newContent = getFileContentAtCommit(repository, commit, filePath);

                // Get old content from parent (if exists)
                if (commit.getParentCount() > 0) {
                    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                    oldContent = getFileContentAtCommit(repository, parent, filePath);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting commit file diff content", e);
        }

        if (filePath.endsWith(".json")) {
            oldContent = normalizeJson(oldContent);
            newContent = normalizeJson(newContent);
        }

        return Arrays.asList(oldContent, newContent);
    }

    /** Create a {@link CanonicalTreeParser} positioned at the root of a commit's tree. */
    private static CanonicalTreeParser prepareTreeParser(Repository repository, RevCommit commit) throws IOException {
        try (ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, commit.getTree().getId());
            return treeParser;
        }
    }

    /** Read the UTF-8 content of a file at a specific commit, or empty string if not found. */
    private static String getFileContentAtCommit(Repository repository, RevCommit commit, String filePath) {
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (treeWalk.next()) {
                ObjectId objectId = treeWalk.getObjectId(0);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (ObjectReader reader = repository.newObjectReader()) {
                    reader.open(objectId).copyTo(out);
                }
                return out.toString();
            }
        } catch (IOException e) {
            logger.error("Error reading file content at commit", e);
        }
        return "";
    }

    /**
     * Discard uncommitted changes for the given paths.
     * Tracked (modified/deleted) files are checked out from HEAD.
     * Untracked (created) files are deleted via git clean.
     *
     * @param projectFolderPath path to the git working directory
     * @param paths             list of resource paths to discard
     * @return true if discard succeeded
     */
    public static boolean discardChanges(Path projectFolderPath, List<String> paths) {
        try (Git git = getGit(projectFolderPath)) {
            Status status = git.status().call();
            Set<String> untracked = status.getUntracked();

            List<String> trackedPaths = new ArrayList<>();
            Set<String> untrackedPaths = new HashSet<>();

            for (String path : paths) {
                boolean isUntracked = false;
                for (String u : untracked) {
                    if (u.equals(path) || u.startsWith(path + "/")) {
                        isUntracked = true;
                        break;
                    }
                }
                if (isUntracked) {
                    untrackedPaths.add(path);
                } else {
                    trackedPaths.add(path);
                }
            }

            // Revert tracked files to HEAD
            if (!trackedPaths.isEmpty()) {
                CheckoutCommand checkout = git.checkout();
                for (String path : trackedPaths) {
                    checkout.addPath(path);
                }
                checkout.call();
            }

            // Remove untracked files
            if (!untrackedPaths.isEmpty()) {
                git.clean()
                        .setPaths(untrackedPaths)
                        .setCleanDirectories(true)
                        .setForce(true)
                        .call();
            }

            return true;
        } catch (Exception e) {
            logger.error("Error discarding changes", e);
            return false;
        }
    }

    /**
     * Create a new commit that reverses the changes of the specified commit (git revert).
     * If the revert produces merge conflicts, the operation is aborted via hard reset
     * and an error is thrown so the repo is never left in a conflicted state.
     */
    public static boolean revertCommit(Path projectFolderPath, String commitHash) {
        try (Git git = getGit(projectFolderPath)) {
            ObjectId commitId = git.getRepository().resolve(commitHash);
            if (commitId == null) {
                throw new RuntimeException("Commit not found: " + commitHash);
            }
            RevCommit result = git.revert().include(commitId).call();
            if (result == null) {
                // Revert produced conflicts — abort to avoid leaving repo in conflicted state
                git.reset().setMode(ResetCommand.ResetType.HARD).call();
                throw new RuntimeException("Revert failed — conflicts detected. The revert has been aborted.");
            }
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error reverting commit " + commitHash, e);
            throw new RuntimeException(e);
        }
    }

    // ── Remote management ──────────────────────────────────────────────

    /**
     * List all remotes configured in the git repository.
     *
     * @param projectFolderPath path to the git working directory
     * @return list of String arrays: [name, url]
     */
    public static List<String[]> listRemotes(Path projectFolderPath) throws Exception {
        List<String[]> remotes = new ArrayList<>();
        try (Git git = getGit(projectFolderPath)) {
            List<RemoteConfig> configs = git.remoteList().call();
            for (RemoteConfig config : configs) {
                String name = config.getName();
                String url = config.getURIs().isEmpty() ? "" : config.getURIs().get(0).toString();
                remotes.add(new String[]{name, url});
            }
        }
        return remotes;
    }

    public static void addRemote(Path projectFolderPath, String name, String url) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            git.remoteAdd().setName(name).setUri(new URIish(url)).call();
        }
    }

    public static void removeRemote(Path projectFolderPath, String name) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            git.remoteRemove().setRemoteName(name).call();
        }
    }

    public static void setRemoteUrl(Path projectFolderPath, String name, String newUrl) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            git.remoteSetUrl().setRemoteName(name).setRemoteUri(new URIish(newUrl)).call();
        }
    }

    /**
     * Get the URL of a named remote from the git config.
     */
    public static String getRemoteUrl(Path projectFolderPath, String remoteName) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            List<RemoteConfig> configs = git.remoteList().call();
            for (RemoteConfig config : configs) {
                if (config.getName().equals(remoteName)) {
                    return config.getURIs().isEmpty() ? null : config.getURIs().get(0).toString();
                }
            }
        }
        return null;
    }

    // ── Merge conflict resolution ──────────────────────────────────────

    /**
     * Get the list of files currently in merge conflict.
     */
    public static List<String> getConflictingFiles(Path projectFolderPath) {
        try (Git git = getGit(projectFolderPath)) {
            Status status = git.status().call();
            return new ArrayList<>(status.getConflicting());
        } catch (Exception e) {
            logger.error("Error getting conflicting files", e);
            return new ArrayList<>();
        }
    }

    /**
     * Resolve a single conflicting file by accepting "ours" or "theirs" stage.
     * After checkout, marks the file as resolved by adding it to the index.
     *
     * @param stage "OURS" or "THEIRS"
     */
    public static boolean resolveConflict(Path projectFolderPath, String filePath, String stage) {
        try (Git git = getGit(projectFolderPath)) {
            CheckoutCommand.Stage checkoutStage = "OURS".equals(stage)
                    ? CheckoutCommand.Stage.OURS
                    : CheckoutCommand.Stage.THEIRS;
            git.checkout().setStage(checkoutStage).addPath(filePath).call();
            git.add().addFilepattern(filePath).call();
            return true;
        } catch (Exception e) {
            logger.error("Error resolving conflict for " + filePath, e);
            throw new RuntimeException("Failed to resolve conflict: " + e.getMessage());
        }
    }

    /**
     * Abort the current merge by performing a hard reset to HEAD.
     */
    public static boolean abortMerge(Path projectFolderPath) {
        try (Git git = getGit(projectFolderPath)) {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            return true;
        } catch (Exception e) {
            logger.error("Error aborting merge", e);
            throw new RuntimeException("Failed to abort merge: " + e.getMessage());
        }
    }

    /**
     * Complete a merge by committing after all conflicts are resolved.
     * Reads the default merge commit message from .git/MERGE_MSG.
     */
    public static boolean completeMergeCommit(Path projectFolderPath, String projectName, String userName) {
        try (Git git = getGit(projectFolderPath)) {
            // Verify no remaining conflicts
            Status status = git.status().call();
            if (!status.getConflicting().isEmpty()) {
                throw new RuntimeException("Cannot complete merge: " +
                        status.getConflicting().size() + " unresolved conflict(s) remain.");
            }
            CommitCommand commit = git.commit();
            commit.setMessage(readMergeMessage(git.getRepository()));
            setCommitAuthor(commit, projectName, userName);
            commit.call();
            return true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error completing merge commit", e);
            throw new RuntimeException("Failed to complete merge: " + e.getMessage());
        }
    }

    /**
     * Read the merge message generated by git during the merge (.git/MERGE_MSG).
     */
    private static String readMergeMessage(Repository repository) {
        try {
            Path mergeMsgFile = repository.getDirectory().toPath().resolve("MERGE_MSG");
            if (java.nio.file.Files.exists(mergeMsgFile)) {
                return new String(java.nio.file.Files.readAllBytes(mergeMsgFile)).trim();
            }
        } catch (IOException e) {
            logger.warn("Could not read MERGE_MSG", e);
        }
        return "Merge commit";
    }

    /**
     * Get the "ours" (HEAD) and "theirs" (MERGE_HEAD) content for a conflicting file.
     *
     * @return two-element list: [oursContent, theirsContent]
     */
    public static List<String> getConflictDiffContent(Path projectFolderPath, String filePath) {
        String oursContent = "";
        String theirsContent = "";

        try (Git git = getGit(projectFolderPath)) {
            Repository repository = git.getRepository();

            // Read ours (HEAD)
            ObjectId headId = repository.resolve("HEAD");
            if (headId != null) {
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit headCommit = revWalk.parseCommit(headId);
                    oursContent = getFileContentAtCommit(repository, headCommit, filePath);
                }
            }

            // Read theirs (MERGE_HEAD)
            ObjectId mergeHeadId = repository.resolve("MERGE_HEAD");
            if (mergeHeadId != null) {
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit mergeCommit = revWalk.parseCommit(mergeHeadId);
                    theirsContent = getFileContentAtCommit(repository, mergeCommit, filePath);
                }
            }
        } catch (Exception e) {
            logger.error("Error getting conflict diff content for " + filePath, e);
        }

        if (filePath.endsWith(".json")) {
            oursContent = normalizeJson(oursContent);
            theirsContent = normalizeJson(theirsContent);
        }

        return Arrays.asList(oursContent, theirsContent);
    }

    // ── Per-remote credential lookup ───────────────────────────────────

    /**
     * Look up a {@link GitRemoteCredentialsRecord} for a given project, user, and remote name.
     *
     * @return the credential record, or null if not found
     */
    public static GitRemoteCredentialsRecord getRemoteCredentialsRecord(
            String projectName, String userName, String remoteName) throws Exception {
        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        SQuery<GitRemoteCredentialsRecord> query = new SQuery<>(GitRemoteCredentialsRecord.META)
                .eq(GitRemoteCredentialsRecord.ProjectId, projectRecord.getId())
                .eq(GitRemoteCredentialsRecord.IgnitionUser, userName)
                .eq(GitRemoteCredentialsRecord.RemoteName, remoteName);
        return getContext().getPersistenceInterface().queryOne(query);
    }

}
