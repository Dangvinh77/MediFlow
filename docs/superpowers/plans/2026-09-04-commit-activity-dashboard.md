# Commit Activity Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a contributor commit-frequency table and two SVG charts from `.changelog/entries.jsonl`, display them below the README title, and keep them updated through a branch-protection-safe GitHub Actions pull request.

**Architecture:** A dependency-free CommonJS Node.js module parses and validates JSONL, aggregates contributors by normalized email in `Asia/Saigon`, renders deterministic Markdown/SVG, and atomically updates generated files. A GitHub Actions workflow runs tests and generation, then creates or updates one automation pull request instead of pushing directly to protected `master`.

**Tech Stack:** Node.js 20, built-in `node:test`, CommonJS, SVG, Markdown, GitHub Actions, GitHub CLI.

---

## File map

- Create `scripts/commit-activity.js`: parsing, aggregation, rendering, marker replacement, atomic writes, and CLI.
- Create `scripts/commit-activity.test.js`: unit and file-level tests using only Node built-ins.
- Create `.github/workflows/update-commit-activity.yml`: regeneration and protected-branch PR automation.
- Create `docs/assets/commit-activity-by-day.svg`: generated daily stacked-bar chart.
- Create `docs/assets/commit-activity-by-hour.svg`: generated contributor-by-hour heatmap.
- Modify `README.md`: generated dashboard block immediately below the H1.
- Modify `docs/ai/10-git-workflow.md`: local regeneration and GitHub Actions behavior.

### Task 1: Parse, validate, deduplicate, and aggregate changelog entries

**Files:**

- Create: `scripts/commit-activity.test.js`
- Create: `scripts/commit-activity.js`

- [ ] **Step 1: Write failing parser and aggregation tests**

Create `scripts/commit-activity.test.js` with imports and fixtures that define the public model:

```js
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
    entry({ author: 'Old Name', email: ' Person@Example.com ', timestamp: 'Thu Sep 3 01:00:00 2026 +0000' }),
    entry({ hash: 'b'.repeat(40), author: 'New Name', email: 'person@example.com', timestamp: 'Fri Sep 4 02:00:00 2026 +0000' }),
  ];

  const model = aggregateEntries(entries, 'Asia/Saigon');

  assert.equal(model.totalCommits, 2);
  assert.equal(model.contributors.length, 1);
  assert.equal(model.contributors[0].name, 'New Name');
  assert.equal(JSON.stringify(model).includes('person@example.com'), false);
});

test('aggregateEntries calculates daily and hourly statistics in Asia/Saigon', () => {
  const entries = [
    entry({ timestamp: 'Thu Sep 3 18:30:00 2026 +0000' }), // 2026-09-04 01:30 ICT
    entry({ hash: 'b'.repeat(40), timestamp: 'Thu Sep 3 19:30:00 2026 +0000' }),
    entry({ hash: 'c'.repeat(40), timestamp: 'Fri Sep 4 18:30:00 2026 +0000' }),
  ];

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
  const model = aggregateEntries([
    entry({ timestamp: 'Tue Sep 1 01:00:00 2026 +0700' }),
    entry({ hash: 'b'.repeat(40), timestamp: 'Thu Sep 3 01:00:00 2026 +0700' }),
  ]);

  assert.deepEqual(model.dates, ['2026-09-01', '2026-09-02', '2026-09-03']);
  assert.equal(model.daily['2026-09-02'][0], 0);
});
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```bash
node --test scripts/commit-activity.test.js
```

Expected: FAIL with `Cannot find module './commit-activity'`.

- [ ] **Step 3: Implement the parser and aggregation model**

Create `scripts/commit-activity.js` with these exact exported boundaries:

```js
const HASH_PATTERN = /^[0-9a-f]{40}$/i;
const DEFAULT_TIME_ZONE = 'Asia/Saigon';

