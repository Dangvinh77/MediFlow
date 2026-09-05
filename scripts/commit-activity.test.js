const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const {
  parseEntries,
  parseAliases,
  aggregateEntries,
  renderDashboardMarkdown,
  renderDailySvg,
  renderHourlySvg,
  replaceGeneratedBlock,
  contentDigest,
  generate,
} = require('./commit-activity');

function entry(overrides = {}) {
  return {
    hash: 'a'.repeat(40),
    author: 'Harori',
    email: 'harori@example.com',
    timestamp: 'Fri Sep 4 19:41:38 2026 +0700',
    message: 'docs: update report',
    ...overrides,
  };
}

test('parseEntries validates records and deduplicates full hashes', () => {
  const first = entry();
  const duplicate = entry({ author: 'Renamed Harori' });
  const parsed = parseEntries(`${JSON.stringify(first)}\n${JSON.stringify(duplicate)}\n`);

  assert.equal(parsed.length, 1);
  assert.equal(parsed[0].author, 'Harori');
});

test('parseEntries reports malformed JSON line numbers', () => {
  assert.throws(
    () => parseEntries(`${JSON.stringify(entry())}\n{broken}\n`),
    /Invalid JSON at line 2/,
  );
});

for (const [field, value] of [
  ['hash', 'short'],
  ['author', ''],
  ['email', ''],
  ['timestamp', 'not-a-date'],
]) {
  test(`parseEntries rejects invalid ${field}`, () => {
    assert.throws(
      () => parseEntries(`${JSON.stringify(entry({ [field]: value }))}\n`),
      new RegExp(`Invalid ${field} at line 1`, 'i'),
    );
  });
}

for (const [field, value] of [
  ['hash', 123],
  ['author', 123],
  ['email', 123],
  ['timestamp', 123],
]) {
  test(`parseEntries rejects non-string ${field} with a line number`, () => {
    assert.throws(
      () => parseEntries(`${JSON.stringify(entry({ [field]: value }))}\n`),
      new RegExp(`Invalid ${field} at line 1`, 'i'),
    );
  });
}

test('parseEntries rejects non-object records with a line number', () => {
  assert.throws(() => parseEntries('null\n'), /Invalid record at line 1/i);
  assert.throws(() => parseEntries('[]\n'), /Invalid record at line 1/i);
});

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
    () => parseAliases(JSON.stringify({
      contributors: [{ id: '', name: 'A', emails: ['a@example.com'] }],
    })),
    /Invalid contributor id at index 0/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({ contributors: [
      { id: 'a', name: 'A', emails: ['same@example.com'] },
      { id: 'b', name: 'B', emails: ['same@example.com'] },
    ] })),
    /Email alias belongs to multiple contributors: same@example.com/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({ contributors: [
      { id: 'same', name: 'A', emails: ['a@example.com'] },
      { id: 'same', name: 'B', emails: ['b@example.com'] },
    ] })),
    /Duplicate contributor id: same/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({
      contributors: [{ id: 'a', name: '', emails: ['a@example.com'] }],
    })),
    /Invalid contributor name at index 0/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({
      contributors: [{ id: 'a', name: 'A', emails: [] }],
    })),
    /Invalid contributor emails at index 0/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({
      contributors: [{ id: 'a', name: 'private@example.com', emails: ['a@example.com'] }],
    })),
    /Contributor name must not contain an email address at index 0/,
  );
  assert.throws(
    () => parseAliases(JSON.stringify({
      contributors: [{ id: 'a', name: '用户@例子.公司', emails: ['a@example.com'] }],
    })),
    /Contributor name must not contain an email address at index 0/,
  );
});

test('aggregateEntries groups by normalized email and uses the latest name', () => {
  const entries = [
    entry({
      author: 'Old Name',
      email: ' Person@Example.com ',
      timestamp: 'Thu Sep 3 01:00:00 2026 +0000',
    }),
    entry({
      hash: 'b'.repeat(40),
      author: 'New Name',
      email: 'person@example.com',
      timestamp: 'Fri Sep 4 02:00:00 2026 +0000',
    }),
  ];

  const model = aggregateEntries(parseEntries(entries.map(JSON.stringify).join('\n')), 'Asia/Saigon');

  assert.equal(model.totalCommits, 2);
  assert.equal(model.contributors.length, 1);
  assert.equal(model.contributors[0].name, 'New Name');
  assert.equal(JSON.stringify(model).includes('person@example.com'), false);
});

