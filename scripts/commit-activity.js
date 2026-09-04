#!/usr/bin/env node

const HASH_PATTERN = /^[0-9a-f]{40}$/i;
const DEFAULT_TIME_ZONE = 'Asia/Saigon';
const START_MARKER = '<!-- commit-activity:start -->';
const END_MARKER = '<!-- commit-activity:end -->';

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

    if (!HASH_PATTERN.test(value.hash || '')) {
      throw new Error(`Invalid hash at line ${lineNumber}`);
    }
    if (!String(value.author || '').trim()) {
      throw new Error(`Invalid author at line ${lineNumber}`);
    }
    if (!String(value.email || '').trim()) {
      throw new Error(`Invalid email at line ${lineNumber}`);
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
  return String(value).replace(/\r?\n/g, ' ').replace(/\|/g, '\\|');
}

function renderDashboardMarkdown(model) {
  if (model.totalCommits === 0) {
    return [
      '## Commit activity',
      '',
      '_No commits recorded._',
      '',
      'Data source: `.changelog/entries.jsonl`.',
    ].join('\n');
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

module.exports = {
  aggregateEntries,
  parseEntries,
  renderDashboardMarkdown,
  replaceGeneratedBlock,
};
