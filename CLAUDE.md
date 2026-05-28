# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ignition Git Module — a Java module for the Inductive Automation Ignition SCADA platform (8.3.0+) that embeds a Git client into the Ignition Designer. It supports commit, push, fetch (without merge), pull (with merge-conflict resolution), revert, branch management, snapshotting gateway-side resources (tags/themes/images) into the project, and both remote (clone) and local-only repository initialization — all from the Designer's dockable panels and status bar. Originally built by AXONE-IO, maintained by Operametrix.

## Build Commands

```bash
./gradlew build        # produces build/Git.modl
```

No automated tests. Testing is manual: install the `.modl` on an Ignition gateway and exercise the Designer UI.

## Architecture

Gradle multi-module project following the **Ignition Module SDK pattern**:

```
common    (scope: DG)   — RPC interface + abstract delegation base
designer  (scope: D)    — Designer UI: dockable panels, popups, status bar
gateway   (scope: G)    — all git operations + persistence
```

Scopes: D = Designer, G = Gateway. The Vision client scope is unused — there is no `system.git.*` script module on Vision clients. The root `build.gradle.kts` (`io.ia.sdk.modl` plugin) assembles the `.modl`.

### Key design patterns

**Hooks** are the per-scope entry points (Ignition `setup`/`startup`/`shutdown`):
- `DesignerHook` — builds the status bar, the dockable Commit/History panels, and a user-verification timer; talks to the gateway via `GatewayConnection.getRpcInterface(...)`. If `isProjectRegistered()` is false it shows a minimal "Configure" + user-button bar so credentials can be added before init; after init via `InitRepoPopup` it calls `reinitializeAfterSetup()` to build the full bar. A 1-second `panelVisibilityTimer` re-shows the Commit/History panels across workspace switches (checks hidden / null-in-DockingManager / `!isDisplayable()`). Exposes a static `instance` for `GitActionManager` callbacks.
- `GatewayHook` — registers the five resource types and starts their `NamedResourceHandler`s, runs the one-time legacy SimpleORM→resource importer, and registers the gateway RPC implementation (`getRpcImplementation()`).

**RPC pattern** (8.3 module RPC): `GitScriptInterface` (common) is the contract, annotated `@RpcInterface(packageId="com.operametrix.ignition.git")` and exposing a shared `SERIALIZER`. The serializer is a custom `ProtoRpcSerializer.newBuilder()` instance — `DEFAULT_INSTANCE` has no `Dataset` support, so it registers a Java-serialization `BinaryAdapter` for `Dataset`/`BasicDataset` (`ObjectSerializers.forUnsafeObject`); without it every `Dataset`-returning RPC round-trips empty with no error. `AbstractScriptModule` (common) is a plain abstract base that delegates each interface method to a `…Impl` abstract method, supplied by `GatewayScriptModule` (gateway) — which must `implements GitScriptInterface` *directly* (8.3's `RpcDelegate` discovers `@RpcInterface` only on the concrete class's direct interfaces, no superclass walk). The gateway registers it via `GatewayHook.getRpcImplementation()` → `GatewayRpcImplementation.of(SERIALIZER, scriptModule)`; the Designer obtains a proxy via `GatewayConnection.getRpcInterface(SERIALIZER, "com.operametrix.ignition.git", GitScriptInterface.class)`.

**Designer project refresh**: after any gateway-side operation that mutates the Ignition project (pull, checkout, init, snapshot), the Designer must call `GitBaseAction.pullProjectFromGateway()`. Under 8.3's resource model this (a) discards stale local edits per `ChangeOperation` via typed `DesignableProject.discardChanges(ResourcePath)` — so a deliberate checkout doesn't open the Resolve-Conflicts dialog (the gateway is authoritative; uncommitted work is preserved by gateway-side git stash/restore) — then (b) reflectively calls the public `IgnitionDesigner.updateProject()`, the 8.3 successor to the removed private `pullAndResolve()`. `closeAllEditorTabs()` runs first and looks up `TabbedResourceWorkspace.close(common.resourcecollection.ResourcePath, boolean)` (the 8.3 resource overhaul moved `ResourcePath` from `common.project.resource.*` to `common.resourcecollection.*`). Without this refresh the gateway changes (via `GitProjectManager.importProject()`) won't show in the Designer.

