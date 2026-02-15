package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.script.hints.ScriptArg;
import com.inductiveautomation.ignition.common.script.hints.ScriptFunction;

import java.util.List;

public abstract class AbstractScriptModule implements GitScriptInterface {

    static {
        BundleUtil.get().addBundle(
            AbstractScriptModule.class.getSimpleName(),
            AbstractScriptModule.class.getClassLoader(),
            AbstractScriptModule.class.getName().replace('.', '/')
        );
    }
    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean pull(@ScriptArg("projectName") String projectName,
                        @ScriptArg("userName") String userName,
                        @ScriptArg("remoteName") String remoteName,
                        @ScriptArg("importTags") boolean importTags,
                        @ScriptArg("importTheme") boolean importTheme,
                        @ScriptArg("importImages") boolean importImages) throws Exception {
        return pullImpl(projectName, userName, remoteName, importTags, importTheme, importImages);
    }


    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean push(@ScriptArg("projectName") String projectName,
                        @ScriptArg("userName") String userName,
                        @ScriptArg("remoteName") String remoteName,
                        @ScriptArg("pushAllBranches") boolean pushAllBranches,
                        @ScriptArg("pushTags") boolean pushTags,
                        @ScriptArg("forcePush") boolean forcePush) throws Exception {
        return pushImpl(projectName, userName, remoteName, pushAllBranches, pushTags, forcePush);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean commit(@ScriptArg("projectName") String projectName,
                          @ScriptArg("userName") String userName,
                          @ScriptArg("changes") List<String> changes,
                          @ScriptArg("message") String message,
                          @ScriptArg("amend") boolean amend) {
        return commitImpl(projectName, userName, changes, message, amend);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public Dataset getUncommitedChanges(@ScriptArg("projectName") String projectName,
                                        @ScriptArg("userName") String userName) {
        return getUncommitedChangesImpl(projectName, userName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean isRegisteredUser(@ScriptArg("projectName") String projectName,
                                    @ScriptArg("userName") String userName) {
        return isRegisteredUserImpl(projectName, userName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean exportConfig(@ScriptArg("projectName") String projectName) {
        return exportConfigImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public void setupLocalRepo(@ScriptArg("projectName") String projectName,
                               @ScriptArg("userName") String userName) throws Exception {
        setupLocalRepoImpl(projectName, userName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public String getRepoURL(@ScriptArg("projectName") String projectName) throws Exception {
        return getRepoURLImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public List<String> getLocalBranches(@ScriptArg("projectName") String projectName) throws Exception {
        return getLocalBranchesImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public List<String> getRemoteBranches(@ScriptArg("projectName") String projectName) throws Exception {
        return getRemoteBranchesImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public String getCurrentBranch(@ScriptArg("projectName") String projectName) throws Exception {
        return getCurrentBranchImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean createBranch(@ScriptArg("projectName") String projectName,
                                @ScriptArg("branchName") String branchName,
                                @ScriptArg("startPoint") String startPoint) throws Exception {
        return createBranchImpl(projectName, branchName, startPoint);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean checkoutBranch(@ScriptArg("projectName") String projectName,
                                  @ScriptArg("branchName") String branchName) throws Exception {
        return checkoutBranchImpl(projectName, branchName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean deleteBranch(@ScriptArg("projectName") String projectName,
                                @ScriptArg("branchName") String branchName) throws Exception {
        return deleteBranchImpl(projectName, branchName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean isSSHAuthentication(@ScriptArg("projectName") String projectName) {
        return isSSHAuthenticationImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean saveUserCredentials(@ScriptArg("projectName") String projectName,
                                       @ScriptArg("ignitionUser") String ignitionUser,
                                       @ScriptArg("email") String email,
                                       @ScriptArg("gitUsername") String gitUsername,
                                       @ScriptArg("password") String password,
                                       @ScriptArg("sshKey") String sshKey) {
        return saveUserCredentialsImpl(projectName, ignitionUser, email, gitUsername, password, sshKey);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public String getUserEmail(@ScriptArg("projectName") String projectName,
                               @ScriptArg("ignitionUser") String ignitionUser) {
        return getUserEmailImpl(projectName, ignitionUser);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public String getUserGitUsername(@ScriptArg("projectName") String projectName,
                                     @ScriptArg("ignitionUser") String ignitionUser) {
        return getUserGitUsernameImpl(projectName, ignitionUser);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean isProjectRegistered(@ScriptArg("projectName") String projectName) {
        return isProjectRegisteredImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean initializeProject(@ScriptArg("projectName") String projectName,
                                     @ScriptArg("repoUri") String repoUri,
                                     @ScriptArg("ignitionUser") String ignitionUser,
                                     @ScriptArg("email") String email,
                                     @ScriptArg("gitUsername") String gitUsername,
                                     @ScriptArg("password") String password,
                                     @ScriptArg("sshKey") String sshKey) throws Exception {
        return initializeProjectImpl(projectName, repoUri, ignitionUser, email, gitUsername, password, sshKey);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public List<String> getResourceDiff(@ScriptArg("projectName") String projectName,
                                        @ScriptArg("resourcePath") String resourcePath) {
        return getResourceDiffImpl(projectName, resourcePath);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public Dataset getCommitHistory(@ScriptArg("projectName") String projectName,
                                    @ScriptArg("skip") int skip,
                                    @ScriptArg("limit") int limit) {
        return getCommitHistoryImpl(projectName, skip, limit);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public List<String> getCommitFiles(@ScriptArg("projectName") String projectName,
                                       @ScriptArg("commitHash") String commitHash) {
        return getCommitFilesImpl(projectName, commitHash);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public List<String> getCommitFileDiff(@ScriptArg("projectName") String projectName,
                                          @ScriptArg("commitHash") String commitHash,
                                          @ScriptArg("filePath") String filePath) {
        return getCommitFileDiffImpl(projectName, commitHash, filePath);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean discardChanges(@ScriptArg("projectName") String projectName,
                                  @ScriptArg("paths") List<String> paths) {
        return discardChangesImpl(projectName, paths);
    }

    protected abstract boolean pullImpl(String projectName, String userName, String remoteName, boolean importTags, boolean importTheme,
                                        boolean importImages) throws Exception;
    protected abstract boolean pushImpl(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception;
    protected abstract boolean commitImpl(String projectName, String userName, List<String> changes, String message, boolean amend);
    protected abstract Dataset getUncommitedChangesImpl(String projectName, String userName);
    protected abstract boolean isRegisteredUserImpl(String projectName, String userName);
    protected abstract boolean exportConfigImpl(String projectName);
    protected abstract void setupLocalRepoImpl(String projectName, String userName) throws Exception;
    protected abstract String getRepoURLImpl(String projectName) throws Exception;
    protected abstract List<String> getLocalBranchesImpl(String projectName) throws Exception;
    protected abstract List<String> getRemoteBranchesImpl(String projectName) throws Exception;
    protected abstract String getCurrentBranchImpl(String projectName) throws Exception;
    protected abstract boolean createBranchImpl(String projectName, String branchName, String startPoint) throws Exception;
    protected abstract boolean checkoutBranchImpl(String projectName, String branchName) throws Exception;
    protected abstract boolean deleteBranchImpl(String projectName, String branchName) throws Exception;
    protected abstract boolean isSSHAuthenticationImpl(String projectName);
    protected abstract boolean saveUserCredentialsImpl(String projectName, String ignitionUser, String email,
                                                       String gitUsername, String password, String sshKey);
    protected abstract String getUserEmailImpl(String projectName, String ignitionUser);
    protected abstract String getUserGitUsernameImpl(String projectName, String ignitionUser);
    protected abstract boolean isProjectRegisteredImpl(String projectName);
    protected abstract boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                                      String email, String gitUsername, String password,
                                                      String sshKey) throws Exception;
    protected abstract List<String> getResourceDiffImpl(String projectName, String resourcePath);
    protected abstract Dataset getCommitHistoryImpl(String projectName, int skip, int limit);
    protected abstract List<String> getCommitFilesImpl(String projectName, String commitHash);
    protected abstract List<String> getCommitFileDiffImpl(String projectName, String commitHash, String filePath);
    protected abstract boolean discardChangesImpl(String projectName, List<String> paths);

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean checkoutCommit(@ScriptArg("projectName") String projectName,
                                  @ScriptArg("commitHash") String commitHash) throws Exception {
        return checkoutCommitImpl(projectName, commitHash);
    }

    protected abstract boolean checkoutCommitImpl(String projectName, String commitHash) throws Exception;

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean revertCommit(@ScriptArg("projectName") String projectName,
                                @ScriptArg("commitHash") String commitHash) throws Exception {
        return revertCommitImpl(projectName, commitHash);
    }

    protected abstract boolean revertCommitImpl(String projectName, String commitHash) throws Exception;

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean initializeLocalProject(@ScriptArg("projectName") String projectName,
                                           @ScriptArg("ignitionUser") String ignitionUser,
                                           @ScriptArg("email") String email) throws Exception {
        return initializeLocalProjectImpl(projectName, ignitionUser, email);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean hasRemoteRepository(@ScriptArg("projectName") String projectName) {
        return hasRemoteRepositoryImpl(projectName);
    }

    protected abstract boolean initializeLocalProjectImpl(String projectName, String ignitionUser,
                                                           String email) throws Exception;
    protected abstract boolean hasRemoteRepositoryImpl(String projectName);

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public Dataset listRemotes(@ScriptArg("projectName") String projectName) throws Exception {
        return listRemotesImpl(projectName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean addRemote(@ScriptArg("projectName") String projectName,
                             @ScriptArg("remoteName") String remoteName,
                             @ScriptArg("remoteUrl") String remoteUrl,
                             @ScriptArg("ignitionUser") String ignitionUser,
                             @ScriptArg("gitUsername") String gitUsername,
                             @ScriptArg("password") String password,
                             @ScriptArg("sshKey") String sshKey) throws Exception {
        return addRemoteImpl(projectName, remoteName, remoteUrl, ignitionUser, gitUsername, password, sshKey);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean removeRemote(@ScriptArg("projectName") String projectName,
                                @ScriptArg("remoteName") String remoteName,
                                @ScriptArg("ignitionUser") String ignitionUser) throws Exception {
        return removeRemoteImpl(projectName, remoteName, ignitionUser);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean setRemoteUrl(@ScriptArg("projectName") String projectName,
                                @ScriptArg("remoteName") String remoteName,
                                @ScriptArg("newUrl") String newUrl,
                                @ScriptArg("ignitionUser") String ignitionUser,
                                @ScriptArg("gitUsername") String gitUsername,
                                @ScriptArg("password") String password,
                                @ScriptArg("sshKey") String sshKey) throws Exception {
        return setRemoteUrlImpl(projectName, remoteName, newUrl, ignitionUser, gitUsername, password, sshKey);
    }

    protected abstract Dataset listRemotesImpl(String projectName) throws Exception;
    protected abstract boolean addRemoteImpl(String projectName, String remoteName, String remoteUrl,
                                              String ignitionUser, String gitUsername, String password,
                                              String sshKey) throws Exception;
    protected abstract boolean removeRemoteImpl(String projectName, String remoteName,
                                                 String ignitionUser) throws Exception;
    protected abstract boolean setRemoteUrlImpl(String projectName, String remoteName, String newUrl,
                                                 String ignitionUser, String gitUsername, String password,
                                                 String sshKey) throws Exception;

}