function parseEntries(jsonl) {
  const seen = new Set();
  const entries = [];

  jsonl.split(/\r?\n/).forEach((line, index) => {
    if (!line.trim()) return;
    const lineNumber = index + 1;
    let value;
    try {
      value = JSON.parse(line);
    } catch {
      throw new Error(`Invalid JSON at line ${lineNumber}`);
    }

    if (!HASH_PATTERN.test(value.hash || '')) throw new Error(`Invalid hash at line ${lineNumber}`);
    if (!String(value.author || '').trim()) throw new Error(`Invalid author at line ${lineNumber}`);
    if (!String(value.email || '').trim()) throw new Error(`Invalid email at line ${lineNumber}`);
    const timestampMs = Date.parse(value.timestamp);
    if (!Number.isFinite(timestampMs)) throw new Error(`Invalid timestamp at line ${lineNumber}`);
    if (seen.has(value.hash.toLowerCase())) return;

    seen.add(value.hash.toLowerCase());
    entries.push({ ...value, timestampMs });
  });

  return entries;
}

function localParts(timestampMs, timeZone) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hourCycle: 'h23',
  });
  const parts = Object.fromEntries(
    formatter.formatToParts(new Date(timestampMs))
      .filter(({ type }) => type !== 'literal')
      .map(({ type, value }) => [type, value]),
  );
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    hour: Number(parts.hour),
    display: `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`,
  };
}

function dateRange(first, last) {
  if (!first || !last) return [];
  const dates = [];
  for (let cursor = Date.parse(`${first}T00:00:00Z`); cursor <= Date.parse(`${last}T00:00:00Z`); cursor += 86_400_000) {
    dates.push(new Date(cursor).toISOString().slice(0, 10));
  }
  return dates;
}

function aggregateEntries(entries, timeZone = DEFAULT_TIME_ZONE) {
  const byEmail = new Map();
  let firstDate = null;
  let lastDate = null;
  let latestTimestampMs = null;

  for (const entry of entries) {
    const emailKey = entry.email.trim().toLowerCase();
    const local = localParts(entry.timestampMs, timeZone);
    let contributor = byEmail.get(emailKey);
    if (!contributor) {
      contributor = {
        name: entry.author.trim(),
        latestNameTimestampMs: entry.timestampMs,
        latestTimestampMs: entry.timestampMs,
        total: 0,
        days: new Map(),
        hours: Array(24).fill(0),
      };
      byEmail.set(emailKey, contributor);
    }

    if (entry.timestampMs >= contributor.latestNameTimestampMs) {
      contributor.name = entry.author.trim();
      contributor.latestNameTimestampMs = entry.timestampMs;
    }
    contributor.latestTimestampMs = Math.max(contributor.latestTimestampMs, entry.timestampMs);
    contributor.total += 1;
    contributor.days.set(local.date, (contributor.days.get(local.date) || 0) + 1);
    contributor.hours[local.hour] += 1;

    firstDate = firstDate === null || local.date < firstDate ? local.date : firstDate;
    lastDate = lastDate === null || local.date > lastDate ? local.date : lastDate;
    latestTimestampMs = latestTimestampMs === null
      ? entry.timestampMs
      : Math.max(latestTimestampMs, entry.timestampMs);
  }

  const states = [...byEmail.values()].sort(
    (left, right) => right.total - left.total || left.name.localeCompare(right.name, 'en'),
  );
  const dates = dateRange(firstDate, lastDate);
  const contributors = states.map((state) => {
    const peakDay = [...state.days.entries()].sort(
      ([leftDate, leftCount], [rightDate, rightCount]) => rightCount - leftCount || leftDate.localeCompare(rightDate),
    )[0];
    const peakHour = state.hours
      .map((count, hour) => ({ hour, count }))
      .sort((left, right) => right.count - left.count || left.hour - right.hour)[0];
    return {
      name: state.name,
      total: state.total,
      activeDays: state.days.size,
      averagePerActiveDay: state.total / state.days.size,
      peakDay: { date: peakDay[0], count: peakDay[1] },
      peakHour,
      latest: localParts(state.latestTimestampMs, timeZone).display,
    };
  });

  return {
    timeZone,
    totalCommits: entries.length,
    generatedAt: latestTimestampMs === null ? 'No commits' : localParts(latestTimestampMs, timeZone).display,
    dates,
    contributors,
    daily: Object.fromEntries(dates.map((date) => [date, states.map((state) => state.days.get(date) || 0)])),
    hourly: states.map((state) => [...state.hours]),
  };
}

