# Changelog Auto-Sync and Chart Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically reconcile eligible `master` commits into the JSONL changelog and ensure README always loads the current single-Harori SVG charts.

**Architecture:** Add an idempotent `--sync` command to the existing changelog CLI, driven by local Git history and protected by explicit bot/dashboard exclusion rules. Run synchronization before dashboard generation on every `master` push, then version SVG links with deterministic content digests so GitHub image caching cannot retain old contributor labels.

**Tech Stack:** Node.js 20 built-ins (`node:test`, `node:assert/strict`, `child_process`, `crypto`, `fs`), Git CLI, GitHub Actions, Markdown, SVG.

---

## File map

- Create `scripts/changelog-sync.test.js`: black-box tests against temporary real Git repositories.
- Modify `scripts/changelog.js`: strict JSONL parsing, eligibility checks, atomic incremental synchronization, and the `--sync` CLI command.
- Modify `scripts/commit-activity.js`: content digests, cache-busted image links, and clearer timestamp wording.
- Modify `scripts/commit-activity.test.js`: cache-busting, canonical-label, and workflow regression coverage.
- Modify `.github/workflows/update-commit-activity.yml`: run for every `master` push, synchronize before generation, and stage the changelog.
- Modify `docs/ai/10-git-workflow.md`: document automatic reconciliation, exclusions, and cache behavior.
- Regenerate `README.md`, `docs/assets/commit-activity-by-day.svg`, and `docs/assets/commit-activity-by-hour.svg`.

### Task 1: Add strict, idempotent changelog synchronization

**Files:**
- Create: `scripts/changelog-sync.test.js`
- Modify: `scripts/changelog.js`

- [ ] **Step 1: Write the failing ordinary-commit and idempotency tests**

Create helpers that initialize an isolated repository, make dated commits, invoke the real CLI, and
read JSONL:

```js
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const changelogCli = path.join(__dirname, 'changelog.js');

function git(root, args, env = {}) {
  const result = spawnSync('git', args, {
    cwd: root,
    encoding: 'utf8',
    env: { ...process.env, ...env },
  });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

function createRepository(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mediflow-changelog-sync-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  fs.mkdirSync(path.join(root, '.changelog'));
  fs.writeFileSync(path.join(root, '.changelog', 'entries.jsonl'), '');
  git(root, ['init', '-b', 'master']);
  git(root, ['config', 'user.name', 'Human']);
  git(root, ['config', 'user.email', 'human@example.com']);
  return root;
}

function commit(root, filename, message, date, identity = {}) {
  fs.writeFileSync(path.join(root, filename), message);
  git(root, ['add', filename]);
  git(root, ['commit', '-m', message], {
    GIT_AUTHOR_NAME: identity.name || 'Human',
    GIT_AUTHOR_EMAIL: identity.email || 'human@example.com',
    GIT_AUTHOR_DATE: date,
    GIT_COMMITTER_NAME: identity.name || 'Human',
    GIT_COMMITTER_EMAIL: identity.email || 'human@example.com',
    GIT_COMMITTER_DATE: date,
  });
  return git(root, ['rev-parse', 'HEAD']);
}

function sync(root) {
  return spawnSync(process.execPath, [changelogCli, '--sync'], {
    cwd: root,
    encoding: 'utf8',
  });
}

function entries(root) {
  const source = fs.readFileSync(path.join(root, '.changelog', 'entries.jsonl'), 'utf8');
  return source.trim() ? source.trim().split(/\r?\n/).map(JSON.parse) : [];
}

test('--sync appends every missing human commit oldest first and is idempotent', (t) => {
  const root = createRepository(t);
  const first = commit(root, 'first.txt', 'feat: first', '2026-09-04T01:00:00+07:00');
  const second = commit(root, 'second.txt', 'fix: second', '2026-09-05T02:00:00+07:00');

  const initial = sync(root);
  assert.equal(initial.status, 0, initial.stderr);
  assert.deepEqual(entries(root).map((entry) => entry.hash), [first, second]);
  const afterFirstSync = fs.readFileSync(
    path.join(root, '.changelog', 'entries.jsonl'),
    'utf8',
  );

  const repeated = sync(root);
  assert.equal(repeated.status, 0, repeated.stderr);
  assert.equal(
    fs.readFileSync(path.join(root, '.changelog', 'entries.jsonl'), 'utf8'),
    afterFirstSync,
  );
  assert.match(repeated.stdout, /added 0/i);
});
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
node --test scripts/changelog-sync.test.js
```

