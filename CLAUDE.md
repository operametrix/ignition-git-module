# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ignition Git Module — a Java module for the Inductive Automation Ignition SCADA platform (8.1.0+) that embeds a Git client into the Ignition Designer. It enables committing, pushing, pulling (with merge conflict resolution), fetching (without merge), reverting commits, branch management, and exporting gateway configuration directly from the Designer's dockable panels and status bar. Supports both remote (clone) and local-only repository initialization via a wizard-style setup dialog. Originally built by AXONE-IO, maintained by Operametrix. Version 1.0.3.

## Build Commands

```bash
# Build the module (produces .modl file)
./gradlew build

# Build output location
# build/Git.modl
```

There are no automated tests in this project. Testing is done manually by installing the .modl on an Ignition gateway and using the Designer UI.

## Architecture

This is a Gradle multi-module project following the **Ignition Module SDK pattern** with three scope-specific subprojects:

```
common    (scope: DG)   — Shared interface + abstract base class
designer  (scope: D)    — Designer UI: dockable panels, popups, status bar
gateway   (scope: G)    — Backend: all git operations, persistence
```

The root `build.gradle.kts` uses the `io.ia.sdk.modl` Gradle plugin to assemble the `.modl` file.

**Scopes**: D = Designer, G = Gateway. Code in a given scope only runs in that Ignition context. (The Vision client scope is unused — there is no `system.git.*` script module on Vision clients.)

### Key Design Patterns

**Hook classes** are the entry points for each scope. Each implements the Ignition lifecycle (`setup`/`startup`/`shutdown`):
- `DesignerHook` — initializes status bar (three hover-highlighted buttons when registered: git icon + branch name, cloud icon + "Remotes", user icon + username), dockable panels, and user verification timer; uses `ModuleRPCFactory` to call gateway methods remotely. On startup, checks `isProjectRegistered()` — if unregistered, shows a two-button status bar ("Configure" + user button) so credentials can be added before initialization. After successful init via `InitRepoPopup`, calls `reinitializeAfterSetup()` to rebuild the full status bar. Exposes a static `instance` field for callbacks from `GitActionManager`. A 1-second polling `Timer` (`panelVisibilityTimer`) keeps the Commit and History dockable panels visible across workspace switches — checks `isHidden()`, null (removed from DockingManager), and `!isDisplayable()` (detached from Swing hierarchy) to catch all workspace transition behaviors.
- `GatewayHook` — creates DB schema and registers the gateway RPC handler

**Script interface pattern**: `GitScriptInterface` (common) defines the API (includes `initializeLocalProject()` for local-only init, `hasRemoteRepository()` for remote detection, `fetch()` for fetching without merge, and merge conflict resolution methods: `getConflictingFiles`, `resolveConflict`, `abortMerge`, `completeMergeCommit`, `getConflictDiff`). `AbstractScriptModule` (common) decorates it with Ignition annotations. `GatewayScriptModule` (gateway) provides the real implementation. The Designer calls gateway methods through `ModuleRPCFactory.create(GitScriptInterface.class)`.

**Designer project refresh**: After any gateway-side operation that modifies the Ignition project (pull, checkout, init), the Designer must call `GitBaseAction.pullProjectFromGateway()` to sync its local project state with the gateway via reflection on the Designer frame's `pullAndResolve()` method. Without this call, gateway-side `GitProjectManager.importProject()` updates the gateway but the Designer UI won't reflect the changes.