module.exports = { parseEntries, aggregateEntries };
```

The returned object is:

```js
{
  timeZone,
  totalCommits,
  generatedAt, // latest commit rendered in the selected timezone, or 'No commits'
  dates,
  contributors: [{ name, total, activeDays, averagePerActiveDay, peakDay, peakHour, latest }],
  daily: { 'YYYY-MM-DD': [countByContributorIndex] },
  hourly: [[24 counts per contributor]],
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run `node --test scripts/commit-activity.test.js`.

Expected: all parser and aggregation tests PASS.

- [ ] **Step 5: Commit the data model**

```bash
git add scripts/commit-activity.js scripts/commit-activity.test.js
git commit -m "feat(tooling): aggregate commit activity from changelog"
```

### Task 2: Render the contributor table and safely update README

**Files:**

- Modify: `scripts/commit-activity.test.js`
- Modify: `scripts/commit-activity.js`
- Modify: `README.md`

- [ ] **Step 1: Add failing Markdown and marker tests**

Append tests that assert escaping, compact table fields, deterministic timestamps, and exact marker replacement:

```js
const {
  renderDashboardMarkdown,
  replaceGeneratedBlock,
} = require('./commit-activity');

test('renderDashboardMarkdown escapes names and never renders email', () => {
  const model = aggregateEntries([
    entry({ author: 'A | <B>', email: 'secret@example.com' }),
  ]);
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
  assert.equal(updated, '# Title\n\nBefore\n<!-- commit-activity:start -->\nnew\n<!-- commit-activity:end -->\nAfter\n');
});

test('replaceGeneratedBlock rejects missing, duplicate, and reversed markers', () => {
  assert.throws(() => replaceGeneratedBlock('# Title\n', 'new'), /exactly one start marker/i);
  assert.throws(() => replaceGeneratedBlock('<!-- commit-activity:start -->\n<!-- commit-activity:start -->\n<!-- commit-activity:end -->', 'new'), /exactly one start marker/i);
  assert.throws(() => replaceGeneratedBlock('<!-- commit-activity:end -->\n<!-- commit-activity:start -->', 'new'), /end marker must follow/i);
});
```

- [ ] **Step 2: Run tests and verify RED**

Expected: FAIL because `renderDashboardMarkdown` and `replaceGeneratedBlock` are not exported.

- [ ] **Step 3: Implement Markdown rendering and marker replacement**

Add constants and functions:

```js
const START_MARKER = '<!-- commit-activity:start -->';
const END_MARKER = '<!-- commit-activity:end -->';

function escapeMarkdown(value) {
  return String(value).replace(/\r?\n/g, ' ').replace(/\|/g, '\\|');
}

function renderDashboardMarkdown(model) {
  if (model.totalCommits === 0) {
    return `## Commit activity\n\n_No commits recorded._\n\nData source: \`.changelog/entries.jsonl\`.`;
  }
  const rows = model.contributors.map((contributor) => [
    escapeMarkdown(contributor.name),
    contributor.total,
    contributor.activeDays,
    contributor.averagePerActiveDay.toFixed(2),
    `${contributor.peakDay.date} (${contributor.peakDay.count})`,
    `${String(contributor.peakHour.hour).padStart(2, '0')}:00 (${contributor.peakHour.count})`,
    contributor.latest,
  ].join(' | '));

  return [
    '## Commit activity',
    '',
    `Updated through **${model.generatedAt} ${model.timeZone}** · **${model.totalCommits} unique commits**`,
    '',
    '| Contributor | Commits | Active days | Avg/active day | Peak date | Peak hour | Latest commit |',
    '|---|---:|---:|---:|---|---|---|',
    ...rows.map((row) => `| ${row} |`),
    '',
    '![Commits by day](docs/assets/commit-activity-by-day.svg)',
    '',
    '![Commits by hour](docs/assets/commit-activity-by-hour.svg)',
    '',
    '_Source: `.changelog/entries.jsonl`; this is repository changelog data, not GitHub Insights._',
  ].join('\n');
}

