# MediFlow Frontend

Web client for MediFlow — **Next.js 16 (App Router) + TypeScript + Tailwind v4**, package manager **pnpm**.
Conventions and the full guide: [`docs/ai/12-frontend.md`](../docs/ai/12-frontend.md).

## Run

```bash
pnpm install      # first time
pnpm dev          # http://localhost:3000
```

The gateway must be running on `http://localhost:8080` (Next proxies `/api/*` to it — see `next.config.ts`).
Full stack start order: **eureka-server → gateway → patient-service → `pnpm dev`**.

Demo login: `admin / admin123` (stub auth on the gateway).

## Structure — feature-based

Mandatory layout, full rationale in [`docs/ai/12-frontend.md`](../docs/ai/12-frontend.md):

```
src/
├── app/                    # ROUTING ONLY — pages stay thin
│   ├── login/page.tsx
│   └── (dashboard)/        # route group: everything behind auth
│       ├── patients/  appointments/  records/  lab/
│       └── pharmacy/  billing/  notifications/  reports/
├── features/               # ONE FOLDER PER BOUNDED CONTEXT (mirrors the backend services)
│   └── <context>/
│       ├── api.ts          # this context's endpoints, built on lib/api.ts
│       ├── types.ts        # this context's DTOs (VN camelCase)
│       └── components/     # UI used only here
├── components/
│   ├── ui/                 # shared primitives: Button, Table, Modal…
│   └── layout/             # Sidebar, Header, PageShell
└── lib/
    ├── api.ts              # the ONLY place that calls fetch
    ├── auth.ts             # login/logout/token
    └── types.ts            # shared envelope types ONLY (ApiResponse, PageResult)
```

**The rule that keeps it clean:** a feature never imports from another feature. Shared code moves
*up* to `components/` or `lib/`, never sideways. Only `app/` may compose several features.

Team decisions, recorded so nobody re-litigates them: **Tailwind utilities only** (no component
library) and **direct `fetch` via `api.ts`** (no TanStack Query / SWR).

> ⚠️ **Migration pending.** The current code is a demo written before this layout: `app/patients/`
> sits outside the `(dashboard)` group and every DTO is crammed into `lib/types.ts`. Move it into
> `features/patient/` as the first worked example. Until then, do **not** add a `page.tsx` under
> `app/(dashboard)/patients/` — two pages resolving to `/patients` is a build error.

## Scripts

```bash
pnpm dev        # dev server
pnpm build      # production build (runs tsc + eslint)
pnpm typecheck  # tsc --noEmit
pnpm lint
```
