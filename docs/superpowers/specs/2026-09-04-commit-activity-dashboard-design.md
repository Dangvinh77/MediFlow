# Commit Activity Dashboard Design

**Date:** 2026-09-04  
**Status:** Approved  
**Scope:** Repository-level changelog analytics displayed near the top of `README.md`

## Goal

Provide a self-updating contributor activity dashboard derived from `.changelog/entries.jsonl`. The dashboard must help the team see commit frequency across the repository's full history, grouped by contributor, calendar day, and hour of day.

## User-facing output

The generated section appears immediately below the main `README.md` title and is bounded by stable markers:

```md
<!-- commit-activity:start -->
<!-- generated content -->
<!-- commit-activity:end -->
```

The section contains:

1. The generation timestamp, timezone, and total number of unique commits.
2. A compact contributor summary table.
3. A daily commit activity SVG, with contributors distinguished by color.
4. A contributor-by-hour SVG heatmap covering hours `00` through `23`.
5. A note that the source is the repository changelog rather than GitHub Insights.

The contributor table contains these columns:

- Contributor display name.
- Total commits.
- Active days.
- Average commits per active day.
- Peak date and commit count on that date.
- Most active hour.
- Latest commit timestamp.

All historical entries are included; there is no rolling date cutoff.

## Identity and time rules

- Contributors are grouped internally by normalized email so a Git display-name change does not split one person into multiple rows.
- Email addresses are never rendered into the README or SVG files.
- The most recent non-empty author name for an email is the displayed name.
- Dates and hours are calculated in the `Asia/Saigon` timezone.
- Commits are deduplicated by full `hash` before aggregation.
- Rows are ordered by total commits descending, then display name ascending for deterministic ties.

## Architecture

### Generator

Create `scripts/commit-activity.js` as a dependency-free Node.js program. It owns four focused responsibilities:

1. Parse and validate JSONL records.
2. Normalize and aggregate commit activity.
3. Render deterministic Markdown and SVG strings.
4. replace only the marked section in `README.md` and write the generated SVG assets.

The program accepts explicit input/output arguments for testability and defaults to:

- Input: `.changelog/entries.jsonl`
- README: `README.md`
- Daily chart: `docs/assets/commit-activity-by-day.svg`
- Hour chart: `docs/assets/commit-activity-by-hour.svg`
- Timezone: `Asia/Saigon`

The calculation and rendering functions are exported so tests can exercise them without invoking a child process.

### Daily chart

The daily SVG uses stacked vertical bars, one bar per calendar date from the earliest to latest commit. Each contributor has a stable color derived from deterministic palette assignment after contributor sorting. Dates with no commits remain visible as zero-value gaps so frequency over time is not distorted.

The chart includes:

- Y-axis commit counts with integer tick marks.
- Sparse date labels selected to prevent overlap.
- A contributor legend.
- Accessible SVG title and description.
- Responsive sizing through `viewBox`, with a minimum internal width that expands for long histories.

### Hour heatmap

The hourly SVG has one row per contributor and 24 columns. Cell intensity represents the contributor's commit count in that hour. Each cell has a tooltip containing contributor, hour, and count. A zero-count cell uses the neutral background color.

### README updater

The updater replaces exactly one section between the marker pair. It fails when:

- Either marker is missing.
- A marker occurs more than once.
- The end marker occurs before the start marker.

This prevents accidental replacement of hand-written README content.

## Data validation and error handling

Generation fails with a non-zero exit code and a clear line number when a non-empty JSONL line:

- Is not valid JSON.
- Lacks a full commit hash.
- Lacks an author name or email.
- Has an invalid timestamp.

Unknown commit types and scopes are accepted because they are not required by this dashboard. Empty changelog input produces an empty-state table and valid empty SVGs rather than crashing.

All generated text is escaped for its output context:

- Markdown table cells escape pipes and normalize line breaks.
- SVG text and tooltip values escape XML characters.
- No author email is passed to a renderer.

