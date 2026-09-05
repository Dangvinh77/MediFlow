#!/usr/bin/env node
/**
 * MediFlow Changelog — JSONL source + SQLite cache
 *
 * Source of truth: .changelog/entries.jsonl  (text, 1 line/commit, git merge-friendly, COMMITTED)
 * Query cache:     .changelog/cache.db          (SQLite, gitignored, rebuilt from JSONL)
 *
 * Usage:
 *   node scripts/changelog.js --init            Scan full git log → entries.jsonl + cache.db
 *   node scripts/changelog.js --update          Record the latest commit (append + cache)
 *   node scripts/changelog.js --rebuild         Rebuild cache.db from entries.jsonl (after merge)
 *   node scripts/changelog.js --dedup           Deduplicate entries.jsonl by hash (resolve merge conflicts)
 *   node scripts/changelog.js                   Show ALL commits (table)
 *   node scripts/changelog.js --summary         Show stats (total, by type, by author)
 *   node scripts/changelog.js --limit <n>       Limit results
 *   node scripts/changelog.js --json            Output as JSON
 *   node scripts/changelog.js --scope <s>       Filter by type or scope
 *   node scripts/changelog.js --since <date>    Filter by date
 *   node scripts/changelog.js --files           Show file details
 */

const { execSync } = require('child_process');
const {
  existsSync,
  mkdirSync,
  appendFileSync,
  readFileSync,
  writeFileSync,
  unlinkSync,
  renameSync,
} = require('fs');
const { join } = require('path');

const REPO_ROOT = execSync('git rev-parse --show-toplevel').toString().trim();
const CHANGELOG_DIR = join(REPO_ROOT, '.changelog');
const JSONL_PATH = join(CHANGELOG_DIR, 'entries.jsonl');
const DB_PATH = join(CHANGELOG_DIR, 'cache.db');

// ─── Helpers ────────────────────────────────────────────────────────

function run(cmd) {
  try { return execSync(cmd, { cwd: REPO_ROOT }).toString().trim(); }
  catch { return ''; }
}

function hasSqlite3() {
  try { execSync('sqlite3 --version', { stdio: 'pipe' }); return true; }
  catch { return false; }
}

function sqliteExec(sql) {
  if (!hasSqlite3()) return false;
  try {
    execSync(`sqlite3 "${DB_PATH}"`, { input: sql, stdio: ['pipe', 'pipe', 'pipe'] });
    return true;
  } catch (e) {
    console.error('[changelog] sqlite3 error:', e.message);
    return false;
  }
}

function sqliteJson(sql) {
  if (!hasSqlite3()) return [];
  try {
    const out = execSync(`sqlite3 -json "${DB_PATH}"`, { input: sql }).toString().trim();
    return out ? JSON.parse(out) : [];
  } catch { return []; }
}

function sqliteTable(sql) {
  if (!hasSqlite3()) { console.error('[changelog] sqlite3 not found'); return ''; }
  try {
    const out = execSync(`sqlite3 -header -column "${DB_PATH}"`, { input: sql }).toString().trim();
    return out || '(no results)';
  } catch { return '(query error)'; }
}

function ensureDir() {
  if (!existsSync(CHANGELOG_DIR)) mkdirSync(CHANGELOG_DIR, { recursive: true });
}

