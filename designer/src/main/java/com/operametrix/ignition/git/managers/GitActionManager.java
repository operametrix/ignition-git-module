package com.operametrix.ignition.git.managers;

import com.operametrix.ignition.git.BranchPopup;
import com.operametrix.ignition.git.CommitDetailPopup;
import com.operametrix.ignition.git.CommitPopup;
import com.operametrix.ignition.git.CredentialsPopup;
import com.operametrix.ignition.git.DesignerHook;
import com.operametrix.ignition.git.DiffViewerPopup;
import com.operametrix.ignition.git.HistoryPopup;
import com.operametrix.ignition.git.InitRepoPopup;
import com.operametrix.ignition.git.PullPopup;
import com.operametrix.ignition.git.CommitPanel;
import com.operametrix.ignition.git.HistoryPanel;
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

import static com.operametrix.ignition.git.DesignerHook.context;
import static com.operametrix.ignition.git.DesignerHook.rpc;
import static com.operametrix.ignition.git.actions.GitBaseAction.*;
public class GitActionManager {

    static CommitPopup commitPopup;
    static PullPopup pullPopup;
    static BranchPopup branchPopup;
    static CredentialsPopup credentialsPopup;
    static InitRepoPopup initRepoPopup;
    static HistoryPopup historyPopup;
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
            Object[] row = {toAdd, resource, ds.getValueAt(i, "type"), ds.getValueAt(i, "actor"), ds.getValueAt(i, "timestamp")};

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
                public void onActionPerformed(List<String> changes, String commitMessage, boolean amend) {
                    handleCommitAction(changes, commitMessage, amend);
                    resetMessage();
                }

                @Override
                public void onDiffRequested(String resource, String type) {
                    showDiffViewer(projectName, resource, type);
                }

