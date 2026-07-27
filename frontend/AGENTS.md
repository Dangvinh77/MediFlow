# AGENTS.md — frontend

> Nearest-file precedence: this governs `frontend/`. Root [`../AGENTS.md`](../AGENTS.md) still applies
> for project-wide context. **Authoritative rules: [`../docs/ai/12-frontend.md`](../docs/ai/12-frontend.md)** —
> this file is a pointer, not a rulebook.

## What this is

MediFlow web client — **Next.js 16 (App Router) + TypeScript + Tailwind v4**, pnpm. Reaches the
backend **only through the gateway** (`:8080`) via same-origin `/api/*`.

## Structure — feature-based, mirroring the backend

```
src/
├── app/          routing only; pages stay thin
├── features/     ONE FOLDER PER BOUNDED CONTEXT (patient, appointment, lab, …)
├── components/   shared, feature-agnostic UI
└── lib/          api.ts (the only fetch), auth.ts, shared envelope types
```

A feature never imports from another feature. Cross-feature code moves up to `components/` or `lib/`.

## Non-negotiables

- Gateway only via `/api/*`. Never call a service port (`:8081`…) directly.
- Every HTTP call goes through `src/lib/api.ts`. No raw `fetch` in components.
- Per-feature DTOs in `features/<ctx>/types.ts`, **Vietnamese camelCase** (`hoTen`, `maBenhNhan`), mirroring the backend exactly.
- **Tailwind utilities only** — no CSS-in-JS, no component library, no ad-hoc per-component CSS files.
- **No data-fetching library** (no TanStack Query, SWR). Fetch directly through `api.ts`.
- Role-based UI hiding is UX, not security — the backend enforces authorization.

## The backend rules do NOT apply here

`../docs/ai/04-microservice-blueprint.md` describes clean architecture for the **Java services**.
In this folder: no `domain/application/infrastructure`, no ports, no adapters, no JPA or
`BigDecimal` rules. The only thing that carries over is the wire contract — Vietnamese camelCase
fields and the shared `ApiResponse` envelope.

## Commands

```bash
pnpm install     # first time (Node 20+; `corepack enable pnpm` if pnpm is missing)
pnpm dev         # http://localhost:3000 — gateway must be up on :8080
pnpm typecheck
pnpm lint
```
