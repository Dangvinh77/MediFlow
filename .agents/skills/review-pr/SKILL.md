---
name: review-pr
description: "Review the current branch diff against docs/ai standards and the design docs."
---

Review the pending changes on the current branch.

1. Resolve the default branch from `origin/HEAD` (fall back to `master`, then `main`), then run `git diff <base>...HEAD`; use `git diff` for uncommitted work.
2. Dispatch the `code_reviewer` Codex agent with that diff, or apply its checklist yourself: blueprint layout, service boundaries, VN/EN naming, API conventions, security roles, event/saga correctness, types, tests, migrations (see `.codex/agents/code-reviewer.toml`).
3. For every finding, cite `file:line`, the rule in `docs/ai/*` it violates, and the fix. Group by Blocker / Should-fix / Nit.
4. Confirm each business rule in the touched service's `docs/ai/services/*.md` is implemented and tested.
5. End with a clear go / no-go for merge.

Do not modify code during review.
