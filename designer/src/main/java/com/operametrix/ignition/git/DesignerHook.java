package com.operametrix.ignition.git;

import com.operametrix.ignition.git.actions.GitBaseAction;
import com.operametrix.ignition.git.managers.GitActionManager;
import com.operametrix.ignition.git.utils.IconUtils;
import com.inductiveautomation.ignition.client.gateway_interface.ModuleRPCFactory;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.SessionInfo;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.project.ChangeOperation;
import com.inductiveautomation.ignition.designer.gui.DesignerToolbar;
import com.inductiveautomation.ignition.designer.gui.StatusBar;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.SaveContext;
import com.jidesoft.action.DockableBarManager;
import com.jidesoft.docking.DockContext;
import com.jidesoft.docking.DockableFrame;
import com.jidesoft.docking.DockingManager;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DesignerHook extends AbstractDesignerModuleHook {
    private static final String PROJECT_BROWSER_KEY = "Project Browser";

    public static DesignerHook instance;
    public static GitScriptInterface rpc = ModuleRPCFactory.create(
            "com.operametrix.ignition.git",
            GitScriptInterface.class
    );
    public static List<ChangeOperation> changes = new ArrayList<>();
    public static DesignerContext context;
    public static String projectName;
    public static String userName;
    JPanel gitStatusBar;
    JButton branchButton;
    Timer gitUserTimer;
    boolean toolBarInitialized;
    CommitPanel commitPanel;
    DockableFrame commitFrame;
    boolean commitFrameInitialized;
    Timer commitRefreshTimer;
    HistoryPanel historyPanel;
    DockableFrame historyFrame;
    boolean historyFrameInitialized;
    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);

        /*manager.addScriptModule(
            "system.git",
            new ClientScriptModule(),
            new PropertiesFileDocProvider()
        );*/
    }

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        super.startup(context, activationState);
        instance = this;
        DesignerHook.context = context;
        BundleUtil.get().addBundle("DesignerHook", getClass(), "DesignerHook");

        projectName = context.getProjectName();

        Optional<SessionInfo> sessionInfo = context.getResourceEditManager().getCurrentSessionInfo();
        userName = sessionInfo.isPresent() ? sessionInfo.get().getUsername() : "";

        boolean registered = rpc.isProjectRegistered(projectName);
        if (registered) {
            rpc.setupLocalRepo(projectName, userName);
            initStatusBar();
            initToolBar();
            initCommitPanel();
            initHistoryPanel();
        } else {
            initStatusBarUnregistered();
        }

    }

    private void initStatusBar(){
        StatusBar statusBar = context.getStatusBar();
        gitStatusBar = new JPanel();

        JLabel gitIconLabel = new JLabel(IconUtils.getIcon("/com/operametrix/ignition/git/icons/ic_git.svg"));
        gitIconLabel.setSize(35, 35);
        gitStatusBar.add(gitIconLabel);

        branchButton = new JButton();
        try {
            branchButton.setText(rpc.getCurrentBranch(projectName));
        } catch (Exception e) {
            branchButton.setText("unknown");
        }
        branchButton.setFont(branchButton.getFont().deriveFont(Font.BOLD));
        branchButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        branchButton.setContentAreaFilled(false);
        branchButton.setBorderPainted(false);
        branchButton.setFocusPainted(false);
        branchButton.setMargin(new Insets(0, 0, 0, 0));
        branchButton.addActionListener(e -> GitBaseAction.handleAction(GitBaseAction.GitActionType.BRANCH));
        gitStatusBar.add(branchButton);

        boolean userValid = rpc.isRegisteredUser(projectName, userName);
        String userIconPath = userValid ? "/com/operametrix/ignition/git/icons/ic_verified_user.svg" : "/com/operametrix/ignition/git/icons/ic_unregister_user.svg";
        JButton userButton = new JButton(IconUtils.getIcon(userIconPath));
        userButton.setToolTipText("Manage Git Credentials");
        userButton.setContentAreaFilled(false);
        userButton.setBorderPainted(false);
        userButton.setFocusPainted(false);
        userButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        userButton.setMargin(new Insets(0, 0, 0, 0));
        userButton.addActionListener(e -> GitActionManager.showCredentialsPopup(projectName, userName));
        gitStatusBar.add(userButton);

        gitStatusBar.add(new JLabel(userName));

        statusBar.addDisplay(gitStatusBar);

        gitUserTimer = new Timer(10000, e -> {
            boolean valid = rpc.isRegisteredUser(projectName, userName);
            String userIconPath1 = valid ? "/com/operametrix/ignition/git/icons/ic_verified_user.svg" : "/com/operametrix/ignition/git/icons/ic_unregister_user.svg";
            userButton.setIcon(IconUtils.getIcon(userIconPath1));

            try {
                branchButton.setText(rpc.getCurrentBranch(projectName));
            } catch (Exception ex) {
                branchButton.setText("unknown");
            }
        });

        gitUserTimer.start();
    }

    private void initStatusBarUnregistered() {
        StatusBar statusBar = context.getStatusBar();
        gitStatusBar = new JPanel();

        JLabel gitIconLabel = new JLabel(IconUtils.getIcon("/com/operametrix/ignition/git/icons/ic_git.svg"));
        gitIconLabel.setSize(35, 35);
        gitStatusBar.add(gitIconLabel);

        JButton notConfiguredButton = new JButton("Not configured");
        notConfiguredButton.setFont(notConfiguredButton.getFont().deriveFont(Font.ITALIC));
        notConfiguredButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        notConfiguredButton.setContentAreaFilled(false);
        notConfiguredButton.setBorderPainted(false);
        notConfiguredButton.setFocusPainted(false);
        notConfiguredButton.setMargin(new Insets(0, 0, 0, 0));
        notConfiguredButton.addActionListener(e -> GitActionManager.showInitRepoPopup(projectName, userName));
        gitStatusBar.add(notConfiguredButton);

        statusBar.addDisplay(gitStatusBar);
    }

    public void reinitializeAfterSetup() {
        if (gitUserTimer != null) {
            gitUserTimer.stop();
        }

        StatusBar statusBar = context.getStatusBar();
        if (gitStatusBar != null) {
            statusBar.removeDisplay(gitStatusBar);
        }

        cleanupCommitPanel();
        cleanupHistoryPanel();

        initStatusBar();
        initToolBar();
        initCommitPanel();
        initHistoryPanel();
    }

    private void initToolBar() {
        DockableBarManager toolBarManager = context.getToolbarManager();
        DesignerToolbar toolbar = new DesignerToolbar("Git", "DesignerHook.Toolbar.Name");
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.PUSH));
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.PULL));
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.COMMIT));
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.HISTORY));
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.EXPORT));
        toolbar.add(new GitBaseAction(GitBaseAction.GitActionType.REPO));

        toolBarManager.addDockableBar(toolbar);
        toolBarInitialized = true;
    }

    private void initCommitPanel() {
        commitPanel = new CommitPanel();
        GitActionManager.wireCommitPanel(commitPanel, projectName, userName);

        commitFrame = new DockableFrame("Commit",
                IconUtils.getIcon("/com/operametrix/ignition/git/icons/ic_commit.svg"));
        commitFrame.setTitle(BundleUtil.get().getStringLenient("DesignerHook.Commit.Title"));
        commitFrame.getContentPane().add(commitPanel);
        commitFrame.setPreferredSize(new Dimension(525, 400));
        commitFrame.setAutohideWidth(525);
        commitFrame.setDockedWidth(525);

        DockingManager dockingManager = context.getDockingManager();

        // Add frame initially hidden, then group it as a tab with the Project Browser
        commitFrame.setInitSide(DockContext.DOCK_SIDE_WEST);
        commitFrame.setInitIndex(0);
        commitFrame.setInitMode(DockContext.STATE_HIDDEN);
        dockingManager.addFrame(commitFrame);
        commitFrameInitialized = true;

        // Defer tab grouping until the Designer layout is fully initialized
        Timer dockTimer = new Timer(2000, e -> {
            DockableFrame projectBrowser = dockingManager.getFrame(PROJECT_BROWSER_KEY);
            dockingManager.showFrame(commitFrame.getKey());
            if (projectBrowser != null) {
                dockingManager.moveFrame(commitFrame.getKey(), PROJECT_BROWSER_KEY);
            }
            if (historyFrameInitialized) {
                dockingManager.showFrame(historyFrame.getKey());
                dockingManager.moveFrame(historyFrame.getKey(), PROJECT_BROWSER_KEY);
            }
            if (projectBrowser != null) {
                dockingManager.activateFrame(PROJECT_BROWSER_KEY);
            }
        });
        dockTimer.setRepeats(false);
        dockTimer.start();

        // Auto-refresh timer
        commitRefreshTimer = new Timer(15000, e -> refreshCommitPanel());
        commitRefreshTimer.start();

        // Initial refresh
        refreshCommitPanel();
    }

    public void refreshBranchLabel() {
        if (branchButton == null) return;
        try {
            String branch = rpc.getCurrentBranch(projectName);
            SwingUtilities.invokeLater(() -> branchButton.setText(branch));
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> branchButton.setText("unknown"));
        }
    }

    public void refreshCommitPanel() {
        if (commitPanel == null) return;
        new Thread(() -> {
            try {
                Dataset ds = rpc.getUncommitedChanges(projectName, userName);
                commitPanel.setChangesData(ds);
            } catch (Exception e) {
                // Silently ignore refresh errors
            }
        }).start();
    }

    private void cleanupCommitPanel() {
        if (commitRefreshTimer != null) {
            commitRefreshTimer.stop();
            commitRefreshTimer = null;
        }
        if (commitFrameInitialized) {
            try {
                DockingManager dockingManager = context.getDockingManager();
                dockingManager.removeFrame("Commit");
            } catch (Exception ignored) {
            }
            commitFrameInitialized = false;
        }
        commitPanel = null;
        commitFrame = null;
    }

    private void initHistoryPanel() {
        historyPanel = new HistoryPanel();
        GitActionManager.wireHistoryPanel(historyPanel, projectName, userName);

        historyFrame = new DockableFrame("History",
                IconUtils.getIcon("/com/operametrix/ignition/git/icons/ic_history.svg"));
        historyFrame.setTitle(BundleUtil.get().getStringLenient("DesignerHook.History.Title"));
        historyFrame.getContentPane().add(historyPanel);
        historyFrame.setPreferredSize(new Dimension(525, 400));
        historyFrame.setAutohideWidth(525);
        historyFrame.setDockedWidth(525);

        DockingManager dockingManager = context.getDockingManager();

        historyFrame.setInitSide(DockContext.DOCK_SIDE_WEST);
        historyFrame.setInitIndex(0);
        historyFrame.setInitMode(DockContext.STATE_HIDDEN);
        dockingManager.addFrame(historyFrame);
        historyFrameInitialized = true;

        // Initial data load
        refreshHistoryPanel();
    }

    public void refreshHistoryPanel() {
        if (historyPanel == null) return;
        new Thread(() -> {
            try {
                Dataset ds = rpc.getCommitHistory(projectName, 0, HistoryPanel.PAGE_SIZE);
                historyPanel.setData(ds, false);
            } catch (Exception e) {
                // Silently ignore refresh errors
            }
        }).start();
    }

    private void cleanupHistoryPanel() {
        if (historyFrameInitialized) {
            try {
                DockingManager dockingManager = context.getDockingManager();
                dockingManager.removeFrame("History");
            } catch (Exception ignored) {
            }
            historyFrameInitialized = false;
        }
        historyPanel = null;
        historyFrame = null;
    }

    @Override
    public void notifyProjectSaveStart(SaveContext save) {
        changes = context.getProject().getChanges();
        super.notifyProjectSaveStart(save);
    }

    @Override
    public void notifyProjectSaveDone(){
        super.notifyProjectSaveDone();
    }

    @Override
    public void shutdown() {
        super.shutdown();

        DockableBarManager toolBarManager = context.getToolbarManager();
        if (toolBarInitialized) {
            toolBarManager.removeDockableBar("Git");
        }

        StatusBar statusBar = context.getStatusBar();
        if (gitStatusBar != null) {
            statusBar.removeDisplay(gitStatusBar);
        }

        if (gitUserTimer != null) {
            gitUserTimer.stop();
        }

        cleanupCommitPanel();
        cleanupHistoryPanel();
    }
}
