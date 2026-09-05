#!/usr/bin/env node

const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

const HASH_PATTERN = /^[0-9a-f]{40}$/i;
const DEFAULT_TIME_ZONE = 'Asia/Saigon';
const START_MARKER = '<!-- commit-activity:start -->';
const END_MARKER = '<!-- commit-activity:end -->';
const PALETTE = [
  '#2563eb',
  '#16a34a',
  '#dc2626',
  '#9333ea',
  '#ea580c',
  '#0891b2',
  '#4f46e5',
  '#65a30d',
];

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

    if (value === null || typeof value !== 'object' || Array.isArray(value)) {
      throw new Error(`Invalid record at line ${lineNumber}`);
    }
    if (typeof value.hash !== 'string' || !HASH_PATTERN.test(value.hash)) {
      throw new Error(`Invalid hash at line ${lineNumber}`);
    }
    if (typeof value.author !== 'string' || !value.author.trim()) {
      throw new Error(`Invalid author at line ${lineNumber}`);
    }
    if (typeof value.email !== 'string' || !value.email.trim()) {
      throw new Error(`Invalid email at line ${lineNumber}`);
    }
    if (typeof value.timestamp !== 'string') {
      throw new Error(`Invalid timestamp at line ${lineNumber}`);
    }

    const timestampMs = Date.parse(value.timestamp);
    if (!Number.isFinite(timestampMs)) {
      throw new Error(`Invalid timestamp at line ${lineNumber}`);
    }

    const normalizedHash = value.hash.toLowerCase();
    if (seen.has(normalizedHash)) return;

    seen.add(normalizedHash);
    entries.push({ ...value, timestampMs });
  });

  return entries;
}

function localParts(timestampMs, timeZone) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
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
  const lastTimestamp = Date.parse(`${last}T00:00:00Z`);
  for (
    let cursor = Date.parse(`${first}T00:00:00Z`);
    cursor <= lastTimestamp;
    cursor += 86_400_000
  ) {
    dates.push(new Date(cursor).toISOString().slice(0, 10));
  }
  return dates;
}

function publicDisplayName(value) {
  const displayName = String(value).trim();
  return displayName.includes('@') || !displayName ? '[redacted]' : displayName;
}

function parseAliases(json) {
  let value;
  try {
    value = JSON.parse(json);
  } catch {
    throw new Error('Invalid contributor aliases JSON');
  }

  if (
    value === null
    || typeof value !== 'object'
    || Array.isArray(value)
    || !Array.isArray(value.contributors)
  ) {
    throw new Error('Contributor aliases must contain a contributors array');
  }

  const ids = new Set();
  const aliases = new Map();
  value.contributors.forEach((contributor, index) => {
    if (contributor === null || typeof contributor !== 'object' || Array.isArray(contributor)) {
      throw new Error(`Invalid contributor at index ${index}`);
    }

    const id = typeof contributor.id === 'string' ? contributor.id.trim() : '';
    const name = typeof contributor.name === 'string' ? contributor.name.trim() : '';
    if (!id) throw new Error(`Invalid contributor id at index ${index}`);
    if (!name) throw new Error(`Invalid contributor name at index ${index}`);
    if (name.includes('@')) {
      throw new Error(`Contributor name must not contain an email address at index ${index}`);
    }
    if (ids.has(id)) throw new Error(`Duplicate contributor id: ${id}`);
    if (!Array.isArray(contributor.emails) || contributor.emails.length === 0) {
      throw new Error(`Invalid contributor emails at index ${index}`);
    }
    ids.add(id);

    contributor.emails.forEach((email) => {
      const normalized = typeof email === 'string' ? email.trim().toLowerCase() : '';
      if (!normalized) throw new Error(`Invalid email alias at index ${index}`);
      if (aliases.has(normalized)) {
        throw new Error(`Email alias belongs to multiple contributors: ${normalized}`);
      }
      aliases.set(normalized, { id, name });
    });
  });

  return aliases;
}

