package com.operametrix.ignition.git.managers;

import com.operametrix.ignition.git.BranchPopup;
import com.operametrix.ignition.git.CommitDetailPopup;
import com.operametrix.ignition.git.CreateBranchPopup;
import com.operametrix.ignition.git.CommitPopup;
import com.operametrix.ignition.git.UserCredentialsPopup;
import com.operametrix.ignition.git.DesignerHook;
import com.operametrix.ignition.git.DiffViewerPopup;
import com.operametrix.ignition.git.InitProgressDialog;
import com.operametrix.ignition.git.InitRepoPopup;
import com.operametrix.ignition.git.PullPopup;
import com.operametrix.ignition.git.PushPopup;
import com.operametrix.ignition.git.FetchPopup;
import com.operametrix.ignition.git.RemotesPopup;
import com.operametrix.ignition.git.CommitPanel;
import com.operametrix.ignition.git.HistoryPanel;
import com.operametrix.ignition.git.MergeConflictPopup;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.resourcecollection.ChangeOperation;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import static com.operametrix.ignition.git.DesignerHook.context;
import static com.operametrix.ignition.git.DesignerHook.rpc;
import static com.operametrix.ignition.git.actions.GitBaseAction.*;
public class GitActionManager {

    static CommitPopup commitPopup;
    static PullPopup pullPopup;
    static PushPopup pushPopup;
    static FetchPopup fetchPopup;
    static BranchPopup branchPopup;
    static UserCredentialsPopup userCredentialsPopup;
    static InitRepoPopup initRepoPopup;
    static RemotesPopup remotesPopup;
    private static final Logger logger = LoggerFactory.getLogger(GitActionManager.class);



    public static Object[][] getCommitPopupData(String projectName, String userName) {
        List<ChangeOperation> changes = DesignerHook.changes;

        // Log the total number of change operations found
        logger.debug("Total number of change operations: {}", changes.size());

        Dataset ds = rpc.getUncommitedChanges(projectName, userName);
        Object[][] data = new Object[ds.getRowCount()][];

        List<String> resourcesChangedId = new ArrayList<>();
        for (ChangeOperation c : changes) {
            ResourceId pri = ChangeOperation.getResourceIdFromChange(c);
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


    /**
     * Run a gateway-state snapshot operation on a background thread with a modal
     * progress dialog, then refresh the Commit panel so the resulting file changes
     * become visible for review and per-file commit selection.
     */
    private static void runSnapshot(String label, SnapshotCall call) {
        InitProgressDialog progress = new InitProgressDialog(context.getFrame(),
                "Snapshotting " + label);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                progress.setStatus("Writing " + label + " to project files...");
                call.run();
                return null;
            }

            @Override
            protected void done() {
                progress.complete();
                try {
                    get();
                    if (DesignerHook.instance != null) {
                        DesignerHook.instance.refreshCommitPanel();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logger.error("Error snapshotting " + label, cause);
                    String detail = cause.getMessage() != null
                            ? cause.getMessage() : cause.getClass().getSimpleName();
                    ErrorUtil.showError("Failed to snapshot " + label + ": " + detail, cause);
                }
            }
        }.execute();
        progress.setVisible(true);
    }

    @FunctionalInterface
    private interface SnapshotCall {
        void run() throws Exception;
    }

    public static void showPullPopup(String projectName, String userName) {
        List<String> remoteNames = getRemoteNames(projectName);

        if (pullPopup != null) {
            pullPopup.setRemotes(remoteNames);
            pullPopup.resetCheckboxes();
            pullPopup.setVisible(true);
            pullPopup.toFront();
        } else {
            pullPopup = new PullPopup(context.getFrame()) {
                @Override
                public void onPullAction(String remoteName, boolean importTags, boolean importTheme, boolean importImages) {
                    handlePullAction(remoteName, importTags, importTheme, importImages);
                    resetCheckboxes();
                }
            };
            pullPopup.setRemotes(remoteNames);
        }
    }

    public static void showPushPopup(String projectName, String userName) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before pushing.",
                    "Push", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> remoteNames = getRemoteNames(projectName);

        // Single remote: skip popup, push immediately
        if (remoteNames.size() <= 1) {
            String remote = remoteNames.isEmpty() ? "origin" : remoteNames.get(0);
            handlePushAction(remote);
            return;
        }

        // Multiple remotes: show popup
        if (pushPopup != null) {
            pushPopup.setRemotes(remoteNames);
            pushPopup.setVisible(true);
            pushPopup.toFront();
        } else {
            pushPopup = new PushPopup(context.getFrame()) {
                @Override
                public void onPush(String remoteName) {
                    handlePushAction(remoteName);
                }
            };
            pushPopup.setRemotes(remoteNames);
        }
    }

