package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public interface GitScriptInterface {

    boolean pull(String projectName, String userName, boolean importTags, boolean importTheme,
                 boolean importImages) throws Exception;
    boolean push(String projectName, String userName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception;
    boolean commit(String projectName, String userName, List<String> changes, String message, boolean amend);
    Dataset getUncommitedChanges(String projectName, String userName);
    boolean isRegisteredUser(String projectName, String userName);
    boolean exportConfig(String projectName);
    void setupLocalRepo(String projectName, String userName) throws Exception;
    String getRepoURL(String projectName) throws Exception;

    List<String> getLocalBranches(String projectName) throws Exception;
    List<String> getRemoteBranches(String projectName) throws Exception;
    String getCurrentBranch(String projectName) throws Exception;
    boolean createBranch(String projectName, String branchName, String startPoint) throws Exception;
    boolean checkoutBranch(String projectName, String branchName) throws Exception;
    boolean deleteBranch(String projectName, String branchName) throws Exception;

    /**
     * Check whether the given project's repository uses SSH authentication (as opposed to HTTPS).
     * This is determined from the raw URI configured in the gateway, not the browsable URL.
     */
    boolean isSSHAuthentication(String projectName);

    /**
     * Save git credentials for a user. Creates a new record if none exists, or updates the existing one.
     * Empty password/sshKey values are ignored to avoid overwriting existing secrets when only
     * updating email or username.
     */
    boolean saveUserCredentials(String projectName, String ignitionUser, String email,
                                String gitUsername, String password, String sshKey);

    /** Get the configured email address for a git user, or empty string if not found. */
    String getUserEmail(String projectName, String ignitionUser);

    /** Get the configured git username for a user, or empty string if not found. */
    String getUserGitUsername(String projectName, String ignitionUser);

    /** Check whether the given project is registered in the gateway's git configuration. */
    boolean isProjectRegistered(String projectName);

    /**
     * Register a project with git and initialize the local repository in one atomic operation.
     * Creates the project config record, user credentials record, and calls setupLocalRepo.
     * On failure, rolls back any created records.
     */
    boolean initializeProject(String projectName, String repoUri, String ignitionUser,
                              String email, String gitUsername, String password, String sshKey) throws Exception;

    /** Get old (HEAD) and new (working tree) content for a resource, for diff viewing.
     *  Returns a 2-element list: [oldContent, newContent]. */
    List<String> getResourceDiff(String projectName, String resourcePath);

    /** Get paginated commit history for the project repository.
     *  Returns a Dataset with columns: hash, shortHash, author, date, message. */
    Dataset getCommitHistory(String projectName, int skip, int limit);

    /** Get the list of files changed in a specific commit.
     *  Returns a list of strings in format "CHANGE_TYPE:path". */
    List<String> getCommitFiles(String projectName, String commitHash);

    /** Get the old and new content for a file at a specific commit.
     *  Returns a 2-element list: [oldContent, newContent]. */
    List<String> getCommitFileDiff(String projectName, String commitHash, String filePath);

    /** Discard uncommitted changes for the given resource paths, reverting them to HEAD state.
     *  Tracked files are checked out from HEAD; untracked files are deleted. */
    boolean discardChanges(String projectName, List<String> paths);

    /**
     * Initialize a local-only git repository (no remote) for the given project.
     * Creates DB records with an empty URI, does git init + initial commit.
     */
    boolean initializeLocalProject(String projectName, String ignitionUser, String email) throws Exception;

    /** Check whether the given project has a remote repository configured. */
    boolean hasRemoteRepository(String projectName);

}