function replaceGeneratedBlock(readme, generated) {
  const startCount = readme.split(START_MARKER).length - 1;
  const endCount = readme.split(END_MARKER).length - 1;
  if (startCount !== 1) throw new Error('README must contain exactly one start marker');
  if (endCount !== 1) throw new Error('README must contain exactly one end marker');

  const startIndex = readme.indexOf(START_MARKER);
  const endIndex = readme.indexOf(END_MARKER);
  if (endIndex < startIndex) throw new Error('README end marker must follow start marker');

  const before = readme.slice(0, startIndex + START_MARKER.length);
  const after = readme.slice(endIndex);
  return `${before}\n${generated.trim()}\n${after}`;
}
```

Export both functions. Add this initial generated-section marker block immediately after the README H1 so the first generator run can replace it:

```md
<!-- commit-activity:start -->
_Commit activity dashboard will be generated from `.changelog/entries.jsonl`._
<!-- commit-activity:end -->
```

- [ ] **Step 4: Run tests and verify GREEN**

Run `node --test scripts/commit-activity.test.js`.

Expected: all tests PASS.

- [ ] **Step 5: Commit README rendering**

```bash
git add scripts/commit-activity.js scripts/commit-activity.test.js README.md
git commit -m "feat(tooling): render commit activity README table"
```

### Task 3: Render deterministic daily and hourly SVG charts

**Files:**

- Modify: `scripts/commit-activity.test.js`
- Modify: `scripts/commit-activity.js`

- [ ] **Step 1: Add failing SVG tests**

```js
const {
  renderDailySvg,
  renderHourlySvg,
} = require('./commit-activity');

test('renderDailySvg renders every date, contributor legend, and accessible text', () => {
  const model = aggregateEntries([
    entry({ author: 'A & B', timestamp: 'Tue Sep 1 01:00:00 2026 +0700' }),
    entry({ hash: 'b'.repeat(40), timestamp: 'Thu Sep 3 01:00:00 2026 +0700' }),
  ]);
  const svg = renderDailySvg(model);

  assert.match(svg, /<title>Commit activity by day<\/title>/);
  assert.match(svg, /2026-09-01/);
  assert.match(svg, /2026-09-03/);
  assert.match(svg, /A &amp; B/);
  assert.doesNotMatch(svg, /@example\.com/);
});

test('renderHourlySvg renders 24 columns and escaped tooltips', () => {
  const model = aggregateEntries([entry({ author: 'A < B' })]);
  const svg = renderHourlySvg(model);

  assert.match(svg, /<title>Commit activity by hour<\/title>/);
  assert.equal((svg.match(/data-hour=/g) || []).length, 24);
  assert.match(svg, /A &lt; B/);
});

