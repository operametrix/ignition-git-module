package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.operametrix.ignition.git.records.GitConfigRemoteRecord;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.operametrix.ignition.git.GatewayHook.getContext;

/**
 * Gateway data-directory ("config-as-code") versioning. A single git repo rooted at the Ignition
 * data directory tracks gateway config (primarily {@code <dataDir>/config/}); the per-project repos
 * under {@code <dataDir>/projects/} are versioned separately and are excluded here via {@code .gitignore}.
 *
 * <p>This is a thin orchestration layer over the static {@link GitManager} primitives — all of which
 * take a working-dir {@link Path} and are reused directly against {@link #dataDir()}. The only
 * config-specific logic lives here: init/{@code .gitignore}, a plain porcelain status (the
 * project-resource actor/metadata helpers in {@link GitManager} must NOT be applied to config files),
 * restore-to-version, and applying restored files to the running gateway via {@code requestScan()}.
 */
public class DataDirGitManager {
    private static final LoggerEx logger = LoggerEx.newBuilder().build(DataDirGitManager.class);

    /**
     * Serializes our own git mutations and status reads so a {@code /dirty} poll never observes a
     * half-staged index while the gateway concurrently rewrites {@code config/} files.
     */
    private static final Object DATA_DIR_LOCK = new Object();

    /** Config-as-code lives under {@code config/}; the {@code .gitignore} sits at the repo root. */
    private static final String[] SCOPE = { "config", ".gitignore" };

    /** Based on Inductive Automation's version-control-guide template, plus {@code projects/}. */
    private static final List<String> GITIGNORE_LINES = List.of(
            "# Ignition data-directory config-as-code — managed by the Git module",
            "**/db/*",
            "**/metricsdb/*",
            "**/autobackup/*",
            "**/db_backup_sqlite.idb",
            "**/valueStore.idb",
            "**/jar-cache/*",
            "**/request*",
            "**/response*",
            "*.tmp",
            "*.bak",
            "**/var",
            "*.log",
            "**/logs",
            "**/certificates/*",
            "**/keystore/",
            "**/config/local",
            "**/config/resources/local",
            "**/.container-init.conf",
            "**/conversion-report.txt",
            "**.digest.json",
            "**/migration-log-*.md",
            "**/.resources/",
            "**/.alarms_*",
            "",
            "# Per-project resources are versioned separately by this module's per-project repos",
            "projects/"
    );

    /** A single uncommitted config change. {@code type} ∈ ADDED | MODIFIED | DELETED | UNTRACKED. */
    public record ConfigChange(String path, String type) {}

    public static Path dataDir() {
        return GitManager.getDataFolderPath();
    }

    /** v1 "is config versioning enabled" signal — the repo state lives entirely in {@code .git}. */
    public static boolean isInitialized() {
        return Files.exists(dataDir().resolve(".git"));
    }

    /**
     * First-run initialization: {@code git init} at the data dir, write {@code .gitignore}, stage all
     * non-ignored files, and make the baseline commit. Explicit (never auto-run at startup).
     */
    public static void initRepo(String actingUser) {
        synchronized (DATA_DIR_LOCK) {
            if (isInitialized()) {
                throw new RuntimeException("Config versioning is already initialized.");
            }
            try (Git git = Git.init().setDirectory(dataDir().toFile()).call()) {
                GitManager.disableSsl(git);
                writeGitignore();
                git.add().addFilepattern(".").call();
                CommitCommand commit = git.commit().setMessage("Initial config-as-code commit");
                commit.setAuthor(actingUser, GitManager.resolveUserEmail(actingUser));
                commit.call();
            } catch (Exception e) {
                logger.error("Error initializing config versioning repo", e);
                throw new RuntimeException(e);
            }
        }
    }

