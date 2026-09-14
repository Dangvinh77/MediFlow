const test = require('node:test');
const assert = require('node:assert/strict');

const {
  parseAllowlist,
  validateCommit,
  sanitizeCommitMessage,
} = require('./verify-commit-policy');

const allowlist = parseAllowlist(JSON.stringify({
  contributors: [
    {
      name: 'Harori',
      emails: [
        'phamdangvinh2002@gmail.com',
        '100329525+Dangvinh77@users.noreply.github.com',
      ],
    },
    {
      name: 'LQHuy0210',
      emails: ['huylqts2206012@fpt.edu.vn', '207120207+LQHuy0210@users.noreply.github.com'],
    },
    {
      name: 'locgit-89',
      emails: ['thantheloc@gmail.com', '171225503+locgit-89@users.noreply.github.com'],
    },
    {
      name: 'TranHoangAnh94',
      emails: ['dragonnight1701@gmail.com', '192096428+TranHoangAnh94@users.noreply.github.com'],
    },
  ],
}));

test('accepts all four canonical contributors and Harori aliases', () => {
  for (const [name, email] of [
    ['Harori', 'phamdangvinh2002@gmail.com'],
    ['Harori', '100329525+Dangvinh77@users.noreply.github.com'],
    ['LQHuy0210', 'huylqts2206012@fpt.edu.vn'],
    ['LQHuy0210', '207120207+LQHuy0210@users.noreply.github.com'],
    ['locgit-89', 'thantheloc@gmail.com'],
    ['locgit-89', '171225503+locgit-89@users.noreply.github.com'],
    ['TranHoangAnh94', 'dragonnight1701@gmail.com'],
    ['TranHoangAnh94', '192096428+TranHoangAnh94@users.noreply.github.com'],
  ]) {
    assert.deepEqual(validateCommit({ name, email, message: 'feat: valid' }, allowlist), []);
  }
});

test('rejects unknown authors and mismatched allowed names and emails', () => {
  assert.match(
    validateCommit({ name: 'Claude', email: 'claude@example.com', message: 'feat: invalid' }, allowlist)[0],
    /not an allowed contributor/i,
  );
  assert.match(
    validateCommit({ name: 'Harori', email: 'thantheloc@gmail.com', message: 'feat: invalid' }, allowlist)[0],
    /not an allowed contributor/i,
  );
});

test('rejects co-author and AI session trailers case-insensitively', () => {
  for (const trailer of [
    'Co-Authored-By: Claude <claude@example.com>',
    'co-authored-by: Someone <someone@example.com>',
    'Claude-Session: abc123',
    'Codex-Session: abc123',
  ]) {
    const errors = validateCommit({
      name: 'Harori',
      email: 'phamdangvinh2002@gmail.com',
      message: `feat: valid subject\n\n${trailer}`,
    }, allowlist);
    assert.match(errors[0], /trailer/i);
  }
});

test('does not treat normal prose mentioning AI as commit attribution', () => {
  assert.deepEqual(validateCommit({
    name: 'locgit-89',
    email: 'thantheloc@gmail.com',
    message: 'docs: explain why AI generated co-author trailers are forbidden',
  }, allowlist), []);
});

test('sanitizes all forbidden trailers and trailing blank lines', () => {
  assert.equal(
    sanitizeCommitMessage([
      'feat: valid subject',
      '',
      'Body remains.',
      'Co-Authored-By: Claude <claude@example.com>',
      'codex-session: abc123',
      '',
    ].join('\r\n')),
    'feat: valid subject\n\nBody remains.\n',
  );
});

test('rejects malformed and ambiguous allowlists', () => {
  assert.throws(() => parseAllowlist('{broken'), /invalid.*json/i);
  assert.throws(() => parseAllowlist(JSON.stringify({ contributors: [] })), /at least one/i);
  assert.throws(() => parseAllowlist(JSON.stringify({
    contributors: [
      { name: 'Harori', emails: ['same@example.com'] },
      { name: 'Other', emails: ['same@example.com'] },
    ],
  })), /duplicate email/i);
});
