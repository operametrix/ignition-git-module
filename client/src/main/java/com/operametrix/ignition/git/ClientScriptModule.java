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
    protected boolean createBranchImpl(String projectName, String branchName) throws Exception {
        return rpc.createBranch(projectName, branchName);
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
    protected boolean isProjectRegisteredImpl(String projectName) {
        return rpc.isProjectRegistered(projectName);
    }

    @Override
    protected boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                             long sshKeyId, long httpsCredentialId) throws Exception {
        return rpc.initializeProject(projectName, repoUri, ignitionUser, sshKeyId, httpsCredentialId);
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
    protected boolean checkoutCommitImpl(String projectName, String commitHash) throws Exception {
        return rpc.checkoutCommit(projectName, commitHash);
    }

    @Override
    protected boolean revertCommitImpl(String projectName, String commitHash) throws Exception {
        return rpc.revertCommit(projectName, commitHash);
    }

    @Override
    protected boolean initializeLocalProjectImpl(String projectName, String ignitionUser) throws Exception {
        return rpc.initializeLocalProject(projectName, ignitionUser);
    }

    @Override
    protected boolean hasRemoteRepositoryImpl(String projectName) {
        return rpc.hasRemoteRepository(projectName);
    }

    @Override
    protected boolean fetchImpl(String projectName, String userName, String remoteName) throws Exception {
        return rpc.fetch(projectName, userName, remoteName);
    }

    @Override
    protected Dataset listRemotesImpl(String projectName) throws Exception {
        return rpc.listRemotes(projectName);
    }

    @Override
    protected boolean addRemoteImpl(String projectName, String remoteName, String remoteUrl,
                                     String ignitionUser) throws Exception {
        return rpc.addRemote(projectName, remoteName, remoteUrl, ignitionUser);
    }

    @Override
    protected boolean removeRemoteImpl(String projectName, String remoteName,
                                        String ignitionUser) throws Exception {
        return rpc.removeRemote(projectName, remoteName, ignitionUser);
    }

    @Override
    protected boolean setRemoteUrlImpl(String projectName, String remoteName, String newUrl,
                                        String ignitionUser) throws Exception {
        return rpc.setRemoteUrl(projectName, remoteName, newUrl, ignitionUser);
    }

    @Override
    protected List<String> getConflictingFilesImpl(String projectName) {
        return rpc.getConflictingFiles(projectName);
    }

    @Override
    protected boolean resolveConflictImpl(String projectName, String filePath, String stage) {
        return rpc.resolveConflict(projectName, filePath, stage);
    }

    @Override
    protected boolean abortMergeImpl(String projectName) {
        return rpc.abortMerge(projectName);
    }

    @Override
    protected boolean completeMergeCommitImpl(String projectName, String userName) throws Exception {
        return rpc.completeMergeCommit(projectName, userName);
    }

    @Override
    protected List<String> getConflictDiffImpl(String projectName, String filePath) {
        return rpc.getConflictDiff(projectName, filePath);
    }

    // ── User-level credential management ──────────────────────────────

    @Override
    protected boolean saveUserSshKeyImpl(String ignitionUser, String keyName, String sshKey) {
        return rpc.saveUserSshKey(ignitionUser, keyName, sshKey);
    }

    @Override
    protected boolean deleteUserSshKeyImpl(String ignitionUser, long keyId) {
        return rpc.deleteUserSshKey(ignitionUser, keyId);
    }

    @Override
    protected Dataset listUserSshKeysImpl(String ignitionUser) {
        return rpc.listUserSshKeys(ignitionUser);
    }

    @Override
    protected boolean saveUserHttpsCredentialImpl(String ignitionUser, String hostPattern,
                                                   String userName, String password) {
        return rpc.saveUserHttpsCredential(ignitionUser, hostPattern, userName, password);
    }

    @Override
    protected boolean deleteUserHttpsCredentialImpl(String ignitionUser, long credentialId) {
        return rpc.deleteUserHttpsCredential(ignitionUser, credentialId);
    }

    @Override
    protected Dataset listUserHttpsCredentialsImpl(String ignitionUser) {
        return rpc.listUserHttpsCredentials(ignitionUser);
    }

    @Override
    protected boolean setRemoteCredentialRefImpl(String projectName, String remoteName,
                                                  String ignitionUser, long sshKeyId,
                                                  long httpsCredentialId) {
        return rpc.setRemoteCredentialRef(projectName, remoteName, ignitionUser, sshKeyId, httpsCredentialId);
    }
}
