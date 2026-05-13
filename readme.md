# Ignition Git Module

[![License](https://img.shields.io/badge/license-Beerware-green.svg)](LICENSE.md)

An Ignition module that embeds a Git client directly into the Ignition Designer, enabling version control of project resources without leaving the development environment.

## Features

- Initialize a git repository directly from the Designer — a wizard-style setup dialog asks whether you have a remote repository. Choose "Yes, clone from remote" to enter a repo URI and pick a pre-configured credential (auto-filtered to SSH keys or HTTPS credentials based on the URI), or "No, initialize locally" to create a local-only git repo (the commit author email is taken from your Ignition user profile). Local repositories can have a remote added later. The status bar shows a "Configure" button for unregistered projects; click it to open the setup wizard.
- Manage remotes: add, edit, and remove named git remotes (e.g. "origin", "upstream") from a dedicated status bar button; each remote points to a user-level credential (SSH key or HTTPS credential) selected from a dropdown
- Manage user-level git credentials (SSH keys and HTTPS credentials per host, with provider-specific hint text for GitHub/GitLab/Azure/Bitbucket) from the Designer by clicking the user icon in the status bar; these credentials are shared across all projects and selectable per-remote
- Commit resources with last-modification timestamps shown per resource (Designer, from the Commit panel or Commit popup); supports amending the last commit (fix the message or add forgotten files) via an "Amend last commit" checkbox in both the Commit popup and Commit panel
- Push, Fetch & Pull resources from the History panel toolbar — push sends only the current branch by default (no surprise pushes of experimental branches); if the remote rejects the push (e.g. after amending a pushed commit), a confirmation dialog offers force push. Fetch retrieves remote commits and updates remote-tracking branches without touching the working directory or merging — after fetching, the History panel shows incoming commits with their remote ref badges so you can review what changed before pulling. Pull fetches and merges; pulled changes are reflected immediately in the Designer. When a pull results in merge conflicts, a dedicated Merge Conflict popup appears listing each conflicting file with per-file "Accept Ours" / "Accept Theirs" resolution, a conflict diff viewer showing the ours vs theirs content side-by-side, and global "Accept All Ours/Theirs", "Abort Merge", and "Complete Merge" actions. When multiple remotes are configured, push, fetch, and pull show a remote selector dropdown to choose the target (e.g. "origin" vs "upstream"); with a single remote the experience is unchanged (no extra popup). For local-only repositories (no remote), push, fetch, and pull show a friendly warning instead of crashing
- Branch management: list, create, checkout, and delete branches with automatic stash/restore of uncommitted changes (Designer, from status bar branch button)
- Metadata noise suppression: `resource.json` and `thumbnail.png` changes are automatically hidden from the uncommitted changes list and commit file list when no sibling source file (e.g. `view.json`, `code.py`) in the same resource directory also changed — eliminates the clutter caused by Ignition updating timestamps and thumbnails on every save
- Side-by-side diff viewer for reviewing changes before committing
- Commit history browser: the dockable History panel shows a paginated log of commits for the current branch with ref badges, drill-down into changed files per commit, and side-by-side diff of historical changes
- Revert commit: undo a specific past commit by creating a new commit that reverses its changes — accessible from the "Revert Commit" button in the commit detail view or via right-click context menu in the History panel. Conflicts are detected and the revert is aborted cleanly
- Checkout commit: inspect the project at any point in history by checking out a specific commit (detached HEAD) — accessible from the "Checkout" button in the commit detail view or via right-click context menu in the History panel. The status bar shows the short hash with "(detached)" indicator
- Dockable Commit panel: an always-visible panel tabbed alongside the Project Browser for at-a-glance uncommitted changes, inline commits, diff viewing, and discarding changes — persists across all workspace switches (Perspective, Vision, SFC, Scripting, etc.)
- Dockable History panel: an always-visible panel showing commit log with ref badges, plus Push, Fetch, and Pull buttons — persists across all workspace switches

### Screenshots

- Commit popup:<br/>
![Commit Popup](./img/CommitPopup.png)
- Status Bar:<br/>
![Git Status Bar](./img/GitStatusBar.png)

## Installation

### Prerequisites

- Java (JDK >= 11)
- An Ignition gateway (8.1.0+)

### Building from source

1. Clone the repository: `git clone <repo-url>`
2. Build the module: `./gradlew build`
3. Install the resulting `build/Git.modl` on your Ignition gateway.

The Gradle wrapper is included in the project, so no separate Gradle installation is needed.

**Note:** If you previously had the AXONE-IO version of this module installed (`com.axone_io.ignition.git`), you must uninstall it before installing this new version (`com.operametrix.ignition.git`), as Ignition treats them as separate modules.

## Contributing

Contributions are welcome! To get started:

1. Fork the repo and clone your fork.
2. Create a branch for your feature: `git checkout -b feature/describe-your-feature`
3. Make your changes and commit with a clear message.
4. Push to your fork and open a pull request.

## Acknowledgements

This module was originally created by [AXONE-IO](https://www.axone-io.com/) (Enzo Sagnelonge). We are grateful to AXONE-IO for their work in building and open-sourcing this project.

## License

This project is licensed under the Beerware license. See [LICENSE.md](LICENSE.md) for details.
