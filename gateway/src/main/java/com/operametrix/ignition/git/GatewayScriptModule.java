package com.operametrix.ignition.git;

import com.operametrix.ignition.git.managers.*;
import com.operametrix.ignition.git.records.GitProjectsConfigRecord;
import com.operametrix.ignition.git.records.GitReposUsersRecord;
import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.util.DatasetBuilder;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.operametrix.ignition.git.managers.GitImageManager.exportImages;
import static com.operametrix.ignition.git.managers.GitManager.*;
import static com.operametrix.ignition.git.managers.GitTagManager.exportTag;
import static com.operametrix.ignition.git.managers.GitThemeManager.exportTheme;

public class GatewayScriptModule extends AbstractScriptModule {
    private final LoggerEx logger = LoggerEx.newBuilder().build(getClass());
    private final GatewayContext context;

    GatewayScriptModule(GatewayContext context) {
        this.context = context;
    }

    @Override
    public boolean pullImpl(String projectName,
                            String userName,
                            boolean importTags,
                            boolean importTheme,
                            boolean importImages) throws Exception {

        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        if (!projectRecord.hasRemote()) {
            throw new RuntimeException("No remote repository configured. Add a remote before pulling.");
        }

        try (Git git = getGit(getProjectFolderPath(projectName))) {
            PullCommand pull = git.pull();
            setAuthentication(pull, projectName, userName);

            PullResult result = pull.call();
            if (!result.isSuccessful()) {
                logger.warn("Cannot pull from git");
            } else {
                logger.info("Pull was successful.");
            }

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
    public boolean pushImpl(String projectName, String userName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception {
        GitProjectsConfigRecord projectRecord = getGitProjectConfigRecord(projectName);
        if (!projectRecord.hasRemote()) {
            throw new RuntimeException("No remote repository configured. Add a remote before pushing.");
        }

        try (Git git = getGit(getProjectFolderPath(projectName))) {
            PushCommand push = git.push();

            setAuthentication(push, projectName, userName);

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
            logger.debug("Missing files: {}" + missing);
            uncommittedChangesBuilder(projectName, missing, "Deleted", changes, builder);

            Set<String> uncommittedChanges = status.getUncommittedChanges();
            logger.debug("Uncommitted changes: {}" + uncommittedChanges);
            uncommittedChangesBuilder(projectName, uncommittedChanges, "Uncommitted", changes, builder);

            Set<String> untracked = status.getUntracked();
            logger.debug("Untracked files: {}" + untracked);
            uncommittedChangesBuilder(projectName, untracked, "Created", changes, builder);

            Set<String> modified = status.getChanged();
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
    protected boolean exportConfigImpl(String projectName) {
        Path projectFolderPath = getProjectFolderPath(projectName);
        exportImages(projectFolderPath);
        exportTheme(projectFolderPath);
        exportTag(projectFolderPath);
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

                FetchCommand fetch = git.fetch().setRemote("origin");

                setAuthentication(fetch, projectName, userName);
                fetch.call();

                ListBranchCommand listBranches = git.branchList();
                listBranches.setListMode(ListBranchCommand.ListMode.REMOTE);
                List<Ref> branches = listBranches.call();

                if (branches.isEmpty()) {
                    setupGitFromCurrentFolder(projectName, userName, git);
                } else {
                    setupGitFromRemoteRepo(projectName, git);
                }
            } catch (Exception e) {
                logger.warn("An error occurred while setting up local repo for '" + projectName + "' project.", e);
            }
        }
    }

    @Override
    protected String getRepoURLImpl(String projectName) throws Exception {
        GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
        if (!gitProjectsConfigRecord.hasRemote()) {
            return "";
        }

        return GitManager.repoUriToUrl(gitProjectsConfigRecord.getURI());
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
    protected boolean createBranchImpl(String projectName, String branchName, String startPoint) throws Exception {
        return GitManager.createBranch(getProjectFolderPath(projectName), branchName, startPoint);
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
    protected boolean isSSHAuthenticationImpl(String projectName) {
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            return gitProjectsConfigRecord.isSSHAuthentication();
        } catch (Exception e) {
            logger.error("Error checking SSH authentication", e);
            return false;
        }
    }

    @Override
    protected boolean saveUserCredentialsImpl(String projectName, String ignitionUser, String email,
                                              String gitUsername, String password, String sshKey) {
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            GitReposUsersRecord user;
            try {
                user = getGitReposUserRecord(gitProjectsConfigRecord, ignitionUser);
            } catch (Exception e) {
                user = context.getPersistenceInterface().createNew(GitReposUsersRecord.META);
                user.setProjectId(gitProjectsConfigRecord.getId());
                user.setIgnitionUser(ignitionUser);
            }
            user.setEmail(email);
            user.setUserName(gitUsername);
            if (password != null && !password.isEmpty()) {
                user.setPassword(password);
            }
            if (sshKey != null && !sshKey.isEmpty()) {
                user.setSSHKey(sshKey);
            }
            context.getPersistenceInterface().save(user);
            return true;
        } catch (Exception e) {
            logger.error("Error saving user credentials", e);
            return false;
        }
    }

    @Override
    protected String getUserEmailImpl(String projectName, String ignitionUser) {
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            GitReposUsersRecord user = getGitReposUserRecord(gitProjectsConfigRecord, ignitionUser);
            return user.getEmail();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected String getUserGitUsernameImpl(String projectName, String ignitionUser) {
        try {
            GitProjectsConfigRecord gitProjectsConfigRecord = getGitProjectConfigRecord(projectName);
            GitReposUsersRecord user = getGitReposUserRecord(gitProjectsConfigRecord, ignitionUser);
            return user.getUserName();
        } catch (Exception e) {
            return "";
        }
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
        return GitManager.getCommitFileList(getProjectFolderPath(projectName), commitHash);
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
    protected boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                             String email, String gitUsername, String password,
                                             String sshKey) throws Exception {
        // Check project not already registered
        if (isProjectRegisteredImpl(projectName)) {
            throw new Exception("Project '" + projectName + "' is already registered.");
        }

        // Create project config record
        GitProjectsConfigRecord projectRecord = context.getPersistenceInterface().createNew(GitProjectsConfigRecord.META);
        projectRecord.setProjectName(projectName);
        projectRecord.setURI(repoUri);
        context.getPersistenceInterface().save(projectRecord);

        // Re-query to get the generated ID
        projectRecord = getGitProjectConfigRecord(projectName);

        // Create user credentials record
        GitReposUsersRecord userRecord = context.getPersistenceInterface().createNew(GitReposUsersRecord.META);
        userRecord.setProjectId(projectRecord.getId());
        userRecord.setIgnitionUser(ignitionUser);
        userRecord.setEmail(email);
        userRecord.setUserName(gitUsername);
        if (!repoUri.toLowerCase().startsWith("http")) {
            // SSH authentication
            userRecord.setSSHKey(sshKey);
        } else {
            // HTTPS authentication
            userRecord.setPassword(password);
        }
        context.getPersistenceInterface().save(userRecord);

        // Attempt to initialize the local repo
        try {
            setupLocalRepoImpl(projectName, ignitionUser);
        } catch (Exception e) {
            // Rollback: delete both records on failure
            try {
                userRecord.deleteRecord();
                context.getPersistenceInterface().save(userRecord);
            } catch (Exception ignored) {
            }
            try {
                projectRecord.deleteRecord();
                context.getPersistenceInterface().save(projectRecord);
            } catch (Exception ignored) {
            }
            throw e;
        }

        return true;
    }

    @Override
    protected boolean initializeLocalProjectImpl(String projectName, String ignitionUser,
                                                  String email) throws Exception {
        if (isProjectRegisteredImpl(projectName)) {
            throw new Exception("Project '" + projectName + "' is already registered.");
        }

        // Create project config record with empty URI (no remote)
        GitProjectsConfigRecord projectRecord = context.getPersistenceInterface().createNew(GitProjectsConfigRecord.META);
        projectRecord.setProjectName(projectName);
        projectRecord.setURI("");
        context.getPersistenceInterface().save(projectRecord);

        // Re-query to get the generated ID
        projectRecord = getGitProjectConfigRecord(projectName);

        // Create user credentials record with email only; git username defaults to Ignition username
        GitReposUsersRecord userRecord = context.getPersistenceInterface().createNew(GitReposUsersRecord.META);
        userRecord.setProjectId(projectRecord.getId());
        userRecord.setIgnitionUser(ignitionUser);
        userRecord.setEmail(email);
        userRecord.setUserName(ignitionUser);
        context.getPersistenceInterface().save(userRecord);

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
                userRecord.deleteRecord();
                context.getPersistenceInterface().save(userRecord);
            } catch (Exception ignored) {
            }
            try {
                projectRecord.deleteRecord();
                context.getPersistenceInterface().save(projectRecord);
            } catch (Exception ignored) {
            }
            throw e;
        }

        return true;
    }

    @Override
    protected boolean hasRemoteRepositoryImpl(String projectName) {
        try {
            GitProjectsConfigRecord record = getGitProjectConfigRecord(projectName);
            return record.hasRemote();
        } catch (Exception e) {
            return false;
        }
    }

    private void setupGitFromCurrentFolder(String projectName, String userName, Git git) throws Exception {
        try {
            git.add().addFilepattern(".").call();

            CommitCommand commit = git.commit().setMessage("Initial commit");
            setCommitAuthor(commit, projectName, userName);
            commit.call();

            PushCommand pushCommand = git.push();

            setAuthentication(pushCommand, projectName, userName);

            String branch = git.getRepository().getBranch();
            pushCommand.setRemote("origin").setRefSpecs(new RefSpec(branch)).call();
        } catch (GitAPIException e) {
            logger.error(e.toString());
            throw new RuntimeException(e);
        }
    }

    private void setupGitFromRemoteRepo(String projectName, Git git) throws Exception {
        try {
            CheckoutCommand checkout = git.checkout()
                    .setName("master")
                    .setCreateBranch(true)
                    .setForced(true)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint("origin/master");
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

    private Path getProjectFolderPath(String projectName) {
        Path dataDir = context.getSystemManager().getDataDir().toPath();
        return dataDir.resolve("projects").resolve(projectName);
    }
}
