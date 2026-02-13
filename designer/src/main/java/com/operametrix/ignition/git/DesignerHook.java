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
    SourceControlPanel sourceControlPanel;
    DockableFrame sourceControlFrame;
    boolean sourceControlFrameInitialized;
    Timer sourceControlRefreshTimer;
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
            initSourceControlPanel();
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

        cleanupSourceControlPanel();

        initStatusBar();
        initToolBar();
        initSourceControlPanel();
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

    private void initSourceControlPanel() {
        sourceControlPanel = new SourceControlPanel();
        GitActionManager.wireSourceControlPanel(sourceControlPanel, projectName, userName);

        sourceControlFrame = new DockableFrame("GitSourceControl",
                IconUtils.getIcon("/com/operametrix/ignition/git/icons/ic_git.svg"));
        sourceControlFrame.setTitle(BundleUtil.get().getStringLenient("DesignerHook.SourceControl.Title"));
        sourceControlFrame.getContentPane().add(sourceControlPanel);
        sourceControlFrame.setPreferredSize(new Dimension(525, 400));
        sourceControlFrame.setAutohideWidth(525);
        sourceControlFrame.setDockedWidth(525);

        DockingManager dockingManager = context.getDockingManager();
        sourceControlFrame.setInitSide(DockContext.DOCK_SIDE_WEST);
        sourceControlFrame.setInitIndex(0);
        sourceControlFrame.setInitMode(DockContext.STATE_AUTOHIDE);
        dockingManager.addFrame(sourceControlFrame);
        sourceControlFrameInitialized = true;

        // Auto-refresh timer
        sourceControlRefreshTimer = new Timer(15000, e -> refreshSourceControlPanel());
        sourceControlRefreshTimer.start();

        // Initial refresh
        refreshSourceControlPanel();
    }

    public void refreshSourceControlPanel() {
        if (sourceControlPanel == null) return;
        new Thread(() -> {
            try {
                Dataset ds = rpc.getUncommitedChanges(projectName, userName);
                sourceControlPanel.setChangesData(ds);
            } catch (Exception e) {
                // Silently ignore refresh errors
            }
        }).start();
    }

    private void cleanupSourceControlPanel() {
        if (sourceControlRefreshTimer != null) {
            sourceControlRefreshTimer.stop();
            sourceControlRefreshTimer = null;
        }
        if (sourceControlFrameInitialized) {
            try {
                DockingManager dockingManager = context.getDockingManager();
                dockingManager.removeFrame("GitSourceControl");
            } catch (Exception ignored) {
            }
            sourceControlFrameInitialized = false;
        }
        sourceControlPanel = null;
        sourceControlFrame = null;
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

        cleanupSourceControlPanel();
    }
}
