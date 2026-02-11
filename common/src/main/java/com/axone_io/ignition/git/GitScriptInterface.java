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

}
