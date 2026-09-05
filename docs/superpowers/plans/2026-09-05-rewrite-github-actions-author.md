# Rewrite GitHub Actions Commit Authorship Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attribute all commit-activity automation commits to Harori, remove the GitHub Actions bot identity from `master` history, and keep changelog/dashboard data consistent after the SHA rewrite.

**Architecture:** First lock the future author and merge behavior with a workflow regression test. Then create an external bundle and perform the author rewrite in a disposable clone with Git's built-in `filter-branch`, rebuild generated history-dependent artifacts, verify the rewritten graph, and update `master` using an exact `--force-with-lease`. The primary workspace is synchronized only after the remote workflow and rebase-merged dashboard commit are verified.

**Tech Stack:** Git 2.54, PowerShell 7, Node.js 20+, Node test runner, GitHub Actions, GitHub pull requests.

---

## File map

- Modify: `.github/workflows/update-commit-activity.yml` — use Harori for generated commits and request rebase merge.
- Modify: `scripts/commit-activity.test.js` — lock the workflow author identity and merge method.
- Regenerate: `.changelog/entries.jsonl` — replace pre-rewrite hashes with rewritten `master` hashes.
- Regenerate: `README.md` — refresh the contributor table and cache-busted chart URLs.
- Regenerate: `docs/assets/commit-activity-by-day.svg` — rebuild daily activity from rewritten entries.
- Regenerate: `docs/assets/commit-activity-by-hour.svg` — rebuild hourly activity from rewritten entries.
- Preserve: `docs/superpowers/specs/2026-09-05-rewrite-github-actions-author-design.md` — approved design and safety constraints.
- Preserve: `docs/superpowers/plans/2026-09-05-rewrite-github-actions-author.md` — this execution plan.

## Fixed identities and paths

Use these exact values throughout the execution:

```text
Bot name:        github-actions[bot]
Bot email:       41898282+github-actions[bot]@users.noreply.github.com
Canonical name:  Harori
Canonical email: 100329525+Dangvinh77@users.noreply.github.com
Primary repo:    E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\Source
Planning tree:   C:\Users\VIP\.config\superpowers\worktrees\Source\rewrite-actions-author
Rewrite clone:   C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905
Backup bundle:   E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\MediFlow-before-actions-author-rewrite-20260905.bundle
Remote:          git@github.com:Dangvinh77/MediFlow.git
```

### Task 1: Lock future automation authorship and merge behavior

**Files:**
- Modify: `scripts/commit-activity.test.js:594`
- Modify: `.github/workflows/update-commit-activity.yml:50`

- [ ] **Step 1: Add failing workflow assertions**

Append these assertions inside the existing `workflow synchronizes every master push before generating dashboard output` test, after the existing staging assertion:

```js
  assert.match(workflow, /git config user\.name 'Harori'/);
  assert.match(
    workflow,
    /git config user\.email '100329525\+Dangvinh77@users\.noreply\.github\.com'/,
  );
  assert.match(workflow, /gh pr merge "\$pr_number" --rebase --auto/);
  assert.doesNotMatch(workflow, /41898282\+github-actions\[bot\]/);
  assert.doesNotMatch(workflow, /gh pr merge "\$pr_number" --squash/);
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run from the planning worktree:

```powershell
node --test --test-name-pattern "workflow synchronizes" scripts/commit-activity.test.js
```

Expected: FAIL because the workflow still contains `github-actions[bot]` and `--squash`.

- [ ] **Step 3: Change the workflow identity and merge method**

Replace the three relevant workflow lines with:

```yaml
          git config user.name 'Harori'
          git config user.email '100329525+Dangvinh77@users.noreply.github.com'
```

and:

```yaml
          gh pr merge "$pr_number" --rebase --auto || echo '::warning::Auto-merge is unavailable; maintainer merge required.'
