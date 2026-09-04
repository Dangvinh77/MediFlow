const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  parseEntries,
  aggregateEntries,
  renderDashboardMarkdown,
  renderDailySvg,
  renderHourlySvg,
  replaceGeneratedBlock,
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
  assert.deepEqual(model.dates, ['2026-09-04', '2026-09-05']);
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
});

test('replaceGeneratedBlock rejects duplicate markers', () => {
  assert.throws(
    () => replaceGeneratedBlock(
      '<!-- commit-activity:start -->\n<!-- commit-activity:start -->\n<!-- commit-activity:end -->',
      'new',
    ),
    /exactly one start marker/i,
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
