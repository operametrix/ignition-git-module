package com.axone_io.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public interface GitScriptInterface {

    boolean pull(String projectName, String userName, boolean importTags, boolean importTheme,
                 boolean importImages) throws Exception;
    boolean push(String projectName, String userName) throws Exception;
    boolean commit(String projectName, String userName, List<String> changes, String message);
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

}
