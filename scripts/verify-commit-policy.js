#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const ALLOWLIST_PATH = path.join(__dirname, 'allowed-contributors.json');
const FORBIDDEN_TRAILER = /^(co-authored-by|claude-session|codex-session)\s*:/im;
const FORBIDDEN_TRAILER_LINE = /^(co-authored-by|claude-session|codex-session)\s*:/i;

function parseAllowlist(raw) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`Invalid contributor allowlist JSON: ${error.message}`);
  }

  if (!Array.isArray(parsed.contributors) || parsed.contributors.length === 0) {
    throw new Error('Contributor allowlist must contain at least one contributor.');
  }

  const identities = new Set();
  const emails = new Set();
  const names = new Set();

  parsed.contributors.forEach((contributor, index) => {
    const name = typeof contributor?.name === 'string' ? contributor.name.trim() : '';
    if (!name || !Array.isArray(contributor.emails) || contributor.emails.length === 0) {
      throw new Error(`Invalid contributor at index ${index}.`);
    }
    if (names.has(name)) {
      throw new Error(`Duplicate contributor name: ${name}`);
    }
    names.add(name);

    contributor.emails.forEach((value) => {
      const email = typeof value === 'string' ? value.trim().toLowerCase() : '';
      if (!email || !email.includes('@')) {
        throw new Error(`Invalid email for contributor ${name}.`);
      }
      if (emails.has(email)) {
        throw new Error(`Duplicate email in contributor allowlist: ${email}`);
      }
      emails.add(email);
      identities.add(`${name}\0${email}`);
    });
  });

  return { identities, contributors: parsed.contributors };
}

function validateCommit(commit, allowlist) {
  const name = String(commit.name ?? '').trim();
  const email = String(commit.email ?? '').trim().toLowerCase();
  const message = String(commit.message ?? '');
  const errors = [];

  if (!allowlist.identities.has(`${name}\0${email}`)) {
    errors.push(`Author ${name || '<empty>'} <${email || 'empty'}> is not an allowed contributor.`);
  }
  const trailer = message.match(FORBIDDEN_TRAILER);
  if (trailer) {
    errors.push(`Forbidden attribution trailer: ${trailer[1]}:`);
  }
  return errors;
}

function sanitizeCommitMessage(message) {
  const lines = String(message).replace(/\r\n/g, '\n').split('\n');
  const filtered = lines.filter((line) => !FORBIDDEN_TRAILER_LINE.test(line));
  while (filtered.length > 0 && filtered.at(-1).trim() === '') {
    filtered.pop();
  }
  return `${filtered.join('\n')}\n`;
}

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8' }).replace(/\r\n/g, '\n');
}

function readCommit(commitId) {
  const output = git(['show', '-s', '--format=%an%x00%ae%x00%B', commitId]);
  const first = output.indexOf('\0');
  const second = output.indexOf('\0', first + 1);
  if (first < 0 || second < 0) {
    throw new Error(`Cannot read commit metadata for ${commitId}.`);
  }
  return {
    id: commitId,
    name: output.slice(0, first),
    email: output.slice(first + 1, second),
    message: output.slice(second + 1),
  };
}

function readCurrentAuthor(messageFile) {
  const ident = git(['var', 'GIT_AUTHOR_IDENT']).trim();
  const match = ident.match(/^(.*) <([^<>]+)> \d+ [+-]\d{4}$/);
  if (!match) {
    throw new Error('Cannot read the current Git author identity.');
  }
  return {
    id: 'pending commit',
    name: match[1],
    email: match[2],
    message: fs.readFileSync(messageFile, 'utf8'),
  };
}

function formatAllowedContributors(allowlist) {
  return allowlist.contributors
    .map(({ name, emails: allowedEmails }) => `${name} <${allowedEmails.join(' | ')}>`)
    .join(', ');
}

function run(argv) {
  const sanitizeIndex = argv.indexOf('--sanitize-message');
  if (sanitizeIndex >= 0 && argv[sanitizeIndex + 1]) {
    const messageFile = argv[sanitizeIndex + 1];
    const original = fs.readFileSync(messageFile, 'utf8');
    fs.writeFileSync(messageFile, sanitizeCommitMessage(original), 'utf8');
    return 0;
  }

  const allowlist = parseAllowlist(fs.readFileSync(ALLOWLIST_PATH, 'utf8'));
  const rangeIndex = argv.indexOf('--range');
  const messageIndex = argv.indexOf('--message-file');
  let commits;

  if (argv.includes('--current-author')) {
    if (messageIndex < 0 || !argv[messageIndex + 1]) {
      throw new Error('--current-author requires --message-file <path>.');
    }
    commits = [readCurrentAuthor(argv[messageIndex + 1])];
  } else if (rangeIndex >= 0 && argv[rangeIndex + 1]) {
    const range = argv[rangeIndex + 1];
    const commitIds = git(['rev-list', '--reverse', range]).trim().split('\n').filter(Boolean);
    commits = commitIds.map(readCommit);
  } else {
    throw new Error('Usage: verify-commit-policy.js --sanitize-message <path> | --current-author --message-file <path> | --range <base..head>');
  }

  const failures = commits.flatMap((commit) => validateCommit(commit, allowlist)
    .map((message) => `${commit.id}: ${message}`));

  if (failures.length > 0) {
    process.stderr.write(`Commit policy failed:\n- ${failures.join('\n- ')}\n`);
    process.stderr.write(`Allowed authors: ${formatAllowedContributors(allowlist)}\n`);
    return 1;
  }

  process.stdout.write(`Commit policy passed for ${commits.length} commit(s).\n`);
  return 0;
}

if (require.main === module) {
  try {
    process.exitCode = run(process.argv.slice(2));
  } catch (error) {
    process.stderr.write(`Commit policy error: ${error.message}\n`);
    process.exitCode = 1;
  }
}

module.exports = {
  parseAllowlist,
  validateCommit,
  sanitizeCommitMessage,
  readCommit,
  readCurrentAuthor,
  run,
};
