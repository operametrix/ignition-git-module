package com.axone_io.ignition.git;

import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public class ClientScriptModule extends AbstractScriptModule {

    private final GitScriptInterface rpc;

    public ClientScriptModule() {
        rpc = ModuleRPCFactory.create(
            "com.axone_io.ignition.git",
            GitScriptInterface.class
        );
    }

    @Override
    protected boolean pullImpl(String projectName, String userName, boolean importTags, boolean importTheme,
                               boolean importImages) throws Exception {
        return rpc.pull(projectName, userName, importTags, importTheme, importImages);
    }

    @Override
    protected boolean pushImpl(String projectName, String userName) throws Exception {
        return rpc.push(projectName, userName);
    }

    @Override
    protected boolean commitImpl(String projectName, String userName, List<String> changes, String message) {
        return rpc.commit(projectName, userName, changes, message);
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
}
