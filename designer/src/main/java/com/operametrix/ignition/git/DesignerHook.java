package com.operametrix.ignition.git;

import com.operametrix.ignition.git.actions.GitBaseAction;
import com.operametrix.ignition.git.ext.DesignerFeature;
import com.operametrix.ignition.git.ext.DesignerFeatures;
import com.operametrix.ignition.git.managers.GitActionManager;
import com.operametrix.ignition.git.utils.IconUtils;
import com.inductiveautomation.ignition.client.gateway_interface.GatewayConnection;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.Dataset;
import com.inductiveautomation.ignition.common.ConcurrencySessionInfo;
import com.inductiveautomation.ignition.common.licensing.LicenseState;

import com.inductiveautomation.ignition.designer.gui.StatusBar;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;

import com.jidesoft.docking.DockContext;
import com.jidesoft.docking.DockableFrame;
import com.jidesoft.docking.DockingManager;
import com.jidesoft.docking.event.DockableFrameEvent;
import com.jidesoft.docking.event.DockableFrameAdapter;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class DesignerHook extends AbstractDesignerModuleHook {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DesignerHook.class);
    private static final String PROJECT_BROWSER_KEY = "Project Browser";

    public static DesignerHook instance;
    public static GitScriptInterface rpc = GatewayConnection.getRpcInterface(
            GitScriptInterface.SERIALIZER,
            "com.operametrix.ignition.git",
            GitScriptInterface.class
    );
    public static DesignerContext context;
    public static String projectName;
    public static String userName;
    JPanel gitStatusBar;
    JButton branchButton;
    Timer gitUserTimer;

    CommitPanel commitPanel;
    DockableFrame commitFrame;
    /** Set when the operator closes the panel; cleared when it is reopened (View menu). */
    private final AtomicBoolean commitDismissed = new AtomicBoolean();
    boolean commitFrameInitialized;
    Timer commitRefreshTimer;
    HistoryPanel historyPanel;
    DockableFrame historyFrame;
    private final AtomicBoolean historyDismissed = new AtomicBoolean();
    boolean historyFrameInitialized;
    Timer panelVisibilityTimer;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        super.startup(context, activationState);
        instance = this;
        DesignerHook.context = context;
        BundleUtil.get().addBundle("DesignerHook", getClass(), "DesignerHook");

        projectName = context.getProjectName();

        Optional<ConcurrencySessionInfo> sessionInfo = context.getResourceEditManager().getCurrentSessionInfo();
        userName = sessionInfo.map(ConcurrencySessionInfo::username).orElse("");

        boolean registered = rpc.isProjectRegistered(projectName);
        if (registered) {
            rpc.setupLocalRepo(projectName, userName);
            initStatusBar();
            initCommitPanel();
            initHistoryPanel();
        } else {
            initStatusBarUnregistered();
        }

        // Start add-on features (proprietary DevOps subproject, if present) after the open-core UI is
        // built. No-op on the free edition (none discovered); each feature gates itself on the license.
        for (DesignerFeature feature : DesignerFeatures.all()) {
            try {
                feature.startup(context, activationState);
            } catch (Exception e) {
                logger.warn("Designer feature '" + feature.id() + "' failed during startup()", e);
            }
        }
    }

    private void initStatusBar(){
        StatusBar statusBar = context.getStatusBar();
        gitStatusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        // Branch button: git icon + branch name
        branchButton = new JButton();
        branchButton.setIcon(IconUtils.getIcon("ic_git"));
        try {
            branchButton.setText(rpc.getCurrentBranch(projectName));
        } catch (Exception e) {
            branchButton.setText("unknown");
        }
        branchButton.setFont(branchButton.getFont().deriveFont(Font.BOLD));
        styleStatusBarButton(branchButton);
        branchButton.addActionListener(e -> GitBaseAction.handleBranchAction());
        gitStatusBar.add(branchButton);

        // Remotes button: filled cloud icon + "Remotes" label
        JButton remotesButton = new JButton("Remotes", IconUtils.getIcon("ic_cloud_filled"));
        remotesButton.setToolTipText("Manage Remotes");
        styleStatusBarButton(remotesButton);
        remotesButton.addActionListener(e -> GitActionManager.showRemotesPopup(projectName, userName));
        gitStatusBar.add(remotesButton);

        // User button: user icon + username
        boolean userValid = rpc.isRegisteredUser(projectName, userName);
        String userIconPath = userValid ? "ic_verified_user" : "ic_unregister_user";
        JButton userButton = new JButton(userName, IconUtils.getIcon(userIconPath));
        userButton.setToolTipText("Manage Git Credentials");
        styleStatusBarButton(userButton);
        userButton.addActionListener(e -> GitActionManager.showCredentialsPopup(projectName, userName));
        gitStatusBar.add(userButton);

        statusBar.addDisplay(gitStatusBar);

        // A Swing timer fires on the EDT, so these gateway round-trips would freeze the Designer
        // every 10 seconds against a slow or unreachable gateway. Poll on a background thread and
        // apply the result on the EDT; the in-flight guard stops slow polls from stacking up.
        AtomicBoolean statusPollRunning = new AtomicBoolean();
        gitUserTimer = new Timer(10000, e -> {
            if (!statusPollRunning.compareAndSet(false, true)) {
                return;
            }
            new Thread(() -> {
                try {
                    boolean valid;
                    try {
                        valid = rpc.isRegisteredUser(projectName, userName);
                    } catch (Exception ex) {
                        valid = false;
                    }
                    String branch;
                    try {
                        branch = rpc.getCurrentBranch(projectName);
                    } catch (Exception ex) {
                        branch = "unknown";
                    }
                    String iconPath = valid
                            ? "ic_verified_user"
                            : "ic_unregister_user";
                    String branchText = branch;
                    SwingUtilities.invokeLater(() -> {
                        userButton.setIcon(IconUtils.getIcon(iconPath));
                        branchButton.setText(branchText);
                    });
                } finally {
                    statusPollRunning.set(false);
                }
            }, "git-status-poll").start();
        });

        gitUserTimer.start();
    }

    private void initStatusBarUnregistered() {
        StatusBar statusBar = context.getStatusBar();
        gitStatusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        // Git icon + "Configure" — opens init wizard
        JButton notConfiguredButton = new JButton("Configure",
                IconUtils.getIcon("ic_git"));
        styleStatusBarButton(notConfiguredButton);
        notConfiguredButton.addActionListener(e -> GitActionManager.showInitRepoPopup(projectName, userName));
        gitStatusBar.add(notConfiguredButton);

        // User button — manage credentials before init
        boolean hasCredentials = hasUserCredentials(userName);
        String userIconPath = hasCredentials
                ? "ic_verified_user"
                : "ic_unregister_user";
        JButton userButton = new JButton(userName, IconUtils.getIcon(userIconPath));
        userButton.setToolTipText("Manage Git Credentials");
        styleStatusBarButton(userButton);
        userButton.addActionListener(e -> GitActionManager.showCredentialsPopup(projectName, userName));
        gitStatusBar.add(userButton);

        statusBar.addDisplay(gitStatusBar);

        // Poll to update the user icon when credentials are added/removed. Off the EDT for the same
        // reason as the registered status bar: hasUserCredentials makes two gateway calls.
        AtomicBoolean credentialPollRunning = new AtomicBoolean();
        gitUserTimer = new Timer(10000, e -> {
            if (!credentialPollRunning.compareAndSet(false, true)) {
                return;
            }
            new Thread(() -> {
                try {
                    boolean hasCreds = hasUserCredentials(userName);
                    String iconPath = hasCreds
                            ? "ic_verified_user"
                            : "ic_unregister_user";
                    SwingUtilities.invokeLater(() -> userButton.setIcon(IconUtils.getIcon(iconPath)));
                } finally {
                    credentialPollRunning.set(false);
                }
            }, "git-credentials-poll").start();
        });
        gitUserTimer.start();
    }

    private boolean hasUserCredentials(String userName) {
        try {
            Dataset sshKeys = rpc.listUserSshKeys(userName);
            if (sshKeys != null && sshKeys.getRowCount() > 0) return true;
            Dataset httpsCreds = rpc.listUserHttpsCredentials(userName);
            if (httpsCreds != null && httpsCreds.getRowCount() > 0) return true;
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private static void styleStatusBarButton(JButton button) {
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBorderPainted(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
            }
        });
    }

    public void reinitializeAfterSetup() {
        if (gitUserTimer != null) {
            gitUserTimer.stop();
        }

        StatusBar statusBar = context.getStatusBar();
        if (gitStatusBar != null) {
            statusBar.removeDisplay(gitStatusBar);
        }

        if (panelVisibilityTimer != null) {
            panelVisibilityTimer.stop();
            panelVisibilityTimer = null;
        }

        cleanupCommitPanel();
        cleanupHistoryPanel();

        initStatusBar();
        initCommitPanel();
        initHistoryPanel();
    }

    private void initCommitPanel() {
        commitPanel = new CommitPanel();
        GitActionManager.wireCommitPanel(commitPanel, projectName, userName);

        commitFrame = new DockableFrame("Commit",
                IconUtils.getIcon("ic_commit"));
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
        respectClose(commitFrame, commitDismissed, dockingManager);
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

        // Keep Commit/History panels visible across workspace switches.
        // Not all workspaces fire JIDE docking events for custom frames,
        // so we poll at a short interval and only act when needed.
        panelVisibilityTimer = new Timer(1000, e -> {
            if (!commitFrameInitialized && !historyFrameInitialized) return;
            DockingManager dm = context.getDockingManager();
            boolean needsRestore = false;
            if (commitFrameInitialized && commitFrame != null && !commitDismissed.get()
                    && isMissing(dm.getFrame(commitFrame.getKey()))) {
                needsRestore = true;
            }
            if (historyFrameInitialized && historyFrame != null && !historyDismissed.get()
                    && isMissing(dm.getFrame(historyFrame.getKey()))) {
                needsRestore = true;
            }
            if (needsRestore) {
                ensurePanelsVisible();
            }
        });
        panelVisibilityTimer.start();

        // Auto-refresh timer
        commitRefreshTimer = new Timer(15000, e -> refreshCommitPanel());
        commitRefreshTimer.start();

        // Initial refresh
        refreshCommitPanel();
    }

    /** Refreshes the status bar's branch label; the lookup runs off the EDT like the other refreshes. */
    public void refreshBranchLabel() {
        if (branchButton == null) return;
        new Thread(() -> {
            String branch;
            try {
                branch = rpc.getCurrentBranch(projectName);
            } catch (Exception e) {
                branch = "unknown";
            }
            String text = branch;
            SwingUtilities.invokeLater(() -> branchButton.setText(text));
        }, "git-branch-label").start();
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
                IconUtils.getIcon("ic_history"));
        historyFrame.setTitle(BundleUtil.get().getStringLenient("DesignerHook.History.Title"));
        historyFrame.getContentPane().add(historyPanel);
        historyFrame.setPreferredSize(new Dimension(525, 400));
        historyFrame.setAutohideWidth(525);
        historyFrame.setDockedWidth(525);

        DockingManager dockingManager = context.getDockingManager();

        historyFrame.setInitSide(DockContext.DOCK_SIDE_WEST);
        historyFrame.setInitIndex(0);
        historyFrame.setInitMode(DockContext.STATE_HIDDEN);
        respectClose(historyFrame, historyDismissed, dockingManager);
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

    /**
     * Whether a panel has actually gone missing and should be restored.
     *
     * <p>{@code isHidden()} catches JIDE's logical hidden state and {@code !isDisplayable()} catches
     * frames detached from the Swing hierarchy by a workspace switch (some workspaces do that without
     * going through JIDE's hidden API).
     *
     * <p>A <em>minimised</em> frame is neither: JIDE collapses it to a side tab in auto-hide state,
     * which takes its content out of the displayed hierarchy, so {@code isDisplayable()} is false
     * while nothing is wrong. Restoring it there re-opened the panel a second later, every second,
     * which is what made minimised panels flicker.
     */
    private static boolean isMissing(DockableFrame f) {
        if (f == null) {
            return true;
        }
        if (f.isAutohide() || f.isAutohideShowing()) {
            return false;
        }
        return f.isHidden() || !f.isDisplayable();
    }

    private void ensurePanelsVisible() {
        if (!commitFrameInitialized && !historyFrameInitialized) return;
        DockingManager dm = context.getDockingManager();

        if (commitFrameInitialized && commitFrame != null && !commitDismissed.get()) {
            if (dm.getFrame(commitFrame.getKey()) == null) {
                dm.addFrame(commitFrame);
            }
            dm.showFrame(commitFrame.getKey());
        }
        if (historyFrameInitialized && historyFrame != null && !historyDismissed.get()) {
            if (dm.getFrame(historyFrame.getKey()) == null) {
                dm.addFrame(historyFrame);
            }
            dm.showFrame(historyFrame.getKey());
        }
    }

    /**
     * Make the panel's close button mean what it says: mark it dismissed, then hide it. The repeating
     * visibility poll skips a dismissed panel, so it stays closed until the operator brings it back
     * through the Designer's View menu, at which point the dismissal is cleared.
     *
     * <p>The close button is intercepted rather than the frame's hidden event, because that event
     * also fires for hides this module and the workspace machinery perform themselves — which would
     * make a workspace switch look like a deliberate close.
     */
    private static void respectClose(DockableFrame frame, AtomicBoolean dismissed, DockingManager dm) {
        String key = frame.getKey();
        frame.setCloseAction(new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dismissed.set(true);
                dm.hideFrame(key);
            }
        });
        frame.addDockableFrameListener(new DockableFrameAdapter() {
            @Override
            public void dockableFrameShown(DockableFrameEvent e) {
                dismissed.set(false);
            }
        });
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
    public void notifyActivationStateChanged(LicenseState licenseState) {
        super.notifyActivationStateChanged(licenseState);
        for (DesignerFeature feature : DesignerFeatures.all()) {
            try {
                feature.licenseChanged(context, licenseState);
            } catch (Exception e) {
                logger.warn("Designer feature '" + feature.id() + "' failed handling license change", e);
            }
        }
    }

    @Override
    public void notifyProjectSaveDone(){
        super.notifyProjectSaveDone();
    }

    @Override
    public void shutdown() {
        super.shutdown();

        for (DesignerFeature feature : DesignerFeatures.all()) {
            try {
                feature.shutdown();
            } catch (Exception e) {
                logger.warn("Designer feature '" + feature.id() + "' failed during shutdown()", e);
            }
        }

        StatusBar statusBar = context.getStatusBar();
        if (gitStatusBar != null) {
            statusBar.removeDisplay(gitStatusBar);
        }

        if (gitUserTimer != null) {
            gitUserTimer.stop();
        }

        if (panelVisibilityTimer != null) {
            panelVisibilityTimer.stop();
            panelVisibilityTimer = null;
        }

        cleanupCommitPanel();
        cleanupHistoryPanel();
    }
}