    private static void writeGitignore() throws IOException {
        Path gitignore = dataDir().resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            Files.write(gitignore,
                    (String.join("\n", GITIGNORE_LINES) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Porcelain list of uncommitted config changes, scoped to {@link #SCOPE} (JGit honors
     * {@code .gitignore}, so db/logs/keystore/projects never appear). JSON key-ordering-only
     * changes are suppressed via {@link GitManager#filterJsonOrderingChanges}.
     */
    public static List<ConfigChange> getStatus() {
        synchronized (DATA_DIR_LOCK) {
            List<ConfigChange> changes = new ArrayList<>();
            try (Git git = GitManager.getGit(dataDir())) {
                Repository repo = git.getRepository();
                Status s = scopedStatus(git);

                // Build path -> type with deletions taking precedence over modifications.
                TreeMap<String, String> byPath = new TreeMap<>();
                for (String p : s.getUntracked()) byPath.put(p, "UNTRACKED");
                for (String p : s.getAdded()) byPath.put(p, "ADDED");
                for (String p : s.getModified()) byPath.put(p, "MODIFIED");
                for (String p : s.getChanged()) byPath.put(p, "MODIFIED");
                for (String p : s.getMissing()) byPath.put(p, "DELETED");
                for (String p : s.getRemoved()) byPath.put(p, "DELETED");

                Set<String> survivors = GitManager.filterJsonOrderingChanges(repo, dataDir(), byPath.keySet());
                for (Map.Entry<String, String> e : byPath.entrySet()) {
                    if (survivors.contains(e.getKey())) {
                        changes.add(new ConfigChange(e.getKey(), e.getValue()));
                    }
                }
            } catch (Exception e) {
                logger.error("Error computing data-dir git status", e);
                throw new RuntimeException(e);
            }
            return changes;
        }
    }

    private static Status scopedStatus(Git git) throws Exception {
        var status = git.status();
        for (String path : SCOPE) {
            status = status.addPath(path);
        }
        return status.call();
    }

    /**
     * Auto-commit path ({@link ConfigAutoCommitter}): commit all dirty config authored as the
     * gateway (its system name), or no-op on a clean tree (avoids empty commits when a change
     * batch was already covered, e.g. the scan after a restore). Committing does not change
     * on-disk config, so the running gateway is already consistent — no {@code requestScan()}
     * here. Returns whether a commit was made.
     */
    public static boolean commitAllIfDirty(String message) {
        synchronized (DATA_DIR_LOCK) {
            if (!isInitialized() || getStatus().isEmpty()) {
                return false;
            }
            try (Git git = GitManager.getGit(dataDir())) {
                // "." respects .gitignore; the setUpdate pass also stages deletions of tracked files.
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                String gatewayName = getContext().getSystemPropertiesManager().getSystemName();
                git.commit().setMessage(message).setAuthor(gatewayName, "").call();
            } catch (Exception e) {
                logger.error("Error committing data-dir config", e);
                throw new RuntimeException(e);
            }
            return true;
        }
    }

    /** Paginated commit history for the config repo. */
    public static List<String[]> history(int skip, int limit) {
        return GitManager.getCommitLog(dataDir(), skip, limit);
    }

    /** Files changed in a commit, as {@code "CHANGE_TYPE:path"}. */
    public static List<String> commitFiles(String commitHash) {
        return GitManager.getCommitFileList(dataDir(), commitHash);
    }

    /**
     * {@code [oldContent, newContent]} for a file (JSON-normalized): at a commit when
     * {@code commitHash} is given, otherwise HEAD vs working tree (uncommitted changes).
     */
    public static List<String> fileDiff(String commitHash, String filePath) {
        if (commitHash == null || commitHash.isBlank()) {
            return GitManager.getWorkingTreeDiffContent(dataDir(), filePath);
        }
        return GitManager.getCommitFileDiffContent(dataDir(), commitHash, filePath);
    }

    // ----- Remote (manual push only — never pushed automatically) -----

    private static final String ORIGIN = "origin";

    /** Last manual push outcome for the page's status line (in-memory; resets on restart). */
    private static volatile long lastPushTime;
    private static volatile String lastPushError;

    public static long getLastPushTime() {
        return lastPushTime;
    }

    public static String getLastPushError() {
        return lastPushError;
    }

    /** Save (create or update) the config remote: persist the record and sync origin in .git/config. */
    public static void saveRemote(String uri, String branch, long sshKeyId, long httpsCredentialId) {
        if (uri == null || uri.isBlank()) {
            throw new RuntimeException("Remote URI cannot be empty.");
        }
        if (branch == null || branch.isBlank()) {
            throw new RuntimeException("Branch cannot be empty.");
        }
        synchronized (DATA_DIR_LOCK) {
            try (Git git = GitManager.getGit(dataDir())) {
                URIish urIish = new URIish(uri.trim());
                if (git.remoteList().call().stream().anyMatch(r -> ORIGIN.equals(r.getName()))) {
                    RemoteSetUrlCommand setUrl = git.remoteSetUrl();
                    setUrl.setRemoteName(ORIGIN);
                    setUrl.setRemoteUri(urIish);
                    setUrl.call();
                } else {
                    git.remoteAdd().setName(ORIGIN).setUri(urIish).call();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            GitConfigRemoteRecord record = GitConfigRemoteRecord.get();
            if (record == null) {
                record = new GitConfigRemoteRecord();
            }
            record.setUri(uri.trim());
            record.setBranch(branch.trim());
            record.setSshKeyId(sshKeyId);
            record.setHttpsCredentialId(httpsCredentialId);
            record.save();
        }
    }

    public static void removeRemote() {
        synchronized (DATA_DIR_LOCK) {
            try (Git git = GitManager.getGit(dataDir())) {
                git.remoteRemove().setRemoteName(ORIGIN).call();
            } catch (Exception e) {
                logger.warn("Could not remove origin from the config repo", e);
            }
            GitConfigRemoteRecord record = GitConfigRemoteRecord.get();
            if (record != null) {
                record.delete();
            }
            lastPushTime = 0;
            lastPushError = null;
        }
    }

    /** Manual push of the local history to the configured remote branch. */
    public static void push() {
        GitConfigRemoteRecord remote = GitConfigRemoteRecord.get();
        if (remote == null) {
            throw new RuntimeException("No remote configured.");
        }
        synchronized (DATA_DIR_LOCK) {
            try (Git git = GitManager.getGit(dataDir())) {
                PushCommand push = git.push()
                        .setRemote(ORIGIN)
                        .setRefSpecs(new RefSpec("HEAD:refs/heads/" + remote.getBranch()));
                GitManager.setAuthenticationFromIds(push, remote.getUri(),
                        remote.getSshKeyId(), remote.getHttpsCredentialId());
                for (PushResult result : push.call()) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        if (update.getStatus() != RemoteRefUpdate.Status.OK
                                && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                            throw new RuntimeException("Push rejected: " + update.getStatus()
                                    + (update.getMessage() != null ? " — " + update.getMessage() : ""));
                        }
                    }
                }
                lastPushTime = System.currentTimeMillis();
                lastPushError = null;
            } catch (Exception e) {
                lastPushTime = System.currentTimeMillis();
                lastPushError = e.getMessage() == null ? e.toString() : e.getMessage();
                logger.error("Push of the config repo failed", e);
                throw new RuntimeException(e);
            }
        }
    }

    /** Validate URI + credential with an ls-remote — touches no local or remote state. */
    public static void testRemote(String uri, long sshKeyId, long httpsCredentialId) {
        try {
            LsRemoteCommand lsRemote = Git.lsRemoteRepository().setRemote(uri).setHeads(true);
            GitManager.setAuthenticationFromIds(lsRemote, uri, sshKeyId, httpsCredentialId);
            lsRemote.call();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }

    /**
     * Restore config to exactly the state of {@code commitHash} and apply it to the running gateway.
     * Uses {@link GitManager#restoreTree} (keeps HEAD on the branch) then makes a forward
     * "Restore config to &lt;shortHash&gt;" commit, then triggers a config scan so the gateway picks
     * up the on-disk changes without a restart. No-op commit is skipped when nothing changed.
     */
    public static void restoreToCommit(String commitHash, String actingUser) {
        synchronized (DATA_DIR_LOCK) {
            String shortHash;
            try (Git git = GitManager.getGit(dataDir())) {
                ObjectId id = git.getRepository().resolve(commitHash);
                if (id == null) {
                    throw new RuntimeException("Commit not found: " + commitHash);
                }
                shortHash = id.abbreviate(7).name();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            GitManager.restoreTree(dataDir(), commitHash);

            try (Git git = GitManager.getGit(dataDir())) {
                Status s = git.status().call();
                boolean staged = !s.getAdded().isEmpty() || !s.getChanged().isEmpty() || !s.getRemoved().isEmpty();
                if (!staged) {
                    logger.infof("Restore to %s produced no changes; skipping commit.", shortHash);
                    return;
                }
                CommitCommand commit = git.commit().setMessage("Restore config to " + shortHash);
                commit.setAuthor(actingUser, GitManager.resolveUserEmail(actingUser));
                commit.call();
            } catch (Exception e) {
                logger.error("Error committing restore to " + commitHash, e);
                throw new RuntimeException(e);
            }
        }
        applyConfigToRunningGateway();
    }

    /** Apply on-disk config to the running gateway in-process (no restart). */
    public static void applyConfigToRunningGateway() {
        try {
            getContext().getConfigurationManager().requestScan().join();
        } catch (Exception e) {
            logger.error("Error requesting config scan after restore", e);
            throw new RuntimeException(e);
        }
    }
}