Writes use temporary sibling files followed by rename so a failed generation does not leave partially written assets.

## GitHub Actions automation

Create `.github/workflows/update-commit-activity.yml` with:

- Triggers on pushes to `master` that change `.changelog/entries.jsonl`, `scripts/commit-activity.js`, or `scripts/commit-activity.test.js`.
- A `workflow_dispatch` trigger for manual regeneration.
- Node.js 20.
- Repository contents and pull-request write permissions.
- Concurrency control so only one dashboard update runs at a time.

The job executes:

1. Checkout `master` with full history.
2. Run `node --test scripts/commit-activity.test.js`.
3. Run `node scripts/commit-activity.js`.
4. Stop successfully when no generated file changed.
5. Reset or create the deterministic branch `automation/commit-activity-dashboard` from the triggering `master` commit.
6. Commit changed README/SVG files as `github-actions[bot]` and force-update only that automation branch using `--force-with-lease`.
7. Create a pull request into `master`, or update the existing open dashboard pull request when one already exists.
8. Enable squash auto-merge on the pull request. If required checks or reviews are pending, the pull request remains open until repository rules are satisfied.

The workflow uses the GitHub CLI already available on GitHub-hosted runners rather than a third-party action. The bot commit changes only generated README/SVG output. When its pull request is merged, the resulting `master` commit does not satisfy the workflow path filter and cannot trigger an update loop.

If repository settings do not permit auto-merge, the workflow still creates or updates the pull request and reports that a maintainer must merge it. The workflow never bypasses branch protection and does not require a Personal Access Token.

## Testing strategy

Create `scripts/commit-activity.test.js` using the built-in `node:test` and `node:assert/strict` modules. Tests use temporary directories and real files; no third-party dependencies are introduced.

Required test cases:

1. Parses valid JSONL and deduplicates repeated hashes.
2. Reports the line number for malformed JSON.
3. Rejects missing required identity, hash, or timestamp fields.
4. Groups changed display names by normalized email without exposing the email.
5. Converts timestamps to `Asia/Saigon` dates and hours correctly.
6. Calculates total commits, active days, average, peak date, peak hour, and latest timestamp.
7. Produces stable contributor ordering and color assignment.
8. Includes zero-commit dates between the first and last activity dates.
9. Escapes Markdown and XML-sensitive author names.
10. Renders valid empty-state Markdown and SVG output.
11. Replaces exactly one README marker block while preserving surrounding content.
12. Rejects missing, duplicate, or reversed README markers.
13. Produces identical output when run twice on unchanged input, except that the displayed generation timestamp is derived deterministically from the latest changelog timestamp rather than wall-clock time.

The real repository generation command is run after unit tests. Verification checks that a second generator run produces no diff.

## Files

- Create `scripts/commit-activity.js`.
- Create `scripts/commit-activity.test.js`.
- Create `.github/workflows/update-commit-activity.yml`.
- Create `docs/assets/commit-activity-by-day.svg`.
- Create `docs/assets/commit-activity-by-hour.svg`.
- Modify `README.md` to add the generated marker block immediately below its title.
- Modify `docs/ai/10-git-workflow.md` to document dashboard regeneration and automation.

## Non-goals

- Querying the GitHub API or GitHub Insights.
- Displaying contributor emails.
- Ranking code quality or using commit count as a performance score.
- Filtering by service, file path, commit type, or arbitrary date range.
- Adding a frontend dashboard or external charting dependency.
- Using a Personal Access Token or bypassing branch protection.

## Success criteria

- A fresh clone can run the generator with Node.js 20 and no package installation.
- README displays the contributor table and both SVG charts near the top.
- Statistics cover every unique valid entry in `.changelog/entries.jsonl`.
- Re-running the generator without changelog changes produces no Git diff.
- The GitHub Actions workflow regenerates changed dashboard output, creates or updates one automation pull request, and enables auto-merge when repository settings permit it.
- All unit tests pass on Windows and GitHub's Linux runner.
