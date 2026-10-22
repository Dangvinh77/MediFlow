const test = require('node:test');
const assert = require('node:assert/strict');
const { waitForMergeable } = require('./wait-dashboard-pr');

function scenario(snapshots) {
  let reads = 0;
  let sleeps = 0;
  return {
    options: {
      maxAttempts: snapshots.length,
      readSnapshot: () => snapshots[reads++],
      sleep: async () => { sleeps++; },
    },
    counts: () => ({ reads, sleeps }),
  };
}

test('retries a stale conflict after pushing the expected head', async () => {
  const run = scenario([
    { headRefOid: 'old-head', mergeable: 'CONFLICTING' },
    { headRefOid: 'expected', mergeable: 'CONFLICTING' },
    { headRefOid: 'expected', mergeable: 'UNKNOWN' },
    { headRefOid: 'expected', mergeable: 'MERGEABLE' },
  ]);
  await waitForMergeable(512, 'expected', run.options);
  assert.deepEqual(run.counts(), { reads: 4, sleeps: 3 });
});

test('does not accept a mergeable snapshot for a different commit', async () => {
  const run = scenario([
    { headRefOid: 'old-head', mergeable: 'MERGEABLE' },
    { headRefOid: 'expected', mergeable: 'MERGEABLE' },
  ]);
  await waitForMergeable(512, 'expected', run.options);
  assert.deepEqual(run.counts(), { reads: 2, sleeps: 1 });
});

test('persistent conflicts fail after the bounded wait without merging', async () => {
  const run = scenario(Array(3).fill({ headRefOid: 'expected', mergeable: 'CONFLICTING' }));
  await assert.rejects(waitForMergeable(512, 'expected', run.options), /CONFLICTING.*expected/);
  assert.deepEqual(run.counts(), { reads: 3, sleeps: 2 });
});

test('a different head never becomes authorized by the wait', async () => {
  const run = scenario([{ headRefOid: 'someone-elses-head', mergeable: 'MERGEABLE' }]);
  await assert.rejects(waitForMergeable(512, 'expected', run.options), /someone-elses-head.*expected/);
});

test('GitHub API failures propagate instead of permitting a merge', async () => {
  await assert.rejects(waitForMergeable(512, 'expected', {
    readSnapshot: () => { throw new Error('GitHub unavailable'); },
  }), /GitHub unavailable/);
});