**Designer popups** are Swing `JDialog`s parented to the Designer frame (`SwingUtilities.getWindowAncestor(parent)` so they overlay correctly on macOS fullscreen); abstract callbacks are overridden in anonymous subclasses inside `GitActionManager`. Concrete RPC signatures and callback names live in the code — don't duplicate them here.
- `CommitPopup` — pick changes + message; "Amend last commit" pre-fills the last message and allows message-only amend; double-click a row → diff.
- `DiffViewerPopup` — side-by-side LCS line diff (green added / red removed, synced scroll); default headers HEAD/Working Tree, overridable (used by `MergeConflictPopup` and `CommitDetailPopup`).
- `CommitDetailPopup` — files in one commit + Checkout / Revert Commit; double-click a file → historical diff.
- `MergeConflictPopup` — per-file Accept Ours/Theirs, conflict diff, global Accept-All/Abort/Complete; window-close confirms abort so the repo can't be left conflicted.
- `PullPopup` / `PushPopup` / `FetchPopup` — remote selector; the lightweight Push/Fetch popups only appear with 2+ remotes (single-remote acts immediately).
- `BranchPopup` — local/remote lists, header icon buttons (create / refresh / refresh-from-remote), context-menu checkout/delete, current branch highlighted.
- `CreateBranchPopup` — branch name + Create (always from HEAD).
- `InitRepoPopup` — `CardLayout` wizard: Choose → Remote (URI + credential dropdown filtered by URI scheme + Configure…) or Local (no fields; commit email comes from the Ignition user profile). Only a credential FK is passed, never inline credentials.
- `RemotesPopup` — `CardLayout` list/form for named remotes; the form picks a saved credential (stores `SshKeyId`/`HttpsCredentialId` FK) and can open `UserCredentialsPopup` inline. Reached from the status-bar remotes button; the status-bar user icon opens `UserCredentialsPopup` directly (no per-project email popup).
- `UserCredentialsPopup` — user-level SSH keys + per-host HTTPS credentials, with provider hint text (GitHub/GitLab PAT, Azure PAT/empty-username, Bitbucket App Password).
- `InitProgressDialog` — modal indeterminate progress used for long ops (init/push/pull/fetch/snapshot/branch-refresh) so the EDT stays responsive.

**Dockable Commit panel** (`CommitPanel.java`, JIDE `DockableFrame`, tabbed by Project Browser): inline commit (message + amend), a Changes table (checkbox / Resource / Type with color-coded A/M/D/U badges and a `SelectAllHeader` guarded against O(n²) cascades), double-click diff, right-click View Diff / Discard. The Changes header has three snapshot buttons ("Tags"/"Themes"/"Images", `VectorIcons.get("project-update")` glyph) plus a refresh button; the snapshot buttons call `rpc.snapshotTags/Themes/Images` via `GitActionManager.runSnapshot(...)` on a `SwingWorker`+`InitProgressDialog`, then refresh — gateway-side edits then appear as normal file changes for per-file commit selection. Auto-refreshes every 15s and after each git op; `setChangesData` posts to the EDT.

**Dockable History panel** (`HistoryPanel.java`): commit log for the current branch *plus the upstream tracking branch* (so fetched commits show with remote ref badges before merge); borderless toolbar Refresh/Push/Fetch/Pull; Refs column rendered as colored badges; double-click → `CommitDetailPopup`; right-click → Checkout/Revert; "Load More" pagination; thread-safe `setData`.

**Manager classes** (`gateway`):
- `GitManager` — core JGit operations:
  - clone; fetch (remote-tracking refs only, `setUnshallow(true)` to backfill history on depth-1 repos); pull; push (current branch only by default, with `pushAllBranches`/`pushTags`/`forcePush` flags; non-fast-forward rejection → force-push confirmation in the Designer); commit (`amend`); status; branch list/create/checkout/delete with per-branch stash/restore; checkout commit (detached HEAD; `getCurrentBranch()` returns short-hash + "(detached)"); diff extraction; history log with ref decorations; commit file list/diff; discard; revert (aborts cleanly on conflict); remote list/add/remove/setUrl.
  - **Lean init fetch**: `setupLocalRepoImpl` does `lsRemote().setHeads(true)` to detect the default branch with no object download, a targeted single-branch shallow fetch for a fast checkout, then an unshallow fetch for full history. `detectDefaultBranchFromRefs` prefers main/master/develop, else the first head ref.
  - **Auth** (`setAuthentication`, the sole auth path): every remote must have an explicit credential FK — `GitRemoteCredentialsRecord.SshKeyId` or `HttpsCredentialId` referencing a user-level `GitUserSshKeyRecord` / `GitUserHttpsCredentialRecord`. Throws with a clear "pick a credential in the Remotes popup" message if no FK is set or the referenced credential is gone. Auth type (SSH vs HTTPS) is read from the remote URL in `.git/config`. Push/pull take a `remoteName` used for both the JGit target and credential lookup. Remote-dependent ops are guarded by `GitProjectsConfigRecord.hasRemote()` so local-only repos degrade gracefully.
  - **Pull conflict sentinel**: `pullImpl` throws `RuntimeException("MERGE_CONFLICT:" + files)` on `MergeStatus.CONFLICTING`, caught by the Designer's `handlePullAction` to open `MergeConflictPopup`.
  - **Metadata noise suppression**: `resource.json`/`thumbnail.png` are filtered from the changes list and commit file list when no sibling source file in the same resource dir also changed; applied in `getUncommitedChangesImpl`/`getCommitFilesImpl`.