function aggregateEntries(entries, timeZone = DEFAULT_TIME_ZONE, aliases = new Map()) {
  const byIdentity = new Map();
  let firstDate = null;
  let lastDate = null;
  let latestTimestampMs = null;

  for (const entry of entries) {
    const normalizedEmail = entry.email.trim().toLowerCase();
    const configured = aliases.get(normalizedEmail);
    const identityKey = configured ? `alias:${configured.id}` : `email:${normalizedEmail}`;
    const local = localParts(entry.timestampMs, timeZone);
    let contributor = byIdentity.get(identityKey);

    if (!contributor) {
      contributor = {
        name: configured ? configured.name : publicDisplayName(entry.author),
        configuredName: Boolean(configured),
        latestNameTimestampMs: entry.timestampMs,
        latestTimestampMs: entry.timestampMs,
        total: 0,
        days: new Map(),
        hours: Array(24).fill(0),
      };
      byIdentity.set(identityKey, contributor);
    }

    if (!contributor.configuredName && entry.timestampMs >= contributor.latestNameTimestampMs) {
      contributor.name = publicDisplayName(entry.author);
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

  const states = [...byIdentity.values()].sort(
    (left, right) => right.total - left.total || left.name.localeCompare(right.name, 'en'),
  );
  const dates = dateRange(firstDate, lastDate);
  const contributors = states.map((state) => {
    const [peakDate, peakDateCount] = [...state.days.entries()].sort(
      ([leftDate, leftCount], [rightDate, rightCount]) => (
        rightCount - leftCount || leftDate.localeCompare(rightDate)
      ),
    )[0];
    const peakHour = state.hours
      .map((count, hour) => ({ hour, count }))
      .sort((left, right) => right.count - left.count || left.hour - right.hour)[0];

    return {
      name: state.name,
      total: state.total,
      activeDays: state.days.size,
      averagePerActiveDay: state.total / state.days.size,
      peakDay: { date: peakDate, count: peakDateCount },
      peakHour,
      latest: localParts(state.latestTimestampMs, timeZone).display,
    };
  });

  return {
    timeZone,
    totalCommits: entries.length,
    generatedAt: latestTimestampMs === null
      ? 'No commits'
      : localParts(latestTimestampMs, timeZone).display,
    dates,
    contributors,
    daily: Object.fromEntries(
      dates.map((date) => [date, states.map((state) => state.days.get(date) || 0)]),
    ),
    hourly: states.map((state) => [...state.hours]),
  };
}

function escapeMarkdown(value) {
  return String(value).replace(/[\r\n]+/g, ' ').replace(/\|/g, '\\|');
}

function contentDigest(content) {
  return createHash('sha256').update(content, 'utf8').digest('hex').slice(0, 12);
}

function renderDashboardMarkdown(model, versions) {
  const assetVersions = versions || {
    daily: contentDigest(`${renderDailySvg(model)}\n`),
    hourly: contentDigest(`${renderHourlySvg(model)}\n`),
  };
  const rows = model.contributors.map((contributor) => [
    escapeMarkdown(contributor.name),
    contributor.total,
    contributor.activeDays,
    contributor.averagePerActiveDay.toFixed(2),
    `${contributor.peakDay.date} (${contributor.peakDay.count})`,
    `${String(contributor.peakHour.hour).padStart(2, '0')}:00 (${contributor.peakHour.count})`,
    contributor.latest,
  ].join(' | '));
  if (rows.length === 0) {
    rows.push('_No commits recorded._ | 0 | 0 | 0.00 | — | — | —');
  }
  const updateStatus = model.totalCommits === 0
    ? `No commits · **0 unique commits** · ${model.timeZone}`
    : `Changelog updated through **${model.generatedAt} ${model.timeZone}** · **${model.totalCommits} unique commits**`;

  return [
    '## Commit activity',
    '',
    updateStatus,
    '',
    '| Contributor | Commits | Active days | Avg/active day | Peak date | Peak hour | Latest commit |',
    '|---|---:|---:|---:|---|---|---|',
    ...rows.map((row) => `| ${row} |`),
    '',
    `![Commits by day](docs/assets/commit-activity-by-day.svg?v=${assetVersions.daily})`,
    '',
    `![Commits by hour](docs/assets/commit-activity-by-hour.svg?v=${assetVersions.hourly})`,
    '',
    '_Source: `.changelog/entries.jsonl`; this is repository changelog data, not GitHub Insights._',
  ].join('\n');
}

function replaceGeneratedBlock(readme, generated) {
  const startCount = readme.split(START_MARKER).length - 1;
  const endCount = readme.split(END_MARKER).length - 1;

  if (startCount !== 1) {
    throw new Error('README must contain exactly one start marker');
  }
  if (endCount !== 1) {
    throw new Error('README must contain exactly one end marker');
  }

  const startIndex = readme.indexOf(START_MARKER);
  const endIndex = readme.indexOf(END_MARKER);
  if (endIndex < startIndex) {
    throw new Error('README end marker must follow start marker');
  }

  const before = readme.slice(0, startIndex + START_MARKER.length);
  const after = readme.slice(endIndex);
  return `${before}\n${generated.trim()}\n${after}`;
}

function escapeXml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function emptyChart(title) {
  return [
    '<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 960 180">',
    `  <title>${escapeXml(title)}</title>`,
    '  <rect width="960" height="180" rx="12" fill="#ffffff" stroke="#d0d7de"/>',
    '  <text x="480" y="94" text-anchor="middle" font-family="system-ui, sans-serif" font-size="16" fill="#57606a">No commits recorded</text>',
    '</svg>',
  ].join('\n');
}

function renderDailySvg(model) {
  if (model.totalCommits === 0) {
    return emptyChart('Commit activity by day');
  }

  const margin = { top: 66, right: 28, bottom: 92, left: 58 };
  const chartWidth = Math.max(820, model.dates.length * 18);
  const chartHeight = 300;
  const legendColumns = Math.min(4, Math.max(1, model.contributors.length));
  const legendRows = Math.ceil(model.contributors.length / legendColumns);
  const width = margin.left + chartWidth + margin.right;
  const height = margin.top + chartHeight + margin.bottom + legendRows * 26;
  const totals = model.dates.map((date) => model.daily[date].reduce((sum, count) => sum + count, 0));
  const maximum = Math.max(...totals, 1);
  const barStep = chartWidth / model.dates.length;
  const barWidth = Math.max(2, barStep * 0.72);
  const labelEvery = Math.max(1, Math.ceil(model.dates.length / 10));
  const yTicks = Math.min(4, maximum);
  const lines = [
    `<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 ${width} ${height}">`,
    '  <title>Commit activity by day</title>',
    `  <desc>${escapeXml(`${model.totalCommits} commits from ${model.dates[0]} through ${model.dates[model.dates.length - 1]}, grouped by contributor.`)}</desc>`,
    `  <rect width="${width}" height="${height}" rx="12" fill="#ffffff" stroke="#d0d7de"/>`,
    '  <style>text{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.axis{fill:#57606a;font-size:11px}.heading{fill:#1f2328;font-size:18px;font-weight:600}.subheading{fill:#57606a;font-size:12px}.grid{stroke:#d8dee4;stroke-width:1}.legend{fill:#1f2328;font-size:12px}</style>',
    `  <text class="heading" x="${margin.left}" y="30">Commits by day</text>`,
    `  <text class="subheading" x="${margin.left}" y="49">Full changelog history · ${escapeXml(model.timeZone)}</text>`,
  ];

  for (let tick = 0; tick <= yTicks; tick += 1) {
    const value = Math.round((maximum * tick) / yTicks);
    const y = margin.top + chartHeight - (value / maximum) * chartHeight;
    lines.push(`  <line class="grid" x1="${margin.left}" y1="${y.toFixed(2)}" x2="${margin.left + chartWidth}" y2="${y.toFixed(2)}"/>`);
    lines.push(`  <text class="axis" x="${margin.left - 9}" y="${(y + 4).toFixed(2)}" text-anchor="end">${value}</text>`);
  }

  model.dates.forEach((date, dateIndex) => {
    const x = margin.left + dateIndex * barStep + (barStep - barWidth) / 2;
    let y = margin.top + chartHeight;
    lines.push(`  <g data-date="${date}">`);
    model.daily[date].forEach((count, contributorIndex) => {
      if (count === 0) return;
      const segmentHeight = (count / maximum) * chartHeight;
      y -= segmentHeight;
      const contributor = model.contributors[contributorIndex];
      lines.push(`    <rect x="${x.toFixed(2)}" y="${y.toFixed(2)}" width="${barWidth.toFixed(2)}" height="${segmentHeight.toFixed(2)}" fill="${PALETTE[contributorIndex % PALETTE.length]}"><title>${escapeXml(`${date} · ${contributor.name}: ${count}`)}</title></rect>`);
    });
    lines.push('  </g>');

    if (dateIndex % labelEvery === 0 || dateIndex === model.dates.length - 1) {
      const labelX = x + barWidth / 2;
      const labelY = margin.top + chartHeight + 17;
      lines.push(`  <text class="axis" x="${labelX.toFixed(2)}" y="${labelY}" text-anchor="end" transform="rotate(-42 ${labelX.toFixed(2)} ${labelY})">${date}</text>`);
    }
  });

  const legendTop = margin.top + chartHeight + margin.bottom - 18;
  const legendColumnWidth = chartWidth / legendColumns;
  model.contributors.forEach((contributor, index) => {
    const column = index % legendColumns;
    const row = Math.floor(index / legendColumns);
    const x = margin.left + column * legendColumnWidth;
    const y = legendTop + row * 26;
    lines.push(`  <rect x="${x.toFixed(2)}" y="${y}" width="12" height="12" rx="2" fill="${PALETTE[index % PALETTE.length]}"/>`);
    lines.push(`  <text class="legend" x="${(x + 18).toFixed(2)}" y="${y + 11}">${escapeXml(contributor.name)} (${contributor.total})</text>`);
  });

  lines.push('</svg>');
  return lines.join('\n');
}

function heatColor(count, maximum) {
  if (count === 0) return '#ebedf0';
  const opacity = 0.25 + (count / maximum) * 0.75;
  return `rgba(37, 99, 235, ${opacity.toFixed(2)})`;
}

function renderHourlySvg(model) {
  if (model.totalCommits === 0) {
    return emptyChart('Commit activity by hour');
  }

  const margin = { top: 82, right: 28, bottom: 42, left: 180 };
  const cellWidth = 34;
  const rowHeight = 32;
  const chartWidth = cellWidth * 24;
  const width = margin.left + chartWidth + margin.right;
  const height = margin.top + model.contributors.length * rowHeight + margin.bottom;
  const maximum = Math.max(...model.hourly.flat(), 1);
  const lines = [
    `<svg xmlns="http://www.w3.org/2000/svg" role="img" viewBox="0 0 ${width} ${height}">`,
    '  <title>Commit activity by hour</title>',
    `  <desc>${escapeXml(`${model.totalCommits} commits grouped into 24 local-hour columns for each contributor.`)}</desc>`,
    `  <rect width="${width}" height="${height}" rx="12" fill="#ffffff" stroke="#d0d7de"/>`,
    '  <style>text{font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.axis{fill:#57606a;font-size:11px}.heading{fill:#1f2328;font-size:18px;font-weight:600}.subheading{fill:#57606a;font-size:12px}.name{fill:#1f2328;font-size:12px}</style>',
    `  <text class="heading" x="${margin.left}" y="30">Commits by hour</text>`,
    `  <text class="subheading" x="${margin.left}" y="49">Local time · ${escapeXml(model.timeZone)} · darker cells mean more commits</text>`,
  ];

  for (let hour = 0; hour < 24; hour += 1) {
    const x = margin.left + hour * cellWidth + cellWidth / 2;
    lines.push(`  <text class="axis" x="${x}" y="70" text-anchor="middle">${String(hour).padStart(2, '0')}</text>`);
  }

  model.contributors.forEach((contributor, contributorIndex) => {
    const y = margin.top + contributorIndex * rowHeight;
    lines.push(`  <text class="name" x="${margin.left - 10}" y="${y + 20}" text-anchor="end">${escapeXml(contributor.name)}</text>`);
    model.hourly[contributorIndex].forEach((count, hour) => {
      const x = margin.left + hour * cellWidth;
      lines.push(`  <rect data-hour="${hour}" x="${x + 2}" y="${y + 2}" width="${cellWidth - 4}" height="${rowHeight - 4}" rx="4" fill="${heatColor(count, maximum)}"><title>${escapeXml(`${contributor.name} · ${String(hour).padStart(2, '0')}:00: ${count}`)}</title></rect>`);
    });
  });

  lines.push('</svg>');
  return lines.join('\n');
}

function atomicWrite(filePath, content) {
  if (fs.existsSync(filePath) && fs.readFileSync(filePath, 'utf8') === content) {
    return false;
  }

  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const temporaryPath = `${filePath}.tmp-${process.pid}-${Date.now()}`;
  try {
    fs.writeFileSync(temporaryPath, content, 'utf8');
    fs.renameSync(temporaryPath, filePath);
  } catch (error) {
    fs.rmSync(temporaryPath, { force: true });
    throw error;
  }
  return true;
}

function generate({
  rootDir = path.resolve(__dirname, '..'),
  changelog,
  aliases,
  readme,
  daily,
  hourly,
  timeZone = DEFAULT_TIME_ZONE,
  write = true,
} = {}) {
  const entriesPath = path.resolve(rootDir, changelog || '.changelog/entries.jsonl');
  const aliasesPath = path.resolve(
    rootDir,
    aliases === undefined ? '.changelog/contributor-aliases.json' : aliases,
  );
  const readmePath = path.resolve(rootDir, readme || 'README.md');
  const dailyPath = path.resolve(rootDir, daily || 'docs/assets/commit-activity-by-day.svg');
  const hourlyPath = path.resolve(rootDir, hourly || 'docs/assets/commit-activity-by-hour.svg');
  const entries = parseEntries(fs.readFileSync(entriesPath, 'utf8'));
  if (aliases !== undefined && !fs.existsSync(aliasesPath)) {
    throw new Error(`Contributor aliases file not found: ${aliasesPath}`);
  }
  const aliasMap = fs.existsSync(aliasesPath)
    ? parseAliases(fs.readFileSync(aliasesPath, 'utf8'))
    : new Map();
  const model = aggregateEntries(entries, timeZone, aliasMap);
  const dailyContent = `${renderDailySvg(model)}\n`;
  const hourlyContent = `${renderHourlySvg(model)}\n`;
  const targets = [
    {
      filePath: readmePath,
      content: replaceGeneratedBlock(
        fs.readFileSync(readmePath, 'utf8'),
        renderDashboardMarkdown(model, {
          daily: contentDigest(dailyContent),
          hourly: contentDigest(hourlyContent),
        }),
      ),
    },
    {
      filePath: dailyPath,
      content: dailyContent,
    },
    {
      filePath: hourlyPath,
      content: hourlyContent,
    },
  ];
  const changed = [];

  for (const target of targets) {
    const relativePath = path.relative(rootDir, target.filePath).replace(/\\/g, '/');
    const differs = !fs.existsSync(target.filePath)
      || fs.readFileSync(target.filePath, 'utf8') !== target.content;
    if (!differs) continue;

    changed.push(relativePath);
    if (write) atomicWrite(target.filePath, target.content);
  }

  return { changed, model };
}

function parseOptions(argv) {
  const options = {
    rootDir: path.resolve(__dirname, '..'),
    timeZone: DEFAULT_TIME_ZONE,
    write: true,
  };
  const valueOptions = {
    '--input': 'changelog',
    '--aliases': 'aliases',
    '--readme': 'readme',
    '--daily': 'daily',
    '--hourly': 'hourly',
    '--timezone': 'timeZone',
    '--time-zone': 'timeZone',
    '--root': 'rootDir',
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--check') {
      options.write = false;
    } else if (argument === '--help' || argument === '-h') {
      options.help = true;
    } else if (valueOptions[argument]) {
      const value = argv[index + 1];
      if (!value || value.startsWith('--')) throw new Error(`Missing value for ${argument}`);
      index += 1;
      const property = valueOptions[argument];
      options[property] = property === 'rootDir' ? path.resolve(value) : value;
    } else {
      throw new Error(`Unknown option: ${argument}`);
    }
  }

  return options;
}

function runCli(argv) {
  const options = parseOptions(argv);
  if (options.help) {
    process.stdout.write([
      'Usage: node scripts/commit-activity.js [options]',
      '',
      '  --input PATH       Changelog JSONL input',
      '  --aliases PATH     Contributor alias registry',
      '  --readme PATH      README output',
      '  --daily PATH       Daily SVG output',
      '  --hourly PATH      Hourly SVG output',
      '  --timezone ZONE    IANA timezone (default: Asia/Saigon)',
      '  --check            Exit 1 when generated files are stale',
      '  --root DIR         Base directory for relative paths',
      '',
    ].join('\n'));
    return 0;
  }

  const result = generate(options);
  if (result.changed.length === 0) {
    process.stdout.write('[commit-activity] Dashboard is up to date.\n');
    return 0;
  }

  if (!options.write) {
    process.stderr.write(`[commit-activity] Dashboard is stale: ${result.changed.join(', ')}\n`);
    return 1;
  }

  process.stdout.write(`[commit-activity] Updated: ${result.changed.join(', ')}\n`);
  return 0;
}

module.exports = {
  aggregateEntries,
  contentDigest,
  generate,
  parseAliases,
  parseEntries,
  renderDashboardMarkdown,
  renderDailySvg,
  renderHourlySvg,
  replaceGeneratedBlock,
};

if (require.main === module) {
  try {
    process.exitCode = runCli(process.argv.slice(2));
  } catch (error) {
    process.stderr.write(`[commit-activity] ${error.message}\n`);
    process.exitCode = 1;
  }
}
