const test = require('node:test');
const assert = require('node:assert/strict');

const {
  parseEntries,
  aggregateEntries,
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
