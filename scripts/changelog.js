#!/usr/bin/env node
/**
 * MediFlow Changelog — SQLite-backed commit history tracker
 *
 * Usage:
 *   node scripts/changelog.js --init          Create CHANGELOG.db schema
 *   node scripts/changelog.js --update        Record the latest commit
 *   node scripts/changelog.js                 Show last 20 commits (table)
 *   node scripts/changelog.js --json          Output as JSON
 *   node scripts/changelog.js --scope <s>     Filter by scope (patient, billing...)
 *   node scripts/changelog.js --since <date>  Filter by date (2026-07-01)
 *   node scripts/changelog.js --limit <n>     Limit results (default 20)
 */

const { execSync } = require('child_process');
const { existsSync, mkdirSync, appendFileSync, readFileSync, writeFileSync } = require('fs');
const { join, dirname } = require('path');

const REPO_ROOT = execSync('git rev-parse --show-toplevel').toString().trim();
const DB_PATH = join(REPO_ROOT, 'CHANGELOG.db');
const JSONL_DIR = join(REPO_ROOT, '.changelog');
const JSONL_PATH = join(JSONL_DIR, 'changelog.jsonl');

// ─── Helpers ────────────────────────────────────────────────────────

function run(cmd) {
  try { return execSync(cmd, { cwd: REPO_ROOT }).toString().trim(); }
  catch { return ''; }
}

function hasSqlite3() {
  try {
    execSync('sqlite3 --version', { stdio: 'pipe' });
    return true;
  } catch { return false; }
}

function sqlite(sql) {
  if (!hasSqlite3()) return false;
  try {
    execSync(`sqlite3 "${DB_PATH}"`, { input: sql, stdio: ['pipe', 'pipe', 'pipe'] });
    return true;
  } catch (e) {
    console.error('[changelog] sqlite3 error:', e.message);
    return false;
  }
}

function sqliteQuery(sql) {
  if (!hasSqlite3()) { console.error('[changelog] sqlite3 not found'); return []; }
  try {
    const out = execSync(`sqlite3 -header -column "${DB_PATH}"`, { input: sql }).toString().trim();
    return out ? out : '(no results)';
  } catch (e) {
    console.error('[changelog] query error:', e.message);
    return [];
  }
}

function sqliteJson(sql) {
  if (!hasSqlite3()) return [];
  try {
    const out = execSync(`sqlite3 -json "${DB_PATH}"`, { input: sql }).toString().trim();
    return out ? JSON.parse(out) : [];
  } catch { return []; }
}

function parseConventionalCommit(msg) {
  const match = msg.match(/^(feat|fix|refactor|docs|chore|test|build|ci)(?:\(([^)]+)\))?:\s*(.+)$/);
  if (match) {
    return { type: match[1], scope: match[2] || null, subject: match[3] };
  }
  return { type: null, scope: null, subject: msg };
}

function generateSummary(type, scope, subject, files) {
  const typeLabels = {
    feat: 'Thêm tính năng mới',
    fix: 'Sửa lỗi',
    refactor: 'Tái cấu trúc code',
    docs: 'Cập nhật tài liệu',
    chore: 'Bảo trì/Cấu hình',
    test: 'Thêm/Sửa test',
    build: 'Build/Dependency',
    ci: 'CI/CD'
  };
  const prefix = type ? (typeLabels[type] || type) : 'Cập nhật';
  const svc = scope ? ` [${scope}]` : '';
  const fileSummary = files.length > 0
    ? ` (${files.length} file: ${files.slice(0, 3).join(', ')}${files.length > 3 ? '...' : ''})`
    : '';
  return `${prefix}${svc}: ${subject}${fileSummary}`;
}

// ─── Schema ──────────────────────────────────────────────────────────

const SCHEMA = `
CREATE TABLE IF NOT EXISTS changelog (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    hash        TEXT NOT NULL UNIQUE,
    author      TEXT NOT NULL,
    email       TEXT NOT NULL,
    timestamp   TEXT NOT NULL,
    message     TEXT NOT NULL,
    type        TEXT,
    scope       TEXT,
    files_count INTEGER DEFAULT 0,
    insertions  INTEGER DEFAULT 0,
    deletions   INTEGER DEFAULT 0,
    summary     TEXT,
    created_at  TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS file_changes (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    changelog_id INTEGER NOT NULL,
    path         TEXT NOT NULL,
    type         TEXT NOT NULL,
    lines_added  INTEGER DEFAULT 0,
    lines_deleted INTEGER DEFAULT 0,
    FOREIGN KEY (changelog_id) REFERENCES changelog(id) ON DELETE CASCADE
);
`;

