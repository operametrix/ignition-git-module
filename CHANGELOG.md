# Changelog

All notable changes to the Ignition Git Module are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.3] - 2026-07-01

### Added
- History panel now has a commit-graph column (vscode-git-graph style): a
  swimlane rendering of the commit DAG with colored lane lines and per-commit
  node dots, drawn to the left of the message. The gateway commit log now emits a
  space-separated `parents` column, and the Designer computes the lane layout via
  incremental lane-tracking, recomputed over all loaded commits on each refresh /
  "Load More".
- History now walks **all** local and remote-tracking branches (`git log --all`
  style) instead of only the current branch plus its upstream, so divergent
  branches appear as distinct lanes in the graph rather than being omitted.

### Fixed
- Designer status bar no longer renders at roughly double its normal height. The
  git status-bar panel used a default `FlowLayout` whose 5px top/bottom vertical
  gap padded an extra 10px around the buttons, forcing Ignition to grow the
  status-bar row. The panel now uses `FlowLayout(LEFT, 4, 0)`, so the bar is only
  as tall as the buttons require. Applies to both the registered and unregistered
  status bars.
- Manage Remotes and User Credentials popups now show a title-bar icon
  (cloud and verified-user glyphs respectively), matching the Branch Management
  popup which was previously the only dialog to set one.
- Icons no longer render jagged/blurry on HiDPI or scaled displays. `IconUtils`
  rasterized the bundled SVGs to a fixed 16×16 bitmap via `ImageIO`, which the
  display then upscaled. Icons are now loaded as resolution-independent vectors
  via Ignition's platform `SvgIconUtil`, which render sharply at any display
  scale. This covers the status-bar glyphs, the Commit and History
  dockable-panel tab icons, and every popup title-bar icon.
- The status-bar refresh timer no longer throws on the EDT when a gateway RPC
  call fails transiently (e.g. HTTP 503 while the gateway/module is still
  starting or restarting). The `isRegisteredUser` poll is now wrapped so a
  transient failure is logged and skipped instead of surfacing as an
  `UndeclaredThrowableException`.

### Changed
- Extracted the duplicated window-icon loading logic into a shared
  `IconUtils.setWindowIcon(Window, bundleKey)` helper, and routed all popups that
  previously rasterized their own title-bar icon through it (Branch Management,
  Create Branch, Manage Remotes, User Credentials, Commit, Commit Detail, Diff
  Viewer, Merge Conflict).
