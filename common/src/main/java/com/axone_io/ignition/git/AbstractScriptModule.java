package com.axone_io.ignition.git;

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
                        @ScriptArg("importTags") boolean importTags,
                        @ScriptArg("importTheme") boolean importTheme,
                        @ScriptArg("importImages") boolean importImages) throws Exception {
        return pullImpl(projectName, userName, importTags, importTheme, importImages);
    }


    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean push(@ScriptArg("projectName") String projectName,
                        @ScriptArg("userName")String userName) throws Exception {
        return pushImpl(projectName,userName);
    }

    @Override
    @ScriptFunction(docBundlePrefix = "AbstractScriptModule")
    public boolean commit(@ScriptArg("projectName") String projectName,
                          @ScriptArg("userName") String userName,
                          @ScriptArg("changes") List<String> changes,
                          @ScriptArg("message") String message) {
        return commitImpl(projectName, userName, changes, message);
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

    protected abstract boolean pullImpl(String projectName, String userName, boolean importTags, boolean importTheme,
                                        boolean importImages) throws Exception;
    protected abstract boolean pushImpl(String projectName, String userName) throws Exception;
    protected abstract boolean commitImpl(String projectName, String userName, List<String> changes, String message);
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

}