**Designer popups** (`designer` module) are Swing `JDialog` dialogs (parented to the Designer frame via `super(SwingUtilities.getWindowAncestor(parent))` so they overlay correctly on macOS fullscreen) with abstract callbacks overridden via anonymous subclasses in `GitActionManager`:
- `CommitPopup` — select changes and enter commit message; displays resource name, type, author, and last-modification timestamp (formatted `yyyy-MM-dd HH:mm`). Includes an "Amend last commit" checkbox that pre-fills the last commit message (via `onAmendToggled` callback) and allows committing with no files selected (message-only amend). Double-clicking a resource row opens the `DiffViewerPopup` via the `onDiffRequested` callback.
- `DiffViewerPopup` — side-by-side diff viewer comparing two versions of a file. Uses an LCS-based line diff algorithm with color-coded backgrounds (green for added, red for removed) and synchronized scrolling. Default headers: "HEAD (committed)" / "Working Tree"; an overloaded constructor accepts custom left/right header labels (used by `MergeConflictPopup` with "Ours (HEAD)" / "Theirs (incoming)"). Opened from `CommitPopup` via `GitActionManager.showDiffViewer()`, which calls `rpc.getResourceDiff()` to fetch content from the gateway. Also reused by `CommitDetailPopup` for historical diffs and by `MergeConflictPopup` for conflict diffs.
- `CommitDetailPopup` — shows files changed in a single commit (change type + path) with commit hash, author, date, and message at top. Not cached (allows multiple side-by-side). Double-clicking a file opens `DiffViewerPopup` with old/new content at that commit via `rpc.getCommitFileDiff()`. Footer buttons: "Checkout" (detached HEAD with confirmation) and "Revert Commit" (creates a new commit undoing the target). No Close button — window X dismisses.
- `MergeConflictPopup` — shown when a pull results in merge conflicts. Lists conflicting files in a table with color-coded status column (red=Unresolved, blue=Accepted Ours, green=Accepted Theirs). Per-file buttons: "View Diff" (opens `DiffViewerPopup` with "Ours (HEAD)" / "Theirs (incoming)" headers), "Accept Ours", "Accept Theirs". Global buttons: "Accept All Ours", "Accept All Theirs", "Abort Merge" (hard reset + confirmation dialog), "Complete Merge" (enabled only when all files resolved). Window close triggers abort confirmation (`DO_NOTHING_ON_CLOSE`). Not cached — created fresh per conflict occurrence. Callbacks: `onResolveConflict`, `onResolveAllConflicts`, `onAbortMerge`, `onCompleteMerge`, `onViewDiff`. Internal state tracks per-file resolution via `Map<String, String>`.
- `PullPopup` — remote selection (JComboBox, hidden when single remote) + toggle import of tags/themes/images. Callback: `onPullAction(String remoteName, boolean importTags, boolean importTheme, boolean importImages)`
- `PushPopup` — lightweight remote selection popup (JComboBox + Push/Cancel buttons). Only shown when 2+ remotes exist; single-remote projects push immediately without a popup. Callback: `onPush(String remoteName)`
- `FetchPopup` — lightweight remote selection popup (mirrors `PushPopup`). Only shown when 2+ remotes exist; single-remote projects fetch immediately without a popup. Callback: `onFetch(String remoteName)`. Wired by `GitActionManager.showFetchAction()` via the History panel's Fetch button.
- `BranchPopup` — two side-by-side lists (local / remote branches) with no bottom button row. Each list header has a title label + icon button row: the local header has a `+` (Create Branch, opens `CreateBranchPopup`) and a `↻` (local-only fast refresh, calls `onRefresh`); the remote header has a `↻` (fetches from all remotes first with `InitProgressDialog` progress feedback, then reloads, calls `onRefreshFromRemote`). Per-item actions are via context menus: right-click a local branch for Checkout/Delete (both disabled on the current branch); right-click a remote branch for Checkout (strips `origin/` prefix, creates tracking branch). Double-click either list to checkout. The current branch is highlighted inline via a `CurrentBranchRenderer` (bold + light blue row background when not selected). Window X dismisses.
- `CreateBranchPopup` — lightweight dialog with a single branch name field and Create/Cancel buttons. Creates a new branch from HEAD (the start-point option was removed as too advanced). Enter key triggers Create. Callback: `onCreateBranch(String branchName)`.
- `RemotesPopup` — `CardLayout`-based popup for managing git remotes. Card 1 ("List") shows a table of configured remotes with a "Configured Remotes" header containing a `+` icon button for Add Remote; double-click a row to Edit, right-click for Edit/Remove context menu (no bottom button row). Card 2 ("Form") has remote name + URL fields, an auth-type label that switches HTTPS/SSH from the URI, a **credential dropdown** populated from the user's saved SSH keys or HTTPS credentials (filtered by URI scheme), and a "Configure..." button that opens `UserCredentialsPopup` to add credentials inline; Back/Save buttons. Callbacks: `onAddRemote`, `onEditRemote`, `onRemoveRemote`, `onRefresh`, `onConfigureCredentials`. Selecting a credential stores the FK (`SshKeyId` or `HttpsCredentialId`) on the remote — no inline credentials are written by this popup. Accessible from the status bar remotes button. The status bar user icon ("Manage Git Credentials") opens `UserCredentialsPopup` directly; there is no separate per-project email popup (email is taken from the Ignition user profile).
- `UserCredentialsPopup` — manages user-level credentials (SSH keys + HTTPS credentials host/username/password). Two stacked sections each with a custom header: "SSH Keys" + `+` icon for Add SSH Key, "HTTPS Credentials" + `+` icon for Add HTTPS Credential. Per-row Remove is only available via right-click context menu (no bottom button row). HTTPS add form shows provider-specific hint text (GitHub/GitLab = PAT, Azure = PAT with empty username, Bitbucket = App Password). Callbacks: `onSaveSshKey`, `onDeleteSshKey`, `onSaveHttpsCredential`, `onDeleteHttpsCredential`.
- `InitRepoPopup` — wizard-style `CardLayout` dialog for initializing a git repo for an unregistered project. Card 1 ("Choose") asks "Do you have a remote repository?" with two buttons. Card 2a ("Remote") is the clone flow: repo URI field, an auth-type label that flips HTTPS/SSH from the URI, a credential dropdown populated from the user's saved SSH keys or HTTPS credentials (filtered by URI scheme), a "Configure..." button that opens `UserCredentialsPopup`, and Initialize/Cancel/Back buttons; calls `onInitialize(repoUri, "", "", "", sshKeyId, httpsCredentialId)` — only the credential FK is passed, no inline credentials. Card 2b ("Local") is the local-only flow: explanatory text only (no fields — commit author email is taken from the Ignition user profile), Initialize/Cancel/Back buttons; calls `onLocalInitialize()` callback (no arguments). On success, creates DB records + clones or inits the repo + refreshes the Designer project.
- `InitProgressDialog` — modal `JDialog` with an indeterminate `JProgressBar` and status label, shown during long-running git operations (init, push, pull, fetch, branch refresh from remote). Takes a title and exposes `setStatus(String)` for phase updates and `complete()` for dismiss. Used in `GitActionManager.showInitRepoPopup` and `GitBaseAction.handlePushAction` / `handlePullAction` / `handleFetchAction` via `SwingWorker` so the EDT stays responsive.