function esc(s) { return (s || '').replace(/'/g, "''"); }

function readExistingJsonl() {
  if (!existsSync(JSONL_PATH)) {
    return { source: '', entries: [], hashes: new Set() };
  }

  const source = readFileSync(JSONL_PATH, 'utf8');
  const entries = [];
  for (const [index, line] of source.split(/\r?\n/).entries()) {
    if (!line.trim()) continue;

    let entry;
    try {
      entry = JSON.parse(line);
    } catch {
      throw new Error(`Invalid changelog JSON at line ${index + 1}`);
    }
    if (!entry || typeof entry.hash !== 'string' || !/^[0-9a-f]{40}$/i.test(entry.hash)) {
      throw new Error(`Invalid changelog hash at line ${index + 1}`);
    }
    entries.push(entry);
  }

  return {
    source,
    entries,
    hashes: new Set(entries.map((entry) => entry.hash)),
  };
}

function writeEntriesAtomically(existingSource, additions) {
  if (additions.length === 0) return;

  const prefix = existingSource.length === 0 || existingSource.endsWith('\n')
    ? existingSource
    : `${existingSource}\n`;
  const output = `${prefix}${additions.map(JSON.stringify).join('\n')}\n`;
  const temporaryPath = `${JSONL_PATH}.tmp-${process.pid}-${Date.now()}`;
  try {
    writeFileSync(temporaryPath, output, 'utf8');
    renameSync(temporaryPath, JSONL_PATH);
  } catch (error) {
    if (existsSync(temporaryPath)) unlinkSync(temporaryPath);
    throw error;
  }
}

function parseConventionalCommit(msg) {
  const match = msg.match(/^(feat|fix|refactor|docs|chore|test|build|ci)(?:\(([^)]+)\))?:\s*(.+)$/);
  if (match) return { type: match[1], scope: match[2] || null, subject: match[3] };
  return { type: null, scope: null, subject: msg };
}

function generateSummary(type, scope, subject, files) {
  const labels = { feat:'Thêm tính năng mới', fix:'Sửa lỗi', refactor:'Tái cấu trúc code', docs:'Cập nhật tài liệu', chore:'Bảo trì/Cấu hình', test:'Thêm/Sửa test', build:'Build/Dependency', ci:'CI/CD' };
  const prefix = type ? (labels[type] || type) : 'Cập nhật';
  const svc = scope ? ` [${scope}]` : '';
  const fs = files.length > 0 ? ` (${files.length} file: ${files.slice(0,3).join(', ')}${files.length > 3 ? '...' : ''})` : '';
  return `${prefix}${svc}: ${subject}${fs}`;
}

// ─── Build entry object from HEAD ─────────────────────────────────

function buildEntry(hash) {
  if (!hash) hash = run('git rev-parse HEAD');
  if (!hash) return null;

  const meta = run(`git log -1 --format="%an|%ae|%ad|%s" ${hash}`);
  if (!meta) return null;
  const p = meta.split('|');
  const authorName = p[0], authorEmail = p[1], timestamp = p[2], message = p.slice(3).join('|');
  const { type, scope, subject } = parseConventionalCommit(message);

  const fileStatuses = run(`git diff-tree --no-commit-id -r --name-status ${hash}`);
  const fileLines = run(`git diff-tree --no-commit-id -r --numstat ${hash}`);

  const files = [];
  let totalAdded = 0, totalDeleted = 0;
  const sl = (fileStatuses || '').split('\n').filter(Boolean);
  const nl = (fileLines || '').split('\n').filter(Boolean);

  for (const st of sl) {
    const t = st.trim();
    let changeType, filePath;
    if (t.startsWith('R')) { changeType = 'renamed'; filePath = t.split('\t')[2] || t.split('\t')[1]; }
    else if (t.startsWith('C')) { changeType = 'copied'; filePath = t.split('\t').pop(); }
    else { const sp = t.split('\t'); changeType = sp[0] === 'A' ? 'added' : sp[0] === 'D' ? 'deleted' : sp[0] === 'M' ? 'modified' : sp[0]; filePath = sp[1] || ''; }
    let added = 0, deleted = 0;
    for (const ns of nl) { const nsp = ns.split('\t'); if (nsp[2] === filePath || nsp.slice(2).join('/') === filePath) { added = parseInt(nsp[0])||0; deleted = parseInt(nsp[1])||0; break; } }
    totalAdded += added; totalDeleted += deleted;
    files.push({ path: filePath, type: changeType, added, deleted });
  }

  const summary = generateSummary(type, scope, subject, files.map(f => f.path.split('/').pop()));
  return { hash, author: authorName, email: authorEmail, timestamp, message, type, scope, files_count: files.length, insertions: totalAdded, deletions: totalDeleted, summary, files };
}

// ─── Schema for cache.db ──────────────────────────────────────────

const SCHEMA = `
CREATE TABLE IF NOT EXISTS changelog (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    hash        TEXT NOT NULL UNIQUE,
    author      TEXT NOT NULL, email TEXT NOT NULL, timestamp TEXT NOT NULL,
    message     TEXT NOT NULL, type TEXT, scope TEXT,
    files_count INTEGER DEFAULT 0, insertions INTEGER DEFAULT 0, deletions INTEGER DEFAULT 0,
    summary     TEXT,
    created_at  TEXT DEFAULT (datetime('now','localtime'))
);
CREATE TABLE IF NOT EXISTS file_changes (
    id INTEGER PRIMARY KEY AUTOINCREMENT, changelog_id INTEGER NOT NULL,
    path TEXT NOT NULL, type TEXT NOT NULL,
    lines_added INTEGER DEFAULT 0, lines_deleted INTEGER DEFAULT 0,
    FOREIGN KEY (changelog_id) REFERENCES changelog(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_changelog_hash ON changelog(hash);
`;

// ─── Insert entry into cache.db ───────────────────────────────────

function insertCacheEntry(entry) {
  if (!hasSqlite3() || !entry) return;
  sqliteExec(`DELETE FROM changelog WHERE hash = '${esc(entry.hash)}'`);
  const r = sqliteExec(`
    INSERT INTO changelog (hash, author, email, timestamp, message, type, scope,
                           files_count, insertions, deletions, summary)
    VALUES ('${esc(entry.hash)}','${esc(entry.author)}','${esc(entry.email)}',
            '${esc(entry.timestamp)}','${esc(entry.message)}',
            ${entry.type ? "'"+esc(entry.type)+"'" : 'NULL'},
            ${entry.scope ? "'"+esc(entry.scope)+"'" : 'NULL'},
            ${entry.files_count}, ${entry.insertions}, ${entry.deletions},
            '${esc(entry.summary)}');
  `);
  if (!r) return;
  if (entry.files && entry.files.length > 0) {
    let sql = '';
    for (const f of entry.files)
      sql += `INSERT INTO file_changes (changelog_id, path, type, lines_added, lines_deleted)
              VALUES ((SELECT id FROM changelog WHERE hash='${esc(entry.hash)}'), '${esc(f.path)}', '${esc(f.type)}', ${f.added||0}, ${f.deleted||0});\n`;
    sqliteExec(sql);
  }
}

// ─── Commands ─────────────────────────────────────────────────────

function cmdInit() {
  ensureDir();
  // Initialize schema (required before any insert)
  if (hasSqlite3()) sqliteExec(SCHEMA);

  // Check if entries.jsonl already exists and has content
  let existingHashes = new Set();
  if (existsSync(JSONL_PATH)) {
    const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
    for (const l of lines) {
      try { const e = JSON.parse(l); existingHashes.add(e.hash); } catch {}
    }
    console.log(`[changelog] Found ${existingHashes.size} existing entries in entries.jsonl`);
  }

  // Scan full git log
  const hashes = run('git log --reverse --format="%H"').split('\n').filter(Boolean);
  const newCount = hashes.filter(h => !existingHashes.has(h)).length;
  console.log(`[changelog] Scanning ${hashes.length} commits (${newCount} new)...`);

  for (const h of hashes) {
    if (existingHashes.has(h)) continue;
    const entry = buildEntry(h);
    if (!entry) continue;
    appendFileSync(JSONL_PATH, JSON.stringify(entry) + '\n');
    insertCacheEntry(entry);
  }

  if (hasSqlite3()) process.stdout.write(`[changelog] Done. ${hashes.length} commits logged.\n`);
  else process.stdout.write(`[changelog] Done. ${hashes.length} commits logged (no sqlite3 — cache skipped).\n`);
}

function cmdUpdate() {
  const hash = run('git rev-parse HEAD');
  if (!hash) { console.error('[changelog] Not a git repository?'); return; }

  ensureDir();

  // Idempotent: check entries.jsonl
  if (existsSync(JSONL_PATH)) {
    const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
    for (const l of lines) {
      try { const e = JSON.parse(l); if (e.hash === hash) return; } catch {}
    }
  }

  const entry = buildEntry(hash);
  if (!entry) { console.error('[changelog] Failed to build entry'); return; }

  appendFileSync(JSONL_PATH, JSON.stringify(entry) + '\n');
  insertCacheEntry(entry);
}

function cmdSync() {
  ensureDir();
  const existing = readExistingJsonl();
  const hashes = run('git log --reverse --format="%H" HEAD').split('\n').filter(Boolean);
  if (hashes.length === 0) {
    throw new Error('Unable to read Git history from HEAD');
  }

  const additions = [];
  for (const hash of hashes) {
    if (existing.hashes.has(hash)) continue;
    const entry = buildEntry(hash);
    if (!entry) {
      throw new Error(`Unable to build changelog entry for ${hash}`);
    }
    additions.push(entry);
  }

  writeEntriesAtomically(existing.source, additions);
  for (const entry of additions) insertCacheEntry(entry);
  console.log(
    `[changelog] Sync complete: scanned ${hashes.length}, added ${additions.length}, excluded 0.`,
  );
}

function cmdRebuild() {
  if (!hasSqlite3()) { console.error('[changelog] sqlite3 not found — cannot rebuild cache'); return; }
  ensureDir();
  if (!existsSync(JSONL_PATH)) { console.error('[changelog] entries.jsonl not found'); return; }

  // Drop and recreate tables
  sqliteExec(SCHEMA);
  sqliteExec('DELETE FROM file_changes; DELETE FROM changelog;');

  const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
  let count = 0;
  for (const l of lines) {
    try { const entry = JSON.parse(l); insertCacheEntry(entry); count++; } catch {}
  }
  console.log(`[changelog] Cache rebuilt: ${count} entries from entries.jsonl`);
}

function cmdDedup() {
  ensureDir();
  if (!existsSync(JSONL_PATH)) { console.error('[changelog] entries.jsonl not found'); return; }

  const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
  const seen = new Map();
  let dupes = 0;
  for (const l of lines) {
    try {
      const e = JSON.parse(l);
      if (seen.has(e.hash)) dupes++;
      else seen.set(e.hash, l);
    } catch {}
  }

  if (dupes === 0) { console.log('[changelog] No duplicates found, entries.jsonl is clean'); return; }

  const deduped = Array.from(seen.values());
  writeFileSync(JSONL_PATH, deduped.join('\n') + '\n');
  console.log(`[changelog] Removed ${dupes} duplicates. ${seen.size} entries remain.`);
  cmdRebuild();
}

function cmdSummary() {
  if (!hasSqlite3()) { console.error('[changelog] sqlite3 needed for summary'); return; }
  if (!existsSync(DB_PATH)) { console.error('[changelog] cache.db not found — run --rebuild first'); return; }

  const total = sqliteJson("SELECT COUNT(*) n FROM changelog")[0]?.n || 0;
  const byType = sqliteJson("SELECT COALESCE(type,'(none)') t, COUNT(*) n FROM changelog GROUP BY t ORDER BY n DESC");
  const byAuthor = sqliteJson("SELECT author a, COUNT(*) n FROM changelog GROUP BY a ORDER BY n DESC");
  const totalFiles = sqliteJson("SELECT SUM(files_count) n FROM changelog")[0]?.n || 0;

  console.log(`\n╔══════════════════════════════════╗`);
  console.log(`║     CHANGELOG SUMMARY            ║`);
  console.log(`╠══════════════════════════════════╣`);
  console.log(`║  Total commits:  ${String(total).padEnd(18)}║`);
  console.log(`║  Total files:    ${String(totalFiles).padEnd(18)}║`);
  console.log(`╚══════════════════════════════════╝`);

  if (byType.length) {
    console.log('\n── By type ──');
    for (const r of byType) console.log(`  ${r.t.padEnd(15)} ${r.n}`);
  }
  if (byAuthor.length) {
    console.log('\n── By author ──');
    for (const r of byAuthor) console.log(`  ${r.a.padEnd(20)} ${r.n} commits`);
  }
}

function cmdRead(opts) {
  const { scope, since, limit, json, files } = opts;

  // If no SQLite cache, read directly from JSONL
  if (!hasSqlite3() || !existsSync(DB_PATH)) {
    if (!existsSync(JSONL_PATH)) { console.log('(no changelog entries)'); return; }
    const lines = readFileSync(JSONL_PATH, 'utf-8').split('\n').filter(Boolean);
    let entries = lines.map(l => JSON.parse(l));
    // Filter
    if (scope) entries = entries.filter(e => e.type === scope || e.scope === scope);
    if (since) entries = entries.filter(e => e.timestamp >= since);
    // Reverse (latest first)
    entries = entries.reverse();
    if (limit) entries = entries.slice(0, parseInt(limit));

    if (json) { console.log(JSON.stringify(entries, null, 2)); return; }
    if (files) {
      for (const e of entries) {
        process.stdout.write(`\n${e.hash.slice(0,8)} | ${e.author} | ${e.timestamp}\n`);
        process.stdout.write(`  ${e.summary}\n`);
        process.stdout.write(`  ${e.files_count} file, +${e.insertions} -${e.deletions}\n`);
        if (e.files) for (const f of e.files) {
          const icon = f.type === 'added' ? '+' : f.type === 'deleted' ? '-' : f.type === 'renamed' ? '→' : '~';
          process.stdout.write(`    ${icon} ${f.path}  (${f.type}, +${f.added||0} -${f.deleted||0})\n`);
        }
      }
      return;
    }
    for (const e of entries) {
      console.log(`\n${e.hash.slice(0,8)} | ${e.author} | ${e.timestamp}`);
      console.log(`  ${e.type ? e.type + (e.scope?'/'+e.scope:'') + ': ' : ''}${e.message}`);
      console.log(`  ${e.files_count} files, +${e.insertions} -${e.deletions}`);
      if (e.summary) console.log(`  ${e.summary}`);
    }
    return;
  }

  // Use SQLite cache
  let where = [];
  if (scope) where.push(`(c.scope = '${esc(scope)}' OR c.type = '${esc(scope)}')`);
  if (since) where.push(`c.timestamp >= '${esc(since)}'`);
  const w = where.length > 0 ? 'WHERE ' + where.join(' AND ') : '';
  const lim = limit ? Math.min(Math.max(parseInt(limit), 1), 9999) : 9999;

  if (json) {
    const data = sqliteJson(`
      SELECT c.hash, c.author, c.email, c.timestamp, c.message,
             c.type, c.scope, c.files_count, c.insertions, c.deletions, c.summary
      FROM changelog c ${w} ORDER BY c.id DESC LIMIT ${lim}
    `);
    if (data && data.length > 0) {
      for (const row of data) {
        row.files = sqliteJson(`
          SELECT path, type, lines_added, lines_deleted
          FROM file_changes WHERE changelog_id = (SELECT id FROM changelog WHERE hash='${esc(row.hash)}')
          ORDER BY id
        `) || [];
      }
    }
    console.log(JSON.stringify(data || [], null, 2));
    return;
  }

  if (files) {
    const commits = sqliteJson(`SELECT c.id, substr(c.hash,1,8) hash, c.author, c.timestamp, c.files_count, c.insertions, c.deletions, c.summary FROM changelog c ${w} ORDER BY c.id DESC LIMIT ${lim}`);
    if (!commits || !commits.length) { process.stdout.write('(no entries)\n'); return; }
    for (const row of commits) {
      const fc = sqliteJson(`SELECT f.path, f.type, f.lines_added, f.lines_deleted FROM file_changes f WHERE f.changelog_id = ${row.id} ORDER BY f.type, f.path`) || [];
      process.stdout.write(`\n${row.hash} | ${row.author} | ${row.timestamp}\n  ${row.summary}\n  ${row.files_count} file, +${row.insertions} -${row.deletions}\n`);
      for (const f of fc) {
        const icon = f.type === 'added' ? '+' : f.type === 'deleted' ? '-' : f.type === 'renamed' ? '→' : '~';
        process.stdout.write(`    ${icon} ${f.path}  (${f.type}, +${f.lines_added} -${f.lines_deleted})\n`);
      }
    }
    return;
  }

  const table = sqliteTable(`
    SELECT substr(c.hash,1,8) hash, c.author, c.timestamp,
           COALESCE(c.type,'') tag, c.message,
           c.files_count files, (c.insertions || '+' || c.deletions || '-') delta, c.summary
    FROM changelog c ${w} ORDER BY c.id DESC LIMIT ${lim}
  `);
  console.log(table || '(no entries)');
}

// ─── CLI ──────────────────────────────────────────────────────────

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--sync')) { cmdSync(); return; }
  if (args.includes('--init')) { cmdInit(); return; }
  if (args.includes('--update')) { cmdUpdate(); return; }
  if (args.includes('--rebuild')) { cmdRebuild(); return; }
  if (args.includes('--dedup')) { cmdDedup(); return; }
  if (args.includes('--summary')) { cmdSummary(); return; }

  const scopeIdx = args.indexOf('--scope');
  const sinceIdx = args.indexOf('--since');
  const limitIdx = args.indexOf('--limit');
  cmdRead({
    scope: scopeIdx >= 0 && args[scopeIdx + 1] ? args[scopeIdx + 1] : null,
    since: sinceIdx >= 0 && args[sinceIdx + 1] ? args[sinceIdx + 1] : null,
    limit: limitIdx >= 0 && args[limitIdx + 1] ? args[limitIdx + 1] : null,
    json: args.includes('--json'),
    files: args.includes('--files') || args.includes('-f')
  });
}

try {
  main();
} catch (error) {
  console.error(`[changelog] ${error.message}`);
  process.exitCode = 1;
}
