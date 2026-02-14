# Ignition Git Module

[![License](https://img.shields.io/badge/license-Beerware-green.svg)](LICENSE.md)

An Ignition module that embeds a Git client directly into the Ignition Designer, enabling version control of project resources without leaving the development environment.

## Features

- Initialize a git repository directly from the Designer — enter the repo URI and credentials without needing Gateway admin access. The status bar shows "Not configured" for unregistered projects; click it to open the setup dialog. Supports both HTTPS and SSH repositories with dynamic field switching based on the URI.
- Link an Ignition project with a remote repo (Gateway Webpage)
- Link an Ignition user to a git project, with SSH or user/password authentication (Gateway Webpage)
- Manage git credentials directly from the Designer by clicking the user icon in the status bar — supports both HTTPS (username/password) and SSH (private key) authentication
- Commit resources with last-modification timestamps shown per resource (Designer, on project saved or from git toolbar)
- Push & Pull resources (Designer, from git toolbar) — push sends only the current branch by default (no surprise pushes of experimental branches); pulled changes are reflected immediately in the Designer
- Branch management: list, create, checkout, and delete branches with automatic stash/restore of uncommitted changes (Designer, from status bar branch button)
- Export of the gateway configuration: tags, images, themes (Designer, from git toolbar)
- Side-by-side diff viewer for reviewing changes before committing
- Commit history browser: paginated log viewer accessible from the toolbar, with drill-down into changed files per commit and side-by-side diff of historical changes
- Dockable Commit panel: an always-visible panel tabbed alongside the Project Browser for at-a-glance uncommitted changes, inline commits, diff viewing, and discarding changes
- Dockable History panel: an always-visible panel showing commit log with ref badges, plus Push and Pull buttons
- Commissioning configuration file for easy deployment

### Screenshots

- Commit popup:<br/>
![Commit Popup](./img/CommitPopup.png)
- Toolbar:<br/>
![Git Toolbar](./img/GitToolbar.png)
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
