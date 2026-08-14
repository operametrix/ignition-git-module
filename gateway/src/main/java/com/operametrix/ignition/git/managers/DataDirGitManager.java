package com.operametrix.ignition.git.managers;

import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.operametrix.ignition.git.records.GitConfigRemoteRecord;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteSetUrlCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
            "projects/",
            "",
            "# Module-internal state at the data-dir root (not config), never versioned",
            ".git-module-legacy-migrated"
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
    public static void initRepo() {
        synchronized (DATA_DIR_LOCK) {
            if (isInitialized()) {
                throw new RuntimeException("Config versioning is already initialized.");
            }
            try (Git git = Git.init().setDirectory(dataDir().toFile()).call()) {
                GitManager.disableSsl(git);
                writeGitignore();
                git.add().addFilepattern(".").call();
                // Authored as the gateway (like the auto-commits), not the acting web user, so the
                // whole config history is uniformly attributed to the gateway.
                git.commit().setMessage("Initial config-as-code commit").setAuthor(gatewayAuthor(), "").call();
            } catch (Exception e) {
                logger.error("Error initializing config versioning repo", e);
                throw new RuntimeException(e);
            }
        }
    }

    /** The gateway's system name — the author for config-repo commits (init + auto-commits). */
    private static String gatewayAuthor() {
        return getContext().getSystemPropertiesManager().getSystemName();
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
                git.commit().setMessage(message).setAuthor(gatewayAuthor(), "").call();
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

    /**
     * Completely remove config versioning: delete {@code <dataDir>/.git} (all history), the
     * {@code .gitignore}, and the remote record. **Credential records are kept** (they are shared
     * with the Designer per-project repos). Afterward {@link #isInitialized()} is false, so the
     * page reverts to the Initialize screen. Opens no JGit handle (so the working tree isn't
     * locked during the recursive delete).
     */
    public static void deleteRepo() {
        synchronized (DATA_DIR_LOCK) {
            if (!isInitialized()) {
                throw new RuntimeException("Config versioning is not initialized.");
            }
            try {
                GitConfigRemoteRecord record = GitConfigRemoteRecord.get();
                if (record != null) {
                    record.delete();
                }
                deleteRecursively(dataDir().resolve(".git"));
                Files.deleteIfExists(dataDir().resolve(".gitignore"));
                lastPushTime = 0;
                lastPushError = null;
            } catch (Exception e) {
                logger.error("Failed to remove config versioning", e);
                throw new RuntimeException(e);
            }
        }
    }

    /** Recursively delete a path (depth-first), tolerating a missing root. */
    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Could not delete " + p, e);
                        }
                    });
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
                        RemoteRefUpdate.Status st = update.getStatus();
                        if (st != RemoteRefUpdate.Status.OK
                                && st != RemoteRefUpdate.Status.UP_TO_DATE) {
                            if (st == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD) {
                                throw new RuntimeException("Push rejected: the remote branch '"
                                        + remote.getBranch() + "' has commits this gateway doesn't "
                                        + "have (non-fast-forward). Reconcile them before pushing — "
                                        + "bring the remote's commits into this gateway, or reset the "
                                        + "remote branch if its history isn't needed (e.g. it was "
                                        + "seeded with a README).");
                            }
                            throw new RuntimeException("Push rejected: " + st
                                    + (update.getMessage() != null ? " — " + update.getMessage() : ""));
                        }
                    }
                }
                // Advance the local remote-tracking ref so the history can mark which commits are
                // on the remote (our refspec doesn't update it automatically).
                Repository repo = git.getRepository();
                ObjectId head = repo.resolve("HEAD");
                if (head != null) {
                    RefUpdate ru = repo.updateRef(trackingRef(remote.getBranch()));
                    ru.setNewObjectId(head);
                    ru.setForceUpdate(true);
                    ru.update();
                }
                lastPushTime = System.currentTimeMillis();
                lastPushError = null;
            } catch (Exception e) {
                String friendly = friendlyPushError(e);
                lastPushTime = System.currentTimeMillis();
                lastPushError = friendly;
                logger.error("Push of the config repo failed", e);
                throw new RuntimeException(friendly, e);
            }
        }
    }

    /** Map a raw push failure (transport/auth/JGit) to a concise, user-facing message. */
    private static String friendlyPushError(Exception e) {
        String raw = e.getMessage() == null ? e.toString() : e.getMessage();
        // Already-friendly messages we threw ourselves (e.g. the non-fast-forward explanation).
        if (raw.startsWith("Push rejected:")) {
            return raw;
        }
        String low = raw.toLowerCase();
        // Authorization: authenticated OK, but this credential can't push (read-only / no write
        // scope). JGit surfaces the refused receive-pack service, usually an HTTP 403.
        if (low.contains("receive-pack") || low.contains("not permitted")
                || low.contains(" 403") || low.contains("forbidden")
                || low.contains("permission denied") || low.contains("write access")) {
            return "Push denied: the credential can read this repository but isn't allowed to push "
                    + "to it. Grant it write access, or use a token/key with write (push) permission.";
        }
        // Authentication: the gateway couldn't sign in at all — wrong or expired credential.
        if (low.contains("not authorized") || low.contains("authentication")
                || low.contains("auth fail") || low.contains(" 401")
                || low.contains("invalid username") || low.contains("invalid credential")) {
            return "Authentication failed: the gateway couldn't sign in to the remote. Check the "
                    + "remote's credential — the token, password or SSH key may be wrong or expired.";
        }
        if (low.contains("unable to access") || low.contains("cannot open")
                || low.contains("unknownhost") || low.contains("unknown host")
                || low.contains("connection") || low.contains("timed out")
                || low.contains("not found") || low.contains("could not read")) {
            return "Could not reach the remote repository. Check the URL and that the gateway has "
                    + "network access to it.";
        }
        return "Push failed: " + raw;
    }

    private static String trackingRef(String branch) {
        return "refs/remotes/" + ORIGIN + "/" + branch;
    }

    /**
     * Ref pointers for the history: {@code [localHeadHash, remoteHeadHash]} (full hashes). The
     * remote head is the local remote-tracking ref that {@link #push()} advances; it is {@code ""}
     * when no remote is configured or nothing has been pushed. These mark just the two tip commits
     * (like git's branch pointers), not every commit.
     */
    public static String[] pointerHashes() {
        String local = "";
        String remote = "";
        if (!isInitialized()) {
            return new String[]{local, remote};
        }
        synchronized (DATA_DIR_LOCK) {
            try (Git git = GitManager.getGit(dataDir())) {
                Repository repo = git.getRepository();
                ObjectId head = repo.resolve("HEAD");
                if (head != null) {
                    local = head.getName();
                }
                GitConfigRemoteRecord r = GitConfigRemoteRecord.get();
                if (r != null) {
                    ObjectId trackingId = repo.resolve(trackingRef(r.getBranch()));
                    if (trackingId != null) {
                        remote = trackingId.getName();
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not resolve config repo ref pointers", e);
            }
        }
        return new String[]{local, remote};
    }

    /**
     * Number of commits on the local branch that are not yet on the remote tracking ref — the
     * "unsynced" count for the page's sync indicator. When nothing has been pushed yet, every
     * commit counts. 0 when no remote is configured or the branches are level.
     */
    public static int aheadCount() {
        GitConfigRemoteRecord remote = GitConfigRemoteRecord.get();
        // Guard on the repo existing too: after a gwbk restore the remote record survives but
        // .git does not, and calling getGit() on the missing repo would log a spurious ERROR
        // on every /remote poll. Not-initialized ⇒ nothing pushed ⇒ 0 ahead.
        if (remote == null || !isInitialized()) {
            return 0;
        }
        synchronized (DATA_DIR_LOCK) {
            try (Git git = GitManager.getGit(dataDir())) {
                Repository repo = git.getRepository();
                ObjectId head = repo.resolve("HEAD");
                if (head == null) {
                    return 0;
                }
                try (RevWalk walk = new RevWalk(repo)) {
                    walk.markStart(walk.parseCommit(head));
                    ObjectId trackingId = repo.resolve(trackingRef(remote.getBranch()));
                    if (trackingId != null) {
                        walk.markUninteresting(walk.parseCommit(trackingId));
                    }
                    int count = 0;
                    for (RevCommit ignored : walk) {
                        count++;
                    }
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Could not compute the unsynced commit count", e);
                return 0;
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

    /** Test with raw plaintext secrets (not yet persisted) — used by the inline Configure drawer. */
    public static void testRemoteRaw(String uri, String sshKeyPlaintext, String httpsUser,
                                     String httpsPassword) {
        try {
            LsRemoteCommand lsRemote = Git.lsRemoteRepository().setRemote(uri).setHeads(true);
            GitManager.setAuthenticationRaw(lsRemote, uri, sshKeyPlaintext, httpsUser, httpsPassword);
            lsRemote.call();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }

    /**
     * Bring config up to the configured remote's HEAD and apply it to the running gateway. Handles
     * two entry states:
     * <ul>
     *   <li><b>Not initialized</b> — e.g. after a gateway-backup restore, which brings back
     *       {@code config/} and the surviving {@link GitConfigRemoteRecord} but <em>not</em>
     *       {@code <dataDir>/.git}: the repo is re-created, {@code origin} is wired from the record,
     *       and the remote branch is fetched and laid down.</li>
     *   <li><b>Initialized but behind</b> — fetch and fast-forward the working tree to the remote tip.</li>
     * </ul>
     * Refuses when the local branch has diverged (commits not on the remote) so nothing is discarded —
     * push or restore those first. The working tree is forced to exactly the remote tree the same way
     * {@link GitManager#restoreTree} does (overwrite tracked, {@code git clean} untracked), so gitignored
     * key material (keystore / projects / db / {@code config/local}) is preserved. Returns the new HEAD
     * short hash.
     */
    public static String updateFromRemote() {
        GitConfigRemoteRecord remote = GitConfigRemoteRecord.get();
        if (remote == null) {
            throw new RuntimeException("No remote is configured to update from.");
        }
        String branch = remote.getBranch();
        synchronized (DATA_DIR_LOCK) {
            boolean fresh = !isInitialized();
            try {
                if (fresh) {
                    // Re-establish the repo from the saved remote (post-restore recovery).
                    try (Git git = Git.init().setDirectory(dataDir().toFile()).call()) {
                        GitManager.disableSsl(git);
                        git.remoteAdd().setName(ORIGIN).setUri(new URIish(remote.getUri())).call();
                    }
                    if (!Files.exists(dataDir().resolve(".gitignore"))) {
                        writeGitignore();
                    }
                }
                try (Git git = GitManager.getGit(dataDir())) {
                    Repository repo = git.getRepository();

                    FetchCommand fetch = git.fetch().setRemote(ORIGIN)
                            .setRefSpecs(new RefSpec("+refs/heads/" + branch + ":" + trackingRef(branch)));
                    GitManager.setAuthenticationFromIds(fetch, remote.getUri(),
                            remote.getSshKeyId(), remote.getHttpsCredentialId());
                    fetch.call();

                    ObjectId remoteId = repo.resolve(trackingRef(branch));
                    if (remoteId == null) {
                        throw new RuntimeException("Branch '" + branch + "' was not found on the remote.");
                    }

                    ObjectId head = fresh ? null : repo.resolve(Constants.HEAD);
                    if (head != null) {
                        if (head.equals(remoteId)) {
                            return remoteId.abbreviate(7).name();  // already up to date
                        }
                        try (RevWalk walk = new RevWalk(repo)) {
                            boolean remoteContainsLocal = walk.isMergedInto(
                                    walk.parseCommit(head), walk.parseCommit(remoteId));
                            if (!remoteContainsLocal) {
                                throw new RuntimeException("Local config has commit(s) not on the remote. "
                                        + "Push or restore first — update-from-remote won't discard them.");
                            }
                        }
                    }

                    // Point the branch (and HEAD) at the remote tip, then bring the working tree to it.
                    git.branchCreate().setName(branch).setStartPoint(remoteId.name()).setForce(true).call();
                    repo.updateRef(Constants.HEAD).link("refs/heads/" + branch);
                    if (fresh) {
                        // Fresh repo laid over a restored working tree: config/ exists but is untracked,
                        // so a hard reset would hit checkout conflicts. Stage the remote tree (MIXED)
                        // then overwrite the tracked files via a path-checkout. clean() runs WITHOUT
                        // setCleanDirectories(true): a directory clean deletes untracked dirs wholesale,
                        // taking gitignored runtime data nested inside them with it (e.g. the tag value
                        // store config/ignition/tags/valueStore.idb) — JGit does not spare it.
                        git.reset().setMode(ResetCommand.ResetType.MIXED).setRef(remoteId.name()).call();
                        git.checkout().setStartPoint(remoteId.name()).setAllPaths(true).call();
                        git.clean().call();
                    } else {
                        // Existing repo (fast-forward): a hard reset moves tracked files (add/modify/
                        // delete) to the remote tip and leaves untracked/ignored runtime data — the tag
                        // value store, db, keystore — untouched. No clean needed.
                        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(remoteId.name()).call();
                    }

                    applyConfigToRunningGateway();
                    return remoteId.abbreviate(7).name();
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Update-from-remote failed", e);
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
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