test('SVG renderers support empty data', () => {
  const model = aggregateEntries([]);
  assert.match(renderDailySvg(model), /No commits recorded/);
  assert.match(renderHourlySvg(model), /No commits recorded/);
});
```

- [ ] **Step 2: Run tests and verify RED**

Expected: FAIL because the SVG functions are not exported.

- [ ] **Step 3: Implement deterministic SVG renderers**

Use a fixed palette and context-safe XML escaping:

```js
const PALETTE = ['#2563eb', '#16a34a', '#dc2626', '#9333ea', '#ea580c', '#0891b2', '#4f46e5', '#65a30d'];

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function renderDailySvg(model) {
  if (model.totalCommits === 0) return emptySvg('Commit activity by day');
  const margin = { top: 70, right: 24, bottom: 100, left: 56 };
  const width = Math.max(960, margin.left + margin.right + model.dates.length * 12);
  const height = 430;
  const chartWidth = width - margin.left - margin.right;
  const chartHeight = height - margin.top - margin.bottom;
  const totals = model.dates.map((date) => model.daily[date].reduce((sum, count) => sum + count, 0));
  const maximum = Math.max(...totals, 1);
  const slot = chartWidth / model.dates.length;
  const barWidth = Math.max(2, slot * 0.72);
  const labelStep = Math.max(1, Math.ceil(model.dates.length / 10));
  const tickSteps = Math.min(maximum, 5);
  const ticks = [...new Set(Array.from({ length: tickSteps + 1 }, (_, index) =>
    Math.round(maximum * index / (tickSteps || 1))))];
  const elements = [];

  for (const tick of ticks) {
    const y = margin.top + chartHeight - tick / maximum * chartHeight;
    elements.push(`<line x1="${margin.left}" y1="${y}" x2="${width - margin.right}" y2="${y}" stroke="#d1d5db"/>`);
    elements.push(`<text x="${margin.left - 8}" y="${y + 4}" text-anchor="end" font-size="12" fill="#4b5563">${tick}</text>`);
  }

  model.dates.forEach((date, dateIndex) => {
    let stack = 0;
    model.daily[date].forEach((count, contributorIndex) => {
      if (!count) return;
      const segmentHeight = count / maximum * chartHeight;
      const x = margin.left + dateIndex * slot + (slot - barWidth) / 2;
      const y = margin.top + chartHeight - stack - segmentHeight;
      elements.push(`<rect x="${x}" y="${y}" width="${barWidth}" height="${segmentHeight}" fill="${PALETTE[contributorIndex % PALETTE.length]}"><title>${escapeXml(model.contributors[contributorIndex].name)} · ${date}: ${count}</title></rect>`);
      stack += segmentHeight;
    });
    if (dateIndex % labelStep === 0 || dateIndex === model.dates.length - 1) {
      const x = margin.left + dateIndex * slot + slot / 2;
      elements.push(`<text x="${x}" y="${margin.top + chartHeight + 20}" transform="rotate(35 ${x} ${margin.top + chartHeight + 20})" font-size="11" fill="#4b5563">${date}</text>`);
    }
  });

  model.contributors.forEach((contributor, index) => {
    const x = margin.left + (index % 4) * 220;
    const y = height - 42 + Math.floor(index / 4) * 22;
    elements.push(`<rect x="${x}" y="${y - 11}" width="12" height="12" fill="${PALETTE[index % PALETTE.length]}"/>`);
    elements.push(`<text x="${x + 18}" y="${y}" font-size="12" fill="#111827">${escapeXml(contributor.name)}</text>`);
  });

  const legendRows = Math.ceil(model.contributors.length / 4);
  const finalHeight = height + Math.max(0, legendRows - 1) * 22;
  return `<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 ${width} ${finalHeight}">
<title>Commit activity by day</title>
<desc>Stacked daily commit counts by contributor from ${model.dates[0]} through ${model.dates.at(-1)}.</desc>
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="${margin.left}" y="32" font-size="20" font-weight="700" fill="#111827">Commits by day</text>
${elements.join('\n')}
</svg>\n`;
}

