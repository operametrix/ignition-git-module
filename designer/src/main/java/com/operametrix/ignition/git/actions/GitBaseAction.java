package com.operametrix.ignition.git.actions;

import com.operametrix.ignition.git.utils.IconUtils;
import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.BundleUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.operametrix.ignition.git.DesignerHook.*;
import static com.operametrix.ignition.git.managers.GitActionManager.*;

public class GitBaseAction extends BaseAction {
    private static final Logger logger = LoggerFactory.getLogger(GitBaseAction.class);

    public enum GitActionType {
        PULL(
            "DesignerHook.Actions.Pull",
            "/com/operametrix/ignition/git/icons/ic_pull.svg"
        ),
        PUSH(
            "DesignerHook.Actions.Push",
            "/com/operametrix/ignition/git/icons/ic_push.svg"
        ),
        COMMIT(
            "DesignerHook.Actions.Commit",
            "/com/operametrix/ignition/git/icons/ic_commit.svg"
        ),
        EXPORT(
            "DesignerHook.Actions.ExportGatewayConfig",
            "/com/operametrix/ignition/git/icons/ic_folder.svg"
        ),

        REPO(
            "DesignerHook.Actions.Repo",
            "/com/operametrix/ignition/git/icons/ic_git.svg"
        ),

        BRANCH(
            "DesignerHook.Actions.Branch",
            "/com/operametrix/ignition/git/icons/ic_branch.svg"
        ),

        HISTORY(
            "DesignerHook.Actions.History",
            "/com/operametrix/ignition/git/icons/ic_history.svg"
        );

        private final String baseBundleKey;
        private final String resourcePath;

        GitActionType(String baseBundleKey, String resourcePath) {
            this.baseBundleKey = baseBundleKey;
            this.resourcePath = resourcePath;
        }

        public Icon getIcon() {
            return IconUtils.getIcon(resourcePath);
        }
    }

    GitActionType type;

    public GitBaseAction(GitActionType type) {
        super(type.baseBundleKey, type.getIcon());
        this.type = type;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        handleAction(type);
    }

    // Todo : Find a way to refactor with handleAction
    public static void handleCommitAction(List<String> changes, String commitMessage, boolean amend) {
        String message = BundleUtil.get().getStringLenient(GitActionType.COMMIT.baseBundleKey + ".ConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            rpc.commit(projectName, userName, changes, commitMessage, amend);
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }

    public static void handlePushAction() {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before pushing.",
                    "Push", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String message = BundleUtil.get().getStringLenient(GitActionType.PUSH.baseBundleKey + ".ConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            rpc.push(projectName, userName, false, false, false);
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
        } catch (Exception ex) {
            String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
            if (exMsg.contains("REJECTED_NONFASTFORWARD")) {
                int choice = JOptionPane.showConfirmDialog(
                        context.getFrame(),
                        "Push was rejected because the remote contains commits not present locally.\n"
                                + "This typically happens after amending a pushed commit.\n\n"
                                + "Do you want to force push? This will overwrite the remote branch.",
                        "Push Rejected",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        rpc.push(projectName, userName, false, false, true);
                        SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
                    } catch (Exception ex2) {
                        ErrorUtil.showError(ex2);
                    }
                }
            } else {
                ErrorUtil.showError(ex);
            }
        } finally {
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        }
    }

