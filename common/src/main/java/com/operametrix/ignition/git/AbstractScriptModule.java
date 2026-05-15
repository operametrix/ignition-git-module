package com.operametrix.ignition.git;

import com.inductiveautomation.ignition.common.Dataset;

import java.util.List;

public abstract class AbstractScriptModule implements GitScriptInterface {

    @Override
    public boolean pull(String projectName, String userName, String remoteName, boolean importTags, boolean importTheme,
                        boolean importImages) throws Exception {
        return pullImpl(projectName, userName, remoteName, importTags, importTheme, importImages);
    }

    @Override
    public boolean push(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception {
        return pushImpl(projectName, userName, remoteName, pushAllBranches, pushTags, forcePush);
    }

    @Override
    public boolean commit(String projectName, String userName, List<String> changes, String message, boolean amend) {
        return commitImpl(projectName, userName, changes, message, amend);
    }

    @Override
    public Dataset getUncommitedChanges(String projectName, String userName) {
        return getUncommitedChangesImpl(projectName, userName);
    }

    @Override
    public boolean isRegisteredUser(String projectName, String userName) {
        return isRegisteredUserImpl(projectName, userName);
    }

    @Override
    public boolean snapshotTags(String projectName) {
        return snapshotTagsImpl(projectName);
    }

    @Override
    public boolean snapshotThemes(String projectName) {
        return snapshotThemesImpl(projectName);
    }

    @Override
    public boolean snapshotImages(String projectName) {
        return snapshotImagesImpl(projectName);
    }

    @Override
    public void setupLocalRepo(String projectName, String userName) throws Exception {
        setupLocalRepoImpl(projectName, userName);
    }


    @Override
    public List<String> getLocalBranches(String projectName) throws Exception {
        return getLocalBranchesImpl(projectName);
    }

    @Override
    public List<String> getRemoteBranches(String projectName) throws Exception {
        return getRemoteBranchesImpl(projectName);
    }

    @Override
    public String getCurrentBranch(String projectName) throws Exception {
        return getCurrentBranchImpl(projectName);
    }

    @Override
    public boolean createBranch(String projectName, String branchName) throws Exception {
        return createBranchImpl(projectName, branchName);
    }

    @Override
    public boolean checkoutBranch(String projectName, String branchName) throws Exception {
        return checkoutBranchImpl(projectName, branchName);
    }

    @Override
    public boolean deleteBranch(String projectName, String branchName) throws Exception {
        return deleteBranchImpl(projectName, branchName);
    }


    @Override
    public boolean isProjectRegistered(String projectName) {
        return isProjectRegisteredImpl(projectName);
    }

    @Override
    public boolean initializeProject(String projectName, String repoUri, String ignitionUser,
                                     long sshKeyId, long httpsCredentialId) throws Exception {
        return initializeProjectImpl(projectName, repoUri, ignitionUser, sshKeyId, httpsCredentialId);
    }

    @Override
    public List<String> getResourceDiff(String projectName, String resourcePath) {
        return getResourceDiffImpl(projectName, resourcePath);
    }

    @Override
    public Dataset getCommitHistory(String projectName, int skip, int limit) {
        return getCommitHistoryImpl(projectName, skip, limit);
    }

    @Override
    public List<String> getCommitFiles(String projectName, String commitHash) {
        return getCommitFilesImpl(projectName, commitHash);
    }

    @Override
    public List<String> getCommitFileDiff(String projectName, String commitHash, String filePath) {
        return getCommitFileDiffImpl(projectName, commitHash, filePath);
    }

    @Override
    public boolean discardChanges(String projectName, List<String> paths) {
        return discardChangesImpl(projectName, paths);
    }

    @Override
    public boolean checkoutCommit(String projectName, String commitHash) throws Exception {
        return checkoutCommitImpl(projectName, commitHash);
    }

    @Override
    public boolean revertCommit(String projectName, String commitHash) throws Exception {
        return revertCommitImpl(projectName, commitHash);
    }

    @Override
    public boolean initializeLocalProject(String projectName, String ignitionUser) throws Exception {
        return initializeLocalProjectImpl(projectName, ignitionUser);
    }

    @Override
    public boolean hasRemoteRepository(String projectName) {
        return hasRemoteRepositoryImpl(projectName);
    }

    @Override
    public boolean fetch(String projectName, String userName, String remoteName) throws Exception {
        return fetchImpl(projectName, userName, remoteName);
    }

    @Override
    public Dataset listRemotes(String projectName) throws Exception {
        return listRemotesImpl(projectName);
    }

    @Override
    public boolean addRemote(String projectName, String remoteName, String remoteUrl,
                             String ignitionUser) throws Exception {
        return addRemoteImpl(projectName, remoteName, remoteUrl, ignitionUser);
    }

    @Override
    public boolean removeRemote(String projectName, String remoteName, String ignitionUser) throws Exception {
        return removeRemoteImpl(projectName, remoteName, ignitionUser);
    }

    @Override
    public boolean setRemoteUrl(String projectName, String remoteName, String newUrl,
                                String ignitionUser) throws Exception {
        return setRemoteUrlImpl(projectName, remoteName, newUrl, ignitionUser);
    }

    @Override
    public List<String> getConflictingFiles(String projectName) {
        return getConflictingFilesImpl(projectName);
    }

    @Override
    public boolean resolveConflict(String projectName, String filePath, String stage) {
        return resolveConflictImpl(projectName, filePath, stage);
    }

    @Override
    public boolean abortMerge(String projectName) {
        return abortMergeImpl(projectName);
    }

    @Override
    public boolean completeMergeCommit(String projectName, String userName) throws Exception {
        return completeMergeCommitImpl(projectName, userName);
    }

    @Override
    public List<String> getConflictDiff(String projectName, String filePath) {
        return getConflictDiffImpl(projectName, filePath);
    }

    @Override
    public boolean saveUserSshKey(String ignitionUser, String keyName, String sshKey) {
        return saveUserSshKeyImpl(ignitionUser, keyName, sshKey);
    }

    @Override
    public boolean deleteUserSshKey(String ignitionUser, long keyId) {
        return deleteUserSshKeyImpl(ignitionUser, keyId);
    }

    @Override
    public Dataset listUserSshKeys(String ignitionUser) {
        return listUserSshKeysImpl(ignitionUser);
    }

    @Override
    public boolean saveUserHttpsCredential(String ignitionUser, String hostPattern,
                                           String userName, String password) {
        return saveUserHttpsCredentialImpl(ignitionUser, hostPattern, userName, password);
    }

    @Override
    public boolean deleteUserHttpsCredential(String ignitionUser, long credentialId) {
        return deleteUserHttpsCredentialImpl(ignitionUser, credentialId);
    }

    @Override
    public Dataset listUserHttpsCredentials(String ignitionUser) {
        return listUserHttpsCredentialsImpl(ignitionUser);
    }

    @Override
    public boolean setRemoteCredentialRef(String projectName, String remoteName, String ignitionUser,
                                          long sshKeyId, long httpsCredentialId) {
        return setRemoteCredentialRefImpl(projectName, remoteName, ignitionUser, sshKeyId, httpsCredentialId);
    }

    protected abstract boolean pullImpl(String projectName, String userName, String remoteName, boolean importTags, boolean importTheme,
                                        boolean importImages) throws Exception;
    protected abstract boolean pushImpl(String projectName, String userName, String remoteName, boolean pushAllBranches, boolean pushTags, boolean forcePush) throws Exception;
    protected abstract boolean commitImpl(String projectName, String userName, List<String> changes, String message, boolean amend);
    protected abstract Dataset getUncommitedChangesImpl(String projectName, String userName);
    protected abstract boolean isRegisteredUserImpl(String projectName, String userName);
    protected abstract boolean snapshotTagsImpl(String projectName);
    protected abstract boolean snapshotThemesImpl(String projectName);
    protected abstract boolean snapshotImagesImpl(String projectName);
    protected abstract void setupLocalRepoImpl(String projectName, String userName) throws Exception;
    protected abstract List<String> getLocalBranchesImpl(String projectName) throws Exception;
    protected abstract List<String> getRemoteBranchesImpl(String projectName) throws Exception;
    protected abstract String getCurrentBranchImpl(String projectName) throws Exception;
    protected abstract boolean createBranchImpl(String projectName, String branchName) throws Exception;
    protected abstract boolean checkoutBranchImpl(String projectName, String branchName) throws Exception;
    protected abstract boolean deleteBranchImpl(String projectName, String branchName) throws Exception;
    protected abstract boolean isProjectRegisteredImpl(String projectName);
    protected abstract boolean initializeProjectImpl(String projectName, String repoUri, String ignitionUser,
                                                     long sshKeyId, long httpsCredentialId) throws Exception;
    protected abstract List<String> getResourceDiffImpl(String projectName, String resourcePath);
    protected abstract Dataset getCommitHistoryImpl(String projectName, int skip, int limit);
    protected abstract List<String> getCommitFilesImpl(String projectName, String commitHash);
    protected abstract List<String> getCommitFileDiffImpl(String projectName, String commitHash, String filePath);
    protected abstract boolean discardChangesImpl(String projectName, List<String> paths);
    protected abstract boolean checkoutCommitImpl(String projectName, String commitHash) throws Exception;
    protected abstract boolean revertCommitImpl(String projectName, String commitHash) throws Exception;
    protected abstract boolean initializeLocalProjectImpl(String projectName, String ignitionUser) throws Exception;
    protected abstract boolean hasRemoteRepositoryImpl(String projectName);
    protected abstract boolean fetchImpl(String projectName, String userName, String remoteName) throws Exception;
    protected abstract Dataset listRemotesImpl(String projectName) throws Exception;
    protected abstract boolean addRemoteImpl(String projectName, String remoteName, String remoteUrl,
                                             String ignitionUser) throws Exception;
    protected abstract boolean removeRemoteImpl(String projectName, String remoteName,
                                                String ignitionUser) throws Exception;
    protected abstract boolean setRemoteUrlImpl(String projectName, String remoteName, String newUrl,
                                                String ignitionUser) throws Exception;
    protected abstract List<String> getConflictingFilesImpl(String projectName);
    protected abstract boolean resolveConflictImpl(String projectName, String filePath, String stage);
    protected abstract boolean abortMergeImpl(String projectName);
    protected abstract boolean completeMergeCommitImpl(String projectName, String userName) throws Exception;
    protected abstract List<String> getConflictDiffImpl(String projectName, String filePath);
    protected abstract boolean saveUserSshKeyImpl(String ignitionUser, String keyName, String sshKey);
    protected abstract boolean deleteUserSshKeyImpl(String ignitionUser, long keyId);
    protected abstract Dataset listUserSshKeysImpl(String ignitionUser);
    protected abstract boolean saveUserHttpsCredentialImpl(String ignitionUser, String hostPattern,
                                                           String userName, String password);
    protected abstract boolean deleteUserHttpsCredentialImpl(String ignitionUser, long credentialId);
    protected abstract Dataset listUserHttpsCredentialsImpl(String ignitionUser);
    protected abstract boolean setRemoteCredentialRefImpl(String projectName, String remoteName,
                                                          String ignitionUser, long sshKeyId,
                                                          long httpsCredentialId);
}