- `GitProjectManager` / `GitTagManager` / `GitThemeManager` / `GitImageManager` — project resource import, and gateway-resource snapshot (tags/themes/images) into the project tree. The theme snapshot stages into a system temp dir and only swaps into `themes/` on full success (a mid-copy failure can't destroy committed theme files); tag snapshot bounds the provider read with a 30s timeout.

**Persistence** — five resource types on the 8.3 resource/config system. Each `*Record` class is now a mutable DTO façade over a nested `NamedResourceHandler`: the config is an inner Java `record`, and the resource name is `String.valueOf(numeric id)` so the RPC contract and Designer UI are unchanged (numeric long ids preserved). `GatewayHook` registers a `ResourceTypeMeta` per type and starts the handlers. A one-time `records.legacy.GitLegacyImporter` runs on first 8.3 startup: it registers the old SimpleORM tables' metas via `SchemaUpdater` (using minimal *public top-level* `Legacy*` `PersistentRecord` classes), reads each row, writes it as a resource, deletes the legacy row, and is idempotent (skips if the resource already exists; absent legacy tables on a fresh install are skipped). The five types:
- `GitProjectsConfigRecord` — project → git repo; `hasRemote()` is false for the empty-URI local-only sentinel; URI is kept in sync with the "origin" remote.
- `GitReposUsersRecord` — project↔user registration marker only (commit email comes from the Ignition user profile; auth is via the credential records).
- `GitRemoteCredentialsRecord` — per (project, user, remote); holds `SshKeyId`/`HttpsCredentialId` FKs into the user-level credential tables.
- `GitUserSshKeyRecord` — user-level SSH key (`IgnitionUser`, `KeyName`, `SSHKey`); shared across projects/remotes.
- `GitUserHttpsCredentialRecord` — user-level HTTPS credential (`IgnitionUser`, `HostPattern`, `UserName`, `Password`). The password is held as a `SecretConfig.embedded(...)`: encrypted on `setPassword` via `GatewayContext.getSystemEncryptionService().encryptToJson(Plaintext)` and decrypted on `getPassword` via `Secret.create(ctx, secretConfig).getPlaintext()`. `HostPattern` is purely an organizational label / disambiguator in the credential picker — auth never matches on it; remotes resolve credentials only via their explicit FK.

### Key libraries

- **Eclipse JGit 6.10.1** — all git operations
- **Apache MINA sshd** (`org.eclipse.jgit.ssh.apache`) — SSH transport (replaced the deprecated JSch backend)
- **Lombok 1.18.42** — annotation processing (designer)
- **IntelliJ forms_rt 7.0.3** — Swing form support for popups

## Module Packaging

`io.ia.sdk.modl` plugin (v0.4.1). Module ID `com.operametrix.ignition.git`; version is `2.0.0.<yyyyMMddHH>` (the build appends a timestamp). The compile SDK (`sdk_version = 8.3.6`, latest stable) is deliberately decoupled from `requiredIgnitionVersion` (`min_ignition_version = 8.3.0`) so the `.modl` installs on any 8.3.x gateway — don't recouple them. 8.3 declares module deps via `moduleDependencySpecs { }` (empty here), replacing the old `moduleDependencies`. `skipModlSigning` is `false` (signing enabled) — copy `gradle.template.properties` to `gradle.properties` (gitignored) and fill in signing credentials, or flip `skipModlSigning` to `true` locally for unsigned dev builds.

## Java Version

Java 17 source/target, set via the toolchain in each subproject's `build.gradle.kts`.

## Dependency Repositories

Resolved from Inductive Automation's Nexus and Maven Central, configured in `settings.gradle`.
