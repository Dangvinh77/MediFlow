---
description: Review the current branch diff against docs/ai standards and the design docs.
---

Review the pending changes on the current branch.

1. Run `git diff main...HEAD` (or `git diff` for uncommitted work) to get the changeset.
2. Dispatch the `code-reviewer` agent with that diff, or apply its checklist yourself: blueprint layout, service boundaries, VN/EN naming, API conventions, security roles, event/saga correctness, types, tests, migrations (see `.claude/agents/code-reviewer.md`).
3. For every finding, cite `file:line`, the rule in `docs/ai/*` it violates, and the fix. Group by Blocker / Should-fix / Nit.
4. Confirm each business rule in the touched service's `docs/ai/services/*.md` is implemented and tested.
5. End with a clear go / no-go for merge.

Do not modify code during review.
