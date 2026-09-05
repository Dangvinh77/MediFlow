# Contributor Aliases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the two email identities currently displayed as separate `Harori` contributors while preserving email-based separation for everyone not explicitly configured.

**Architecture:** Add an optional JSON alias registry that resolves normalized emails to stable contributor IDs and canonical names before aggregation. Keep aliases outside renderers, extend the existing generator/CLI to load the registry, and make the existing GitHub Actions workflow regenerate when aliases change.

**Tech Stack:** Dependency-free Node.js, `node:test`, JSON, SVG, Markdown, GitHub Actions.

---

## File structure

- Create `.changelog/contributor-aliases.json`: canonical contributor IDs, names, and email aliases.
- Modify `scripts/commit-activity.js`: parse aliases, resolve identities, and load an optional alias path.
- Modify `scripts/commit-activity.test.js`: parser, aggregation, generator, CLI, privacy, and workflow tests.
- Modify `.github/workflows/update-commit-activity.yml`: trigger regeneration when aliases change.
- Modify `docs/ai/10-git-workflow.md`: document alias maintenance.
- Regenerate `README.md` and `docs/assets/commit-activity-*.svg`: show one combined `Harori` contributor.

### Task 1: Parse aliases and resolve canonical contributor identities

**Files:**

- Modify: `scripts/commit-activity.js`
- Modify: `scripts/commit-activity.test.js`

- [ ] **Step 1: Write failing alias parser tests**

Add `parseAliases` to the test import and add:

```js
test('parseAliases normalizes emails and returns canonical identities', () => {
  const aliases = parseAliases(JSON.stringify({
    contributors: [{
      id: 'harori',
      name: 'Harori',
      emails: [' FIRST@Example.com ', 'second@example.com'],
    }],
  }));

  assert.deepEqual(aliases.get('first@example.com'), { id: 'harori', name: 'Harori' });
  assert.deepEqual(aliases.get('second@example.com'), { id: 'harori', name: 'Harori' });
});

test('parseAliases rejects ambiguous or malformed configuration', () => {
  assert.throws(() => parseAliases('{broken'), /Invalid contributor aliases JSON/);
  assert.throws(
    () => parseAliases(JSON.stringify({ contributors: [{ id: '', name: 'A', emails: ['a@example.com'] }] })),
    /Invalid contributor id at index 0/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({ contributors: [
      { id: 'a', name: 'A', emails: ['same@example.com'] },
      { id: 'b', name: 'B', emails: ['same@example.com'] },
    ] })),
    /Email alias belongs to multiple contributors: same@example.com/,
  );
});
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
node --test scripts/commit-activity.test.js
```

Expected: the new tests fail because `parseAliases` is not exported.

- [ ] **Step 3: Implement strict alias parsing**

Add this focused parser before `aggregateEntries`:

```js
function parseAliases(json) {
  let value;
  try {
    value = JSON.parse(json);
  } catch {
    throw new Error('Invalid contributor aliases JSON');
  }
  if (value === null || typeof value !== 'object' || Array.isArray(value)
      || !Array.isArray(value.contributors)) {
    throw new Error('Contributor aliases must contain a contributors array');
  }

  const ids = new Set();
  const aliases = new Map();
  value.contributors.forEach((contributor, index) => {
    if (contributor === null || typeof contributor !== 'object' || Array.isArray(contributor)) {
      throw new Error(`Invalid contributor at index ${index}`);
    }
    const id = typeof contributor.id === 'string' ? contributor.id.trim() : '';
    const name = typeof contributor.name === 'string' ? contributor.name.trim() : '';
    if (!id) throw new Error(`Invalid contributor id at index ${index}`);
    if (!name) throw new Error(`Invalid contributor name at index ${index}`);
    if (ids.has(id)) throw new Error(`Duplicate contributor id: ${id}`);
    if (!Array.isArray(contributor.emails) || contributor.emails.length === 0) {
      throw new Error(`Invalid contributor emails at index ${index}`);
    }
    ids.add(id);

    contributor.emails.forEach((email) => {
      const normalized = typeof email === 'string' ? email.trim().toLowerCase() : '';
      if (!normalized) throw new Error(`Invalid email alias at index ${index}`);
      if (aliases.has(normalized)) {
        throw new Error(`Email alias belongs to multiple contributors: ${normalized}`);
      }
      aliases.set(normalized, { id, name });
    });
  });
  return aliases;
}
```

