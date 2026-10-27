# AGENTS.md — Notifications route

Parent `frontend/src/AGENTS.md`, `frontend/src/app/AGENTS.md`,
`frontend/src/app/(dashboard)/AGENTS.md`, `docs/ai/12-frontend.md`, and
`docs/ai/15-frontend-ownership.md` remain mandatory.

- Canonical owner: Vinh (`Dangvinh77` / `Harori`), taking over Lộc's frontend scope on 2026-10-27.
- Writable route scope: `frontend/src/app/(dashboard)/notifications/**`.
- Matching feature: `frontend/src/features/notification/**`.
- Service guide: `frontend/docs/services/notification.md`.

Keep this route thin: read params, compose authorized feature components, and own page-level
loading/error state. Do not add business logic, direct `fetch`, foreign-feature imports, or shared
shell changes.

The canonical owner may `IMPLEMENT` inside this route and its matching feature. Any other developer
needs an explicit human `DELEGATED` task naming this context or exact paths and bounded deliverable.
Cross-context composition requires authority for every participating context.
