# AGENTS.md — frontend features

Parent [`frontend/src/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md) remain mandatory.
Each context folder has a nested owner rule that must be read before editing.

- One folder represents one backend bounded context.
- Context code owns only its API facade, wire types, presentation helpers, components, and local
  tests.
- Feature API modules use `src/lib/api.ts`; components never call `fetch` directly.
- Wire types mirror the live owner controller and DTO records field-for-field.
- A feature never imports from a sibling feature.
- Keep context-specific code local. Moving it to `src/components/**` or `src/lib/**` requires Vinh's
  coordination or an explicit human `DELEGATED` shared-scope task.
- Foreign contexts are read-only unless a human task delegates the exact context or paths.