**Dockable Commit panel** (`CommitPanel.java`) — a JIDE `DockableFrame`-based panel (key: `"Commit"`, icon: `ic_commit.svg`) tabbed alongside the Project Browser (key: `"Project Browser"`) on the left side, with Project Browser as the default active tab. Provides an at-a-glance view of uncommitted changes without opening popups:
- Commit section: message text area + "Amend last commit" checkbox + Commit button for inline commits; amend checkbox pre-fills last commit message and allows message-only amend (no files selected)
- Changes section header: "Changes (N)" label on the left + right-aligned toolbar group with three snapshot buttons each rendered as the `VectorIcons.get("project-update")` glyph + short text label ("Tags", "Themes", "Images"), followed by a `VectorIcons.get("refresh")` icon button — all share the same borderless hover-background styling via `createHeaderTextButton(icon, label, …)` / `createHeaderIconButton` / `styleHeaderButton`. (`VectorIcons` keys are kebab-case and come from `com/inductiveautomation/ignition/client/icons/vector-icons.json` in `client-api`; there is no `download` key — an unknown key renders as a missing-glyph square.) The three snapshot buttons call `rpc.snapshotTags` / `snapshotThemes` / `snapshotImages` via `GitActionManager.runSnapshot(...)`, which runs each call on a `SwingWorker` with an `InitProgressDialog` and refreshes the panel on completion — gateway-side tag/theme/image edits then appear as file changes in the Changes table for normal per-file review and commit selection
- Changes table: checkbox + Resource + Type columns with `SelectAllHeader` (fixed against O(n²) event cascades via an `updating` guard flag); Type column shows color-coded single-letter badges (A=green/created, M=amber/modified, D=red/deleted, U=orange/uncommitted)
- Double-click a row to view diff; right-click context menu for "View Diff" and "Discard Changes" (with confirmation dialog)
- Uses `java.util.function` callback setters wired by `GitActionManager.wireCommitPanel()`
- Auto-refreshes every 15 seconds via a `Timer`, plus immediate refresh after any git operation (commit, pull, push, checkout)
- Thread-safe: `setChangesData(Dataset)` posts updates to EDT via `SwingUtilities.invokeLater()`