Export `parseAliases`.

- [ ] **Step 4: Run tests and verify GREEN**

Run `node --test scripts/commit-activity.test.js`.

Expected: all tests pass.

- [ ] **Step 5: Write failing aggregation tests**

Add:

```js
test('aggregateEntries merges configured aliases under the canonical name', () => {
  const entries = parseEntries([
    entry({ author: 'Old Harori', email: 'first@example.com' }),
    entry({ hash: 'b'.repeat(40), author: 'Other Name', email: 'SECOND@example.com' }),
  ].map(JSON.stringify).join('\n'));
  const aliases = parseAliases(JSON.stringify({
    contributors: [{
      id: 'harori',
      name: 'Harori',
      emails: ['first@example.com', 'second@example.com'],
    }],
  }));

  const model = aggregateEntries(entries, 'Asia/Saigon', aliases);

  assert.equal(model.contributors.length, 1);
  assert.equal(model.contributors[0].name, 'Harori');
  assert.equal(model.contributors[0].total, 2);
});

test('aggregateEntries keeps unconfigured identical display names separate', () => {
  const entries = parseEntries([
    entry({ author: 'Same', email: 'first@example.com' }),
    entry({ hash: 'b'.repeat(40), author: 'Same', email: 'second@example.com' }),
  ].map(JSON.stringify).join('\n'));

  assert.equal(aggregateEntries(entries).contributors.length, 2);
});
```

- [ ] **Step 6: Run tests and verify RED**

Run `node --test scripts/commit-activity.test.js`.

Expected: the merge test fails with two contributors because aggregation ignores aliases.

- [ ] **Step 7: Resolve identities before aggregation**

Change the function signature from:

```js
function aggregateEntries(entries, timeZone = DEFAULT_TIME_ZONE) {
  const byEmail = new Map();
```

to:

```js
function aggregateEntries(entries, timeZone = DEFAULT_TIME_ZONE, aliases = new Map()) {
  const byIdentity = new Map();
```

Replace the opening identity-resolution block inside the entry loop, from `const emailKey` through
the latest-name update, with:

```js
    const normalizedEmail = entry.email.trim().toLowerCase();
    const configured = aliases.get(normalizedEmail);
    const identityKey = configured ? `alias:${configured.id}` : `email:${normalizedEmail}`;
    const local = localParts(entry.timestampMs, timeZone);
    let contributor = byIdentity.get(identityKey);

    if (!contributor) {
      contributor = {
        name: configured ? configured.name : entry.author.trim(),
        configuredName: Boolean(configured),
        latestNameTimestampMs: entry.timestampMs,
        latestTimestampMs: entry.timestampMs,
        total: 0,
        days: new Map(),
        hours: Array(24).fill(0),
      };
      byIdentity.set(identityKey, contributor);
    }

    if (!contributor.configuredName && entry.timestampMs >= contributor.latestNameTimestampMs) {
      contributor.name = entry.author.trim();
      contributor.latestNameTimestampMs = entry.timestampMs;
    }
```

Leave the total/day/hour/latest statements immediately following this block in place. Replace
`const states = [...byEmail.values()]` with `const states = [...byIdentity.values()]`.

Do not include `identityKey`, normalized email, or alias email in the returned model.

- [ ] **Step 8: Run tests and commit**

Run:

```bash
node --test scripts/commit-activity.test.js
git add scripts/commit-activity.js scripts/commit-activity.test.js
git commit -m "feat(tooling): support contributor alias identities"
```

