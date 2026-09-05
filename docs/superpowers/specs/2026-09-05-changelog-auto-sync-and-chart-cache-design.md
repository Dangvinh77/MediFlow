# Changelog Auto-Sync and Chart Cache Design

**Date:** 2026-09-05
**Status:** Approved
**Scope:** Keep `.changelog/entries.jsonl`, the README activity summary, and both SVG charts synchronized with `master`

## Problem

The dashboard currently treats `.changelog/entries.jsonl` as its only data source. Local Git hooks
append entries after local commits, but hooks do not run for merge commits created on GitHub. A
successful GitHub Actions run therefore cannot display a newer date when the committed changelog
does not contain the newer commit.

The alias registry already combines both Harori email identities in the generated SVG source. The
two-name chart still visible to users is a stale GitHub image-cache response because the SVG path
does not change when its content changes.

## Goals

- Reconcile every eligible commit reachable from `master` into the committed JSONL changelog.
- Include ordinary commits and merge commits authored by team members.
- Exclude bot commits and commits created solely by dashboard automation.
- Regenerate README statistics and both SVG charts from the reconciled changelog.
- Make a changed SVG use a changed image URL so GitHub cannot serve the previous chart.
- Preserve the protected-branch workflow and avoid self-triggering update loops.

## Approach

Extend the existing dependency-free changelog script with an incremental `--sync` command. The
workflow will run this command before the dashboard generator on every push to `master`. This keeps
the JSONL file as the repository source of truth while filling gaps that local hooks cannot see.

This is preferred over rebuilding the whole JSONL file because it preserves existing records and
minimizes merge conflicts. It is preferred over GitHub Insights or API-based aggregation because it
requires no extra token scope, network data model, or second source of truth.

## Commit reconciliation

`node scripts/changelog.js --sync` will:

1. Parse the existing JSONL file and collect full commit hashes.
2. Walk all commits reachable from the checked-out `HEAD` in stable oldest-to-newest order.
3. Build an entry for every missing eligible hash using the existing `buildEntry` logic.
4. Append all new records in one atomic file replacement.
5. Print the scanned, added, and excluded counts and exit successfully without touching the file
   when there is nothing to add.

Repeated execution against the same Git history must be byte-identical. Existing JSONL records are
never rewritten, reordered, or regenerated. Invalid existing JSONL is a hard error with a line
number; silently skipping malformed records would make deduplication unreliable.

### Eligibility rules

Human-authored ordinary and merge commits are included. A commit is excluded when either condition
is true:

- Its author name ends in `[bot]` or its normalized email identifies a GitHub bot account.
- Its subject is the dashboard automation subject
  `docs(tooling): update commit activity dashboard`, optionally followed by a squash-merge PR
  number, or it is the default merge message for the
  `automation/commit-activity-dashboard` branch.

These rules keep member merge activity while preventing the dashboard's own refresh commits from
increasing member totals or creating an endless update chain. Exclusion is based on immutable Git
metadata and does not depend on contributor aliases.

The existing `--init` and `--update` commands retain their current behavior for compatibility.
Only `--sync` applies the reconciliation eligibility policy.

## Dashboard generation and cache busting

Contributor identity resolution remains email-based with
`.changelog/contributor-aliases.json` providing canonical identities. README, daily SVG, and hourly
SVG all consume the same aggregated model, so one canonical `Harori` produces one table row, one
daily legend entry, and one hourly heatmap row.

The generator will render both SVG strings first and compute a short SHA-256 content digest for each
asset. The README image links become:

```text
docs/assets/commit-activity-by-day.svg?v=<daily-content-digest>
docs/assets/commit-activity-by-hour.svg?v=<hourly-content-digest>
```

The files keep their stable names, but any content change produces a new URL and invalidates the
GitHub image cache. Digest values are deterministic, contain 12 lowercase hexadecimal characters,
and do not depend on wall-clock time.

The README timestamp continues to come from the latest eligible changelog entry in
`Asia/Saigon`. Its label will explicitly say `Changelog updated through` so readers do not confuse
the value with the workflow execution time.

## GitHub Actions flow

The workflow trigger changes from selected paths to every push on `master`, plus the existing manual
dispatch. Its job will:

1. Check out the triggering `master` commit with full history.
2. Run changelog and dashboard tests.
3. Run `node scripts/changelog.js --sync`.
4. Run `node scripts/commit-activity.js`.
5. Stop successfully if the changelog, README, and SVG files are unchanged.
6. Create or reset `automation/commit-activity-dashboard` from the triggering commit.
7. Commit `.changelog/entries.jsonl`, README, and both SVGs as `github-actions[bot]`.
8. Create or update the single automation PR and request squash auto-merge.

When that PR reaches `master`, the workflow runs once more. The bot/dashboard commit is excluded by
`--sync`; the already-generated outputs remain unchanged, so the second run exits without creating
another commit or PR.

## Error handling

- Missing Git history or an unreadable repository causes `--sync` to fail loudly.
- Malformed JSONL reports the exact line and leaves the original file unchanged.
- Failure to inspect any missing commit aborts the sync instead of creating a partial changelog.
- The workflow does not push when tests, sync, or generation fail.
- Existing branch-protection and PR permission behavior remains unchanged.

## Testing strategy

Tests use temporary real Git repositories and Node's built-in test runner. Required cases are:

1. `--sync` adds missing ordinary and merge commits in stable order.
2. A second sync makes no file change and adds no duplicate hashes.
3. Existing malformed JSONL fails with a line number and remains unchanged.
4. GitHub bot and dashboard automation commits are excluded.
5. A human merge commit is retained.
6. README and both SVGs expose exactly one canonical Harori label/series.
7. Changing aliases or chart data changes the matching cache-busting digest even when the latest
   commit timestamp is unchanged.
8. Unchanged data produces byte-identical README and SVG output.
9. Workflow tests require an unrestricted `master` push trigger, run sync before generation, stage
   the changelog with generated assets, and retain the no-change exit.

The regression test for each bug must be observed failing before production code is changed, then
passing afterward.

## Files expected to change

- `scripts/changelog.js`
- A changelog synchronization test file under `scripts/`
- `scripts/commit-activity.js`
- `scripts/commit-activity.test.js`
- `.github/workflows/update-commit-activity.yml`
- `README.md`
- `docs/assets/commit-activity-by-day.svg`
- `docs/assets/commit-activity-by-hour.svg`
- `docs/ai/10-git-workflow.md`

## Non-goals

- Replacing JSONL with GitHub Insights or a database.
- Counting code quality, lines changed, or individual performance.
- Removing historical entries already committed to the changelog.
- Changing the existing contributor alias format.
- Bypassing branch protection or introducing a Personal Access Token.

## Success criteria

- A push or GitHub-created merge on `master` is represented after the automation PR is merged.
- Dashboard automation commits never appear in contributor totals.
- README's date matches the latest eligible entry in the committed changelog.
- README, daily SVG, and hourly SVG each represent Harori as one contributor.
- A changed chart receives a new image URL and a repeated unchanged generation is byte-identical.
- The automation converges after its update PR merge without opening another update PR.