function renderHourlySvg(model) {
  if (model.totalCommits === 0) return emptySvg('Commit activity by hour');
  const labelWidth = 180;
  const cell = 30;
  const top = 62;
  const width = labelWidth + 24 * cell + 24;
  const height = top + model.contributors.length * cell + 38;
  const elements = [];

  for (let hour = 0; hour < 24; hour += 1) {
    elements.push(`<text x="${labelWidth + hour * cell + cell / 2}" y="${top - 12}" text-anchor="middle" font-size="10" fill="#4b5563">${String(hour).padStart(2, '0')}</text>`);
  }

  model.contributors.forEach((contributor, contributorIndex) => {
    const counts = model.hourly[contributorIndex];
    const maximum = Math.max(...counts, 1);
    const y = top + contributorIndex * cell;
    elements.push(`<text x="${labelWidth - 10}" y="${y + 20}" text-anchor="end" font-size="12" fill="#111827">${escapeXml(contributor.name)}</text>`);
    counts.forEach((count, hour) => {
      const opacity = count === 0 ? 0 : 0.2 + 0.8 * count / maximum;
      const fill = count === 0 ? '#f3f4f6' : PALETTE[contributorIndex % PALETTE.length];
      elements.push(`<rect data-hour="${hour}" x="${labelWidth + hour * cell}" y="${y}" width="${cell - 2}" height="${cell - 2}" rx="3" fill="${fill}" fill-opacity="${opacity}"><title>${escapeXml(contributor.name)} · ${String(hour).padStart(2, '0')}:00: ${count}</title></rect>`);
    });
  });

  return `<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 ${width} ${height}">
<title>Commit activity by hour</title>
<desc>Commit counts by contributor and hour in ${escapeXml(model.timeZone)}.</desc>
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="24" y="30" font-size="20" font-weight="700" fill="#111827">Commits by hour · ${escapeXml(model.timeZone)}</text>
${elements.join('\n')}
</svg>\n`;
}

