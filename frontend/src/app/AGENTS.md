# AGENTS.md — frontend app routes

Parent [`frontend/src/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md) remain mandatory.

Vinh (`Dangvinh77` / `Harori`) coordinates the root app shell, `layout.tsx`, `loading.tsx`,
`globals.css`, `page.tsx`, login, auth-facing routing, and other app-wide infrastructure. A
non-Vinh developer needs an explicit human `DELEGATED` task naming the exact shared paths and
bounded deliverable.

Direct context route roots have more specific nested rules. Route pages stay thin: read params,
compose authorized feature components, and own page-level loading/error state. They never contain
feature business logic or call `fetch` directly.