Expected: FAIL because `--sync` is not recognized and no records are appended.

- [ ] **Step 3: Add strict parsing and atomic synchronization**

In `scripts/changelog.js`, import `renameSync` and add focused helpers:

```js
function readExistingJsonl() {
  if (!existsSync(JSONL_PATH)) return { source: '', entries: [], hashes: new Set() };
  const source = readFileSync(JSONL_PATH, 'utf8');
  const entries = [];
  for (const [index, line] of source.split(/\r?\n/).entries()) {
    if (!line.trim()) continue;
    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      throw new Error(`Invalid changelog JSON at line ${index + 1}`);
    }
    if (!entry || typeof entry.hash !== 'string' || !/^[0-9a-f]{40}$/i.test(entry.hash)) {
      throw new Error(`Invalid changelog hash at line ${index + 1}`);
    }
    entries.push(entry);
  }
  return { source, entries, hashes: new Set(entries.map((entry) => entry.hash)) };
}

function writeEntriesAtomically(existingSource, additions) {
  if (additions.length === 0) return;
  const prefix = existingSource.length === 0 || existingSource.endsWith('\n')
    ? existingSource
    : `${existingSource}\n`;
  const output = `${prefix}${additions.map(JSON.stringify).join('\n')}\n`;
  const temporaryPath = `${JSONL_PATH}.tmp-${process.pid}`;
  writeFileSync(temporaryPath, output, 'utf8');
  renameSync(temporaryPath, JSONL_PATH);
}

function cmdSync() {
  ensureDir();
  const existing = readExistingJsonl();
  const hashes = run('git log --reverse --format="%H" HEAD').split('\n').filter(Boolean);
  if (hashes.length === 0) throw new Error('Unable to read Git history from HEAD');

  const additions = [];
  for (const hash of hashes) {
    if (existing.hashes.has(hash)) continue;
    const entry = buildEntry(hash);
    if (!entry) throw new Error(`Unable to build changelog entry for ${hash}`);
    additions.push(entry);
  }
  writeEntriesAtomically(existing.source, additions);
  console.log(
    `[changelog] Sync complete: scanned ${hashes.length}, added ${additions.length}, excluded 0.`,
  );
}
```

Dispatch `--sync` before `--init` in `main()`. Wrap the `main()` call so thrown failures are
reported as `[changelog] <message>` and set a non-zero exit code without a stack trace.

- [ ] **Step 4: Run the synchronization test and verify GREEN**

Run:

```bash
node --test scripts/changelog-sync.test.js
```

Expected: PASS with one test and no warnings.

- [ ] **Step 5: Add malformed-input atomicity coverage**

Append:

```js
test('--sync rejects malformed existing JSONL without modifying it', (t) => {
  const root = createRepository(t);
  commit(root, 'first.txt', 'feat: first', '2026-09-05T03:00:00+07:00');
  const changelog = path.join(root, '.changelog', 'entries.jsonl');
  fs.writeFileSync(changelog, '{broken\n');

  const result = sync(root);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /line 1/i);
  assert.equal(fs.readFileSync(changelog, 'utf8'), '{broken\n');
});
```

- [ ] **Step 6: Run all Task 1 tests**

Run:

```bash
node --test scripts/changelog-sync.test.js
```

Expected: 2 tests pass.

- [ ] **Step 7: Commit Task 1**

```bash
git add scripts/changelog.js scripts/changelog-sync.test.js
git commit -m "feat(tooling): sync missing changelog commits"
```

Leave the post-commit hook's newly appended JSONL line unstaged.

### Task 2: Exclude automation while retaining human merge commits

**Files:**
- Modify: `scripts/changelog-sync.test.js`
- Modify: `scripts/changelog.js`

- [ ] **Step 1: Write failing eligibility tests**

