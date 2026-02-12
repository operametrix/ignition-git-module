package com.axone_io.ignition.git.managers;

import com.axone_io.ignition.git.BranchPopup;
import com.axone_io.ignition.git.CommitPopup;
import com.axone_io.ignition.git.CredentialsPopup;
import com.axone_io.ignition.git.DesignerHook;
import com.axone_io.ignition.git.InitRepoPopup;
import com.axone_io.ignition.git.PullPopup;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.project.ChangeOperation;
import com.inductiveautomation.ignition.common.project.resource.ProjectResourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.axone_io.ignition.git.DesignerHook.context;
import static com.axone_io.ignition.git.DesignerHook.rpc;
import static com.axone_io.ignition.git.actions.GitBaseAction.*;
public class GitActionManager {

    static CommitPopup commitPopup;
    static PullPopup pullPopup;
    static BranchPopup branchPopup;
    static CredentialsPopup credentialsPopup;
    static InitRepoPopup initRepoPopup;
    private static final Logger logger = LoggerFactory.getLogger(GitActionManager.class);



    public static Object[][] getCommitPopupData(String projectName, String userName) {
        List<ChangeOperation> changes = DesignerHook.changes;

        // Log the total number of change operations found
        logger.debug("Total number of change operations: {}", changes.size());

        Dataset ds = rpc.getUncommitedChanges(projectName, userName);
        Object[][] data = new Object[ds.getRowCount()][];

        List<String> resourcesChangedId = new ArrayList<>();
        for (ChangeOperation c : changes) {
            ProjectResourceId pri = ChangeOperation.getResourceIdFromChange(c);
            resourcesChangedId.add(pri.getResourcePath().toString());

            // Log each change operation's details
            logger.debug("ChangeOperation Type: {}, Resource: {}", c.getOperationType(), pri.getResourcePath());
        }

        for (int i = 0; i < ds.getRowCount(); i++) {
            String resource = (String) ds.getValueAt(i, "resource");

            boolean toAdd = resourcesChangedId.contains(resource);
            Object[] row = {toAdd, resource, ds.getValueAt(i, "type"), ds.getValueAt(i, "actor")};

            // Log the decision to add or not add the resource to the commit popup
            logger.debug("Resource: {}, Add to commit popup: {}", resource, toAdd);

            data[i] = row;
        }

        return data;
    }


    public static void showCommitPopup(String projectName, String userName) {
        Object[][] data = GitActionManager.getCommitPopupData(projectName, userName);
        if (commitPopup != null) {
            commitPopup.setData(data);
            commitPopup.setVisible(true);
            commitPopup.toFront();
        } else {
            commitPopup = new CommitPopup(data, context.getFrame()) {
                @Override
                public void onActionPerformed(List<String> changes, String commitMessage) {
                    handleCommitAction(changes, commitMessage);
                    resetMessage();
                }
            };
        }
    }
    public static void openRepositoryLink(String projectName) {
        try {
            Desktop desktop = Desktop.getDesktop();
            String repoLink = rpc.getRepoURL(projectName);
            desktop.browse(new URI(repoLink)); // This line might throw IOException or URISyntaxException
        } catch (Exception e) {
            logger.error("Error opening repository link", e);
        }
    }



    public static void showPullPopup(String projectName, String userName) {
        if (pullPopup != null) {
            pullPopup.setVisible(true);
            pullPopup.toFront();
        } else {
            pullPopup = new PullPopup(context.getFrame()) {
                @Override
                public void onPullAction(boolean importTags, boolean importTheme, boolean importImages) {
                    handlePullAction(importTags, importTheme, importImages);
                    resetCheckboxes();
                }
            };
        }
    }

    public static void showBranchPopup(String projectName, String userName) {
        try {
            String currentBranch = rpc.getCurrentBranch(projectName);
            List<String> localBranches = rpc.getLocalBranches(projectName);
            List<String> remoteBranches = rpc.getRemoteBranches(projectName);

            if (branchPopup != null) {
                branchPopup.setData(currentBranch, localBranches, remoteBranches);
                branchPopup.setVisible(true);
                branchPopup.toFront();
            } else {
                branchPopup = new BranchPopup(currentBranch, localBranches, remoteBranches, context.getFrame()) {
                    @Override
                    public void onCheckoutBranch(String branchName) {
                        handleCheckoutAction(branchName);
                        onRefresh();
                    }

                    @Override
                    public void onCreateBranch(String branchName, String startPoint) {
                        handleCreateBranchAction(branchName, startPoint);
                        onRefresh();
                    }

                    @Override
                    public void onDeleteBranch(String branchName) {
                        handleDeleteBranchAction(branchName);
                        onRefresh();
                    }

                    @Override
                    public void onRefresh() {
                        try {
                            String current = rpc.getCurrentBranch(projectName);
                            List<String> local = rpc.getLocalBranches(projectName);
                            List<String> remote = rpc.getRemoteBranches(projectName);
                            setData(current, local, remote);
                        } catch (Exception ex) {
                            logger.error("Error refreshing branch data", ex);
                        }
                    }
                };
            }
        } catch (Exception e) {
            logger.error("Error showing branch popup", e);
        }
    }

    public static void showCredentialsPopup(String projectName, String userName) {
        try {
            String authType = rpc.isSSHAuthentication(projectName) ? "SSH" : "HTTPS";
            String currentEmail = rpc.getUserEmail(projectName, userName);
            String currentGitUsername = rpc.getUserGitUsername(projectName, userName);

            if (credentialsPopup != null) {
                credentialsPopup.setData(authType, currentEmail, currentGitUsername);
                credentialsPopup.setVisible(true);
                credentialsPopup.toFront();
            } else {
                credentialsPopup = new CredentialsPopup(authType, currentEmail, currentGitUsername, context.getFrame()) {
                    @Override
                    public void onSave(String email, String gitUsername, String password, String sshKey) {
                        boolean success = rpc.saveUserCredentials(projectName, userName, email, gitUsername, password, sshKey);
                        if (success) {
                            showConfirmPopup("Credentials saved successfully.", JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                            credentialsPopup = null;
                        } else {
                            showConfirmPopup("Failed to save credentials.", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
            }
        } catch (Exception e) {
            logger.error("Error showing credentials popup", e);
        }
    }

    public static void showInitRepoPopup(String projectName, String userName) {
        if (initRepoPopup != null) {
            initRepoPopup.setVisible(true);
            initRepoPopup.toFront();
        } else {
            initRepoPopup = new InitRepoPopup(context.getFrame()) {
                @Override
                public void onInitialize(String repoUri, String email, String gitUsername, String password, String sshKey) {
                    try {
                        rpc.initializeProject(projectName, repoUri, userName, email, gitUsername, password, sshKey);
                        pullProjectFromGateway();
                        showConfirmPopup("Repository initialized successfully.", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                        initRepoPopup = null;
                        DesignerHook.instance.reinitializeAfterSetup();
                    } catch (Exception e) {
                        logger.error("Error initializing repository", e);
                        showConfirmPopup("Failed to initialize repository: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
        }
    }

    public static void showConfirmPopup(String message, int messageType) {
        JOptionPane.showConfirmDialog(context.getFrame(),
                message, "Info", JOptionPane.DEFAULT_OPTION, messageType);
    }
}