function emptySvg(title) {
  return `<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 960 180">
<title>${escapeXml(title)}</title>
<desc>No commits recorded.</desc>
<rect width="100%" height="100%" fill="#ffffff"/>
<text x="480" y="92" text-anchor="middle" font-size="18" fill="#6b7280">No commits recorded</text>
</svg>\n`;
}
```

Export `renderDailySvg` and `renderHourlySvg`. The code above produces complete XML-safe SVG documents ending in a newline.

- [ ] **Step 4: Run tests and verify GREEN**

Run `node --test scripts/commit-activity.test.js`.

Expected: all tests PASS and no author email appears in captured SVG strings.

- [ ] **Step 5: Commit SVG rendering**

```bash
git add scripts/commit-activity.js scripts/commit-activity.test.js
git commit -m "feat(tooling): render commit activity charts"
```

### Task 4: Add the atomic generator CLI and generate repository artifacts

**Files:**

- Modify: `scripts/commit-activity.test.js`
- Modify: `scripts/commit-activity.js`
- Modify: `README.md`
- Create: `docs/assets/commit-activity-by-day.svg`
- Create: `docs/assets/commit-activity-by-hour.svg`

- [ ] **Step 1: Add failing file-level generation tests**

Use `node:fs`, `node:os`, and `node:path` to create a temporary repository fixture:

```js
const { mkdtempSync, readFileSync, writeFileSync, mkdirSync, existsSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { generate } = require('./commit-activity');

test('generate writes README and both SVGs and is idempotent', () => {
  const root = mkdtempSync(join(tmpdir(), 'commit-activity-'));
  const changelog = join(root, 'entries.jsonl');
  const readme = join(root, 'README.md');
  const daily = join(root, 'assets', 'daily.svg');
  const hourly = join(root, 'assets', 'hourly.svg');
  mkdirSync(join(root, 'assets'));
  writeFileSync(changelog, `${JSON.stringify(entry())}\n`);
  writeFileSync(readme, '# Demo\n\n<!-- commit-activity:start -->\nold\n<!-- commit-activity:end -->\n');

  generate({ changelog, readme, daily, hourly, timeZone: 'Asia/Saigon' });
  const first = [readFileSync(readme, 'utf8'), readFileSync(daily, 'utf8'), readFileSync(hourly, 'utf8')];
  generate({ changelog, readme, daily, hourly, timeZone: 'Asia/Saigon' });
  const second = [readFileSync(readme, 'utf8'), readFileSync(daily, 'utf8'), readFileSync(hourly, 'utf8')];

  assert.deepEqual(second, first);
  assert.equal(existsSync(`${readme}.tmp`), false);
});
```

- [ ] **Step 2: Run tests and verify RED**

Expected: FAIL because `generate` is not exported.

- [ ] **Step 3: Implement atomic generation and CLI defaults**

Add:

```js
const { readFileSync, writeFileSync, renameSync, mkdirSync } = require('node:fs');
const { dirname, resolve } = require('node:path');

function atomicWrite(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  const temporary = `${path}.tmp`;
  writeFileSync(temporary, content, 'utf8');
  renameSync(temporary, path);
}

function generate(options) {
  const entries = parseEntries(readFileSync(options.changelog, 'utf8'));
  const model = aggregateEntries(entries, options.timeZone);
  const readme = readFileSync(options.readme, 'utf8');
  atomicWrite(options.daily, renderDailySvg(model));
  atomicWrite(options.hourly, renderHourlySvg(model));
  atomicWrite(options.readme, replaceGeneratedBlock(readme, renderDashboardMarkdown(model)));
  return model;
}

function parseOptions(args, root) {
  const options = {
    changelog: resolve(root, '.changelog/entries.jsonl'),
    readme: resolve(root, 'README.md'),
    daily: resolve(root, 'docs/assets/commit-activity-by-day.svg'),
    hourly: resolve(root, 'docs/assets/commit-activity-by-hour.svg'),
    timeZone: DEFAULT_TIME_ZONE,
  };
  const names = {
    '--input': 'changelog',
    '--readme': 'readme',
    '--daily': 'daily',
    '--hourly': 'hourly',
    '--timezone': 'timeZone',
  };

  for (let index = 0; index < args.length; index += 2) {
    const property = names[args[index]];
    const value = args[index + 1];
    if (!property) throw new Error(`Unknown option: ${args[index]}`);
    if (!value) throw new Error(`Missing value for ${args[index]}`);
    options[property] = property === 'timeZone' ? value : resolve(root, value);
  }
  return options;
}

function main(args = process.argv.slice(2)) {
  const { execFileSync } = require('node:child_process');
  try {
    const root = execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
    const model = generate(parseOptions(args, root));
    process.stdout.write(`[commit-activity] Generated dashboard for ${model.totalCommits} commits.\n`);
  } catch (error) {
    process.stderr.write(`[commit-activity] ${error.message}\n`);
    process.exitCode = 1;
  }
}

if (require.main === module) main();

module.exports = {
  aggregateEntries,
  generate,
  parseEntries,
  renderDailySvg,
  renderDashboardMarkdown,
  renderHourlySvg,
  replaceGeneratedBlock,
};
```

The CLI flags are `--input`, `--readme`, `--daily`, `--hourly`, and `--timezone`. Importing the module has no side effects; CLI failures print `[commit-activity] <message>` to stderr and set exit code `1`.

- [ ] **Step 4: Run unit tests and verify GREEN**

Run:

```bash
node --test scripts/commit-activity.test.js
```

Expected: all tests PASS.

- [ ] **Step 5: Generate real README and SVG artifacts twice**

```bash
node scripts/commit-activity.js
git diff -- README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
git add README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
node scripts/commit-activity.js
git diff --exit-code -- README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
```

The staged files form the first-run baseline; expected second-run result is exit code `0` with no unstaged diff. Confirm the README table total matches `node scripts/changelog.js --summary`.

- [ ] **Step 6: Commit the generator and generated output**

```bash
git add scripts/commit-activity.js scripts/commit-activity.test.js README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
git commit -m "feat(tooling): generate commit activity dashboard"
```

### Task 5: Add the branch-protection-safe GitHub Actions workflow

**Files:**

- Create: `.github/workflows/update-commit-activity.yml`

- [ ] **Step 1: Add the workflow file**

Create `.github/workflows/update-commit-activity.yml`:

```yaml
name: Update commit activity dashboard

on:
  push:
    branches: [master]
    paths:
      - .changelog/entries.jsonl
      - scripts/commit-activity.js
      - scripts/commit-activity.test.js
      - .github/workflows/update-commit-activity.yml
  workflow_dispatch:

permissions:
  contents: write
  pull-requests: write

concurrency:
  group: commit-activity-dashboard
  cancel-in-progress: true

jobs:
  update:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: master
          fetch-depth: 0

      - uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Test generator
        run: node --test scripts/commit-activity.test.js

      - name: Generate dashboard
        run: node scripts/commit-activity.js

      - name: Create or update dashboard pull request
        env:
          GH_TOKEN: ${{ github.token }}
          BRANCH: automation/commit-activity-dashboard
        shell: bash
        run: |
          if git diff --quiet -- README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg; then
            echo 'Dashboard is already current.'
            exit 0
          fi

          git config user.name 'github-actions[bot]'
          git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
          git switch -C "$BRANCH"
          git add README.md docs/assets/commit-activity-by-day.svg docs/assets/commit-activity-by-hour.svg
          git commit -m 'docs(tooling): update commit activity dashboard'
          git push --force-with-lease origin "HEAD:$BRANCH"

          pr_number="$(gh pr list --head "$BRANCH" --base master --state open --json number --jq '.[0].number // empty')"
          if [ -z "$pr_number" ]; then
            pr_url="$(gh pr create --base master --head "$BRANCH" --title 'docs(tooling): update commit activity dashboard' --body 'Automated refresh from `.changelog/entries.jsonl`.')"
            pr_number="${pr_url##*/}"
          fi

          gh pr merge "$pr_number" --squash --auto || echo '::warning::Auto-merge is unavailable; maintainer merge required.'
```

- [ ] **Step 2: Validate workflow syntax and behavior assumptions**

Run:

```bash
git diff --check -- .github/workflows/update-commit-activity.yml
node --test scripts/commit-activity.test.js
node scripts/commit-activity.js
```

Expected: no whitespace errors, tests PASS, and generator exits `0`.

Review the workflow manually for these invariants:

- Only `automation/commit-activity-dashboard` is force-updated.
- `master` is never pushed directly.
- Generated commits contain only README and the two SVG files.
- A merged generated-only PR cannot re-trigger the path filter.
- Auto-merge failure leaves a visible open PR rather than failing generation.

- [ ] **Step 3: Commit workflow automation**

```bash
git add .github/workflows/update-commit-activity.yml
git commit -m "ci(tooling): automate commit activity dashboard"
```

### Task 6: Document operation and perform final verification

**Files:**

- Modify: `docs/ai/10-git-workflow.md`

- [ ] **Step 1: Document local and remote regeneration**

Add a `### Commit activity dashboard` subsection after the changelog viewing commands:

````md
### Commit activity dashboard

`README.md` contains a generated contributor table and SVG charts derived from the full
`.changelog/entries.jsonl` history. Regenerate and verify locally with:

```bash
node --test scripts/commit-activity.test.js
node scripts/commit-activity.js
```

On `master`, changes to the changelog or generator trigger
`.github/workflows/update-commit-activity.yml`. The workflow updates the deterministic
`automation/commit-activity-dashboard` branch, opens or refreshes one pull request, and enables
auto-merge when repository settings allow it. Contributor emails are used only as internal
identity keys and are never rendered.
````

- [ ] **Step 2: Run the complete verification suite**

```bash
node --test scripts/commit-activity.test.js
node scripts/changelog.js --dedup
node scripts/changelog.js --rebuild
node scripts/changelog.js --summary
node scripts/commit-activity.js
git diff --check
```

Record these facts from output:

- All dashboard tests pass with zero failures.
- Changelog has zero duplicate hashes.
- Dashboard total equals changelog summary total.
- Generated files contain no contributor email addresses.
- A second generator run leaves README and both SVG hashes unchanged.
- Only the expected changelog hook update and any explicitly ignored Word lock files remain outside the implementation diff.

- [ ] **Step 3: Commit documentation**

```bash
git add docs/ai/10-git-workflow.md
git commit -m "docs(tooling): document commit activity automation"
```

- [ ] **Step 4: Review the final branch diff**

```bash
git status --short --branch
git diff --stat origin/master...HEAD
git log --oneline origin/master..HEAD
```

Expected: only the dashboard implementation, tests, generated README/SVG output, workflow, and Git workflow documentation are present. Do not add files matching `~$*.docx`.