**Dockable History panel** (`HistoryPanel.java`) — a JIDE `DockableFrame`-based panel (key: `"History"`, icon: `ic_history.svg`) tabbed alongside the Project Browser and Changes panel on the left side. Simple commit history log for the current branch:
- Top toolbar (borderless icon buttons with hover background via `createToolbarButton`): Refresh (`VectorIcons.get("refresh")`), Push, Fetch, Pull (custom SVGs from `IconUtils`)
- History table: columns [Message, Author, Refs]; the Refs column uses a custom `RefsRenderer` that draws colored rounded-rect badges for branch/tag ref decorations; date is shown in tooltip on hover
- Commit log uses `git log` from HEAD plus the upstream remote-tracking branch (if configured), so fetched commits appear with their remote ref badges before merging; each commit includes ref decorations from the gateway
- 8-color cycling palette for ref badge coloring
- Double-click a commit row to open `CommitDetailPopup` (with author and date) via `onCommitSelected` callback
- Right-click a commit row to show context menu with "Checkout Commit" (via `onCheckoutRequested` callback) and "Revert Commit" (via `onRevertRequested` callback) options
- "Load More" button for pagination (appends rows)
- Wired by `GitActionManager.wireHistoryPanel()`; auto-refreshes after any git operation
- Thread-safe: `setData(Dataset, boolean append)` posts updates to EDT via `SwingUtilities.invokeLater()`

**Manager classes** in `gateway` encapsulate domain logic:
- `GitManager` — core JGit operations (clone, fetch (updates remote-tracking branches only via `FetchCommand`; no working directory changes; regular `fetchImpl` sets `setUnshallow(true)` which is a no-op on non-shallow repos but pulls full history for repos that were initialized with `depth=1`), pull, push (current branch only by default; `pushAllBranches`/`pushTags`/`forcePush` flags available; push results are checked for `RemoteRefUpdate.Status` — non-fast-forward rejections trigger a force-push confirmation dialog in the Designer), commit (with `amend` flag for replacing the last commit via `CommitCommand.setAmend(true)`), status, branch list/create/checkout/delete with per-branch stash/restore, checkout commit (detached HEAD with stash of current branch's changes; `getCurrentBranch()` returns truncated hash + "(detached)" when not on a branch), resource diff content extraction, commit history log with ref decorations for current branch, commit file list, commit file diff, discard changes, revert commit (creates a new commit undoing target commit's changes; aborts cleanly on conflicts via hard reset), merge conflict resolution (`getConflictingFiles` via `git.status().getConflicting()`, `resolveConflict` via `CheckoutCommand.Stage.OURS/THEIRS` + `git add`, `abortMerge` via hard reset, `completeMergeCommit` reads `.git/MERGE_MSG` and commits after verifying no remaining conflicts, `getConflictDiffContent` reads file content at `HEAD` and `MERGE_HEAD`), remote management (list/add/remove/setUrl via JGit `RemoteAddCommand`/`RemoteRemoveCommand`/`RemoteSetUrlCommand`/`RemoteListCommand`), per-remote credential lookup from `GitRemoteCredentialsRecord`). Remote-dependent operations (`pullImpl`, `pushImpl`, `fetchImpl`, `setupLocalRepoImpl`) are guarded by `GitProjectsConfigRecord.hasRemote()` — local-only repos skip remote operations gracefully. **Lean init fetch**: `setupLocalRepoImpl` uses `git.lsRemote().setHeads(true)` first to detect the default branch without downloading any objects, then does a targeted single-branch shallow fetch (`setRefSpecs("+refs/heads/<branch>:refs/remotes/origin/<branch>").setDepth(1)`) for a fast working-tree checkout, then runs a follow-up `FetchCommand.setUnshallow(true)` to pull full commit history for the History panel. `detectDefaultBranchFromRefs` checks well-known names (main/master/develop) then falls back to the first head ref. `setAuthentication(command, project, user, remoteName)` is the sole authentication method and uses a **two-tier resolution chain**: Tier 1 = FK reference from `GitRemoteCredentialsRecord.SshKeyId`/`HttpsCredentialId` to a user-level `GitUserSshKeyRecord`/`GitUserHttpsCredentialRecord`; Tier 2 = user-level fallback (the single user SSH key if exactly one exists, or a HTTPS credential whose `HostPattern` matches the URL host). Throws if no tier resolves. Push/pull accept a `remoteName` parameter and use it for both the JGit remote target and credential lookup. Auth type (SSH vs HTTPS) is determined from the remote's URL in `.git/config`. Pull conflict detection: `pullImpl` checks `PullResult.getMergeResult().getMergeStatus() == CONFLICTING`, throws `RuntimeException("MERGE_CONFLICT:" + newline-joined file list)` as a sentinel caught by the Designer's `handlePullAction` to open `MergeConflictPopup`. **Metadata noise suppression**: `filterMetadataOnlyChanges(allChangedFiles, targetSet)` removes `resource.json` and `thumbnail.png` entries from a status set when no sibling source file in the same Ignition resource directory also changed (checked across all status categories via `allChangedFiles` union). `filterMetadataOnlyCommitFiles(files)` does the same for commit file lists (`"CHANGE_TYPE:path"` format). `findDataFile()` also skips `thumbnail.png` alongside `resource.json` when selecting the diff target. These filters are applied in `GatewayScriptModule.getUncommitedChangesImpl()` and `getCommitFilesImpl()`
- `GitProjectManager`, `GitTagManager`, `GitThemeManager`, `GitImageManager` — resource import/export