Expected: all tests pass and only the two script files are committed. Leave hook-generated
`.changelog/entries.jsonl` changes unstaged.

### Task 2: Load aliases in the generator and CLI

**Files:**

- Modify: `scripts/commit-activity.js`
- Modify: `scripts/commit-activity.test.js`

- [ ] **Step 1: Write a failing file-level generator test**

Extend the existing temporary-directory generator test with an alias file containing two emails,
two matching JSONL rows, and:

```js
const result = generate({
  rootDir,
  aliases: 'aliases.json',
  timeZone: 'Asia/Saigon',
});

assert.equal(result.model.contributors.length, 1);
assert.equal(result.model.contributors[0].name, 'Harori');
assert.equal(result.model.contributors[0].total, 2);
```

Add a separate test that omits the default aliases file and verifies generation still succeeds.

- [ ] **Step 2: Run tests and verify RED**

Run `node --test scripts/commit-activity.test.js`.

Expected: explicit aliases are ignored and the model contains two contributors.

- [ ] **Step 3: Load an optional alias registry**

Add `aliases` after `changelog` in the existing `generate` parameter list. Then replace:

```js
  const entries = parseEntries(fs.readFileSync(entriesPath, 'utf8'));
  const model = aggregateEntries(entries, timeZone);
```

with:

```js
  const aliasesPath = path.resolve(rootDir, aliases || '.changelog/contributor-aliases.json');
  const entries = parseEntries(fs.readFileSync(entriesPath, 'utf8'));
  const aliasMap = fs.existsSync(aliasesPath)
    ? parseAliases(fs.readFileSync(aliasesPath, 'utf8'))
    : new Map();
  const model = aggregateEntries(entries, timeZone, aliasMap);
```

- [ ] **Step 4: Write a failing explicit CLI test**

Add `--aliases`, followed by the temporary alias path, to the existing CLI success invocation and
assert the generated README contains one `Harori` contributor row.

- [ ] **Step 5: Run tests and verify RED**

Run `node --test scripts/commit-activity.test.js`.

Expected: CLI exits `1` with `Unknown option: --aliases`.

- [ ] **Step 6: Add the CLI option**

Add `'--aliases': 'aliases'` to `valueOptions` and add this help line:

```text
  --aliases PATH     Contributor alias registry
```

- [ ] **Step 7: Run tests and commit**

Run:

```bash
node --test scripts/commit-activity.test.js
git add scripts/commit-activity.js scripts/commit-activity.test.js
git commit -m "feat(tooling): load contributor aliases in generator"
```

Expected: all tests pass and hook-generated changelog lines remain unstaged.

### Task 3: Add the Harori registry and automation trigger

**Files:**

- Create: `.changelog/contributor-aliases.json`
- Modify: `.github/workflows/update-commit-activity.yml`
- Modify: `scripts/commit-activity.test.js`
- Modify: `docs/ai/10-git-workflow.md`

- [ ] **Step 1: Add a failing workflow/config integration test**

Add:

```js
test('repository aliases merge Harori and trigger dashboard automation', () => {
  const root = path.join(__dirname, '..');
  const aliases = parseAliases(fs.readFileSync(
    path.join(root, '.changelog', 'contributor-aliases.json'),
    'utf8',
  ));
  const workflow = fs.readFileSync(
    path.join(root, '.github', 'workflows', 'update-commit-activity.yml'),
    'utf8',
  );

  assert.deepEqual(
    aliases.get('phamdangvinh2002@gmail.com'),
    { id: 'harori', name: 'Harori' },
  );
  assert.deepEqual(
    aliases.get('100329525+dangvinh77@users.noreply.github.com'),
    { id: 'harori', name: 'Harori' },
  );
  assert.match(workflow, /\.changelog\/contributor-aliases\.json/);
});
```

- [ ] **Step 2: Run tests and verify RED**

