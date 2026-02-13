# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ignition Git Module — a Java module for the Inductive Automation Ignition SCADA platform (8.1.0+) that embeds a Git client into the Ignition Designer. It enables committing, pushing, pulling project resources, branch management, and exporting gateway configuration directly from the Designer toolbar and status bar. Built by AXONE-IO, version 1.0.3.

## Build Commands

```bash
# Build the module (produces .modl file)
./gradlew build

# Build output location
# build/Git.modl
```

There are no automated tests in this project. Testing is done manually by installing the .modl on an Ignition gateway and using the Designer UI. A Docker Compose setup exists in `docker/` for local testing environments.

## Architecture

This is a Gradle multi-module project following the **Ignition Module SDK pattern** with four scope-specific subprojects:

```
common    (scope: CDG)  — Shared interface + abstract base class
client    (scope: C)    — Vision client hook (minimal)
designer  (scope: CD)   — Designer UI: toolbar, popups, status bar
gateway   (scope: G)    — Backend: all git operations, persistence, web config pages
```

The root `build.gradle.kts` uses the `io.ia.sdk.modl` Gradle plugin to assemble the `.modl` file.

**Scopes**: C = Client, D = Designer, G = Gateway. Code in a given scope only runs in that Ignition context.

### Key Design Patterns

**Hook classes** are the entry points for each scope. Each implements the Ignition lifecycle (`setup`/`startup`/`shutdown`):
- `ClientHook` — registers the script module on the client
- `DesignerHook` — initializes toolbar actions, status bar (with clickable branch button), user verification timer; uses `ModuleRPCFactory` to call gateway methods remotely. On startup, checks `isProjectRegistered()` — if unregistered, shows a minimal "Not configured" status bar instead of the full git UI. After successful init via `InitRepoPopup`, calls `reinitializeAfterSetup()` to rebuild the full status bar. Exposes a static `instance` field for callbacks from `GitActionManager`.
- `GatewayHook` — creates DB schema, loads commissioning config, registers web config pages

**Script interface pattern**: `GitScriptInterface` (common) defines the API. `AbstractScriptModule` (common) decorates it with Ignition annotations. `GatewayScriptModule` (gateway) provides the real implementation. `ClientScriptModule` (client) proxies all calls via RPC. Designer calls gateway methods via RPC.

**Designer project refresh**: After any gateway-side operation that modifies the Ignition project (pull, checkout, init), the Designer must call `GitBaseAction.pullProjectFromGateway()` to sync its local project state with the gateway via reflection on the Designer frame's `pullAndResolve()` method. Without this call, gateway-side `GitProjectManager.importProject()` updates the gateway but the Designer UI won't reflect the changes.

**Designer popups** (`designer` module) are Swing `JFrame` dialogs with abstract callbacks overridden via anonymous subclasses in `GitActionManager`:
- `CommitPopup` — select changes and enter commit message; displays resource name, type, author, and last-modification timestamp (formatted `yyyy-MM-dd HH:mm`). Double-clicking a resource row opens the `DiffViewerPopup` via the `onDiffRequested` callback.
- `DiffViewerPopup` — side-by-side diff viewer comparing HEAD (committed) vs working tree content. Uses an LCS-based line diff algorithm with color-coded backgrounds (green for added, red for removed) and synchronized scrolling. Opened from `CommitPopup` via `GitActionManager.showDiffViewer()`, which calls `rpc.getResourceDiff()` to fetch content from the gateway.
- `PullPopup` — toggle import of tags/themes/images
- `BranchPopup` — list/create/checkout/delete branches
- `CredentialsPopup` — manage email, username, password/SSH key for an already-registered project
- `InitRepoPopup` — initialize a git repo for an unregistered project: enter repo URI (dynamic HTTPS/SSH field switching based on URI prefix), email, and credentials. On success, creates DB records + clones the repo + refreshes the Designer project.

**Manager classes** in `gateway` encapsulate domain logic:
- `GitManager` — core JGit operations (clone, fetch, pull, push, commit, status, branch list/create/checkout/delete with per-branch stash/restore, resource diff content extraction)
- `GitProjectManager`, `GitTagManager`, `GitThemeManager`, `GitImageManager` — resource import/export
- `GitCommissioningUtils` — file-based config loading for automated deployment

**Persistence** uses Ignition's SimpleORM with two record types:
- `GitProjectsConfigRecord` — maps Ignition projects to git repos
- `GitReposUsersRecord` — maps Ignition users to git credentials (SSH or HTTPS)

### Key Libraries

- **Eclipse JGit 6.10.1** — all git operations
- **Apache MINA sshd** (via `org.eclipse.jgit.ssh.apache`) — SSH transport (replaced the deprecated JSch-based `org.eclipse.jgit.ssh.jsch` in favor of the actively maintained Apache MINA sshd backend)
- **Lombok 1.18.42** — annotation processing in designer module
- **IntelliJ forms_rt 7.0.3** — Swing form support for Designer popups

## Module Packaging

The root `build.gradle.kts` uses `io.ia.sdk.modl` plugin (v0.4.1) to assemble the `.modl` file. Module ID is `com.axone_io.ignition.git`. The version includes a build timestamp (`yyyyMMddHH`). Module signing is disabled by default (`skipModlSigning = true`); to enable, copy `gradle.template.properties` to `gradle.properties` and fill in signing credentials.

## Java Version

Java 11 source and target. Set via Java toolchain in each subproject's `build.gradle.kts`.

## Dependency Repositories

Dependencies are resolved from Inductive Automation's Nexus server and Maven Central. These are configured in `settings.gradle`.
