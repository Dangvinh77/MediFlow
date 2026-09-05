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

test('--sync excludes bots and dashboard automation but retains human merges', (t) => {
  const root = createRepository(t);
  const human = commit(
    root,
    'human.txt',
    'feat: human work',
    '2026-09-05T04:00:00+07:00',
  );

  git(root, ['switch', '-c', 'feature']);
  const feature = commit(
    root,
    'feature.txt',
    'feat: branch work',
    '2026-09-05T05:00:00+07:00',
  );
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
  commit(
    root,
    'dashboard-merge.txt',
    'Merge pull request #12 from Dangvinh77/automation/commit-activity-dashboard',
    '2026-09-05T09:00:00+07:00',
  );

  const result = sync(root);

  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(entries(root).map((entry) => entry.hash), [human, feature, humanMerge]);
  assert.match(result.stdout, /excluded 3/i);
});