```

- [ ] **Step 4: Run focused and complete tests and confirm GREEN**

```powershell
node --test --test-name-pattern "workflow synchronizes" scripts/commit-activity.test.js
node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
git diff --check
```

Expected: focused test passes, all 39+ repository tooling tests pass, and `git diff --check` prints nothing.

- [ ] **Step 5: Commit only the workflow and regression test**

```powershell
git add -- .github/workflows/update-commit-activity.yml scripts/commit-activity.test.js
git diff --cached --check
git commit -m "fix(ci): attribute dashboard commits to Harori"
```

Expected: the project hook may append a line to `.changelog/entries.jsonl`; leave that generated line uncommitted because the changelog will be rebuilt after the rewrite.

### Task 2: Freeze the remote state and create recovery artifacts

**Files:**
- Create outside repository: `E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\MediFlow-before-actions-author-rewrite-20260905.bundle`
- Create outside repository: `C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905\`

- [ ] **Step 1: Confirm the planning branch contains the approved documents and workflow fix**

```powershell
$planningTree = 'C:\Users\VIP\.config\superpowers\worktrees\Source\rewrite-actions-author'
git -C $planningTree status --short --branch
git -C $planningTree log -5 --oneline --decorate
```

Expected: branch `codex/rewrite-actions-author`; only hook-generated `.changelog/entries.jsonl` may be modified.

- [ ] **Step 2: Fetch and capture the exact remote master SHA**

```powershell
$primaryRepo = 'E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\Source'
git -C $primaryRepo fetch origin
$expectedRemoteMaster = git -C $primaryRepo rev-parse origin/master
$actualRemoteMaster = (git -C $primaryRepo ls-remote origin refs/heads/master).Split()[0]
if ($expectedRemoteMaster -ne $actualRemoteMaster) {
  throw "origin/master changed during capture: $expectedRemoteMaster != $actualRemoteMaster"
}
$expectedRemoteMaster
```

Expected: both values are the same 40-character SHA.

- [ ] **Step 3: Create and verify the external Git bundle**

```powershell
$backupBundle = 'E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\MediFlow-before-actions-author-rewrite-20260905.bundle'
if (Test-Path -LiteralPath $backupBundle) {
  throw "Backup already exists; do not overwrite it: $backupBundle"
}
git -C $primaryRepo bundle create $backupBundle --all
git bundle verify $backupBundle
```

Expected: bundle verification reports that all listed refs are complete.

- [ ] **Step 4: Create the isolated clone from the planning branch**

```powershell
$rewriteRepo = 'C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905'
$rewriteParent = Split-Path -Parent $rewriteRepo
if (Test-Path -LiteralPath $rewriteRepo) {
  throw "Rewrite clone already exists; preserve it and choose a new reviewed path: $rewriteRepo"
}
New-Item -ItemType Directory -Path $rewriteParent -Force | Out-Null
git clone --no-local --branch codex/rewrite-actions-author $primaryRepo $rewriteRepo
git -C $rewriteRepo remote set-url origin 'git@github.com:Dangvinh77/MediFlow.git'
git -C $rewriteRepo branch -M master
git -C $rewriteRepo fetch origin master
git -C $rewriteRepo update-ref refs/safety/expected-origin-master $expectedRemoteMaster
git -C $rewriteRepo merge-base --is-ancestor refs/safety/expected-origin-master master
if ($LASTEXITCODE -ne 0) { throw 'Planning history is not based on captured origin/master.' }
git -C $rewriteRepo config user.name 'Harori'
git -C $rewriteRepo config user.email '100329525+Dangvinh77@users.noreply.github.com'
```

Expected: the isolated `master` contains the captured remote plus the approved spec, plan, and workflow fix; the safety ref records the lease SHA.

### Task 3: Rewrite only the exact bot identity

**Files:**
- Rewrite in isolated clone: Git commit graph reachable from `master`

- [ ] **Step 1: Capture the pre-rewrite graph and assert the bot count**

Run this as one PowerShell block so the validation variables remain available through the rewrite:

```powershell
$rewriteRepo = 'C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905'
$botEmail = '41898282+github-actions[bot]@users.noreply.github.com'
$canonicalEmail = '100329525+Dangvinh77@users.noreply.github.com'
Push-Location $rewriteRepo
try {
  $beforeCount = [int](git rev-list --count master)
  $beforeMergeCount = [int](git rev-list --count --merges master)
  $beforeHistory = @(git log --reverse --format='%T%x09%an%x09%ae%x09%cn%x09%ce%x09%aI%x09%cI%x09%s' master)
  $beforeBot = @(git log master --author=$botEmail --format='%H%x09%T%x09%aI%x09%cI%x09%s')
  if ($beforeBot.Count -ne 3) {
    throw "Expected exactly 3 bot-authored commits, found $($beforeBot.Count)."
  }

  $env:FILTER_BRANCH_SQUELCH_WARNING = '1'
  $envFilter = @'
if [ "$GIT_AUTHOR_EMAIL" = "41898282+github-actions[bot]@users.noreply.github.com" ]; then
  export GIT_AUTHOR_NAME="Harori"
  export GIT_AUTHOR_EMAIL="100329525+Dangvinh77@users.noreply.github.com"
fi
if [ "$GIT_COMMITTER_EMAIL" = "41898282+github-actions[bot]@users.noreply.github.com" ]; then
  export GIT_COMMITTER_NAME="Harori"
  export GIT_COMMITTER_EMAIL="100329525+Dangvinh77@users.noreply.github.com"
fi
'@
  git filter-branch --force --env-filter $envFilter -- master
  if ($LASTEXITCODE -ne 0) { throw 'git filter-branch failed.' }

  $afterCount = [int](git rev-list --count master)
  $afterMergeCount = [int](git rev-list --count --merges master)
  $afterHistory = @(git log --reverse --format='%T%x09%an%x09%ae%x09%cn%x09%ce%x09%aI%x09%cI%x09%s' master)
  $expectedHistory = @($beforeHistory | ForEach-Object {
    $_.Replace("github-actions[bot]`t$botEmail", "Harori`t$canonicalEmail")
  })

  if ($afterCount -ne $beforeCount) { throw 'Commit count changed during rewrite.' }
  if ($afterMergeCount -ne $beforeMergeCount) { throw 'Merge topology count changed during rewrite.' }
  if (Compare-Object $expectedHistory $afterHistory -SyncWindow 0) {
    throw 'History metadata changed beyond the approved identity mapping.'
  }
  if (git log master --format='%ae%n%ce' | Select-String -SimpleMatch $botEmail) {
    throw 'Bot identity remains reachable from rewritten master.'
  }
} finally {
  Remove-Item Env:FILTER_BRANCH_SQUELCH_WARNING -ErrorAction SilentlyContinue
  Pop-Location
}
```

Expected: the three bot identities are mapped to Harori, commit/merge counts match, and every tree, date, subject, and non-bot identity remains unchanged.

- [ ] **Step 2: Record the rewritten head and inspect the mapped commits**

```powershell
$rewrittenHead = git -C $rewriteRepo rev-parse master
git -C $rewriteRepo log master --format='%h %an <%ae> | %cn <%ce> | %s' |
  Select-String -Pattern 'update commit activity dashboard'