**Persistence** uses Ignition's SimpleORM with five record types:
- `GitProjectsConfigRecord` — maps Ignition projects to git repos; `hasRemote()` returns `true` if URI is non-null and non-empty (empty string `""` is the sentinel for local-only repos). URI is kept in sync with the "origin" remote — adding/removing/editing "origin" in `RemotesPopup` updates this field
- `GitReposUsersRecord` — maps Ignition users to projects (registration marker only). Commit author email is taken from the Ignition user profile; auth is handled exclusively via the credential records below
- `GitRemoteCredentialsRecord` — per-(project, user, remote) record holding two FK columns (`SshKeyId`, `HttpsCredentialId`) that point into the user-level credential tables. Created when adding a remote via `RemotesPopup` or during initial project setup via `InitRepoPopup`
- `GitUserSshKeyRecord` — user-level SSH key (columns: `IgnitionUser`, `KeyName`, `SSHKey`). Managed via `UserCredentialsPopup`; shared across all projects/remotes and referenced by `GitRemoteCredentialsRecord.SshKeyId`
- `GitUserHttpsCredentialRecord` — user-level HTTPS credential (columns: `IgnitionUser`, `HostPattern`, `UserName`, `Password` as EncodedStringField). Managed via `UserCredentialsPopup`; `HostPattern` enables Tier 3 host-match fallback in `setAuthentication`. Referenced by `GitRemoteCredentialsRecord.HttpsCredentialId`

### Key Libraries

- **Eclipse JGit 6.10.1** — all git operations
- **Apache MINA sshd** (via `org.eclipse.jgit.ssh.apache`) — SSH transport (replaced the deprecated JSch-based `org.eclipse.jgit.ssh.jsch` in favor of the actively maintained Apache MINA sshd backend)
- **Lombok 1.18.42** — annotation processing in designer module
- **IntelliJ forms_rt 7.0.3** — Swing form support for Designer popups

## Module Packaging

The root `build.gradle.kts` uses `io.ia.sdk.modl` plugin (v0.4.1) to assemble the `.modl` file. Module ID is `com.operametrix.ignition.git`. The version includes a build timestamp (`yyyyMMddHH`). Module signing is disabled by default (`skipModlSigning = true`); to enable, copy `gradle.template.properties` to `gradle.properties` and fill in signing credentials.

## Java Version

Java 11 source and target. Set via Java toolchain in each subproject's `build.gradle.kts`.

## Dependency Repositories

Dependencies are resolved from Inductive Automation's Nexus server and Maven Central. These are configured in `settings.gradle`.
