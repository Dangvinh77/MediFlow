# Contributor Aliases Design

## Goal

Merge commit activity recorded under multiple email addresses into one contributor without
merging unrelated people who happen to share the same display name. The current two `Harori`
rows become one row in the README table and one series/heatmap row in the SVG charts.

## Configuration

Add `.changelog/contributor-aliases.json` with a stable contributor ID, canonical display name,
and all known email aliases:

```json
{
  "contributors": [
    {
      "id": "harori",
      "name": "Harori",
      "emails": [
        "phamdangvinh2002@gmail.com",
        "100329525+Dangvinh77@users.noreply.github.com"
      ]
    }
  ]
}
```

Email matching is case-insensitive and ignores surrounding whitespace. Contributor IDs and
configured names must be non-empty. IDs must be unique, every alias list must be non-empty, and
one normalized email may belong to only one configured contributor. Invalid configuration fails
generation with a clear error.

The aliases file is optional. When it is absent, the generator retains the current normalized
email grouping behavior.

## Aggregation

The generator builds an internal email-to-contributor lookup before aggregation:

- A listed email uses the configured stable ID and canonical display name.
- An unlisted email uses its normalized email as the internal identity and the latest author name
  from the changelog, preserving existing behavior.
- Commits, active dates, peak date/hour, latest timestamp, daily series, and hourly cells are
  combined after identity resolution.
- Emails remain internal and are never passed to Markdown or SVG renderers.

Two unconfigured contributors with the same author name remain separate. This prevents accidental
merges based only on display text.

## Generator and automation

`scripts/commit-activity.js` loads `.changelog/contributor-aliases.json` by default and accepts an
optional `--aliases PATH` CLI argument. The file-level `generate` API accepts the corresponding
`aliases` path. GitHub Actions watches the alias file in addition to the changelog and generator,
so alias changes regenerate the README and both SVG assets through the existing protected-branch
pull-request workflow.

The Git workflow documentation explains how to add or change aliases.

## Testing

Tests cover:

1. Two normalized emails configured for one ID produce one contributor with combined totals.
2. The configured canonical name is used regardless of changelog author-name history.
3. Unconfigured identical display names remain separate contributors.
4. Duplicate IDs, duplicate email ownership, empty fields, and invalid JSON are rejected.
5. The CLI and file-level generator accept an explicit alias path.
6. Generated README/SVG output contains one `Harori` contributor and no email addresses.
7. Repeated generation remains byte-identical.

## Files changed

- Add `.changelog/contributor-aliases.json`.
- Modify `scripts/commit-activity.js` and `scripts/commit-activity.test.js`.
- Modify `.github/workflows/update-commit-activity.yml`.
- Modify `docs/ai/10-git-workflow.md`.
- Regenerate `README.md` and both `docs/assets/commit-activity-*.svg` files.