    public static void handlePullAction(boolean importTags, boolean importTheme, boolean importImages) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before pulling.",
                    "Pull", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String message = BundleUtil.get().getStringLenient(GitActionType.PULL.baseBundleKey + ".ConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            rpc.pull(projectName, userName, importTags, importTheme, importImages);
            pullProjectFromGateway();
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        } finally {
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        }
    }

    public static void handleCheckoutAction(String branchName) {
        String message = BundleUtil.get().getStringLenient(GitActionType.BRANCH.baseBundleKey + ".CheckoutConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            closeAllEditorTabs();
            rpc.checkoutBranch(projectName, branchName);
            pullProjectFromGateway();
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        } finally {
            if (instance != null) {
                instance.refreshBranchLabel();
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        }
    }

    private static void closeAllEditorTabs() {
        try {
            java.awt.Frame frame = context.getFrame();
            Method getWorkspace = frame.getClass().getMethod("getWorkspace");
            Object workspaceManager = getWorkspace.invoke(frame);

            Method getCount = workspaceManager.getClass().getMethod("getWorkspaceCount");
            Method getWs = workspaceManager.getClass().getMethod("getWorkspace", int.class);

            int count = (int) getCount.invoke(workspaceManager);
            for (int i = 0; i < count; i++) {
                Object ws = getWs.invoke(workspaceManager, i);
                Class<?> tabbedClass;
                try {
                    tabbedClass = Class.forName(
                            "com.inductiveautomation.ignition.designer.tabbedworkspace.TabbedResourceWorkspace");
                } catch (ClassNotFoundException e) {
                    return;
                }
                if (tabbedClass.isInstance(ws)) {
                    Method getEditors = tabbedClass.getMethod("getEditors");
                    Collection<?> editors = (Collection<?>) getEditors.invoke(ws);
                    List<?> editorsCopy = new ArrayList<>(editors);

                    Method closeMethod = tabbedClass.getMethod("close",
                            com.inductiveautomation.ignition.common.project.resource.ResourcePath.class,
                            boolean.class);

                    for (Object editor : editorsCopy) {
                        Method getResourcePath = editor.getClass().getMethod("getResourcePath");
                        Object path = getResourcePath.invoke(editor);
                        closeMethod.invoke(ws, path, false);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to close editor tabs", e);
        }
    }

    public static void pullProjectFromGateway() {
        try {
            java.awt.Frame frame = context.getFrame();
            Class<?> frameClass = frame.getClass();

            // Clear all local "dirty" flags so pullAndResolve() won't detect conflicts.
            // After a branch checkout the gateway has the correct state; any local
            // modifications are stale and must not trigger conflict resolution.
            Object project = context.getProject();
            Method getChanges = project.getClass().getMethod("getChanges");
            List<?> changes = (List<?>) getChanges.invoke(project);
            if (changes != null && !changes.isEmpty()) {
                Method notifyPushComplete = project.getClass().getMethod("notifyPushComplete", List.class);
                notifyPushComplete.invoke(project, changes);
            }

            // Call pullAndResolve() directly, skipping commitAll() which would
            // push stale open-editor content back to the gateway and cause conflicts
            Method pullAndResolve = frameClass.getDeclaredMethod("pullAndResolve");
            pullAndResolve.setAccessible(true);
            pullAndResolve.invoke(frame);

            // Notify module hooks that the update cycle is complete
            Method notifyModules = frameClass.getDeclaredMethod("notifyModulesProjectSaveDone");
            notifyModules.setAccessible(true);
            notifyModules.invoke(frame);

            // Clear the pending-update state so the "Merge" button disappears
            Field remField = frameClass.getDeclaredField("resourceEditManager");
            remField.setAccessible(true);
            Object rem = remField.get(frame);
            Method onComplete = rem.getClass().getMethod("onDesignerUpdateComplete");
            onComplete.invoke(rem);
        } catch (Exception e) {
            logger.error("Failed to pull project from gateway", e);
        }
    }

    public static void handleCreateBranchAction(String branchName, String startPoint) {
        String message = BundleUtil.get().getStringLenient(GitActionType.BRANCH.baseBundleKey + ".CreateConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            rpc.createBranch(projectName, branchName, startPoint);
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }

    public static void handleDeleteBranchAction(String branchName) {
        String message = BundleUtil.get().getStringLenient(GitActionType.BRANCH.baseBundleKey + ".DeleteConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;

        try {
            rpc.deleteBranch(projectName, branchName);
            SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }

    public static void handleAction(GitActionType type) {
        String message = BundleUtil.get().getStringLenient(type.baseBundleKey + ".ConfirmMessage");
        int messageType = JOptionPane.INFORMATION_MESSAGE;
        boolean confirmPopup = Boolean.TRUE;

        try {
            switch (type) {
                case PULL:
                    confirmPopup = Boolean.FALSE;
                    showPullPopup(projectName, userName);
                    break;
                case PUSH:
                    confirmPopup = Boolean.FALSE;
                    handlePushAction();
                    break;
                case COMMIT:
                    confirmPopup = Boolean.FALSE;
                    showCommitPopup(projectName, userName);
                    break;
                case EXPORT:
                    rpc.exportConfig(projectName);
                    break;
                case REPO:
                    openRepositoryLink(projectName);
                    break;
                case BRANCH:
                    confirmPopup = Boolean.FALSE;
                    showBranchPopup(projectName, userName);
                    break;
                case HISTORY:
                    confirmPopup = Boolean.FALSE;
                    showHistoryPopup(projectName);
                    break;
            }
            if(confirmPopup) SwingUtilities.invokeLater(new Thread(() -> showConfirmPopup(message, messageType)));
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }
}