Run `node --test scripts/commit-activity.test.js`.

Expected: test fails because the registry file does not exist.

- [ ] **Step 3: Create the registry**

Create `.changelog/contributor-aliases.json` exactly as specified in
`docs/superpowers/specs/2026-09-04-contributor-aliases-design.md`.

- [ ] **Step 4: Add the workflow trigger**

Under `on.push.paths` in `.github/workflows/update-commit-activity.yml`, add:

```yaml
      - .changelog/contributor-aliases.json
```

- [ ] **Step 5: Document alias maintenance**

Append to the `Commit activity dashboard` subsection in `docs/ai/10-git-workflow.md`:

```md
Khi một thành viên commit bằng nhiều email, khai báo một canonical contributor trong
`.changelog/contributor-aliases.json`. Mỗi email chỉ được thuộc một contributor; generator sẽ
dừng với lỗi rõ ràng nếu ID hoặc email bị trùng. Email không có trong registry vẫn được nhóm độc
lập theo email chuẩn hóa.
```

- [ ] **Step 6: Run tests and commit**

Run:

```bash
node --test scripts/commit-activity.test.js
git diff --check
git add .changelog/contributor-aliases.json .github/workflows/update-commit-activity.yml scripts/commit-activity.test.js docs/ai/10-git-workflow.md
git commit -m "chore(tooling): configure contributor aliases"
```

Expected: tests pass; only the registry, workflow, tests, and documentation are committed. Do not
stage `.changelog/entries.jsonl`.

### Task 4: Regenerate, verify, review, and update the pull request

**Files:**

- Modify: `README.md`
- Modify: `docs/assets/commit-activity-by-day.svg`
- Modify: `docs/assets/commit-activity-by-hour.svg`

- [ ] **Step 1: Generate from committed changelog data while preserving hook lines**

Run:

```bash
git stash push -m "temporary alias dashboard generation" -- .changelog/entries.jsonl
node scripts/commit-activity.js
git stash pop
```

Expected: README and both SVGs change; all hook-generated changelog lines return unstaged.

- [ ] **Step 2: Verify the combined contributor and privacy behavior**

Run:

```bash
node --test scripts/commit-activity.test.js
node scripts/commit-activity.js --check
git diff --check
```

Temporarily stash `.changelog/entries.jsonl` around `--check` as in Step 1 so the check compares
against the committed source. Then run this assertion:

```bash
node -e "const fs=require('fs');const assert=require('assert/strict');const readme=fs.readFileSync('README.md','utf8');assert.equal((readme.match(/^\\| Harori \\|/gm)||[]).length,1);assert.doesNotMatch(readme,/@/);"
```

Also parse both SVG files as XML and verify each contains one `Harori` legend/row label.

- [ ] **Step 3: Commit generated output**

Run:

```bash
git add README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
git commit -m "docs(tooling): merge Harori commit activity"
```

Expected: only the three generated files are committed. Leave `.changelog/entries.jsonl`
unstaged after the hook runs.

- [ ] **Step 4: Request code review and resolve all Critical/Important findings**

Review `HEAD` against the commit before Task 1 with emphasis on alias ambiguity, privacy,
backward compatibility, deterministic output, and workflow triggering. Apply review fixes using a
new failing test before production changes.

- [ ] **Step 5: Run fresh final verification**

Run with the hook-generated changelog temporarily stashed:

```bash
node --check scripts/commit-activity.js
node --test scripts/commit-activity.test.js
node scripts/commit-activity.js --check
git diff --check
```

Expected: syntax check exits `0`, all tests pass, generator reports up to date, and diff check has
no errors. Restore the changelog stash and confirm it is the only unstaged file.

- [ ] **Step 6: Push the existing feature branch**

Run:

```bash
git push origin codex/commit-activity-dashboard
```

Expected: GitHub PR #9 updates to the reviewed commit; no direct push to `master` occurs.