                @Override
                public void onAmendToggled(boolean amend) {
                    if (amend) {
                        new Thread(() -> {
                            try {
                                Dataset history = rpc.getCommitHistory(projectName, 0, 1);
                                if (history.getRowCount() > 0) {
                                    String lastMessage = (String) history.getValueAt(0, "message");
                                    SwingUtilities.invokeLater(() -> setCommitMessage(lastMessage));
                                }
                            } catch (Exception e) {
                                logger.error("Error fetching last commit message", e);
                            }
                        }).start();
                    } else {
                        setCommitMessage("");
                    }
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

    public static void showDiffViewer(String projectName, String resource, String type) {
        try {
            List<String> diff = rpc.getResourceDiff(projectName, resource);
            String oldContent = diff.get(0);
            String newContent = diff.get(1);
            new DiffViewerPopup(resource, oldContent, newContent, context.getFrame());
        } catch (Exception e) {
            logger.error("Error showing diff viewer", e);
            JOptionPane.showMessageDialog(context.getFrame(),
                    "Failed to load diff: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void showHistoryPopup(String projectName) {
        try {
            Dataset data = rpc.getCommitHistory(projectName, 0, HistoryPopup.PAGE_SIZE);
            if (historyPopup != null) {
                historyPopup.setData(data, false);
                historyPopup.setVisible(true);
                historyPopup.toFront();
            } else {
                historyPopup = new HistoryPopup(data, context.getFrame()) {
                    @Override
                    public void onCommitSelected(String fullHash, String shortHash, String message,
                                                  String author, String date) {
                        showCommitDetailPopup(projectName, fullHash, shortHash, message, author, date);
                    }

                    @Override
                    public void onLoadMore() {
                        try {
                            Dataset moreData = rpc.getCommitHistory(projectName, getCurrentOffset(), getPageSize());
                            setData(moreData, true);
                        } catch (Exception ex) {
                            logger.error("Error loading more commits", ex);
                        }
                    }

                    @Override
                    public void onRefresh() {
                        try {
                            Dataset freshData = rpc.getCommitHistory(projectName, 0, HistoryPopup.PAGE_SIZE);
                            setData(freshData, false);
                        } catch (Exception ex) {
                            logger.error("Error refreshing commit history", ex);
                        }
                    }
                };
            }
        } catch (Exception e) {
            logger.error("Error showing history popup", e);
        }
    }

    public static void showCommitDetailPopup(String projectName, String fullHash, String shortHash,
                                               String message, String author, String date) {
        try {
            List<String> files = rpc.getCommitFiles(projectName, fullHash);
            new CommitDetailPopup(fullHash, shortHash, message, author, date, files, context.getFrame()) {
                @Override
                public void onFileDiffRequested(String commitHash, String filePath, String changeType) {
                    showCommitFileDiff(projectName, commitHash, filePath, shortHash);
                }
            };
        } catch (Exception e) {
            logger.error("Error showing commit detail popup", e);
            JOptionPane.showMessageDialog(context.getFrame(),
                    "Failed to load commit details: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void showCommitFileDiff(String projectName, String commitHash, String filePath, String shortHash) {
        try {
            List<String> diff = rpc.getCommitFileDiff(projectName, commitHash, filePath);
            String oldContent = diff.get(0);
            String newContent = diff.get(1);
            new DiffViewerPopup(filePath + " @ " + shortHash, oldContent, newContent, context.getFrame());
        } catch (Exception e) {
            logger.error("Error showing commit file diff", e);
            JOptionPane.showMessageDialog(context.getFrame(),
                    "Failed to load diff: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void wireCommitPanel(CommitPanel panel, String projectName, String userName) {
        panel.setOnRefreshRequested(() -> {
            if (DesignerHook.instance != null) {
                DesignerHook.instance.refreshCommitPanel();
            }
        });

        panel.setOnDiffRequested((resource, type) -> showDiffViewer(projectName, resource, type));

        panel.setOnDiscardRequested(paths -> new Thread(() -> {
            try {
                rpc.discardChanges(projectName, paths);
                pullProjectFromGateway();
                if (DesignerHook.instance != null) {
                    DesignerHook.instance.refreshCommitPanel();
                }
            } catch (Exception e) {
                logger.error("Error discarding changes", e);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(context.getFrame(),
                                "Failed to discard changes: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE));
            }
        }).start());

        panel.setOnCommitRequested((changes, message) -> new Thread(() -> {
            try {
                handleCommitAction(changes, message, panel.isAmendSelected());
                if (DesignerHook.instance != null) {
                    DesignerHook.instance.refreshCommitPanel();
                }
            } catch (Exception e) {
                logger.error("Error committing from panel", e);
            }
        }).start());

        panel.setOnAmendToggled(amend -> {
            if (amend) {
                new Thread(() -> {
                    try {
                        Dataset history = rpc.getCommitHistory(projectName, 0, 1);
                        if (history.getRowCount() > 0) {
                            String lastMessage = (String) history.getValueAt(0, "message");
                            panel.setCommitMessage(lastMessage);
                        }
                    } catch (Exception e) {
                        logger.error("Error fetching last commit message", e);
                    }
                }).start();
            } else {
                panel.setCommitMessage("");
            }
        });
    }

    public static void wireHistoryPanel(HistoryPanel panel, String projectName, String userName) {
        panel.setOnPushRequested(() -> handlePushAction());

        panel.setOnPullRequested(() -> showPullPopup(projectName, userName));

        panel.setOnRefreshRequested(() -> {
            if (DesignerHook.instance != null) {
                DesignerHook.instance.refreshHistoryPanel();
            }
        });

        panel.setOnCommitSelected(node ->
                showCommitDetailPopup(projectName, node.hash, node.shortHash, node.message,
                        node.author, node.date));

        panel.setOnLoadMore(() -> new Thread(() -> {
            try {
                Dataset moreData = rpc.getCommitHistory(projectName, panel.getCurrentOffset(), HistoryPanel.PAGE_SIZE);
                panel.setData(moreData, true);
            } catch (Exception e) {
                logger.error("Error loading more commits for history", e);
            }
        }).start());
    }

    public static void showConfirmPopup(String message, int messageType) {
        JOptionPane.showConfirmDialog(context.getFrame(),
                message, "Info", JOptionPane.DEFAULT_OPTION, messageType);
    }
}
