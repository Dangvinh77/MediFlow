# AGENTS.md — Lab route

Parent `frontend/src/AGENTS.md`, `frontend/src/app/AGENTS.md`,
`frontend/src/app/(dashboard)/AGENTS.md`, `docs/ai/12-frontend.md`, and
`docs/ai/15-frontend-ownership.md` remain mandatory.

- Canonical owner: Vinh (`Dangvinh77` / `Harori`).
- Writable route scope: `frontend/src/app/(dashboard)/lab/**`.
- Matching feature: `frontend/src/features/lab/**`.
- Service guide: `frontend/docs/services/lab.md`.

Keep this route thin: read params, compose authorized feature components, and own page-level
loading/error state. Do not add business logic, direct `fetch`, foreign-feature imports, or shared
shell changes.

The canonical owner may `IMPLEMENT` inside this route and its matching feature. Any other developer
needs an explicit human `DELEGATED` task naming this context or exact paths and bounded deliverable.
Cross-context composition requires authority for every participating context.