Add a test that creates human, bot, dashboard, and human merge commits in the temporary repository:

```js
test('--sync excludes bots and dashboard automation but retains human merges', (t) => {
  const root = createRepository(t);
  const human = commit(root, 'human.txt', 'feat: human work', '2026-09-05T04:00:00+07:00');

  git(root, ['switch', '-c', 'feature']);
  const feature = commit(root, 'feature.txt', 'feat: branch work', '2026-09-05T05:00:00+07:00');
  git(root, ['switch', 'master']);
  git(root, ['merge', '--no-ff', 'feature', '-m', 'Merge feature'], {
    GIT_AUTHOR_NAME: 'Maintainer',
    GIT_AUTHOR_EMAIL: 'maintainer@example.com',
    GIT_AUTHOR_DATE: '2026-09-05T06:00:00+07:00',
    GIT_COMMITTER_NAME: 'Maintainer',
    GIT_COMMITTER_EMAIL: 'maintainer@example.com',
    GIT_COMMITTER_DATE: '2026-09-05T06:00:00+07:00',
  });
  const humanMerge = git(root, ['rev-parse', 'HEAD']);

  commit(
    root,
    'bot.txt',
    'chore: generated bot work',
    '2026-09-05T07:00:00+07:00',
    {
      name: 'github-actions[bot]',
      email: '41898282+github-actions[bot]@users.noreply.github.com',
    },
  );
  commit(
    root,
    'dashboard.txt',
    'docs(tooling): update commit activity dashboard (#11)',
    '2026-09-05T08:00:00+07:00',
  );

  const result = sync(root);
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(entries(root).map((entry) => entry.hash), [human, feature, humanMerge]);
  assert.match(result.stdout, /excluded 2/i);
});
```

- [ ] **Step 2: Run the eligibility test and verify RED**

Run:

```bash
node --test --test-name-pattern="excludes bots" scripts/changelog-sync.test.js
```

Expected: FAIL because all five commits are currently added.

- [ ] **Step 3: Implement explicit eligibility policy**

Add:

```js
function isDashboardAutomationCommit(entry) {
  const name = entry.author.trim().toLowerCase();
  const email = entry.email.trim().toLowerCase();
  const subject = entry.message.trim();
  const bot = name.endsWith('[bot]')
    || /^[^@]*\[bot\]@users\.noreply\.github\.com$/i.test(email);
  const dashboardSubject =
    /^docs\(tooling\): update commit activity dashboard(?: \(#\d+\))?$/i.test(subject);
  const dashboardMerge =
    /^Merge pull request #\d+ from .+\/automation\/commit-activity-dashboard$/i.test(subject);
  return bot || dashboardSubject || dashboardMerge;
}
```

Change `cmdSync()` to increment an `excluded` counter and skip entries for which
`isDashboardAutomationCommit(entry)` is true. Keep ordinary human merge commits unchanged.

- [ ] **Step 4: Run all synchronization tests and verify GREEN**

Run:

```bash
node --test scripts/changelog-sync.test.js
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add scripts/changelog.js scripts/changelog-sync.test.js
git commit -m "fix(tooling): exclude dashboard bot commits"
```

Leave the hook-generated changelog line unstaged.

### Task 3: Version SVG links and lock one canonical Harori series

**Files:**
- Modify: `scripts/commit-activity.js`
- Modify: `scripts/commit-activity.test.js`

- [ ] **Step 1: Write the failing cache-busting test**

Import `contentDigest` from the generator and add:

