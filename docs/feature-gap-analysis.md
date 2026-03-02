# Ignition Git Module — Feature Gap Analysis vs GitKraken / Git SCM

## Current capabilities

Init, clone, commit (with file selection + timestamp + amend), push (current branch only by default; force push with confirmation on rejection), pull (with merge conflict resolution — per-file "Accept Ours"/"Accept Theirs" + conflict diff viewer + abort/complete merge), fetch (without merge — updates remote-tracking branches only; fetched commits visible in History panel via upstream-aware log), branch list/create/checkout/delete, auto-stash on checkout, checkout commit (detached HEAD), status (uncommitted changes), SSH + HTTPS auth, credential management UI, resource import/export, commissioning automation, Designer toolbar + status bar, side-by-side diff viewer, commit history browser with per-commit file list and historical diff, dockable Commit panel with inline commit/discard/diff/amend, dockable History panel with commit log/ref badges/push/fetch/pull, discard (revert) uncommitted changes, multi-remote management (add/edit/remove named remotes with per-remote credentials) with push/pull/fetch remote target selection.

---

## Missing features (basic to advanced)

### Tier 1 — Fundamental gaps

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 1 | ~~**Diff viewer**~~ | ~~Can't preview what changed before committing~~ | **Implemented.** Double-click a resource in the commit popup to open a side-by-side diff viewer. |
| 2 | ~~**Commit log / history**~~ | ~~No way to see past commits~~ | **Implemented.** History toolbar button opens paginated commit log; double-click a commit to see changed files; double-click a file to view its diff at that commit. |
| 3 | ~~**Fetch without merge**~~ | ~~Pull always merges, no way to just fetch~~ | **Implemented.** Fetch button in History panel toolbar retrieves remote commits without merging. Single-remote projects fetch immediately; multi-remote projects show a remote selector popup. History panel's commit log includes the upstream tracking branch so fetched commits appear with remote ref badges before pulling. |
| 4 | ~~**Discard / restore changes**~~ | ~~No way to revert a file to its last committed state~~ | **Implemented.** Right-click a resource in the Changes panel and select "Discard Changes" to revert tracked files to HEAD or delete untracked files. Also available as `discardChanges` RPC method. |
| 5 | **Staged vs unstaged distinction** | All changes shown as one flat list | Standard Git separates working tree from index |

### Tier 2 — Core branching and collaboration gaps

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 6 | **Merge (explicit)** | No standalone merge command; only implicit via pull | Can't merge feature branches locally |
| 7 | ~~**Merge conflict resolution UI**~~ | ~~Conflicts on pull silently fail with a log warning~~ | **Implemented.** Pull detects `MergeStatus.CONFLICTING` and opens a `MergeConflictPopup` listing conflicting files. Per-file "Accept Ours" / "Accept Theirs" resolution with conflict diff viewing (ours vs theirs via `DiffViewerPopup`). Global "Accept All Ours/Theirs", "Abort Merge", and "Complete Merge" actions. Window close triggers abort confirmation to prevent leaving the repo in a conflicted state. |
| 8 | **Branch rename** | Must delete and recreate | Minor but standard |
| 9 | **Delete remote branches** | Only deletes local branches | Stale remote branches accumulate |
| 10 | **.gitignore management** | No UI for ignoring files | Users must manually edit .gitignore |
| 11 | ~~**Amend last commit**~~ | ~~No way to fix a typo in the last commit~~ | **Implemented.** "Amend last commit" checkbox in both CommitPopup and CommitPanel pre-fills the last commit message and allows message-only or message+files amend. |
| 12 | **Tag management** | Tags are imported/exported as Ignition resources only, no Git tag create/delete/annotate | Confusing overlap between Git tags and Ignition tags |

### Tier 3 — Intermediate workflow gaps

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 13 | **Rebase** | No linear history option; pull always merges | Many teams require rebase workflows |
| 14 | **Cherry-pick** | Can't pick specific commits across branches | Common for hotfixes |
| 15 | ~~**Revert commit**~~ | ~~No way to undo a specific past commit~~ | **Implemented.** "Revert Commit" button in CommitDetailPopup and right-click context menu in HistoryPanel create a new commit that undoes the target commit's changes via `git revert`. Conflicts are detected and the revert is aborted cleanly. |
| 16 | **Blame / annotate** | Can't see who changed which line and when | Useful for debugging and accountability |
| 17 | **File history** | No way to track a single resource's change history | |
| 18 | **Commit search / filter** | No way to search history by author, date, or message | |
| 19 | **Stash management UI** | Auto-stash exists on checkout but no manual stash/list/apply/drop | Users can't manually shelve work |
| 20 | **Reset (soft/mixed/hard)** | No way to undo commits or unstage files | Only hard reset exists during initial setup |
| 21 | ~~**Selective push**~~ | ~~Pushes ALL branches + ALL tags every time~~ | **Implemented.** Push now sends only the current branch with no tags by default, matching standard `git push` behavior. The RPC method accepts `pushAllBranches` and `pushTags` flags for callers that need the old behavior. |
| 22 | **Visual commit graph** | No DAG/tree visualization of branch history | The original Graph panel was replaced with a simplified History panel (commit table with ref badges). A true DAG with colored lanes and merge lines does not exist. |
| 23 | ~~**Multiple remotes**~~ | ~~Hardcoded to single remote (origin)~~ | **Implemented.** Remotes popup (status bar button) manages named remotes with per-remote credentials. Push and pull accept a remote name parameter; when 2+ remotes exist, a dropdown lets the user choose the target. Single-remote projects behave as before. |

