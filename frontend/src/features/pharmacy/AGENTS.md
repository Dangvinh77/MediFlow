# AGENTS.md — Pharmacy frontend feature

Parent `frontend/src/AGENTS.md`, `frontend/src/features/AGENTS.md`,
`docs/ai/12-frontend.md`, and `docs/ai/15-frontend-ownership.md` remain mandatory.

- Canonical owner: Huy (`LQHuy0210`).
- Writable feature scope: `frontend/src/features/pharmacy/**`.
- Matching route scope: `frontend/src/app/(dashboard)/pharmacy/**`.
- Service guide: `frontend/docs/services/pharmacy.md`.
- Backend contract authority: `backend/pharmacy-service/` controllers, DTOs, security, and tests.

The canonical owner may `IMPLEMENT` inside this feature and its matching route. Any other developer
needs an explicit human `DELEGATED` task naming this context or exact paths and bounded deliverable.
Read foreign contexts only to verify public contracts. Never edit them, import another feature,
infer wire fields, or widen delegation.

Use `frontend/docs/handoffs/` for a real frontend dependency and `docs/handoffs/` for a missing
backend contract. Verify changed scope, then run `pnpm typecheck` and `pnpm lint` when TypeScript
changed.