```js
test('generated README versions each SVG from its content', (t) => {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commit-activity-versioned-'));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  fs.mkdirSync(path.join(rootDir, '.changelog'));
  fs.writeFileSync(
    path.join(rootDir, '.changelog', 'entries.jsonl'),
    `${JSON.stringify(entry())}\n`,
  );
  fs.writeFileSync(
    path.join(rootDir, 'README.md'),
    '# Test\n\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n',
  );

  const first = generate({ rootDir });
  const readme = fs.readFileSync(path.join(rootDir, 'README.md'), 'utf8');
  const daily = fs.readFileSync(path.join(rootDir, 'docs', 'assets', 'commit-activity-by-day.svg'), 'utf8');
  const hourly = fs.readFileSync(path.join(rootDir, 'docs', 'assets', 'commit-activity-by-hour.svg'), 'utf8');

  assert.match(
    readme,
    new RegExp(`commit-activity-by-day\\.svg\\?v=${contentDigest(daily)}`),
  );
  assert.match(
    readme,
    new RegExp(`commit-activity-by-hour\\.svg\\?v=${contentDigest(hourly)}`),
  );
  assert.deepEqual(generate({ rootDir }).changed, []);
  assert.ok(first.changed.includes('README.md'));
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
node --test --test-name-pattern="versions each SVG" scripts/commit-activity.test.js
```

Expected: FAIL because `contentDigest` and versioned image URLs do not exist.

- [ ] **Step 3: Implement deterministic asset digests**

Add `createHash` from `node:crypto` and:

```js
function contentDigest(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex').slice(0, 12);
}
```

Render the daily and hourly SVG strings before README Markdown. Pass their digests into
`renderDashboardMarkdown` and render:

```js
![Commits by day](docs/assets/commit-activity-by-day.svg?v=${versions.daily})

![Commits by hour](docs/assets/commit-activity-by-hour.svg?v=${versions.hourly})
```

Change the leading line to:

```js
Changelog updated through **${model.generatedAt} ${model.timeZone}** · **${model.totalCommits} unique commits**
```

Export `contentDigest` for direct testing.

- [ ] **Step 4: Add exact canonical-label assertions**

Extend the repository-alias generation test:

```js
assert.equal((daily.match(/>Harori \(\d+\)<\/text>/g) || []).length, 1);
assert.equal((hourly.match(/<text class="name"[^>]*>Harori<\/text>/g) || []).length, 1);
assert.equal((readme.match(/^\| Harori \|/gm) || []).length, 1);
```

Also assert that neither configured Harori email is present in README or either SVG.

- [ ] **Step 5: Run dashboard tests and verify GREEN**

Run:

```bash
node --test scripts/commit-activity.test.js
```

Expected: all dashboard tests pass.

- [ ] **Step 6: Commit Task 3**

```bash
git add scripts/commit-activity.js scripts/commit-activity.test.js
git commit -m "fix(tooling): refresh cached activity charts"
```

Leave the hook-generated changelog line unstaged.

### Task 4: Wire synchronization into protected-branch automation

**Files:**
- Modify: `scripts/commit-activity.test.js`
- Modify: `.github/workflows/update-commit-activity.yml`
- Modify: `docs/ai/10-git-workflow.md`

- [ ] **Step 1: Tighten the failing workflow regression test**

Replace the current workflow assertions with:

```js
test('workflow synchronizes every master push before generating dashboard output', () => {
  const workflow = fs.readFileSync(
    path.join(__dirname, '..', '.github', 'workflows', 'update-commit-activity.yml'),
    'utf8',
  );
  assert.match(workflow, /push:\s*\n\s+branches: \[master\]/);
  assert.doesNotMatch(workflow, /\n\s+paths:/);
  assert.ok(
    workflow.indexOf('node scripts/changelog.js --sync')
      < workflow.indexOf('node scripts/commit-activity.js'),
  );
  assert.match(
    workflow,
    /git status --porcelain -- \.changelog\/entries\.jsonl README\.md/,
  );
  assert.match(
    workflow,
    /git add \.changelog\/entries\.jsonl README\.md docs\/assets\/commit-activity-by-day\.svg docs\/assets\/commit-activity-by-hour\.svg/,
  );
  assert.match(workflow, /Dashboard is already current\./);
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
node --test --test-name-pattern="synchronizes every master push" scripts/commit-activity.test.js
```

Expected: FAIL because the workflow still has path filters and does not run `--sync`.

- [ ] **Step 3: Update the workflow**

Make the trigger:

```yaml
on:
  push:
    branches: [master]
  workflow_dispatch:
```

Run both test files, then synchronize before generation:

```yaml
- name: Test changelog and dashboard
  run: node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js

- name: Synchronize changelog
  run: node scripts/changelog.js --sync

- name: Generate dashboard
  run: node scripts/commit-activity.js
```

