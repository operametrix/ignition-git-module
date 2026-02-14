package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public class ClientScriptModule extends AbstractScriptModule {

    private final GitScriptInterface rpc;

    public ClientScriptModule() {
        rpc = ModuleRPCFactory.create(
            "com.operametrix.ignition.git",
            GitScriptInterface.class
        );
    }

    @Override
    protected boolean pullImpl(String projectName, String userName, String remoteName, boolean importTags, boolean importTheme,
                               boolean importImages) throws Exception {
        return rpc.pull(projectName, userName, remoteName, importTags, importTheme, importImages);
    }

    @Override
    protected boolean pushImpl(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception {
        return rpc.push(projectName, userName, remoteName, pushAllBranches, pushTags, forcePush);
    }

    @Override
    protected boolean commitImpl(String projectName, String userName, List<String> changes, String message, boolean amend) {
        return rpc.commit(projectName, userName, changes, message, amend);
    }

    @Override
    protected Dataset getUncommitedChangesImpl(String projectName, String userName) {
        return rpc.getUncommitedChanges(projectName, userName);
    }

    @Override
    protected boolean isRegisteredUserImpl(String projectName, String userName){
        return rpc.isRegisteredUser(projectName, userName);
    }

    @Override
    protected boolean exportConfigImpl(String projectName) {
        return rpc.exportConfig(projectName);
    }

    @Override
    protected void setupLocalRepoImpl(String projectName, String userName) throws Exception {
        rpc.setupLocalRepo(projectName, userName);
    }

    @Override
    protected String getRepoURLImpl(String projectName) throws Exception {
        return rpc.getRepoURL(projectName);
    }

    @Override
    protected List<String> getLocalBranchesImpl(String projectName) throws Exception {
        return rpc.getLocalBranches(projectName);
    }

    @Override
    protected List<String> getRemoteBranchesImpl(String projectName) throws Exception {
        return rpc.getRemoteBranches(projectName);
    }

    @Override
    protected String getCurrentBranchImpl(String projectName) throws Exception {
        return rpc.getCurrentBranch(projectName);
    }

    @Override
    protected boolean createBranchImpl(String projectName, String branchName, String startPoint) throws Exception {
        return rpc.createBranch(projectName, branchName, startPoint);
    }

    @Override
    protected boolean checkoutBranchImpl(String projectName, String branchName) throws Exception {
        return rpc.checkoutBranch(projectName, branchName);
    }

    @Override
    protected boolean deleteBranchImpl(String projectName, String branchName) throws Exception {
        return rpc.deleteBranch(projectName, branchName);
    }

    @Override
    protected boolean isSSHAuthenticationImpl(String projectName) {
        return rpc.isSSHAuthentication(projectName);
    }

    @Override
    protected boolean saveUserCredentialsImpl(String projectName, String ignitionUser, String email,
                                              String gitUsername, String password, String sshKey) {
        return rpc.saveUserCredentials(projectName, ignitionUser, email, gitUsername, password, sshKey);
    }

    @Override
    protected String getUserEmailImpl(String projectName, String ignitionUser) {
        return rpc.getUserEmail(projectName, ignitionUser);
    }

    @Override
    protected String getUserGitUsernameImpl(String projectName, String ignitionUser) {
        return rpc.getUserGitUsername(projectName, ignitionUser);
    }

    @Override
    protected boolean isProjectRegisteredImpl(String projectName) {
        return rpc.isProjectRegistered(projectName);
    }

    @Override
    protected boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                             String email, String gitUsername, String password,
                                             String sshKey) throws Exception {
        return rpc.initializeProject(projectName, repoUri, ignitionUser, email, gitUsername, password, sshKey);
    }

    @Override
    protected List<String> getResourceDiffImpl(String projectName, String resourcePath) {
        return rpc.getResourceDiff(projectName, resourcePath);
    }

    @Override
    protected Dataset getCommitHistoryImpl(String projectName, int skip, int limit) {
        return rpc.getCommitHistory(projectName, skip, limit);
    }

    @Override
    protected List<String> getCommitFilesImpl(String projectName, String commitHash) {
        return rpc.getCommitFiles(projectName, commitHash);
    }

    @Override
    protected List<String> getCommitFileDiffImpl(String projectName, String commitHash, String filePath) {
        return rpc.getCommitFileDiff(projectName, commitHash, filePath);
    }

    @Override
    protected boolean discardChangesImpl(String projectName, List<String> paths) {
        return rpc.discardChanges(projectName, paths);
    }

    @Override
    protected boolean initializeLocalProjectImpl(String projectName, String ignitionUser,
                                                  String email) throws Exception {
        return rpc.initializeLocalProject(projectName, ignitionUser, email);
    }

    @Override
    protected boolean hasRemoteRepositoryImpl(String projectName) {
        return rpc.hasRemoteRepository(projectName);
    }

    @Override
    protected Dataset listRemotesImpl(String projectName) throws Exception {
        return rpc.listRemotes(projectName);
    }

    @Override
    protected boolean addRemoteImpl(String projectName, String remoteName, String remoteUrl,
                                     String ignitionUser, String gitUsername, String password,
                                     String sshKey) throws Exception {
        return rpc.addRemote(projectName, remoteName, remoteUrl, ignitionUser, gitUsername, password, sshKey);
    }

    @Override
    protected boolean removeRemoteImpl(String projectName, String remoteName,
                                        String ignitionUser) throws Exception {
        return rpc.removeRemote(projectName, remoteName, ignitionUser);
    }

    @Override
    protected boolean setRemoteUrlImpl(String projectName, String remoteName, String newUrl,
                                        String ignitionUser, String gitUsername, String password,
                                        String sshKey) throws Exception {
        return rpc.setRemoteUrl(projectName, remoteName, newUrl, ignitionUser, gitUsername, password, sshKey);
    }
}