    private static List<String> getRemoteNames(String projectName) {
        List<String> remoteNames = new ArrayList<>();
        try {
            Dataset remotes = rpc.listRemotes(projectName);
            for (int i = 0; i < remotes.getRowCount(); i++) {
                remoteNames.add((String) remotes.getValueAt(i, "name"));
            }
        } catch (Exception e) {
            logger.error("Error listing remotes", e);
        }
        if (remoteNames.isEmpty()) {
            remoteNames.add("origin");
        }
        return remoteNames;
    }

    public static void showFetchAction(String projectName, String userName) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before fetching.",
                    "Fetch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<String> remoteNames = getRemoteNames(projectName);

        // Single remote: skip popup, fetch immediately
        if (remoteNames.size() <= 1) {
            String remote = remoteNames.isEmpty() ? "origin" : remoteNames.get(0);
            handleFetchAction(remote);
            return;
        }

        // Multiple remotes: show popup
        if (fetchPopup != null) {
            fetchPopup.setRemotes(remoteNames);
            fetchPopup.setVisible(true);
            fetchPopup.toFront();
        } else {
            fetchPopup = new FetchPopup(context.getFrame()) {
                @Override
                public void onFetch(String remoteName) {
                    handleFetchAction(remoteName);
                }
            };
            fetchPopup.setRemotes(remoteNames);
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
                    public void onCreateBranchRequested() {
                        BranchPopup branchPopupRef = this;
                        new CreateBranchPopup(context.getFrame()) {
                            @Override
                            public void onCreateBranch(String branchName) {
                                handleCreateBranchAction(branchName);
                                branchPopupRef.onRefresh();
                            }
                        };
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

                    @Override
                    public void onRefreshFromRemote() {
                        BranchPopup popup = this;
                        InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Refreshing Branches");
                        new SwingWorker<Void, Void>() {
                            @Override
                            protected Void doInBackground() throws Exception {
                                if (rpc.hasRemoteRepository(projectName)) {
                                    Dataset remotes = rpc.listRemotes(projectName);
                                    for (int i = 0; i < remotes.getRowCount(); i++) {
                                        String remoteName = (String) remotes.getValueAt(i, "name");
                                        progress.setStatus("Fetching from " + remoteName + "...");
                                        try {
                                            rpc.fetch(projectName, userName, remoteName);
                                        } catch (Exception fetchEx) {
                                            logger.warn("Fetch from '" + remoteName + "' failed; continuing with local refresh", fetchEx);
                                        }
                                    }
                                }
                                return null;
                            }

                            @Override
                            protected void done() {
                                progress.complete();
                                try {
                                    get();
                                    String current = rpc.getCurrentBranch(projectName);
                                    List<String> local = rpc.getLocalBranches(projectName);
                                    List<String> remote = rpc.getRemoteBranches(projectName);
                                    popup.setData(current, local, remote);
                                    if (DesignerHook.instance != null) {
                                        DesignerHook.instance.refreshHistoryPanel();
                                    }
                                } catch (Exception ex) {
                                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                    logger.error("Error refreshing branch data", cause);
                                }
                            }
                        }.execute();
                        progress.setVisible(true);
                    }
                };
            }
        } catch (Exception e) {
            logger.error("Error showing branch popup", e);
        }
    }

    public static void showCredentialsPopup(String projectName, String userName) {
        try {
            if (userCredentialsPopup != null) {
                refreshUserCredentialsPopup(userName);
                userCredentialsPopup.setVisible(true);
                userCredentialsPopup.toFront();
            } else {
                userCredentialsPopup = new UserCredentialsPopup(context.getFrame()) {
                    @Override
                    public void onSaveSshKey(String keyName, String sshKey) {
                        boolean success = rpc.saveUserSshKey(userName, keyName, sshKey);
                        if (success) {
                            refreshUserCredentialsPopup(userName);
                        } else {
                            JOptionPane.showMessageDialog(userCredentialsPopup,
                                    "Failed to save SSH key.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    @Override
                    public void onDeleteSshKey(long keyId) {
                        boolean success = rpc.deleteUserSshKey(userName, keyId);
                        if (success) {
                            refreshUserCredentialsPopup(userName);
                        } else {
                            JOptionPane.showMessageDialog(userCredentialsPopup,
                                    "Failed to delete SSH key.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    @Override
                    public void onSaveHttpsCredential(String hostPattern, String credUserName, String password) {
                        boolean success = rpc.saveUserHttpsCredential(userName, hostPattern, credUserName, password);
                        if (success) {
                            refreshUserCredentialsPopup(userName);
                        } else {
                            JOptionPane.showMessageDialog(userCredentialsPopup,
                                    "Failed to save HTTPS credential.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    @Override
                    public void onDeleteHttpsCredential(long credentialId) {
                        boolean success = rpc.deleteUserHttpsCredential(userName, credentialId);
                        if (success) {
                            refreshUserCredentialsPopup(userName);
                        } else {
                            JOptionPane.showMessageDialog(userCredentialsPopup,
                                    "Failed to delete HTTPS credential.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                refreshUserCredentialsPopup(userName);
            }
        } catch (Exception e) {
            logger.error("Error showing user credentials popup", e);
        }
    }

    private static void refreshUserCredentialsPopup(String userName) {
        if (userCredentialsPopup == null) return;
        Dataset sshKeys = rpc.listUserSshKeys(userName);
        Dataset httpsCreds = rpc.listUserHttpsCredentials(userName);
        userCredentialsPopup.setSshKeyData(sshKeys);
        userCredentialsPopup.setHttpsCredentialData(httpsCreds);
    }

    /**
     * Add a one-shot WindowListener to the UserCredentialsPopup that runs the given
     * callback when the popup is closed, then removes itself.
     */
    private static void addCredentialsCloseListener(String userName, Runnable onClose) {
        if (userCredentialsPopup == null) return;
        userCredentialsPopup.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                onClose.run();
                userCredentialsPopup.removeWindowListener(this);
            }
        });
    }

    public static void showRemotesPopup(String projectName, String userName) {
        try {
            Dataset remotes = rpc.listRemotes(projectName);
            Dataset sshKeys = rpc.listUserSshKeys(userName);
            Dataset httpsCreds = rpc.listUserHttpsCredentials(userName);

            if (remotesPopup != null) {
                remotesPopup.setSavedCredentials(sshKeys, httpsCreds);
                remotesPopup.setData(remotes);
                remotesPopup.setVisible(true);
                remotesPopup.toFront();
            } else {
                remotesPopup = new RemotesPopup(context.getFrame()) {
                    @Override
                    public void onAddRemote(String name, String url) {
                        try {
                            rpc.addRemote(projectName, name, url, userName);
                            onRefresh();
                        } catch (Exception e) {
                            logger.error("Error adding remote", e);
                            ErrorUtil.showError("Failed to add remote: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onEditRemote(String name, String newUrl) {
                        try {
                            rpc.setRemoteUrl(projectName, name, newUrl, userName);
                            onRefresh();
                        } catch (Exception e) {
                            logger.error("Error updating remote", e);
                            ErrorUtil.showError("Failed to update remote: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onRemoveRemote(String name) {
                        try {
                            rpc.removeRemote(projectName, name, userName);
                            onRefresh();
                        } catch (Exception e) {
                            logger.error("Error removing remote", e);
                            ErrorUtil.showError("Failed to remove remote: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onRefresh() {
                        try {
                            Dataset freshRemotes = rpc.listRemotes(projectName);
                            Dataset freshSshKeys = rpc.listUserSshKeys(userName);
                            Dataset freshHttpsCreds = rpc.listUserHttpsCredentials(userName);
                            setSavedCredentials(freshSshKeys, freshHttpsCreds);
                            setData(freshRemotes);
                        } catch (Exception e) {
                            logger.error("Error refreshing remotes", e);
                        }
                    }

                    @Override
                    public void onSavedCredentialSelected(String remoteName, long sshKeyId, long httpsCredentialId) {
                        try {
                            rpc.setRemoteCredentialRef(projectName, remoteName, userName, sshKeyId, httpsCredentialId);
                        } catch (Exception e) {
                            logger.error("Error setting credential reference", e);
                            ErrorUtil.showError("Failed to associate credential: " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onConfigureCredentials() {
                        showCredentialsPopup(projectName, userName);
                        addCredentialsCloseListener(userName, () -> {
                            Dataset freshSshKeys = rpc.listUserSshKeys(userName);
                            Dataset freshHttpsCreds = rpc.listUserHttpsCredentials(userName);
                            if (remotesPopup != null) {
                                remotesPopup.setSavedCredentials(freshSshKeys, freshHttpsCreds);
                                remotesPopup.refreshCredentialDropdown();
                            }
                        });
                    }
                };
                remotesPopup.setSavedCredentials(sshKeys, httpsCreds);
                remotesPopup.setData(remotes);
            }
        } catch (Exception e) {
            logger.error("Error showing remotes popup", e);
        }
    }

    public static void showInitRepoPopup(String projectName, String userName) {
        if (initRepoPopup != null) {
            initRepoPopup.setVisible(true);
            initRepoPopup.toFront();
        } else {
            Dataset sshKeys = rpc.listUserSshKeys(userName);
            Dataset httpsCreds = rpc.listUserHttpsCredentials(userName);

            initRepoPopup = new InitRepoPopup(context.getFrame()) {
                @Override
                public void onInitialize(String repoUri, long sshKeyId, long httpsCredentialId) {
                    setEnabled(false);
                    InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Cloning Repository");
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            progress.setStatus("Connecting to remote and cloning repository...");
                            rpc.initializeProject(projectName, repoUri, userName, sshKeyId, httpsCredentialId);
                            return null;
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                progress.setStatus("Syncing project to Designer...");
                                pullProjectFromGateway();
                                progress.setStatus("Complete");
                                progress.complete();
                                dispose();
                                initRepoPopup = null;
                                DesignerHook.instance.reinitializeAfterSetup();
                            } catch (Exception e) {
                                progress.complete();
                                Throwable cause = e.getCause() != null ? e.getCause() : e;
                                logger.error("Error initializing repository", cause);
                                ErrorUtil.showError("Failed to initialize repository: " + cause.getMessage(), cause);
                                setEnabled(true);
                            }
                        }
                    }.execute();
                    progress.setVisible(true);
                }

                @Override
                public void onLocalInitialize() {
                    setEnabled(false);
                    InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Initializing Repository");
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            progress.setStatus("Creating local repository...");
                            rpc.initializeLocalProject(projectName, userName);
                            return null;
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                progress.setStatus("Complete");
                                progress.complete();
                                dispose();
                                initRepoPopup = null;
                                DesignerHook.instance.reinitializeAfterSetup();
                            } catch (Exception e) {
                                progress.complete();
                                Throwable cause = e.getCause() != null ? e.getCause() : e;
                                logger.error("Error initializing local repository", cause);
                                ErrorUtil.showError("Failed to initialize local repository: " + cause.getMessage(), cause);
                                setEnabled(true);
                            }
                        }
                    }.execute();
                    progress.setVisible(true);
                }

                @Override
                public void onConfigureCredentials() {
                    showCredentialsPopup(projectName, userName);
                    addCredentialsCloseListener(userName, () -> {
                        Dataset freshSshKeys = rpc.listUserSshKeys(userName);
                        Dataset freshHttpsCreds = rpc.listUserHttpsCredentials(userName);
                        if (initRepoPopup != null) {
                            initRepoPopup.setSavedCredentials(freshSshKeys, freshHttpsCreds);
                            initRepoPopup.refreshCredentialDropdown();
                        }
                    });
                }
            };
            initRepoPopup.setSavedCredentials(sshKeys, httpsCreds);
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

    public static void showCommitDetailPopup(String projectName, String fullHash, String shortHash,
                                               String message, String author, String date) {
        try {
            List<String> files = rpc.getCommitFiles(projectName, fullHash);
            new CommitDetailPopup(fullHash, shortHash, message, author, date, files, context.getFrame()) {
                @Override
                public void onFileDiffRequested(String commitHash, String filePath, String changeType) {
                    showCommitFileDiff(projectName, commitHash, filePath, shortHash);
                }

                @Override
                public void onRevertRequested(String commitHash, String shortHash, String message) {
                    handleRevertCommitAction(commitHash, shortHash, message);
                }

                @Override
                public void onCheckoutRequested(String commitHash, String shortHash) {
                    handleCheckoutCommitAction(commitHash, shortHash);
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

    public static void showMergeConflictPopup(String projectName, String userName,
                                                List<String> conflictingFiles) {
        new MergeConflictPopup(conflictingFiles, context.getFrame()) {
            @Override
            public void onResolveConflict(String filePath, String stage) {
                new Thread(() -> {
                    try {
                        rpc.resolveConflict(projectName, filePath, stage);
                        SwingUtilities.invokeLater(() -> markResolved(filePath, stage));
                    } catch (Exception e) {
                        logger.error("Error resolving conflict for " + filePath, e);
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this,
                                        "Failed to resolve conflict: " + e.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE));
                    }
                }).start();
            }

            @Override
            public void onResolveAllConflicts(String stage) {
                new Thread(() -> {
                    try {
                        List<String> remaining = rpc.getConflictingFiles(projectName);
                        for (String filePath : remaining) {
                            rpc.resolveConflict(projectName, filePath, stage);
                        }
                        SwingUtilities.invokeLater(() -> markAllResolved(stage));
                    } catch (Exception e) {
                        logger.error("Error resolving all conflicts", e);
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this,
                                        "Failed to resolve conflicts: " + e.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE));
                    }
                }).start();
            }

            @Override
            public void onAbortMerge() {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Abort the merge? All changes from the pull will be discarded.",
                        "Abort Merge", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;

                new Thread(() -> {
                    try {
                        rpc.abortMerge(projectName);
                        pullProjectFromGateway();
                        SwingUtilities.invokeLater(this::dispose);
                    } catch (Exception e) {
                        logger.error("Error aborting merge", e);
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this,
                                        "Failed to abort merge: " + e.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE));
                    } finally {
                        if (DesignerHook.instance != null) {
                            DesignerHook.instance.refreshCommitPanel();
                            DesignerHook.instance.refreshHistoryPanel();
                        }
                    }
                }).start();
            }

            @Override
            public void onCompleteMerge() {
                new Thread(() -> {
                    try {
                        rpc.completeMergeCommit(projectName, userName);
                        pullProjectFromGateway();
                        SwingUtilities.invokeLater(this::dispose);
                    } catch (Exception e) {
                        logger.error("Error completing merge", e);
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this,
                                        "Failed to complete merge: " + e.getMessage(),
                                        "Error", JOptionPane.ERROR_MESSAGE));
                    } finally {
                        if (DesignerHook.instance != null) {
                            DesignerHook.instance.refreshCommitPanel();
                            DesignerHook.instance.refreshHistoryPanel();
                        }
                    }
                }).start();
            }

            @Override
            public void onViewDiff(String filePath) {
                try {
                    List<String> diff = rpc.getConflictDiff(projectName, filePath);
                    String oursContent = diff.get(0);
                    String theirsContent = diff.get(1);
                    new DiffViewerPopup(filePath, oursContent, theirsContent, this,
                            "Ours (HEAD)", "Theirs (incoming)");
                } catch (Exception e) {
                    logger.error("Error showing conflict diff", e);
                    JOptionPane.showMessageDialog(this,
                            "Failed to load conflict diff: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
    }

    public static void wireCommitPanel(CommitPanel panel, String projectName, String userName) {
        panel.setOnRefreshRequested(() -> {
            if (DesignerHook.instance != null) {
                DesignerHook.instance.refreshCommitPanel();
            }
        });

        panel.setOnSnapshotTagsRequested(() ->
                runSnapshot("tags", () -> rpc.snapshotTags(projectName)));
        panel.setOnSnapshotThemesRequested(() ->
                runSnapshot("themes", () -> rpc.snapshotThemes(projectName)));
        panel.setOnSnapshotImagesRequested(() ->
                runSnapshot("images", () -> rpc.snapshotImages(projectName)));

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

        panel.setOnCommitRequested((changes, message, amend) -> new Thread(() -> {
            try {
                handleCommitAction(changes, message, amend);
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
        panel.setOnPushRequested(() -> showPushPopup(projectName, userName));

        panel.setOnFetchRequested(() -> showFetchAction(projectName, userName));

        panel.setOnPullRequested(() -> showPullPopup(projectName, userName));

        panel.setOnRefreshRequested(() -> {
            if (DesignerHook.instance != null) {
                DesignerHook.instance.refreshHistoryPanel();
            }
        });

        panel.setOnCommitSelected(node ->
                showCommitDetailPopup(projectName, node.hash, node.shortHash, node.message,
                        node.author, node.date));

        panel.setOnRevertRequested(node ->
                handleRevertCommitAction(node.hash, node.shortHash, node.message));

        panel.setOnCheckoutRequested(node ->
                handleCheckoutCommitAction(node.hash, node.shortHash));

        panel.setOnLoadMore(() -> new Thread(() -> {
            try {
                Dataset moreData = rpc.getCommitHistory(projectName, panel.getCurrentOffset(), HistoryPanel.PAGE_SIZE);
                panel.setData(moreData, true);
            } catch (Exception e) {
                logger.error("Error loading more commits for history", e);
            }
        }).start());
    }

}