### Tier 4 — Advanced workflow and team gaps

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 24 | **Interactive rebase** | Can't squash, reorder, or edit commits | Needed for clean history |
| 25 | **Pull request integration** | No GitHub/GitLab/Bitbucket PR creation or review | Teams must switch to browser |
| 26 | ~~**Force push (with lease)**~~ | ~~No way to push after rebase or amend~~ | **Implemented.** Push result checking detects `REJECTED_NONFASTFORWARD` and offers a force-push confirmation dialog. Also surfaces other push rejection types as errors. |
| 27 | **Branch comparison** | Can't compare two branches side-by-side | |
| 28 | **Commit templates** | No standardized commit message format | |
| 29 | **Background auto-fetch** | Status bar shows branch name but not ahead/behind counts | GitKraken shows unsynced indicators |
| 30 | **Diff/merge tool integration** | No external tool launch (Beyond Compare, KDiff3, etc.) | |
| 31 | **Undo/redo operations** | No GUI-level undo for accidental commits or merges | Tower's signature feature |

### Tier 5 — Advanced Git features

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 32 | **Submodules** | No nested repository support | |
| 33 | **Git LFS** | No large file tracking | Relevant for Ignition image resources |
| 34 | **GPG/SSH commit signing** | No signature verification | |
| 35 | **Reflog** | No recovery from lost commits or branches | |
| 36 | **Bisect** | No binary search for bug-introducing commits | |
| 37 | **Hooks** | No pre-commit, pre-push, or post-merge hooks | |
| 38 | **Worktrees** | No multiple working directories | |
| 39 | **Shallow/partial clone** | Always full clone | Slow for large repos |

### Tier 6 — Modern GUI / AI features

| # | Feature | Impact | Notes |
|---|---------|--------|-------|
| 40 | **AI commit message generation** | Must write messages manually | GitKraken generates from diff |
| 41 | **Hunk/line-level staging** | All-or-nothing file selection | Can't commit part of a file |
| 42 | **Syntax-highlighted diffs** | Diff viewer exists but without syntax highlighting | |
| 43 | **Drag-and-drop operations** | N/A without a commit graph | |
| 44 | **Multi-repo workspace** | One project = one repo, no bulk ops | |
| 45 | **Issue tracker integration** | No Jira/Trello/GitHub Issues link | |

---

## Priority recommendation

### Completed so far

1. ~~**Diff viewer** (#1)~~ — **Done.**
2. ~~**Commit log/history** (#2)~~ — **Done.**
3. ~~**Discard changes** (#4)~~ — **Done.**
4. ~~**Amend last commit** (#11)~~ — **Done.**
5. ~~**Selective push** (#21)~~ — **Done.**
6. ~~**Force push** (#26)~~ — **Done.**
7. ~~**Multiple remotes** (#23)~~ — **Done.**
8. ~~**Revert commit** (#15)~~ — **Done.**
9. ~~**Merge conflict resolution UI** (#7)~~ — **Done.**
10. ~~**Fetch without merge** (#3)~~ — **Done.**

### Proposed next steps

Top 5 highest-impact additions for Ignition Designer teams, ordered by urgency:

1. **Commit search / filter** (#18) — as commit history grows, finding a specific change becomes tedious. Add a text field to HistoryPanel that filters by message or author. Can be done client-side (filter loaded dataset) with an optional gateway-side `LogCommand` `--grep`/`--author` parameter for deeper search. Scope: ~1 JTextField in HistoryPanel, optional gateway-side `LogCommand` params.
2. **Delete remote branches** (#9) — stale remote branches accumulate and clutter the branch popup. Trivial: `git push --delete origin <branch>`. Add a "Delete remote branch" option to BranchPopup for remote branch entries. Scope: ~1 new RPC method, ~1 menu item in BranchPopup.
3. **Branch rename** (#8) — trivial via JGit's `RenameBranchCommand`. Add a "Rename" option to BranchPopup. Eliminates the delete-and-recreate workaround. Scope: ~1 new RPC method, ~1 button in BranchPopup.
4. **Cherry-pick** (#14) — useful for hotfix workflows. Add to CommitDetailPopup and HistoryPanel context menu alongside "Revert". JGit's `CherryPickCommand` is analogous to `RevertCommand` (already implemented). Needs conflict detection similar to revert. Scope: mirrors revert commit implementation — RPC method + UI hook + conflict handling.
5. **Merge (explicit)** (#6) — standalone merge command for feature branches. Requires a branch selector UI and conflict resolution (reuse `MergeConflictPopup`). Higher effort but covers workflows where pull-based merge is insufficient.

Deferred: **.gitignore management (#10)** — would let users permanently exclude metadata files like `thumbnail.png` at the git level rather than relying on display-side filtering; worth considering after the top 5.
