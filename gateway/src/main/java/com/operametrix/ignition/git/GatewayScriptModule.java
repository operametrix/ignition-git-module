package com.operametrix.ignition.git;

import com.operametrix.ignition.git.managers.*;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitRemoteCredentialsRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.operametrix.ignition.git.records.GitUserHttpsCredentialRecord;
import com.operametrix.ignition.git.records.GitUserSshKeyRecord;
import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.util.DatasetBuilder;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.operametrix.ignition.git.managers.GitImageManager.exportImages;
import static com.operametrix.ignition.git.managers.GitManager.*;
import static com.operametrix.ignition.git.managers.GitTagManager.exportTag;
import static com.operametrix.ignition.git.managers.GitThemeManager.exportTheme;

// Declares GitScriptInterface directly (not just via AbstractScriptModule): the 8.3
// RpcDelegate discovers @RpcInterface only on impl.getClass().getInterfaces() and does
// not walk superclasses, so the interface must be on the concrete class itself.
public class GatewayScriptModule extends AbstractScriptModule implements GitScriptInterface {
    private final LoggerEx logger = LoggerEx.newBuilder().build(getClass());
    private final GatewayContext context;

    GatewayScriptModule(GatewayContext context) {
        this.context = context;
    }

    @Override
    public boolean pullImpl(String projectName,
                            String userName,
                            String remoteName,
                            boolean importTags,
                            boolean importTheme,
                            boolean importImages) throws Exception {

        if (!projectHasRemote(projectName)) {
            throw new RuntimeException("No remote repository configured. Add a remote before pulling.");
        }

        try (Git git = getGit(getProjectFolderPath(projectName))) {
            PullCommand pull = git.pull();
            pull.setRemote(remoteName);
            setAuthentication(pull, projectName, userName, remoteName);

            PullResult result = pull.call();
            if (!result.isSuccessful()) {
                // Check specifically for merge conflicts
                if (result.getMergeResult() != null
                        && result.getMergeResult().getMergeStatus()
                            == MergeResult.MergeStatus.CONFLICTING) {
                    Set<String> conflicting = git.status().call().getConflicting();
                    String fileList = String.join("\n", conflicting);
                    throw new RuntimeException("MERGE_CONFLICT:" + fileList);
                }
                throw new RuntimeException("Pull failed: " +
                        (result.getMergeResult() != null
                                ? result.getMergeResult().getMergeStatus().toString()
                                : "unknown status"));
            }

            logger.info("Pull was successful.");
            GitProjectManager.importProject(projectName);

            if (importTags) {
                GitTagManager.importTagManager(projectName);
            }
            if (importTheme) {
                GitThemeManager.importTheme(projectName);
            }
            if (importImages) {
                GitImageManager.importImages(projectName);
            }
        } catch (GitAPIException e) {
            logger.error(e.toString());
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean pushImpl(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception {
        if (!projectHasRemote(projectName)) {
            throw new RuntimeException("No remote repository configured. Add a remote before pushing.");
        }

        try (Git git = getGit(getProjectFolderPath(projectName))) {
            PushCommand push = git.push();
            push.setRemote(remoteName);
            setAuthentication(push, projectName, userName, remoteName);

            if (pushAllBranches) {
                push.setPushAll();
            }
            if (pushTags) {
                push.setPushTags();
            }
            if (forcePush) {
                push.setForce(true);
            }
            Iterable<PushResult> results = push.call();
            for (PushResult result : results) {
                logger.trace(result.getMessages());
                for (org.eclipse.jgit.transport.RemoteRefUpdate update : result.getRemoteUpdates()) {
                    org.eclipse.jgit.transport.RemoteRefUpdate.Status status = update.getStatus();
                    if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                        throw new RuntimeException("REJECTED_NONFASTFORWARD: Push rejected — the remote contains commits that the local branch does not have. "
                                + "This typically happens after amending a commit that was already pushed.");
                    } else if (status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_NODELETE
                            || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED
                            || status == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                        throw new RuntimeException("Push rejected: " + update.getMessage());
                    }
                }
            }

        } catch (GitAPIException e) {
            logger.error(e.toString(), e);
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    protected boolean commitImpl(String projectName, String userName, List<String> changes, String message, boolean amend) {
        if (message == null || message.trim().isEmpty()) {
            throw new RuntimeException("Commit message cannot be empty.");
        }
        if (!amend && (changes == null || changes.isEmpty())) {
            throw new RuntimeException("Nothing to commit — select at least one file.");
        }
        try (Git git = getGit(getProjectFolderPath(projectName))) {
            for (String change : changes) {
                git.add().addFilepattern(change).call();
                git.add().setUpdate(true).addFilepattern(change).call();
            }

            CommitCommand commit = git.commit().setMessage(message);
            if (amend) {
                commit.setAmend(true);
            }
            setCommitAuthor(commit, projectName, userName);
            commit.call();
        } catch (GitAPIException e) {
            logger.error(e.toString(), e);
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public Dataset getUncommitedChangesImpl(String projectName, String userName) {
        Path projectPath = getProjectFolderPath(projectName);
        Dataset ds;
        List<String> changes = new ArrayList<>();
        DatasetBuilder builder = new DatasetBuilder();
        builder.colNames(List.of("resource", "type", "actor", "timestamp"));
        builder.colTypes(List.of(String.class, String.class, String.class, String.class));

        try (Git git = getGit(projectPath)) {
            Status status = git.status().call();

            Set<String> missing = status.getMissing();
            Set<String> uncommittedChanges = GitManager.filterJsonOrderingChanges(
                    git.getRepository(), projectPath, status.getUncommittedChanges());
            Set<String> untracked = status.getUntracked();
            Set<String> modified = GitManager.filterJsonOrderingChanges(
                    git.getRepository(), projectPath, status.getChanged());

            // Build union of all changed files for cross-category sibling detection
            Set<String> allChanged = new HashSet<>();
            allChanged.addAll(missing);
            allChanged.addAll(uncommittedChanges);
            allChanged.addAll(untracked);
            allChanged.addAll(modified);

            // Filter metadata-only changes from each set
            missing = GitManager.filterMetadataOnlyChanges(allChanged, missing);
            uncommittedChanges = GitManager.filterMetadataOnlyChanges(allChanged, uncommittedChanges);
            untracked = GitManager.filterMetadataOnlyChanges(allChanged, untracked);
            modified = GitManager.filterMetadataOnlyChanges(allChanged, modified);

            logger.debug("Missing files: {}" + missing);
            uncommittedChangesBuilder(projectName, missing, "Deleted", changes, builder);

            logger.debug("Uncommitted changes: {}" + uncommittedChanges);
            uncommittedChangesBuilder(projectName, uncommittedChanges, "Uncommitted", changes, builder);

            logger.debug("Untracked files: {}" + untracked);
            uncommittedChangesBuilder(projectName, untracked, "Created", changes, builder);

            logger.debug("Modified files: {}" + modified);
            uncommittedChangesBuilder(projectName, modified, "Modified", changes, builder);
        } catch (Exception e) {
            logger.error(e.toString(), e);

        }
        ds = builder.build();

        return ds != null ? ds : new BasicDataset();
    }

    @Override
    public boolean isRegisteredUserImpl(String projectName, String userName) {
        boolean registered;
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            getGitReposUserRecord(gitProjectsConfigRecord, userName);
            registered = true;
        } catch (Exception e) {
            registered = false;
        }
        return registered;
    }

    @Override
    protected boolean snapshotTagsImpl(String projectName) {
        exportTag(getProjectFolderPath(projectName));
        return true;
    }

    @Override
    protected boolean snapshotThemesImpl(String projectName) {
        exportTheme(getProjectFolderPath(projectName));
        return true;
    }

    @Override
    protected boolean snapshotImagesImpl(String projectName) {
        exportImages(getProjectFolderPath(projectName));
        return true;
    }

    @Override
    public void setupLocalRepoImpl(String projectName, String userName) throws Exception {
        Path projectFolderPath = getProjectFolderPath(projectName);
        GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);

        Path path = projectFolderPath.resolve(".git");

        if (!gitProjectsConfigRecord.hasRemote()) {
            // Local-only repo: just ensure .git exists
            if (!Files.exists(path)) {
                try (Git git = Git.init().setDirectory(projectFolderPath.toFile()).call()) {
                    disableSsl(git);
                }
            }
            return;
        }

        if (!Files.exists(path)) {
            try (Git git = Git.init().setDirectory(projectFolderPath.toFile()).call()) {
                disableSsl(git);

                final URIish urIish = new URIish(gitProjectsConfigRecord.getURI());

                git.remoteAdd().setName("origin").setUri(urIish).call();

                // Lightweight ls-remote to detect the default branch without downloading objects
                LsRemoteCommand lsRemote = git.lsRemote().setRemote("origin").setHeads(true);
                setAuthentication(lsRemote, projectName, userName, "origin");
                java.util.Collection<Ref> remoteRefs = lsRemote.call();

                if (remoteRefs.isEmpty()) {
                    // Empty remote — push current project as initial content
                    setupGitFromCurrentFolder(projectName, userName, git);
                } else {
                    // Detect default branch, then shallow-fetch only that branch
                    String defaultBranch = detectDefaultBranchFromRefs(remoteRefs);

                    FetchCommand fetch = git.fetch()
                            .setRemote("origin")
                            .setRefSpecs(new RefSpec(
                                    "+refs/heads/" + defaultBranch + ":refs/remotes/origin/" + defaultBranch))
                            .setDepth(1);
                    setAuthentication(fetch, projectName, userName, "origin");
                    fetch.call();

                    setupGitFromRemoteRepo(projectName, defaultBranch, git);

                    // Unshallow to pull full commit history for the History panel
                    FetchCommand unshallow = git.fetch()
                            .setRemote("origin")
                            .setUnshallow(true);
                    setAuthentication(unshallow, projectName, userName, "origin");
                    unshallow.call();
                }
            } catch (Exception e) {
                logger.error("An error occurred while setting up local repo for '" + projectName + "' project.", e);
                throw e;
            }
        }
    }

    @Override
    protected List<String> getLocalBranchesImpl(String projectName) throws Exception {
        return GitManager.listLocalBranches(getProjectFolderPath(projectName));
    }

    @Override
    protected List<String> getRemoteBranchesImpl(String projectName) throws Exception {
        return GitManager.listRemoteBranches(getProjectFolderPath(projectName));
    }

    @Override
    protected String getCurrentBranchImpl(String projectName) throws Exception {
        return GitManager.getCurrentBranch(getProjectFolderPath(projectName));
    }

    @Override
    protected boolean createBranchImpl(String projectName, String branchName) throws Exception {
        return GitManager.createBranch(getProjectFolderPath(projectName), branchName);
    }

    @Override
    protected boolean checkoutBranchImpl(String projectName, String branchName) throws Exception {
        boolean result = GitManager.checkoutBranch(getProjectFolderPath(projectName), branchName);
        GitProjectManager.importProject(projectName);
        return result;
    }

    @Override
    protected boolean deleteBranchImpl(String projectName, String branchName) throws Exception {
        return GitManager.deleteBranch(getProjectFolderPath(projectName), branchName);
    }

    @Override
    protected boolean isProjectRegisteredImpl(String projectName) {
        try {
            getGitProjectConfigRecord(projectName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected List<String> getResourceDiffImpl(String projectName, String resourcePath) {
        return GitManager.getResourceDiffContent(projectName, resourcePath);
    }

    @Override
    protected Dataset getCommitHistoryImpl(String projectName, int skip, int limit) {
        java.util.List<String[]> commits = GitManager.getCommitLog(getProjectFolderPath(projectName), skip, limit);
        DatasetBuilder builder = new DatasetBuilder();
        builder.colNames(java.util.List.of("hash", "shortHash", "author", "date", "message", "refs"));
        builder.colTypes(java.util.List.of(String.class, String.class, String.class, String.class, String.class, String.class));
        for (String[] row : commits) {
            builder.addRow((Object[]) row);
        }
        Dataset ds = builder.build();
        return ds != null ? ds : new BasicDataset();
    }

    @Override
    protected List<String> getCommitFilesImpl(String projectName, String commitHash) {
        List<String> files = GitManager.getCommitFileList(getProjectFolderPath(projectName), commitHash);
        return GitManager.filterMetadataOnlyCommitFiles(files);
    }

    @Override
    protected List<String> getCommitFileDiffImpl(String projectName, String commitHash, String filePath) {
        return GitManager.getCommitFileDiffContent(getProjectFolderPath(projectName), commitHash, filePath);
    }

    @Override
    protected boolean discardChangesImpl(String projectName, List<String> paths) {
        boolean result = GitManager.discardChanges(getProjectFolderPath(projectName), paths);
        if (result) {
            GitProjectManager.importProject(projectName);
        }
        return result;
    }

    @Override
    protected boolean checkoutCommitImpl(String projectName, String commitHash) throws Exception {
        boolean result = GitManager.checkoutCommit(getProjectFolderPath(projectName), commitHash);
        GitProjectManager.importProject(projectName);
        return result;
    }

    @Override
    protected boolean revertCommitImpl(String projectName, String commitHash) throws Exception {
        boolean result = GitManager.revertCommit(getProjectFolderPath(projectName), commitHash);
        if (result) {
            GitProjectManager.importProject(projectName);
        }
        return result;
    }

    @Override
    protected boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                             long sshKeyId, long httpsCredentialId) throws Exception {
        // Check project not already registered
        if (isProjectRegisteredImpl(projectName)) {
            throw new Exception("Project '" + projectName + "' is already registered.");
        }

        // Create project config record
        GitProjectsConfigRecord projectRecord = new GitProjectsConfigRecord();
        projectRecord.setProjectName(projectName);
        projectRecord.setURI(repoUri);
        projectRecord.save();

        // Create user registration record
        GitReposUsersRecord userRecord = new GitReposUsersRecord();
        userRecord.setProjectId(projectRecord.getId());
        userRecord.setIgnitionUser(ignitionUser);
        userRecord.save();

        // Create per-remote credential record for "origin" with FK refs to user-level credentials
        GitRemoteCredentialsRecord remoteCreds = new GitRemoteCredentialsRecord();
        remoteCreds.setProjectId(projectRecord.getId());
        remoteCreds.setIgnitionUser(ignitionUser);
        remoteCreds.setRemoteName("origin");
        if (sshKeyId > 0) {
            remoteCreds.setSshKeyId(sshKeyId);
        }
        if (httpsCredentialId > 0) {
            remoteCreds.setHttpsCredentialId(httpsCredentialId);
        }
        remoteCreds.save();

        // Attempt to initialize the local repo
        try {
            setupLocalRepoImpl(projectName, ignitionUser);
        } catch (Exception e) {
            // Rollback: delete all records on failure
            try {
                remoteCreds.delete();
            } catch (Exception ignored) {
            }
            try {
                userRecord.delete();
            } catch (Exception ignored) {
            }
            try {
                projectRecord.delete();
            } catch (Exception ignored) {
            }
            throw e;
        }

        return true;
    }

    @Override
    protected boolean initializeLocalProjectImpl(String projectName, String ignitionUser) throws Exception {
        if (isProjectRegisteredImpl(projectName)) {
            throw new Exception("Project '" + projectName + "' is already registered.");
        }

        // Create project config record with empty URI (no remote)
        GitProjectsConfigRecord projectRecord = new GitProjectsConfigRecord();
        projectRecord.setProjectName(projectName);
        projectRecord.setURI("");
        projectRecord.save();

        // Create user registration record
        GitReposUsersRecord userRecord = new GitReposUsersRecord();
        userRecord.setProjectId(projectRecord.getId());
        userRecord.setIgnitionUser(ignitionUser);
        userRecord.save();

        // Initialize local repo: git init + add . + initial commit
        try {
            Path projectFolderPath = getProjectFolderPath(projectName);
            try (Git git = Git.init().setDirectory(projectFolderPath.toFile()).call()) {
                disableSsl(git);
                git.add().addFilepattern(".").call();

                CommitCommand commit = git.commit().setMessage("Initial commit");
                setCommitAuthor(commit, projectName, ignitionUser);
                commit.call();
            }
        } catch (Exception e) {
            // Rollback: delete both records on failure
            try {
                userRecord.delete();
            } catch (Exception ignored) {
            }
            try {
                projectRecord.delete();
            } catch (Exception ignored) {
            }
            throw e;
        }

        return true;
    }

    @Override
    protected boolean hasRemoteRepositoryImpl(String projectName) {
        try {
            return projectHasRemote(projectName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether the project's git repo has any remote configured in {@code .git/config}.
     * Unlike {@link GitProjectsConfigRecord#hasRemote()} — which only reflects the record's
     * stored URI (populated at init for the cloned remote) — this inspects the live git
     * config, so user-named (non-origin) remotes added later are recognized.
     */
    private boolean projectHasRemote(String projectName) throws Exception {
        return !GitManager.listRemotes(getProjectFolderPath(projectName)).isEmpty();
    }

    @Override
    protected Dataset listRemotesImpl(String projectName) throws Exception {
        List<String[]> remotes = GitManager.listRemotes(getProjectFolderPath(projectName));
        DatasetBuilder builder = new DatasetBuilder();
        builder.colNames(List.of("name", "url"));
        builder.colTypes(List.of(String.class, String.class));
        for (String[] row : remotes) {
            builder.addRow((Object[]) row);
        }
        Dataset ds = builder.build();
        return ds != null ? ds : new BasicDataset();
    }

    @Override
    protected boolean addRemoteImpl(String projectName, String remoteName, String remoteUrl,
                                     String ignitionUser) throws Exception {
        Path projectPath = getProjectFolderPath(projectName);
        GitManager.addRemote(projectPath, remoteName, remoteUrl);

        // Create empty credential record — FK refs are attached separately via setRemoteCredentialRef
        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        GitRemoteCredentialsRecord creds = new GitRemoteCredentialsRecord();
        creds.setProjectId(projectRecord.getId());
        creds.setIgnitionUser(ignitionUser);
        creds.setRemoteName(remoteName);
        creds.save();

        return true;
    }

    @Override
    protected boolean removeRemoteImpl(String projectName, String remoteName,
                                        String ignitionUser) throws Exception {
        Path projectPath = getProjectFolderPath(projectName);
        GitManager.removeRemote(projectPath, remoteName);

        // Delete credential record
        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        GitRemoteCredentialsRecord creds = GitRemoteCredentialsRecord.findByProjectUserRemote(
                projectRecord.getId(), ignitionUser, remoteName);
        if (creds != null) {
            creds.delete();
        }

        return true;
    }

    @Override
    protected boolean setRemoteUrlImpl(String projectName, String remoteName, String newUrl,
                                        String ignitionUser) throws Exception {
        Path projectPath = getProjectFolderPath(projectName);
        GitManager.setRemoteUrl(projectPath, remoteName, newUrl);

        // Ensure a credential record exists for this remote (FK refs are set via setRemoteCredentialRef)
        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        GitRemoteCredentialsRecord creds = GitRemoteCredentialsRecord.findByProjectUserRemote(
                projectRecord.getId(), ignitionUser, remoteName);
        if (creds == null) {
            creds = new GitRemoteCredentialsRecord();
            creds.setProjectId(projectRecord.getId());
            creds.setIgnitionUser(ignitionUser);
            creds.setRemoteName(remoteName);
            creds.save();
        }

        return true;
    }

    @Override
    protected List<String> getConflictingFilesImpl(String projectName) {
        return GitManager.getConflictingFiles(getProjectFolderPath(projectName));
    }

    @Override
    protected boolean resolveConflictImpl(String projectName, String filePath, String stage) {
        return GitManager.resolveConflict(getProjectFolderPath(projectName), filePath, stage);
    }

    @Override
    protected boolean abortMergeImpl(String projectName) {
        boolean result = GitManager.abortMerge(getProjectFolderPath(projectName));
        if (result) {
            GitProjectManager.importProject(projectName);
        }
        return result;
    }

    @Override
    protected boolean completeMergeCommitImpl(String projectName, String userName) throws Exception {
        boolean result = GitManager.completeMergeCommit(getProjectFolderPath(projectName), projectName, userName);
        if (result) {
            GitProjectManager.importProject(projectName);
        }
        return result;
    }

    @Override
    protected List<String> getConflictDiffImpl(String projectName, String filePath) {
        return GitManager.getConflictDiffContent(getProjectFolderPath(projectName), filePath);
    }

    @Override
    protected boolean fetchImpl(String projectName, String userName, String remoteName) throws Exception {
        if (!projectHasRemote(projectName)) {
            throw new RuntimeException("No remote repository configured. Add a remote before fetching.");
        }

        try (Git git = getGit(getProjectFolderPath(projectName))) {
            FetchCommand fetch = git.fetch();
            fetch.setRemote(remoteName);
            fetch.setUnshallow(true);
            setAuthentication(fetch, projectName, userName, remoteName);
            fetch.call();
            logger.info("Fetch from '" + remoteName + "' was successful.");
        } catch (GitAPIException e) {
            logger.error(e.toString(), e);
            throw new RuntimeException(e);
        }
        return true;
    }

    private void setupGitFromCurrentFolder(String projectName, String userName, Git git) throws Exception {
        try {
            git.add().addFilepattern(".").call();

            CommitCommand commit = git.commit().setMessage("Initial commit");
            setCommitAuthor(commit, projectName, userName);
            commit.call();

            PushCommand pushCommand = git.push();

            setAuthentication(pushCommand, projectName, userName, "origin");

            String branch = git.getRepository().getBranch();
            pushCommand.setRemote("origin").setRefSpecs(new RefSpec(branch)).call();
        } catch (GitAPIException e) {
            logger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    private void setupGitFromRemoteRepo(String projectName, String defaultBranch, Git git) throws Exception {
        try {
            CheckoutCommand checkout = git.checkout()
                    .setName(defaultBranch)
                    .setCreateBranch(true)
                    .setForced(true)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint("origin/" + defaultBranch);
            checkout.call();

            git.clean().setForce(true).call();
            git.reset().setMode(ResetCommand.ResetType.HARD).call();

            GitProjectManager.importProject(projectName);

            GitTagManager.importTagManager(projectName);

            GitThemeManager.importTheme(projectName);

            GitImageManager.importImages(projectName);
        } catch (GitAPIException e) {
            logger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    /**
     * Detect the default branch from ls-remote refs (lightweight, no data transfer needed).
     * Checks for HEAD symref target first, then falls back to common names.
     */
    private String detectDefaultBranchFromRefs(java.util.Collection<Ref> refs) {
        // Check for well-known default branch names among the heads
        java.util.Set<String> refNames = new java.util.HashSet<>();
        for (Ref ref : refs) {
            refNames.add(ref.getName());
        }
        for (String candidate : new String[]{"main", "master", "develop"}) {
            if (refNames.contains("refs/heads/" + candidate)) {
                return candidate;
            }
        }
        // Last resort: first branch ref found
        for (Ref ref : refs) {
            if (ref.getName().startsWith("refs/heads/")) {
                return ref.getName().substring("refs/heads/".length());
            }
        }
        return "main";
    }

    private Path getProjectFolderPath(String projectName) {
        Path dataDir = context.getSystemManager().getDataDir().toPath();
        return dataDir.resolve("projects").resolve(projectName);
    }

    // ── User-level SSH key management ─────────────────────────────────

    @Override
    protected boolean saveUserSshKeyImpl(String ignitionUser, String keyName, String sshKey) {
        try {
            // Update an existing key with the same name in place, otherwise create a new one.
            GitUserSshKeyRecord record = GitUserSshKeyRecord.findByUserAndKeyName(ignitionUser, keyName);
            if (record == null) {
                record = new GitUserSshKeyRecord();
                record.setIgnitionUser(ignitionUser);
                record.setKeyName(keyName);
            }
            record.setSSHKey(sshKey);
            record.save();
            return true;
        } catch (Exception e) {
            logger.error("Error saving user SSH key", e);
            return false;
        }
    }

    @Override
    protected boolean deleteUserSshKeyImpl(String ignitionUser, long keyId) {
        try {
            GitUserSshKeyRecord record = GitUserSshKeyRecord.findByIdAndUser(keyId, ignitionUser);
            if (record == null) return false;

            // Clear FK references in remote credential records pointing to this key
            List<GitRemoteCredentialsRecord> refs = GitRemoteCredentialsRecord.listBySshKeyId(keyId);
            for (GitRemoteCredentialsRecord ref : refs) {
                ref.setSshKeyId(0);
                ref.save();
            }

            record.delete();
            return true;
        } catch (Exception e) {
            logger.error("Error deleting user SSH key", e);
            return false;
        }
    }

    @Override
    protected Dataset listUserSshKeysImpl(String ignitionUser) {
        try {
            List<GitUserSshKeyRecord> keys = GitUserSshKeyRecord.listByUser(ignitionUser);
            DatasetBuilder builder = new DatasetBuilder();
            builder.colNames("id", "keyName");
            builder.colTypes(Long.class, String.class);
            for (GitUserSshKeyRecord key : keys) {
                builder.addRow(key.getId(), key.getKeyName());
            }
            return builder.build();
        } catch (Exception e) {
            logger.error("Error listing user SSH keys", e);
            return new BasicDataset();
        }
    }

    // ── User-level HTTPS credential management ────────────────────────

    @Override
    protected boolean saveUserHttpsCredentialImpl(String ignitionUser, String hostPattern,
                                                   String userName, String password) {
        try {
            // Check if a credential already exists for this user+host
            GitUserHttpsCredentialRecord existing =
                    GitUserHttpsCredentialRecord.findByUserAndHostPattern(ignitionUser, hostPattern);
            if (existing != null) {
                existing.setUserName(userName);
                if (password != null && !password.isEmpty()) {
                    existing.setPassword(password);
                }
                existing.save();
            } else {
                GitUserHttpsCredentialRecord record = new GitUserHttpsCredentialRecord();
                record.setIgnitionUser(ignitionUser);
                record.setHostPattern(hostPattern);
                record.setUserName(userName);
                record.setPassword(password);
                record.save();
            }
            return true;
        } catch (Exception e) {
            logger.error("Error saving user HTTPS credential", e);
            return false;
        }
    }

    @Override
    protected boolean deleteUserHttpsCredentialImpl(String ignitionUser, long credentialId) {
        try {
            GitUserHttpsCredentialRecord record =
                    GitUserHttpsCredentialRecord.findByIdAndUser(credentialId, ignitionUser);
            if (record == null) return false;

            // Clear FK references in remote credential records pointing to this credential
            List<GitRemoteCredentialsRecord> refs =
                    GitRemoteCredentialsRecord.listByHttpsCredentialId(credentialId);
            for (GitRemoteCredentialsRecord ref : refs) {
                ref.setHttpsCredentialId(0);
                ref.save();
            }

            record.delete();
            return true;
        } catch (Exception e) {
            logger.error("Error deleting user HTTPS credential", e);
            return false;
        }
    }

    @Override
    protected Dataset listUserHttpsCredentialsImpl(String ignitionUser) {
        try {
            List<GitUserHttpsCredentialRecord> creds =
                    GitUserHttpsCredentialRecord.listByUser(ignitionUser);
            DatasetBuilder builder = new DatasetBuilder();
            builder.colNames("id", "hostPattern", "userName");
            builder.colTypes(Long.class, String.class, String.class);
            for (GitUserHttpsCredentialRecord cred : creds) {
                builder.addRow(cred.getId(), cred.getHostPattern(), cred.getUserName());
            }
            return builder.build();
        } catch (Exception e) {
            logger.error("Error listing user HTTPS credentials", e);
            return new BasicDataset();
        }
    }

    // ── Remote credential association ─────────────────────────────────

    @Override
    protected boolean setRemoteCredentialRefImpl(String projectName, String remoteName,
                                                  String ignitionUser, long sshKeyId,
                                                  long httpsCredentialId) {
        try {
            GitRemoteCredentialsRecord creds = getRemoteCredentialsRecord(
                    projectName, ignitionUser, remoteName);
            if (creds == null) {
                // Create a minimal record to hold the FK reference
                GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
                creds = new GitRemoteCredentialsRecord();
                creds.setProjectId(projectRecord.getId());
                creds.setIgnitionUser(ignitionUser);
                creds.setRemoteName(remoteName);
            }
            creds.setSshKeyId(sshKeyId);
            creds.setHttpsCredentialId(httpsCredentialId);
            creds.save();
            return true;
        } catch (Exception e) {
            logger.error("Error setting remote credential reference", e);
            return false;
        }
    }

    @Override
    protected Dataset getRemoteCredentialRefImpl(String projectName, String remoteName,
                                                 String ignitionUser) {
        DatasetBuilder builder = new DatasetBuilder();
        builder.colNames(List.of("sshKeyId", "httpsCredentialId"));
        builder.colTypes(List.of(Long.class, Long.class));
        try {
            GitRemoteCredentialsRecord creds = getRemoteCredentialsRecord(
                    projectName, ignitionUser, remoteName);
            long sshKeyId = creds != null ? creds.getSshKeyId() : 0L;
            long httpsCredentialId = creds != null ? creds.getHttpsCredentialId() : 0L;
            builder.addRow(sshKeyId, httpsCredentialId);
        } catch (Exception e) {
            logger.error("Error getting remote credential reference", e);
            builder.addRow(0L, 0L);
        }
        Dataset ds = builder.build();
        return ds != null ? ds : new BasicDataset();
    }
}