$rewrittenHead
```

Expected: dashboard commits show Harori for the identities that previously matched the bot, and `$rewrittenHead` is a new SHA.

### Task 4: Rebuild history-dependent changelog and dashboard files

**Files:**
- Regenerate: `.changelog/entries.jsonl`
- Regenerate: `README.md`
- Regenerate: `docs/assets/commit-activity-by-day.svg`
- Regenerate: `docs/assets/commit-activity-by-hour.svg`

- [ ] **Step 1: Remove only the isolated clone's stale JSONL file**

```powershell
$rewriteRepo = 'C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905'
$entriesPath = [System.IO.Path]::GetFullPath((Join-Path $rewriteRepo '.changelog\entries.jsonl'))
$expectedEntriesPath = [System.IO.Path]::GetFullPath('C:\Users\VIP\.config\superpowers\history-rewrites\mediflow-actions-author-20260905\.changelog\entries.jsonl')
if ($entriesPath -ne $expectedEntriesPath) {
  throw "Refusing to remove unexpected path: $entriesPath"
}
if (-not (Test-Path -LiteralPath $entriesPath)) {
  throw "Expected changelog file is missing: $entriesPath"
}
Remove-Item -LiteralPath $entriesPath
```

Expected: only the isolated clone's `.changelog/entries.jsonl` is removed; the external bundle and primary workspace are untouched.

- [ ] **Step 2: Recreate entries from rewritten master**

```powershell
Push-Location $rewriteRepo
try {
  node scripts/changelog.js --sync
  if ($LASTEXITCODE -ne 0) { throw 'Changelog synchronization failed.' }
} finally {
  Pop-Location
}
```

Expected: sync scans rewritten `master`, excludes dashboard automation subjects, and creates a fresh JSONL file.

- [ ] **Step 3: Verify every entry is unique and an ancestor of rewritten HEAD**

```powershell
Push-Location $rewriteRepo
try {
@'
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');

const lines = fs.readFileSync('.changelog/entries.jsonl', 'utf8')
  .split(/\r?\n/)
  .filter(Boolean);
const entries = lines.map((line, index) => {
  try { return JSON.parse(line); }
  catch (error) { throw new Error(`Invalid JSONL line ${index + 1}: ${error.message}`); }
});
const hashes = new Set();
for (const entry of entries) {
  if (hashes.has(entry.hash)) throw new Error(`Duplicate hash: ${entry.hash}`);
  hashes.add(entry.hash);
  const ancestor = spawnSync('git', ['merge-base', '--is-ancestor', entry.hash, 'HEAD']);
  if (ancestor.status !== 0) throw new Error(`Stale or unreachable hash: ${entry.hash}`);
  if (/^docs\(tooling\): update commit activity dashboard(?: \(#\d+\))?$/i.test(entry.message)) {
    throw new Error(`Dashboard automation leaked into JSONL: ${entry.hash}`);
  }
}
console.log(`Verified ${entries.length} unique, reachable changelog entries.`);
'@ | node
  if ($LASTEXITCODE -ne 0) { throw 'Changelog reachability verification failed.' }
} finally {
  Pop-Location
}
```

Expected: prints the verified entry count and exits successfully.

- [ ] **Step 4: Regenerate and test the dashboard**

```powershell
Push-Location $rewriteRepo
try {
  node scripts/commit-activity.js
  node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
  node scripts/commit-activity.js --check
  git diff --check
  if ($LASTEXITCODE -ne 0) { throw 'Dashboard verification failed.' }
} finally {
  Pop-Location
}
```

Expected: all tests pass, dashboard check reports it is current, and diff check prints nothing.

- [ ] **Step 5: Commit only regenerated artifacts with the canonical identity**

```powershell
Push-Location $rewriteRepo
try {
  git config user.name 'Harori'
  git config user.email '100329525+Dangvinh77@users.noreply.github.com'
  git add -- .changelog/entries.jsonl README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
  git diff --cached --check
  git commit -m 'chore(tooling): reconcile changelog after author rewrite'
} finally {
  Pop-Location
}
```

Expected: one canonical Harori-authored commit. If a hook appends its own JSONL line afterward, leave that line uncommitted; the post-push workflow will reconcile it.

### Task 5: Perform the pre-push verification gate

**Files:**
- Verify only; no intended file changes

- [ ] **Step 1: Run complete tooling verification**

```powershell
Push-Location $rewriteRepo
try {
  node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
  git diff --check HEAD
  git fsck --full
} finally {
  Pop-Location
}
```

Expected: all tests pass, no whitespace errors, and Git reports no object corruption.

- [ ] **Step 2: Verify author history and workflow text**

```powershell
$botEmail = '41898282+github-actions[bot]@users.noreply.github.com'
$identityHits = git -C $rewriteRepo log master --format='%ae%n%ce' | Select-String -SimpleMatch $botEmail
if ($identityHits) { throw 'Bot author or committer remains on rewritten master.' }

$workflow = git -C $rewriteRepo show 'master:.github/workflows/update-commit-activity.yml'
if ($workflow -match [regex]::Escape($botEmail)) { throw 'Workflow still configures the bot email.' }
if ($workflow -notmatch "git config user\.name 'Harori'") { throw 'Workflow lacks canonical name.' }
if ($workflow -notmatch 'gh pr merge "\$pr_number" --rebase --auto') { throw 'Workflow does not request rebase merge.' }
if ($workflow -match 'gh pr merge "\$pr_number" --squash') { throw 'Workflow still requests squash merge.' }

git -C $rewriteRepo log -1 --format='%H%n%an <%ae>%n%s' master
```

Expected: no bot matches; workflow configures Harori and rebase merge; final local commit is authored by the canonical identity.

- [ ] **Step 3: Verify dashboard identity output**

```powershell
$readme = git -C $rewriteRepo show 'master:README.md'
$daily = git -C $rewriteRepo show 'master:docs/assets/commit-activity-by-day.svg'
$hourly = git -C $rewriteRepo show 'master:docs/assets/commit-activity-by-hour.svg'
if (([regex]::Matches(($readme -join "`n"), '(?m)^\| Harori \|')).Count -ne 1) { throw 'README Harori row count is not one.' }
if (([regex]::Matches(($daily -join "`n"), '<text class="legend"[^>]*>Harori \(')).Count -ne 1) { throw 'Daily chart Harori legend count is not one.' }
if (([regex]::Matches(($hourly -join "`n"), '<text class="name"[^>]*>Harori</text>')).Count -ne 1) { throw 'Hourly chart Harori row count is not one.' }
```

Expected: each public dashboard surface has exactly one canonical Harori entry.

### Task 6: Update remote master with an exact force-with-lease

**Files:**
- Update remote ref: `refs/heads/master`

- [ ] **Step 1: Confirm there is no open dashboard PR and the lease still matches**

```powershell
$openDashboardPrs = Invoke-RestMethod -Headers @{Accept='application/vnd.github+json'; 'User-Agent'='Codex'} -Uri 'https://api.github.com/repos/Dangvinh77/MediFlow/pulls?state=open&head=Dangvinh77:automation/commit-activity-dashboard'
if ($openDashboardPrs.Count -ne 0) { throw 'An automation dashboard PR is open; resolve it before rewriting master.' }

$expectedRemoteMaster = git -C $rewriteRepo rev-parse refs/safety/expected-origin-master
$currentRemoteMaster = (git -C $rewriteRepo ls-remote origin refs/heads/master).Split()[0]
if ($currentRemoteMaster -ne $expectedRemoteMaster) {
  throw "Remote master advanced; stop instead of overwriting $currentRemoteMaster."
}
```

Expected: zero open dashboard PRs and an exact lease match.

- [ ] **Step 2: Force-push only rewritten master with the explicit lease**

```powershell
$rewrittenHead = git -C $rewriteRepo rev-parse master
git -C $rewriteRepo push --force-with-lease="refs/heads/master:$expectedRemoteMaster" origin master:master
if ($LASTEXITCODE -ne 0) {
  throw 'Force push failed. Preserve the bundle and clone; do not retry without reassessing branch rules and remote state.'
}
$remoteAfterPush = (git -C $rewriteRepo ls-remote origin refs/heads/master).Split()[0]
if ($remoteAfterPush -ne $rewrittenHead) { throw 'Remote master does not match rewritten head.' }
```

Expected: remote `master` equals `$rewrittenHead`. If GitHub rejects force pushes, stop and ask the repository owner to temporarily allow force pushes for the applicable branch rule.

### Task 7: Verify the workflow-created commit before merging it

**Files:**
- Update remote branch through workflow: `automation/commit-activity-dashboard`
- Rebase-merge the generated dashboard PR into `master`

- [ ] **Step 1: Wait for the workflow run triggered by rewritten master**

```powershell
$deadline = (Get-Date).AddMinutes(5)
do {
  $runs = Invoke-RestMethod -Headers @{Accept='application/vnd.github+json'; 'User-Agent'='Codex'} -Uri 'https://api.github.com/repos/Dangvinh77/MediFlow/actions/workflows/update-commit-activity.yml/runs?per_page=10'
  $run = $runs.workflow_runs | Where-Object head_sha -eq $rewrittenHead | Select-Object -First 1
  if ($run -and $run.status -eq 'completed') { break }
  Start-Sleep -Seconds 10
} while ((Get-Date) -lt $deadline)
if (-not $run -or $run.status -ne 'completed') { throw 'Timed out waiting for rewrite workflow.' }
if ($run.conclusion -ne 'success') { throw "Workflow failed: $($run.html_url)" }
$run.html_url
```

Expected: workflow conclusion is `success` and it creates or updates the dashboard PR.

- [ ] **Step 2: Verify the automation branch commit is authored by Harori**

```powershell
git -C $rewriteRepo fetch origin '+refs/heads/automation/commit-activity-dashboard:refs/remotes/origin/automation/commit-activity-dashboard'
$automationAuthor = git -C $rewriteRepo log -1 --format='%an <%ae>' origin/automation/commit-activity-dashboard
if ($automationAuthor -ne 'Harori <100329525+Dangvinh77@users.noreply.github.com>') {
  throw "Unexpected automation author: $automationAuthor"
}
$automationHead = git -C $rewriteRepo rev-parse origin/automation/commit-activity-dashboard
$automationHead
```

Expected: exact canonical Harori identity.

- [ ] **Step 3: Fetch and rebase-merge the dashboard PR**

Use the GitHub connector to fetch the single open PR whose base is `master` and head is `automation/commit-activity-dashboard`. Verify `mergeable=true` and that its `head_sha` exactly equals the 40-character `$automationHead` printed in Step 2. Call `github_merge_pull_request` with `repository_full_name="Dangvinh77/MediFlow"`, `merge_method="rebase"`, `expected_head_sha` set to that exact `$automationHead` value, and `pr_number` set to the numeric `number` returned for that single open PR.

Expected: `merged=true`. Do not use squash because a bot-created PR would credit the bot as the squash author.

- [ ] **Step 4: Verify rewritten remote history and the follow-up workflow**

```powershell
git -C $rewriteRepo fetch origin master
$finalRemoteMaster = git -C $rewriteRepo rev-parse origin/master
$finalAuthor = git -C $rewriteRepo log -1 --format='%an <%ae>' origin/master
if ($finalAuthor -ne 'Harori <100329525+Dangvinh77@users.noreply.github.com>') {
  throw "Dashboard commit on master has unexpected author: $finalAuthor"
}
if (git -C $rewriteRepo log origin/master --format='%ae%n%ce' | Select-String -SimpleMatch '41898282+github-actions[bot]@users.noreply.github.com') {
  throw 'Bot identity returned to remote master.'
}
$finalRemoteMaster
```

Poll the workflow runs for `$finalRemoteMaster` with the loop from Step 1. Expected: the follow-up run succeeds, reports no dashboard changes, and opens no new PR.

### Task 8: Synchronize and verify the primary workspace

**Files:**
- Update primary local `master` to rewritten `origin/master`
- Preserve unexpected local changes and existing recovery stashes

- [ ] **Step 1: Audit the primary workspace before resetting old history**

```powershell
$primaryRepo = 'E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\Source'
$primaryRoot = (git -C $primaryRepo rev-parse --show-toplevel).Trim()
if ([System.IO.Path]::GetFullPath($primaryRoot) -ne [System.IO.Path]::GetFullPath($primaryRepo)) {
  throw "Unexpected repository root: $primaryRoot"
}
$primaryStatus = @(git -C $primaryRepo status --porcelain)
$unexpected = @($primaryStatus | Where-Object { $_ -notmatch '^ M \.changelog/entries\.jsonl$' })
if ($unexpected.Count -gt 0) {
  $unexpected | ForEach-Object { Write-Host $_ }
  throw 'Unexpected user changes found; stop before resetting the primary workspace.'
}
```

Expected: only the known hook-generated changelog line may be modified. Any other change stops execution.

- [ ] **Step 2: Preserve the stale hook line and reset to explicit rewritten master**

```powershell
if ($primaryStatus.Count -gt 0) {
  git -C $primaryRepo stash push -m 'backup-stale-changelog-before-actions-author-rewrite' -- .changelog/entries.jsonl
}
git -C $primaryRepo fetch origin master
$fetchedMaster = git -C $primaryRepo rev-parse origin/master
if ($fetchedMaster -ne $finalRemoteMaster) {
  throw "Fetched master $fetchedMaster differs from verified remote $finalRemoteMaster"
}
git -C $primaryRepo reset --hard origin/master
```

Expected: reset is permitted by the approved history rewrite, targets the verified `origin/master`, and the old hook line remains recoverable in the named stash.

- [ ] **Step 3: Run final local checks**

```powershell
Push-Location $primaryRepo
try {
  node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
  node scripts/commit-activity.js --check
  node scripts/changelog.js --summary
  git diff --check
  git status --short --branch
  git rev-parse HEAD
  git rev-parse origin/master
} finally {
  Pop-Location
}
```

Expected: all tests pass, dashboard is current, tracked workspace is clean, and local/remote SHAs match.

- [ ] **Step 4: Report recovery and collaborator instructions**

Keep the verified bundle at:

```text
E:\DEV\Coding_Resource\Project\e_PROJECT\Semester4\MediFlow-before-actions-author-rewrite-20260905.bundle
```

Tell collaborators with clean local branches to run:

```powershell
git fetch origin
git switch master
git reset --hard origin/master
```

Collaborators with uncommitted or unpublished work must stash or branch that work first, then rebase/cherry-pick it onto the rewritten `origin/master`. Report that GitHub's Contributors cache may need approximately 24 hours to remove the bot.
