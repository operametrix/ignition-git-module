package com.operametrix.ignition.git.managers;

import com.operametrix.ignition.git.SshTransportConfigCallback;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.inductiveautomation.ignition.common.gson.Gson;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;


import static com.operametrix.ignition.git.GatewayHook.context;

public class GitManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitManager.class);

    static public Git getGit(Path projectFolderPath) {
        Git git;
        try {
            git = Git.open(projectFolderPath.resolve(".git").toFile());
            disableSsl(git);
        } catch (IOException e) {
            logger.error("Unable to retrieve Git repository", e);
            throw new RuntimeException(e);
        }
        return git;
    }

    public static Path getProjectFolderPath(String projectName) {
        Path dataDir = getDataFolderPath();
        return dataDir.resolve("projects").resolve(projectName);
    }

    public static Path getDataFolderPath() {
        return context.getSystemManager().getDataDir().toPath();
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

    public static void setAuthentication(TransportCommand<?, ?> command, String projectName, String userName) throws Exception {
        GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
        GitReposUsersRecord user = getGitReposUserRecord(gitProjectsConfigRecord, userName);

        setAuthentication(command, gitProjectsConfigRecord, user);
    }

    public static void setAuthentication(TransportCommand<?, ?> command, GitProjectsConfigRecord gitProjectsConfigRecord, GitReposUsersRecord user) {

        if (gitProjectsConfigRecord.isSSHAuthentication()) {
            command.setTransportConfigCallback(getSshTransportConfigCallback(user));
        } else {
            command.setCredentialsProvider(getUsernamePasswordCredentialsProvider(user));
        }
    }


    public static void setCommitAuthor(CommitCommand command, String projectName, String userName) {
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            GitReposUsersRecord user = getGitReposUserRecord(gitProjectsConfigRecord, userName);
            command.setAuthor("", user.getEmail());
        } catch (Exception e) {
            logger.error("An error occurred while setting up commit author.", e);
        }

    }

    public static GitProjectsConfigRecord getGitProjectConfigRecord(String projectName) throws Exception {
        SQuery<GitProjectsConfigRecord> projectQuery = new SQuery<>(GitProjectsConfigRecord.META)
                .eq(GitProjectsConfigRecord.ProjectName, projectName);
        GitProjectsConfigRecord gitProjectsConfigRecord = context.getPersistenceInterface().queryOne(projectQuery);

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
        GitReposUsersRecord user = context.getPersistenceInterface().queryOne(userQuery);

        if (user == null) {
            throw new Exception("Git User not configured.");
        }

        return user;
    }

    public static UsernamePasswordCredentialsProvider getUsernamePasswordCredentialsProvider(GitReposUsersRecord user) {
        return new UsernamePasswordCredentialsProvider(user.getUserName(), user.getPassword());
    }

    public static SshTransportConfigCallback getSshTransportConfigCallback(GitReposUsersRecord user) {
        return new SshTransportConfigCallback(user.getSSHKey());
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
        ProjectManager projectManager = context.getProjectManager();
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
        ProjectManager projectManager = context.getProjectManager();
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
                FetchCommand fetch = git.fetch()
                        .setRemote(urIish.getHumanishName())
                        .setRefSpecs(new RefSpec("refs/heads/" + branchName + ":refs/remotes/" + urIish.getHumanishName() + "/" + branchName));

                setAuthentication(fetch, projectName, userName);
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

    public static String repoUriToUrl(String uri) {
        String url = uri;

        if (!uri.toLowerCase().startsWith("http")) {
            String[] splitUri = uri.split("[@:/]");

            if (splitUri.length >= 4) {
                String repoName = splitUri[3].split("\\.git")[0];
                url = "https://" + splitUri[1] + "/" + splitUri[2] + "/" + repoName;
            }
        }

        return url;
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
            return git.getRepository().getBranch();
        }
    }

    public static boolean createBranch(Path projectFolderPath, String branchName, String startPoint) throws Exception {
        try (Git git = getGit(projectFolderPath)) {
            CreateBranchCommand command = git.branchCreate().setName(branchName);
            if (startPoint != null && !startPoint.isEmpty()) {
                command.setStartPoint(startPoint);
            }
            command.call();
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

            Iterable<RevCommit> log = git.log().setSkip(skip).setMaxCount(limit).call();
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
}
