# Ignition Git Module

[![License](https://img.shields.io/badge/license-Beerware-green.svg)](LICENSE.md)

An Ignition module that embeds a Git client directly into the Ignition Designer, enabling version control of project resources without leaving the development environment.

## Features

- **Setup wizard** — clone from a remote or create a local-only repo from the Designer
- **Remotes** — add/edit/remove named remotes, each bound to a saved credential.
- **User credentials** — manage SSH keys and per-host HTTPS credentials (GitHub/GitLab/Azure/Bitbucket hint text), shared across projects.
- **Commit** — from the dockable Commit panel or a popup, with per-resource timestamps and amend-last-commit.
- **Push / Fetch / Pull** — from the History panel, with multi-remote support
- **Merge conflict resolution** — pull conflicts open a dedicated UI with per-file Accept Ours/Theirs, a side-by-side conflict diff, and abort/complete-merge actions.
- **Branches** — list, create, checkout, delete, with automatic stash/restore of uncommitted changes.
- **Snapshot gateway resources** — capture tags / themes / images into the project from the Commit panel header so they become reviewable, per-file-selectable changes before committing.
- **Metadata noise suppression** — `resource.json`/`thumbnail.png`-only churn is hidden when no sibling source file changed.
- **History browser** — paginated commit log with ref badges, per-commit file list, and side-by-side historical diff.
- **Revert / checkout any past commit** (detached HEAD) from the History panel or commit detail view.
- **Side-by-side diff viewer** for reviewing changes before committing.

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
