package com.operametrix.ignition.git.actions;

import com.operametrix.ignition.git.InitProgressDialog;
import com.inductiveautomation.ignition.client.util.gui.ErrorUtil;
import com.inductiveautomation.ignition.common.resourcecollection.ChangeOperation;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceId;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.operametrix.ignition.git.DesignerHook.*;
import static com.operametrix.ignition.git.managers.GitActionManager.*;

public class GitBaseAction {
    private static final Logger logger = LoggerFactory.getLogger(GitBaseAction.class);

    public static void handleCommitAction(List<String> changes, String commitMessage, boolean amend) {
        try {
            rpc.commit(projectName, userName, changes, commitMessage, amend);
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }

    public static void handlePushAction(String remoteName) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before pushing.",
                    "Push", JOptionPane.WARNING_MESSAGE);
            return;
        }

        InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Pushing");
        new SwingWorker<Void, Void>() {
            private boolean rejected = false;

            @Override
            protected Void doInBackground() throws Exception {
                progress.setStatus("Pushing to " + remoteName + "...");
                try {
                    rpc.push(projectName, userName, remoteName, false, false, false);
                } catch (Exception ex) {
                    String exMsg = ex.getMessage() != null ? ex.getMessage() : "";
                    if (exMsg.contains("REJECTED_NONFASTFORWARD")) {
                        rejected = true;
                    } else {
                        throw ex;
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                progress.complete();
                try {
                    get();
                    if (rejected) {
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
                            handleForcePush(remoteName);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    ErrorUtil.showError(cause.getMessage(), cause);
                } finally {
                    if (instance != null) {
                        instance.refreshCommitPanel();
                        instance.refreshHistoryPanel();
                    }
                }
            }
        }.execute();
        progress.setVisible(true);
    }

    private static void handleForcePush(String remoteName) {
        InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Force Pushing");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                progress.setStatus("Force pushing to " + remoteName + "...");
                rpc.push(projectName, userName, remoteName, false, false, true);
                return null;
            }

            @Override
            protected void done() {
                progress.complete();
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    ErrorUtil.showError(cause.getMessage(), cause);
                } finally {
                    if (instance != null) {
                        instance.refreshCommitPanel();
                        instance.refreshHistoryPanel();
                    }
                }
            }
        }.execute();
        progress.setVisible(true);
    }

    public static void handlePullAction(String remoteName, boolean importTags, boolean importTheme, boolean importImages) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before pulling.",
                    "Pull", JOptionPane.WARNING_MESSAGE);
            return;
        }

        InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Pulling");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                progress.setStatus("Pulling from " + remoteName + "...");
                rpc.pull(projectName, userName, remoteName, importTags, importTheme, importImages);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    progress.setStatus("Syncing project to Designer...");
                    pullProjectFromGateway();
                    progress.complete();
                } catch (Exception e) {
                    progress.complete();
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String exMsg = cause.getMessage() != null ? cause.getMessage() : "";
                    if (exMsg.contains("MERGE_CONFLICT:")) {
                        String filesPart = exMsg.substring(exMsg.indexOf("MERGE_CONFLICT:") + "MERGE_CONFLICT:".length());
                        List<String> conflictingFiles = new ArrayList<>();
                        for (String f : filesPart.split("\n")) {
                            String trimmed = f.trim();
                            if (!trimmed.isEmpty()) {
                                conflictingFiles.add(trimmed);
                            }
                        }
                        showMergeConflictPopup(projectName, userName, conflictingFiles);
                    } else {
                        ErrorUtil.showError(cause.getMessage(), cause);
                    }
                } finally {
                    if (instance != null) {
                        instance.refreshCommitPanel();
                        instance.refreshHistoryPanel();
                    }
                }
            }
        }.execute();
        progress.setVisible(true);
    }

    public static void handleCheckoutAction(String branchName) {
        try {
            closeAllEditorTabs();
            rpc.checkoutBranch(projectName, branchName);
            pullProjectFromGateway();
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

                    // 8.3: TabbedResourceWorkspace.close now takes the resourcecollection
                    // ResourcePath (the resource model moved packages); looking it up with
                    // the old common.project.resource.ResourcePath threw NoSuchMethodException,
                    // leaving editors open so their stale content conflicted on pull.
                    Method closeMethod = tabbedClass.getMethod("close",
                            com.inductiveautomation.ignition.common.resourcecollection.ResourcePath.class,
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
            // 8.3 replacement for the removed DesignableProject.notifyPushComplete(List):
            // before pulling the gateway/branch state, discard any local Designer edits so
            // the pull adopts the gateway state wholesale instead of opening the Resolve
            // Conflicts dialog. On a deliberate branch checkout/pull the gateway is
            // authoritative; uncommitted work is preserved per-branch by the gateway-side
            // git stash/restore, so stale local Designer edits must not be treated as
            // conflicts.
            var project = context.getProject();
            List<ResourcePath> stalePaths = new ArrayList<>();
            for (ChangeOperation op : project.getChanges()) {
                ResourceId rid = ChangeOperation.getResourceIdFromChange(op);
                if (rid != null) {
                    stalePaths.add(rid.getResourcePath());
                }
            }
            for (ResourcePath p : stalePaths) {
                project.discardChanges(p);
            }

            // IgnitionDesigner.updateProject() (public) performs the full gateway pull +
            // Designer refresh — the 8.3 successor to the private no-arg pullAndResolve()
            // the pre-8.3 module reflected into (its signature changed in 8.3). Invoked
            // reflectively to avoid a compile-time dependency on the concrete frame class.
            java.awt.Frame frame = context.getFrame();
            Method updateProject = frame.getClass().getMethod("updateProject");
            updateProject.invoke(frame);

            // Best-effort repair after the in-place refresh (see repairModuleFolderNodes).
            repairModuleFolderNodes();
        } catch (Exception e) {
            logger.error("Failed to pull project from gateway", e);
            ErrorUtil.showError("Git updated the project on the gateway, but the Designer could not "
                    + "refresh automatically. The project view may be out of sync — update or reopen "
                    + "the project to load the latest state.", e);
        }
    }

    /**
     * Best-effort workaround for an Ignition Designer limitation exposed by in-place project
     * refresh. When {@code updateProject()} first introduces a module's resources into an
     * already-open project (e.g. a git pull/checkout/merge that adds Perspective content),
     * the incremental tree-update path can leave that module's namespace node
     * (e.g. {@code com.inductiveautomation.perspective}) registered as a <em>non-folder</em>.
     * A later save then fails inside Perspective's {@code SessionPropsAdapter} with
     * "Unable to create folder path, found non-folder in the way at
     * 'com.inductiveautomation.perspective'". Ignition's own {@code validateFolderStructure()}
     * self-repair explicitly skips module-level nodes, so it never heals this; only a full
     * project reload (constructor path) otherwise fixes it.
     *
     * <p>Here we mend just the broken node in place: {@code DesignerProjectTreeImpl}
     * (the concrete {@code DesignableProject}) keeps its module roots in a private
     * {@code moduleNodes} map; any entry that is not a folder is converted to one via the
     * tree's own {@code makeFolder}. All access is reflective — these are Designer internals,
     * not public API — and any failure is swallowed so the plain {@code updateProject()}
     * refresh remains the fallback (worst case: the pre-existing reopen-to-fix behaviour).
     */
    private static void repairModuleFolderNodes() {
        try {
            Object tree = context.getProject(); // DesignerProjectTreeImpl implements DesignableProject
            Class<?> treeClass = tree.getClass();

            java.lang.reflect.Field moduleNodesField = treeClass.getDeclaredField("moduleNodes");
            moduleNodesField.setAccessible(true);
            java.util.Map<?, ?> moduleNodes = (java.util.Map<?, ?>) moduleNodesField.get(tree);
            if (moduleNodes == null || moduleNodes.isEmpty()) {
                return;
            }

            Method makeFolder = treeClass.getDeclaredMethod("makeFolder", ResourcePath.class);
            makeFolder.setAccessible(true);

            boolean repairedAny = false;
            for (Object node : moduleNodes.values()) {
                if (node == null) {
                    continue;
                }
                Class<?> nodeClass = node.getClass();
                Method isFolder = nodeClass.getDeclaredMethod("isFolder");
                isFolder.setAccessible(true);
                if (Boolean.TRUE.equals(isFolder.invoke(node))) {
                    continue;
                }
                Method getPath = nodeClass.getDeclaredMethod("getPath");
                getPath.setAccessible(true);
                Object path = getPath.invoke(node);

                Object folderResource = makeFolder.invoke(tree, path);
                Method update = nodeClass.getDeclaredMethod("update",
                        com.inductiveautomation.ignition.common.resourcecollection.Resource.class);
                update.setAccessible(true);
                update.invoke(node, folderResource);
                repairedAny = true;
                logger.info("Repaired non-folder module node '{}' after in-place project refresh.", path);
            }

            if (repairedAny) {
                // Propagate the fix through the resource collection's effective state.
                Method updateEffectiveState = null;
                for (Class<?> c = treeClass; c != null && updateEffectiveState == null; c = c.getSuperclass()) {
                    try {
                        updateEffectiveState = c.getDeclaredMethod("updateEffectiveState");
                    } catch (NoSuchMethodException ignore) {
                        // walk up
                    }
                }
                if (updateEffectiveState != null) {
                    updateEffectiveState.setAccessible(true);
                    updateEffectiveState.invoke(tree);
                }
            }
        } catch (Throwable t) {
            logger.warn("Could not repair module folder nodes after project refresh; a save may fail "
                    + "with a 'non-folder in the way' error until the project is reopened: {}", t.toString());
        }
    }

    public static void handleCreateBranchAction(String branchName) {
        try {
            rpc.createBranch(projectName, branchName);
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
            return;
        }
        handleCheckoutAction(branchName);
    }

    public static void handleDeleteBranchAction(String branchName) {
        try {
            rpc.deleteBranch(projectName, branchName);
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }

    public static void handleCheckoutCommitAction(String commitHash, String shortHash) {
        int choice = JOptionPane.showConfirmDialog(context.getFrame(),
                "Check out commit " + shortHash + "?\n\n"
                        + "This will put the repository in 'detached HEAD' state.\n"
                        + "Any new commits will not belong to a branch.\n"
                        + "To keep changes, create a new branch first.",
                "Checkout Commit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            closeAllEditorTabs();
            rpc.checkoutCommit(projectName, commitHash);
            pullProjectFromGateway();
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

    public static void handleFetchAction(String remoteName) {
        if (!rpc.hasRemoteRepository(projectName)) {
            JOptionPane.showMessageDialog(context.getFrame(),
                    "No remote repository configured. Add a remote before fetching.",
                    "Fetch", JOptionPane.WARNING_MESSAGE);
            return;
        }

        InitProgressDialog progress = new InitProgressDialog(context.getFrame(), "Fetching");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                progress.setStatus("Fetching from " + remoteName + "...");
                rpc.fetch(projectName, userName, remoteName);
                return null;
            }

            @Override
            protected void done() {
                progress.complete();
                try {
                    get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    ErrorUtil.showError(cause.getMessage(), cause);
                } finally {
                    if (instance != null) {
                        instance.refreshHistoryPanel();
                    }
                }
            }
        }.execute();
        progress.setVisible(true);
    }

    public static void handleRevertCommitAction(String commitHash, String shortHash, String message) {
        int choice = JOptionPane.showConfirmDialog(context.getFrame(),
                "Revert commit " + shortHash + "?\n\n\"" + message + "\"\n\n"
                        + "This will create a new commit that undoes the changes.",
                "Revert Commit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            rpc.revertCommit(projectName, commitHash);
            pullProjectFromGateway();
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        } finally {
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        }
    }

    public static void handleBranchAction() {
        try {
            showBranchPopup(projectName, userName);
            if (instance != null) {
                instance.refreshCommitPanel();
                instance.refreshHistoryPanel();
            }
        } catch (Exception ex) {
            ErrorUtil.showError(ex);
        }
    }
}
