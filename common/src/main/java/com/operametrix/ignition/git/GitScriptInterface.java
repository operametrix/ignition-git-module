package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public interface GitScriptInterface {

    boolean pull(String projectName, String userName, String remoteName, boolean importTags, boolean importTheme,
                 boolean importImages) throws Exception;
    boolean push(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception;
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
     * Ensure a user registration record exists for the given project.
     * Creates a new record if none exists.
     */
    boolean saveUserCredentials(String projectName, String ignitionUser);

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
                              String gitUsername, String password, String sshKey) throws Exception;

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
    boolean initializeLocalProject(String projectName, String ignitionUser) throws Exception;

    /** Check out a specific commit by hash, entering detached HEAD state. */
    boolean checkoutCommit(String projectName, String commitHash) throws Exception;

    /** Create a new commit that reverses the changes of the specified commit (git revert). */
    boolean revertCommit(String projectName, String commitHash) throws Exception;

    /** Check whether the given project has a remote repository configured. */
    boolean hasRemoteRepository(String projectName);

    /** Fetch from a remote without merging. Updates remote-tracking branches only. */
    boolean fetch(String projectName, String userName, String remoteName) throws Exception;

    /** List all remotes configured in the project's git repository. Returns Dataset with [name, url]. */
    Dataset listRemotes(String projectName) throws Exception;

    /** Add a named remote with URL and credentials. */
    boolean addRemote(String projectName, String remoteName, String remoteUrl,
                      String ignitionUser, String gitUsername, String password, String sshKey) throws Exception;

    /** Remove a named remote and its credentials. */
    boolean removeRemote(String projectName, String remoteName, String ignitionUser) throws Exception;

    /** Update a remote's URL and credentials. */
    boolean setRemoteUrl(String projectName, String remoteName, String newUrl,
                         String ignitionUser, String gitUsername, String password, String sshKey) throws Exception;

    /** Get list of files currently in merge conflict. */
    List<String> getConflictingFiles(String projectName);

    /** Resolve a single conflicting file. stage is "OURS" or "THEIRS". */
    boolean resolveConflict(String projectName, String filePath, String stage);

    /** Abort the current merge (hard reset to HEAD). */
    boolean abortMerge(String projectName);

    /** Complete the merge commit after all conflicts are resolved. */
    boolean completeMergeCommit(String projectName, String userName) throws Exception;

    /** Get ours (HEAD) and theirs (MERGE_HEAD) content for a conflicting file.
     *  Returns a 2-element list: [oursContent, theirsContent]. */
    List<String> getConflictDiff(String projectName, String filePath);

    // ── User-level credential management ──────────────────────────────

    /** Save or update a user-level SSH key. If isDefault, clears default on all other keys for this user. */
    boolean saveUserSshKey(String ignitionUser, String keyName, String sshKey, boolean isDefault);

    /** Delete a user-level SSH key by ID. Clears any FK references in remote credential records. */
    boolean deleteUserSshKey(String ignitionUser, long keyId);

    /** Set a specific SSH key as the user's default, clearing default on all others. */
    boolean setDefaultSshKey(String ignitionUser, long keyId);

    /** List all SSH keys for a user. Returns Dataset with columns: id, keyName, isDefault. */
    Dataset listUserSshKeys(String ignitionUser);

    /** Save or update a user-level HTTPS credential for a specific host. */
    boolean saveUserHttpsCredential(String ignitionUser, String hostPattern,
                                     String userName, String password);

    /** Delete a user-level HTTPS credential by ID. Clears any FK references in remote credential records. */
    boolean deleteUserHttpsCredential(String ignitionUser, long credentialId);

    /** List all HTTPS credentials for a user. Returns Dataset with columns: id, hostPattern, userName. */
    Dataset listUserHttpsCredentials(String ignitionUser);

    /** Associate a remote with a user-level credential (SSH key or HTTPS). Pass 0 for unused ID. */
    boolean setRemoteCredentialRef(String projectName, String remoteName, String ignitionUser,
                                    long sshKeyId, long httpsCredentialId);

}
