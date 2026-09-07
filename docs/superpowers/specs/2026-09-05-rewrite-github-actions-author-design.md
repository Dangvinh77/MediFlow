# Rewrite GitHub Actions Commit Authorship

## Goal

Make every commit created by the commit-activity automation attributable to the repository owner instead of `github-actions[bot]`, and remove the bot from the default branch's contributor history.

The canonical automation identity is:

- Name: `Harori`
- Email: `100329525+Dangvinh77@users.noreply.github.com`

## Current state

The default branch contains three commits authored by:

```text
github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>
```

The workflow explicitly configures that bot name and email before creating dashboard commits. Merely changing the workflow would fix future commits but would not remove the existing bot-authored commits from the contributor history.

## Selected approach

Rewrite the default branch in an isolated clone, mapping only the exact GitHub Actions bot identity to the canonical Harori identity. Update the workflow in the rewritten history so future dashboard commits use the same Harori identity.

This approach is selected because it produces the requested final history while limiting the identity rewrite to a precise email match. A `.mailmap`-only solution is rejected because it leaves the original commit authors in Git history and does not guarantee that GitHub's Contributors view removes the bot.

## Safety and isolation

The rewrite must not run directly in the primary workspace.

1. Confirm the primary workspace and `origin/master` state.
2. Create a Git bundle backup outside the repository containing all refs needed for recovery.
3. Create a fresh isolated clone from the current repository.
4. Perform and verify the rewrite only in that clone.
5. Push with `--force-with-lease` using the exact previously observed remote `master` SHA.

If the remote branch advances after the initial fetch, the lease must fail and the rewrite must stop for reassessment. No unconditional force push is allowed.

## History rewrite

For every commit reachable from `master`:

- If the author email exactly equals the GitHub Actions bot email, replace the author name and email with the canonical Harori identity.
- If the committer email exactly equals the GitHub Actions bot email, replace the committer name and email with the canonical Harori identity.
- Preserve author dates, committer dates, commit messages, trees, parent relationships, and all other identities.

Rewriting a commit changes its SHA and therefore changes every descendant SHA. This is expected and must be reflected in the changelog data.

## Commit signatures

The rewrite changes the SHA of 16 previously signed commits, so their `gpgsig` headers and GitHub `Verified` status are lost. We will not re-sign commits authored by other people. The user explicitly accepted this signature loss before any remote push.

## Workflow change

In `.github/workflows/update-commit-activity.yml`, configure Git before the automation commit with:

```sh
git config user.name 'Harori'
git config user.email '100329525+Dangvinh77@users.noreply.github.com'
```

The workflow may continue to run under GitHub's automation token and may continue to open pull requests as `github-actions[bot]`. The requirement applies to Git commit authorship; the automation actor in the Actions audit trail and the pull-request creator are not disguised or removed.

Dashboard pull requests must be merged with **rebase merge**, not squash merge. GitHub credits the pull-request creator as the author of a squash commit, so a bot-created pull request would reintroduce the bot on `master` even when the branch commit is authored by Harori. Rebase merge carries the Harori-authored dashboard commit onto `master` while GitHub updates only its committer metadata and SHA.

## Changelog and dashboard reconciliation

Existing `.changelog/entries.jsonl` hashes after the first rewritten commit become stale. The final rewritten tree must therefore rebuild the changelog from the rewritten `master` history rather than append to the old JSONL file.

The reconciliation must:

1. Recreate entries from the rewritten Git history in chronological order.
2. Apply the existing dashboard-automation exclusion rules.
3. Produce unique full commit hashes with no stale pre-rewrite hashes.
4. Regenerate the README dashboard and both SVG charts from the rebuilt entries.
5. Keep contributor alias normalization intact so every owner identity renders as one `Harori` contributor.

## Verification

Before pushing, verify all of the following in the isolated clone:

- No commit reachable from rewritten `master` has the GitHub Actions bot author or committer email.
- The three former bot commits retain their messages, trees, dates, and relative positions but now use the Harori identity.
- Non-bot author and committer identities are unchanged.
- Every changelog hash is reachable from rewritten `master`, unique, and eligible under the existing exclusion rules.
- The changelog and dashboard test suites pass.
- Dashboard generation is idempotent and `--check` passes.
- README and each chart contain one canonical Harori contributor row or legend entry.
- The workflow contains no bot identity in its Git author configuration.
- The workflow requests rebase merge and contains no squash-merge command for dashboard pull requests.

After pushing, verify:

- Remote `master` equals the verified rewritten SHA.
- The workflow run succeeds and creates a dashboard pull request when an update is required.
- The automation commit in that pull request is authored by Harori.
- The dashboard pull request is rebase-merged, the resulting `master` commit is authored by Harori, and the follow-up workflow is successful.
- The primary workspace is synchronized to the rewritten remote without losing uncommitted user files.

## Collaboration impact

All commit SHAs from the first rewritten bot commit onward will change. Collaborators with existing clones must save any local work, fetch the rewritten branch, and either reset clean branches to the new `origin/master` or rebase active work onto it.

GitHub's Contributors graph is cached and may take approximately 24 hours to stop showing the bot after the rewritten default branch is pushed.

## Recovery

If verification or the force push fails, stop and retain both the isolated clone and the external Git bundle. The bundle is the recovery source for the pre-rewrite branch. Do not delete it until the rewritten remote, automation workflow, changelog, and primary workspace have all been verified.