Include `.changelog/entries.jsonl` in both `git status --porcelain -- ...` and `git add ...`.
Keep the deterministic automation branch, force-with-lease, PR reuse, and squash auto-merge logic.

- [ ] **Step 4: Document the new behavior**

In `docs/ai/10-git-workflow.md`, state:

- local hooks remain a convenience and are not the only capture mechanism;
- every `master` push reconciles missing human commits from Git history;
- human merge commits count, bots/dashboard automation do not;
- the automation PR carries changelog plus generated dashboard outputs;
- SVG query digests invalidate GitHub's image cache;
- `Changelog updated through` is the latest tracked commit time, not workflow wall-clock time.

- [ ] **Step 5: Run both test files**

Run:

```bash
node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
```

Expected: all tests pass with zero failures.

- [ ] **Step 6: Commit Task 4**

```bash
git add .github/workflows/update-commit-activity.yml docs/ai/10-git-workflow.md scripts/commit-activity.test.js
git commit -m "ci(tooling): reconcile dashboard on master pushes"
```

Leave the hook-generated changelog line unstaged.

### Task 5: Regenerate, verify, and prepare integration

**Files:**
- Modify: `README.md`
- Modify if content changes: `docs/assets/commit-activity-by-day.svg`
- Modify if content changes: `docs/assets/commit-activity-by-hour.svg`

- [ ] **Step 1: Preserve local hook-only changelog lines**

Record the current JSONL diff and stash only that path:

```bash
git diff -- .changelog/entries.jsonl
git stash push -m "local hook changelog lines before dashboard generation" -- .changelog/entries.jsonl
```

Verify `git status --short` no longer lists the changelog before generating.

- [ ] **Step 2: Regenerate committed artifacts**

Run:

```bash
node scripts/commit-activity.js
node scripts/commit-activity.js --check
```

Expected: README uses two `?v=<12 hex>` image URLs and the check exits 0.

- [ ] **Step 3: Verify rendered contributor identities**

Run a Node assertion that checks the exact public labels:

```bash
node -e "const fs=require('fs');const r=fs.readFileSync('README.md','utf8');const d=fs.readFileSync('docs/assets/commit-activity-by-day.svg','utf8');const h=fs.readFileSync('docs/assets/commit-activity-by-hour.svg','utf8');if((r.match(/^\| Harori \|/gm)||[]).length!==1)throw Error('README Harori count');if((d.match(/>Harori \(\d+\)<\/text>/g)||[]).length!==1)throw Error('daily Harori count');if((h.match(/<text class=\"name\"[^>]*>Harori<\/text>/g)||[]).length!==1)throw Error('hourly Harori count');if(!/commit-activity-by-day\.svg\?v=[0-9a-f]{12}/.test(r)||!/commit-activity-by-hour\.svg\?v=[0-9a-f]{12}/.test(r))throw Error('asset versions');"
```

Expected: exit 0 with no output.

- [ ] **Step 4: Commit generated output**

```bash
git add README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
git diff --cached --check
git commit -m "docs(tooling): regenerate activity dashboard"
```

Do not add `.changelog/entries.jsonl` to this commit.

- [ ] **Step 5: Restore the user's local hook lines**

```bash
git stash pop
git status --short
```

Expected: only `.changelog/entries.jsonl` is modified by local hook records.

- [ ] **Step 6: Run full fresh verification**

Temporarily stash the hook-only JSONL path again, then run:

```bash
node --test scripts/changelog-sync.test.js scripts/commit-activity.test.js
node scripts/commit-activity.js --check
node scripts/commit-activity.js --check
git diff --check origin/master...HEAD
```

Expected: all synchronization and dashboard tests pass, both dashboard checks exit 0, and diff
check prints nothing. Restore the hook-only stash afterward.

- [ ] **Step 7: Review the final branch**

Run:

```bash
git status --short --branch
git log --oneline origin/master..HEAD
git diff --stat origin/master...HEAD
```

Confirm every spec requirement maps to a tested change and no unrelated workspace files are staged.
The branch is then ready for review, push, and protected-branch PR integration when authorized.