test('aggregateEntries merges configured aliases under the canonical name', () => {
  const entries = parseEntries([
    entry({
      author: 'Old Harori',
      email: 'first@example.com',
      timestamp: 'Tue Sep 1 01:00:00 2026 +0700',
    }),
    entry({
      hash: 'b'.repeat(40),
      author: 'Other Name',
      email: 'SECOND@example.com',
      timestamp: 'Wed Sep 2 02:00:00 2026 +0700',
    }),
    entry({
      hash: 'c'.repeat(40),
      author: 'Other Name',
      email: 'second@example.com',
      timestamp: 'Wed Sep 2 02:30:00 2026 +0700',
    }),
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
  assert.equal(model.contributors[0].total, 3);
  assert.equal(model.contributors[0].activeDays, 2);
  assert.equal(model.contributors[0].averagePerActiveDay, 1.5);
  assert.deepEqual(model.contributors[0].peakDay, { date: '2026-09-02', count: 2 });
  assert.deepEqual(model.contributors[0].peakHour, { hour: 2, count: 2 });
  assert.equal(model.contributors[0].latest, '2026-09-02 02:30:00');
  assert.deepEqual(model.daily['2026-09-01'], [1]);
  assert.deepEqual(model.daily['2026-09-02'], [2]);
  assert.equal(model.hourly[0][1], 1);
  assert.equal(model.hourly[0][2], 2);
});

test('aggregateEntries keeps unconfigured identical display names separate', () => {
  const entries = parseEntries([
    entry({ author: 'Same', email: 'first@example.com' }),
    entry({ hash: 'b'.repeat(40), author: 'Same', email: 'second@example.com' }),
  ].map(JSON.stringify).join('\n'));

  assert.equal(aggregateEntries(entries).contributors.length, 2);
});

test('Git author names containing at-signs are redacted from every public renderer', () => {
  for (const author of ['private@[192.0.2.1]', '用户@例子.公司']) {
    const model = aggregateEntries(parseEntries(JSON.stringify(entry({
      author,
      email: 'identity@example.com',
    }))));
    const output = [
      renderDashboardMarkdown(model),
      renderDailySvg(model),
      renderHourlySvg(model),
    ].join('\n');

    assert.equal(model.contributors[0].name, '[redacted]');
    assert.equal(output.includes(author), false);
  }
});

test('aggregateEntries calculates daily and hourly statistics in Asia/Saigon', () => {
  const entries = parseEntries([
    entry({ timestamp: 'Thu Sep 3 18:30:00 2026 +0000' }),
    entry({ hash: 'b'.repeat(40), timestamp: 'Thu Sep 3 19:30:00 2026 +0000' }),
    entry({ hash: 'c'.repeat(40), timestamp: 'Fri Sep 4 18:30:00 2026 +0000' }),
  ].map(JSON.stringify).join('\n'));

  const model = aggregateEntries(entries, 'Asia/Saigon');
  const contributor = model.contributors[0];

  assert.equal(contributor.total, 3);
  assert.equal(contributor.activeDays, 2);
  assert.equal(contributor.averagePerActiveDay, 1.5);
  assert.deepEqual(contributor.peakDay, { date: '2026-09-04', count: 2 });
  assert.deepEqual(contributor.peakHour, { hour: 1, count: 2 });
  assert.equal(contributor.latest, '2026-09-05 01:30:00');
  assert.deepEqual(model.dates, ['2026-09-04', '2026-09-05']);
});

test('contributor ordering and chart color assignment are deterministic', () => {
  const records = [
    entry({ author: 'Zulu', email: 'z@example.com' }),
    entry({ hash: 'b'.repeat(40), author: 'Alpha', email: 'a@example.com' }),
  ];
  const forward = aggregateEntries(parseEntries(records.map(JSON.stringify).join('\n')));
  const reverse = aggregateEntries(parseEntries(records.reverse().map(JSON.stringify).join('\n')));

  assert.deepEqual(forward.contributors.map(({ name }) => name), ['Alpha', 'Zulu']);
  assert.equal(renderDailySvg(forward), renderDailySvg(reverse));
  assert.match(renderDailySvg(forward), /#2563eb[\s\S]*Alpha/);
});

test('aggregateEntries includes dates with zero commits', () => {
  const entries = parseEntries([
    entry({ timestamp: 'Tue Sep 1 01:00:00 2026 +0700' }),
    entry({ hash: 'b'.repeat(40), timestamp: 'Thu Sep 3 01:00:00 2026 +0700' }),
  ].map(JSON.stringify).join('\n'));

  const model = aggregateEntries(entries);

  assert.deepEqual(model.dates, ['2026-09-01', '2026-09-02', '2026-09-03']);
  assert.equal(model.daily['2026-09-02'][0], 0);
});

test('renderDashboardMarkdown escapes names and never renders email', () => {
  const model = aggregateEntries(parseEntries(`${JSON.stringify(entry({
    author: 'A | <B>',
    email: 'secret@example.com',
  }))}\n`));

  const markdown = renderDashboardMarkdown(model);

  assert.equal(markdown.includes('A \\| <B>'), true);
  assert.doesNotMatch(markdown, /secret@example\.com/);
  assert.match(markdown, /docs\/assets\/commit-activity-by-day\.svg/);
  assert.match(markdown, /docs\/assets\/commit-activity-by-hour\.svg/);
});

test('renderDashboardMarkdown handles an empty changelog', () => {
  const markdown = renderDashboardMarkdown(aggregateEntries([]));

  assert.match(markdown, /No commits recorded/);
  assert.match(markdown, /0 unique commits/);
  assert.match(markdown, /\| Contributor \| Commits \|/);
  assert.match(markdown, /commit-activity-by-day\.svg/);
  assert.match(markdown, /commit-activity-by-hour\.svg/);
});

test('replaceGeneratedBlock changes only the single marked section', () => {
  const readme = '# Title\n\nBefore\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\nAfter\n';
  const updated = replaceGeneratedBlock(readme, 'new');

  assert.equal(
    updated,
    '# Title\n\nBefore\n<!-- commit-activity:start -->\nnew\n<!-- commit-activity:end -->\nAfter\n',
  );
});

test('replaceGeneratedBlock rejects missing markers', () => {
  assert.throws(
    () => replaceGeneratedBlock('# Title\n', 'new'),
    /exactly one start marker/i,
  );
  assert.throws(
    () => replaceGeneratedBlock('<!-- commit-activity:start -->\nold', 'new'),
    /exactly one end marker/i,
  );
});

test('replaceGeneratedBlock rejects duplicate markers', () => {
  assert.throws(
    () => replaceGeneratedBlock(
      '<!-- commit-activity:start -->\n<!-- commit-activity:start -->\n<!-- commit-activity:end -->',
      'new',
    ),
    /exactly one start marker/i,
  );
  assert.throws(
    () => replaceGeneratedBlock(
      '<!-- commit-activity:start -->\n<!-- commit-activity:end -->\n<!-- commit-activity:end -->',
      'new',
    ),
    /exactly one end marker/i,
  );
});

test('replaceGeneratedBlock rejects reversed markers', () => {
  assert.throws(
    () => replaceGeneratedBlock(
      '<!-- commit-activity:end -->\n<!-- commit-activity:start -->',
      'new',
    ),
    /end marker must follow/i,
  );
});

test('renderDailySvg renders every date, contributor legend, and accessible text', () => {
  const model = aggregateEntries(parseEntries([
    entry({ author: 'A & B', timestamp: 'Tue Sep 1 01:00:00 2026 +0700' }),
    entry({
      hash: 'b'.repeat(40),
      author: 'A & B',
      timestamp: 'Thu Sep 3 01:00:00 2026 +0700',
    }),
  ].map(JSON.stringify).join('\n')));

  const svg = renderDailySvg(model);

  assert.match(svg, /<title>Commit activity by day<\/title>/);
  assert.match(svg, /2026-09-01/);
  assert.match(svg, /2026-09-02/);
  assert.match(svg, /2026-09-03/);
  assert.match(svg, /A &amp; B/);
  assert.doesNotMatch(svg, /@example\.com/);
});

test('renderHourlySvg renders 24 columns and escaped tooltips', () => {
  const model = aggregateEntries(parseEntries(JSON.stringify(entry({ author: 'A < B' }))));

  const svg = renderHourlySvg(model);

  assert.match(svg, /<title>Commit activity by hour<\/title>/);
  assert.equal((svg.match(/data-hour=/g) || []).length, 24);
  assert.match(svg, /A &lt; B/);
  assert.doesNotMatch(svg, /@example\.com/);
});

test('chart renderers handle an empty changelog', () => {
  const model = aggregateEntries([]);

  assert.match(renderDailySvg(model), /No commits recorded/);
  assert.match(renderHourlySvg(model), /No commits recorded/);
});

test('generate writes all dashboard artifacts atomically and is idempotent', (t) => {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commit-activity-'));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  fs.mkdirSync(path.join(rootDir, '.changelog'));
  fs.writeFileSync(
    path.join(rootDir, '.changelog', 'entries.jsonl'),
    `${JSON.stringify(entry())}\n`,
  );
  fs.writeFileSync(
    path.join(rootDir, 'README.md'),
    '# Test\n\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n\nKeep me.\n',
  );

  const first = generate({ rootDir, timeZone: 'Asia/Saigon' });
  const second = generate({ rootDir, timeZone: 'Asia/Saigon' });

  assert.deepEqual(first.changed.sort(), [
    'README.md',
    'docs/assets/commit-activity-by-day.svg',
    'docs/assets/commit-activity-by-hour.svg',
  ]);
  assert.deepEqual(second.changed, []);
  assert.match(fs.readFileSync(path.join(rootDir, 'README.md'), 'utf8'), /Keep me\./);
  assert.match(
    fs.readFileSync(path.join(rootDir, 'docs', 'assets', 'commit-activity-by-day.svg'), 'utf8'),
    /Commit activity by day/,
  );
  assert.deepEqual(
    fs.readdirSync(path.join(rootDir, 'docs', 'assets')).filter((name) => name.includes('.tmp-')),
    [],
  );
});

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
  const daily = fs.readFileSync(
    path.join(rootDir, 'docs', 'assets', 'commit-activity-by-day.svg'),
    'utf8',
  );
  const hourly = fs.readFileSync(
    path.join(rootDir, 'docs', 'assets', 'commit-activity-by-hour.svg'),
    'utf8',
  );

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

test('generate loads an explicit contributor alias registry', (t) => {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commit-activity-aliases-'));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  fs.mkdirSync(path.join(rootDir, '.changelog'));
  fs.writeFileSync(
    path.join(rootDir, '.changelog', 'entries.jsonl'),
    `${[
      entry({ email: 'first@example.com' }),
      entry({ hash: 'b'.repeat(40), email: 'second@example.com' }),
    ].map(JSON.stringify).join('\n')}\n`,
  );
  fs.writeFileSync(
    path.join(rootDir, 'aliases.json'),
    JSON.stringify({
      contributors: [{
        id: 'harori',
        name: 'Harori',
        emails: ['first@example.com', 'second@example.com'],
      }],
    }),
  );
  fs.writeFileSync(
    path.join(rootDir, 'README.md'),
    '# Test\n\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n',
  );

  const first = generate({ rootDir, aliases: 'aliases.json' });
  const readme = fs.readFileSync(path.join(rootDir, 'README.md'), 'utf8');
  const daily = fs.readFileSync(
    path.join(rootDir, 'docs', 'assets', 'commit-activity-by-day.svg'),
    'utf8',
  );
  const hourly = fs.readFileSync(
    path.join(rootDir, 'docs', 'assets', 'commit-activity-by-hour.svg'),
    'utf8',
  );
  const rendered = [readme, daily, hourly].join('\n');
  const second = generate({ rootDir, aliases: 'aliases.json' });

  assert.equal(first.model.contributors.length, 1);
  assert.equal(first.model.contributors[0].name, 'Harori');
  assert.equal(first.model.contributors[0].total, 2);
  assert.deepEqual(second.changed, []);
  assert.equal((readme.match(/^\| Harori \|/gm) || []).length, 1);
  assert.equal((daily.match(/>Harori \(\d+\)<\/text>/g) || []).length, 1);
  assert.equal((hourly.match(/<text class="name"[^>]*>Harori<\/text>/g) || []).length, 1);
  assert.doesNotMatch(rendered, /first@example\.com|second@example\.com/);
});

test('explicit alias paths fail when the value or file is missing', (t) => {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commit-activity-alias-cli-'));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  const changelog = path.join(rootDir, 'source.jsonl');
  const readme = path.join(rootDir, 'README.md');
  const daily = path.join(rootDir, 'daily.svg');
  const hourly = path.join(rootDir, 'hourly.svg');
  fs.writeFileSync(changelog, `${JSON.stringify(entry())}\n`);
  fs.writeFileSync(
    readme,
    '# Test\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n',
  );
  const common = [
    path.join(__dirname, 'commit-activity.js'),
    '--root', rootDir,
    '--input', changelog,
    '--readme', readme,
    '--daily', daily,
    '--hourly', hourly,
  ];

  const missingValue = spawnSync(process.execPath, [...common, '--aliases', '--check'], {
    encoding: 'utf8',
  });
  const missingFile = spawnSync(
    process.execPath,
    [...common, '--aliases', path.join(rootDir, 'missing.json')],
    { encoding: 'utf8' },
  );

  assert.equal(missingValue.status, 1);
  assert.match(missingValue.stderr, /Missing value for --aliases/);
  assert.equal(missingFile.status, 1);
  assert.match(missingFile.stderr, /Contributor aliases file not found/);
  assert.equal(fs.existsSync(daily), false);
  assert.equal(fs.existsSync(hourly), false);
});

test('CLI accepts explicit input and output paths and prefixes failures', (t) => {
  const rootDir = fs.mkdtempSync(path.join(os.tmpdir(), 'commit-activity-cli-'));
  t.after(() => fs.rmSync(rootDir, { recursive: true, force: true }));
  const changelog = path.join(rootDir, 'source.jsonl');
  const aliases = path.join(rootDir, 'aliases.json');
  const readme = path.join(rootDir, 'custom-readme.md');
  const daily = path.join(rootDir, 'charts', 'daily.svg');
  const hourly = path.join(rootDir, 'charts', 'hourly.svg');
  fs.writeFileSync(changelog, `${[
    entry({ email: 'first@example.com' }),
    entry({ hash: 'b'.repeat(40), email: 'second@example.com' }),
  ].map(JSON.stringify).join('\n')}\n`);
  fs.writeFileSync(aliases, JSON.stringify({
    contributors: [{
      id: 'harori',
      name: 'Harori',
      emails: ['first@example.com', 'second@example.com'],
    }],
  }));
  fs.writeFileSync(
    readme,
    '# Test\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n',
  );

  const success = spawnSync(process.execPath, [
    path.join(__dirname, 'commit-activity.js'),
    '--input', changelog,
    '--aliases', aliases,
    '--readme', readme,
    '--daily', daily,
    '--hourly', hourly,
    '--timezone', 'Asia/Saigon',
  ], { encoding: 'utf8' });
  const failure = spawnSync(
    process.execPath,
    [path.join(__dirname, 'commit-activity.js'), '--unknown'],
    { encoding: 'utf8' },
  );

  assert.equal(success.status, 0, success.stderr);
  assert.equal(fs.existsSync(daily), true);
  assert.equal(fs.existsSync(hourly), true);
  assert.equal(
    (fs.readFileSync(readme, 'utf8').match(/^\| Harori \|/gm) || []).length,
    1,
  );
  assert.equal(failure.status, 1);
  assert.match(failure.stderr, /^\[commit-activity\] Unknown option:/);
});

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
  assert.match(workflow, /github\.event_name == 'push'.*github\.sha/);
  assert.match(workflow, /Allow GitHub Actions to create and approve pull requests/);
});

test('repository aliases merge Harori identities', () => {
  const root = path.join(__dirname, '..');
  const aliases = parseAliases(fs.readFileSync(
    path.join(root, '.changelog', 'contributor-aliases.json'),
    'utf8',
  ));

  assert.deepEqual(
    aliases.get('phamdangvinh2002@gmail.com'),
    { id: 'harori', name: 'Harori' },
  );
  assert.deepEqual(
    aliases.get('100329525+dangvinh77@users.noreply.github.com'),
    { id: 'harori', name: 'Harori' },
  );
});