// ─── Commands ───────────────────────────────────────────────────────

function cmdInit() {
  if (sqlite(SCHEMA)) {
    console.log('[changelog] CHANGELOG.db initialized at', DB_PATH);
  } else {
    // Fallback: create JSONL dir
    if (!existsSync(JSONL_DIR)) mkdirSync(JSONL_DIR, { recursive: true });
    console.log('[changelog] SQLite not available — using JSONL fallback at', JSONL_PATH);
  }
}

function cmdUpdate() {
  const hash = run('git rev-parse HEAD');
  if (!hash) { console.error('[changelog] Not a git repository?'); return; }

  // Idempotent: skip if already recorded
  const existing = sqliteJson(`SELECT hash FROM changelog WHERE hash = '${hash}'`);
  if (existing && existing.length > 0) return;

  // Get commit metadata
  const meta = run('git log -1 --format="%an|%ae|%ad|%s"');
  if (!meta) { console.error('[changelog] Failed to read commit metadata'); return; }
  const parts = meta.split('|');
  const authorName = parts[0] || 'unknown';
  const authorEmail = parts[1] || 'unknown';
  const timestamp = parts[2] || 'unknown';
  const message = parts.slice(3).join('|') || '(no message)';

  // Parse Conventional Commit
  const { type, scope, subject } = parseConventionalCommit(message);

  // Get file changes
  const fileStatuses = run('git diff-tree --no-commit-id -r --name-status HEAD');
  const fileLines = run('git diff-tree --no-commit-id -r --numstat HEAD');

  const files = [];
  let totalAdded = 0, totalDeleted = 0;

  const statusLines = fileStatuses ? fileStatuses.split('\n').filter(Boolean) : [];
  const numstatLines = fileLines ? fileLines.split('\n').filter(Boolean) : [];

  for (let i = 0; i < statusLines.length; i++) {
    const st = statusLines[i].trim();
    // Format: "A\tfile/path" or "M\tfile/path" or "D\tfile/path" or "R100\told\tnew"
    let changeType, filePath;
    if (st.startsWith('R')) {
      // rename: "R100\told/path\tnew/path"
      const parts2 = st.split('\t');
      changeType = 'renamed';
      filePath = parts2[2] || parts2[1];
    } else if (st.startsWith('C')) {
      changeType = 'copied';
      filePath = st.split('\t').pop();
    } else {
      const parts2 = st.split('\t');
      changeType = parts2[0] === 'A' ? 'added' : parts2[0] === 'D' ? 'deleted' : parts2[0] === 'M' ? 'modified' : parts2[0];
      filePath = parts2[1] || '';
    }

    // Match numstat for this file (numstat also has 3 tab-separated fields: added\tdeleted\tpath)
    let added = 0, deleted = 0;
    for (const ns of numstatLines) {
      const nsp = ns.split('\t');
      if (nsp[2] === filePath || nsp.slice(2).join('/') === filePath) {
        added = parseInt(nsp[0]) || 0;
        deleted = parseInt(nsp[1]) || 0;
        break;
      }
    }
    totalAdded += added;
    totalDeleted += deleted;
    files.push({ path: filePath, type: changeType, added, deleted });
  }

  // Generate summary
  const summary = generateSummary(type, scope, subject,
    files.map(f => f.path.split('/').pop()));

  if (hasSqlite3()) {
    // Insert into SQLite
    const esc = s => (s || '').replace(/'/g, "''");
    const insertChangelog = `
      INSERT INTO changelog (hash, author, email, timestamp, message, type, scope,
                            files_count, insertions, deletions, summary)
      VALUES ('${esc(hash)}', '${esc(authorName)}', '${esc(authorEmail)}',
              '${esc(timestamp)}', '${esc(message)}',
              ${type ? "'" + esc(type) + "'" : 'NULL'},
              ${scope ? "'" + esc(scope) + "'" : 'NULL'},
              ${files.length}, ${totalAdded}, ${totalDeleted},
              '${esc(summary)}');
    `;
    sqlite(insertChangelog);

    // Get the id
    const idRow = sqliteJson(`SELECT id FROM changelog WHERE hash = '${esc(hash)}'`);
    const changelogId = idRow && idRow.length > 0 ? idRow[0].id : null;

    // Insert file changes
    if (changelogId && files.length > 0) {
      let fileSQL = '';
      for (const f of files) {
        fileSQL += `INSERT INTO file_changes (changelog_id, path, type, lines_added, lines_deleted)
                    VALUES (${changelogId}, '${esc(f.path)}', '${esc(f.type)}',
                            ${f.added}, ${f.deleted});\n`;
      }
      sqlite(fileSQL);
    }
  } else {
    // Fallback: append to JSONL
    if (!existsSync(JSONL_DIR)) mkdirSync(JSONL_DIR, { recursive: true });
    const entry = {
      hash, author: authorName, email: authorEmail, timestamp, message,
      type, scope, files_count: files.length,
      insertions: totalAdded, deletions: totalDeleted, summary,
      files,
      recorded_at: new Date().toISOString()
    };
    appendFileSync(JSONL_PATH, JSON.stringify(entry) + '\n');
  }
}

function cmdRead(opts) {
  const { scope, since, limit, json } = opts;

  // Build query
  let where = [];
  if (scope) where.push(`(c.scope = '${scope.replace(/'/g, "''")}' OR c.type = '${scope.replace(/'/g, "''")}')`);
  if (since) where.push(`c.timestamp >= '${since}'`);
  const whereClause = where.length > 0 ? 'WHERE ' + where.join(' AND ') : '';
  const lim = Math.min(Math.max(parseInt(limit) || 20, 1), 500);

  if (hasSqlite3()) {
    if (json) {
      const data = sqliteJson(`
        SELECT c.hash, c.author, c.email, c.timestamp, c.message,
               c.type, c.scope, c.files_count, c.insertions, c.deletions, c.summary,
               c.created_at as recorded_at
        FROM changelog c
        ${whereClause}
        ORDER BY c.id DESC
        LIMIT ${lim}
      `);
      // Attach file changes per commit
      if (data && data.length > 0) {
        for (const row of data) {
          const fc = sqliteJson(`
            SELECT path, type, lines_added, lines_deleted
            FROM file_changes
            WHERE changelog_id = (SELECT id FROM changelog WHERE hash = '${row.hash.replace(/'/g, "''")}')
            ORDER BY id
          `);
          row.files = fc || [];
        }
      }
      console.log(JSON.stringify(data || [], null, 2));
    } else {
      const table = sqliteQuery(`
        SELECT substr(c.hash,1,8) hash, c.author, c.timestamp,
               COALESCE(c.type || c.scope, '') tag,
               c.message, c.files_count files, (c.insertions || '+' || c.deletions || '-') delta,
               c.summary
        FROM changelog c
        ${whereClause}
        ORDER BY c.id DESC
        LIMIT ${lim}
      `);
      console.log(table || '(no entries)');
    }
  } else {
    // Read from JSONL
    if (!existsSync(JSONL_PATH)) {
      console.log('(no changelog entries yet)');
      return;
    }
    const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
    const entries = lines.map(l => JSON.parse(l)).reverse().slice(0, lim);

    if (json) {
      console.log(JSON.stringify(entries, null, 2));
    } else {
      for (const e of entries) {
        const tag = e.type + (e.scope ? '/' + e.scope : '');
        console.log(`${e.hash.slice(0,8)} | ${e.author} | ${e.timestamp}`);
        console.log(`  ${tag ? tag + ': ' : ''}${e.message}`);
        console.log(`  ${e.files_count} files, +${e.insertions} -${e.deletions}`);
        if (e.summary) console.log(`  ${e.summary}`);
        console.log('');
      }
    }
  }
}

// ─── CLI ────────────────────────────────────────────────────────────

function main() {
  const args = process.argv.slice(2);

  if (args.includes('--init')) {
    cmdInit();
    return;
  }

  if (args.includes('--update')) {
    cmdUpdate();
    return;
  }

  // Read mode
  const scopeIdx = args.indexOf('--scope');
  const sinceIdx = args.indexOf('--since');
  const limitIdx = args.indexOf('--limit');
  const isJson = args.includes('--json');

  cmdRead({
    scope: scopeIdx >= 0 && args[scopeIdx + 1] ? args[scopeIdx + 1] : null,
    since: sinceIdx >= 0 && args[sinceIdx + 1] ? args[sinceIdx + 1] : null,
    limit: limitIdx >= 0 && args[limitIdx + 1] ? args[limitIdx + 1] : null,
    json: isJson
  });
}

main();
