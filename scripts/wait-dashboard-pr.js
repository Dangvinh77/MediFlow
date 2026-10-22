const { execFileSync } = require('node:child_process');
const { setTimeout: sleep } = require('node:timers/promises');

function readSnapshot(prNumber) {
  return JSON.parse(execFileSync('gh', [
    'pr', 'view', String(prNumber), '--json', 'headRefOid,mergeable',
  ], { encoding: 'utf8', timeout: 30000 }));
}

async function waitForMergeable(prNumber, headSha, options = {}) {
  const {
    maxAttempts = 12,
    delayMs = 5000,
    readSnapshot: read = readSnapshot,
    sleep: pause = sleep,
  } = options;
  let last;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    last = await read(prNumber);
    if (last.headRefOid === headSha && last.mergeable === 'MERGEABLE') return;
    // After a force-push GitHub can briefly return the previous conflict result,
    // even alongside the new head SHA. Retry every non-ready state, boundedly.
    if (attempt + 1 < maxAttempts) await pause(delayMs);
  }
  throw new Error(
    `Dashboard PR #${prNumber} did not become mergeable: ${last?.mergeable} ` +
    `at ${last?.headRefOid}; expected head ${headSha}. No merge attempted.`,
  );
}

if (require.main === module) {
  const [prNumber, headSha] = process.argv.slice(2);
  if (!/^\d+$/.test(prNumber || '') || !/^[a-f0-9]{40}$/.test(headSha || '')) {
    console.error('Usage: node scripts/wait-dashboard-pr.js <PR number> <full head SHA>');
    process.exitCode = 1;
  } else {
    waitForMergeable(prNumber, headSha).catch((error) => {
      console.error(error.message);
      process.exitCode = 1;
    });
  }
}

module.exports = { waitForMergeable };
