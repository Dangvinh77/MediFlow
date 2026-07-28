# 12 — Frontend Blueprint (MANDATORY)

> The frontend counterpart of `04`. Where `04` governs the Java services, this governs
> [`frontend/`](../../frontend/). The layout below is **not negotiable** — it is what lets eight
> people work in parallel without colliding.

## Stack

| | |
|---|---|
| Framework | **Next.js 16**, App Router |
| Language | **TypeScript 5** |
| Styling | **Tailwind CSS v4** — utilities only |
| Package manager | **pnpm** |
| Data fetching | **native `fetch`**, wrapped once in `src/lib/api.ts` |

### Three decisions the team made, and why they stay

1. **No component library** (no shadcn/ui, no MUI). Tailwind utilities only. Fewer dependencies to
   version-manage, and nobody has to learn a second API surface. Cost: you write your own table,
   modal, and form primitives — put them in `src/components/ui/` and reuse them.
2. **No data-fetching library** (no TanStack Query, no SWR). Call `api.ts` directly from a client
   component. Cost: you handle `loading` / `error` state by hand in each page. Accept it — if the
   app later needs caching or background refetching, that is a deliberate future change, not
   something to smuggle in per-page.
3. **Feature-based, not layer-based.** The folder tree mirrors the backend's bounded contexts.

Do not add a dependency that undoes one of these without changing this file first.

## The mandatory tree

```
frontend/
├── next.config.ts                  # rewrites /api/* → gateway (GATEWAY_URL)
└── src/
    ├── app/                        # ROUTING ONLY — pages stay thin
    │   ├── layout.tsx
    │   ├── globals.css             # Tailwind entry + design tokens ONLY
    │   ├── page.tsx
    │   ├── login/page.tsx
    │   └── (dashboard)/            # route group: everything behind auth
    │       ├── layout.tsx          # auth guard + app shell (nav, header)
    │       ├── patients/page.tsx
    │       ├── appointments/page.tsx
    │       ├── records/page.tsx
    │       ├── lab/page.tsx
    │       ├── pharmacy/page.tsx
    │       ├── billing/page.tsx
    │       ├── notifications/page.tsx
    │       └── reports/page.tsx
    │
    ├── features/                   # ONE FOLDER PER BOUNDED CONTEXT
    │   ├── patient/
    │   │   ├── api.ts              # only this context's endpoints
    │   │   ├── types.ts            # only this context's DTOs
    │   │   └── components/         # UI used only by this feature
    │   ├── organization/           # departments, staff, accounts
    │   ├── appointment/
    │   ├── medical-record/
    │   ├── lab/
    │   ├── pharmacy/
    │   ├── billing/
    │   ├── notification/
    │   └── report/
    │
    ├── components/                 # shared, feature-agnostic
    │   ├── ui/                     # Button, Input, Table, Modal, Badge, Spinner…
    │   └── layout/                 # Sidebar, Header, PageShell
    │
    └── lib/
        ├── api.ts                  # THE ONLY place that calls fetch
        ├── auth.ts                 # login/logout/getToken/getRole
        ├── types.ts                # shared envelope types ONLY
        └── roles.ts                # role constants mirroring common/Roles.java
```

### Naming: feature folder vs route folder

They differ on purpose, and each mirrors something real:

| | Mirrors | Form | Example |
|---|---|---|---|
| `features/<name>/` | the backend **bounded context** / module | singular | `features/patient/`, `features/medical-record/` |
| `app/<name>/` | the backend **URL resource** (`05`) | plural, matches the API path | `app/(dashboard)/patients/`, `app/(dashboard)/records/` |

If you can't tell which to use: a folder holding *code about* patients is `features/patient/`; a
folder that *produces a URL* is `app/(dashboard)/patients/`.

## The one rule that keeps this from rotting

**A feature never imports from another feature.**

```ts
// ❌ forbidden — couples two bounded contexts in the UI layer
import { PatientCard } from "@/features/patient/components/PatientCard";
// inside features/appointment/components/AppointmentRow.tsx
```

When two features need the same thing, it moves **up**, not sideways:

- shared UI → `src/components/ui/`
- shared helper or type → `src/lib/`

This mirrors the backend rule that services don't reach into each other's tables. Same discipline,
different layer — and it is why a feature can be deleted in one `rm -rf` without breaking the rest.

Composition happens in `app/` — a page may import from several features. That is the *only* place
allowed to.

## Layer responsibilities

| Layer | Does | Never does |
|-------|------|------------|
| `app/**/page.tsx` | route, read params, compose feature components, own page-level loading/error state | business logic, direct `fetch`, heavy markup |
| `features/<ctx>/api.ts` | typed calls for one context, built on `lib/api.ts` | call `fetch` directly, touch another context |
| `features/<ctx>/types.ts` | DTOs for one context | redefine `ApiResponse`/`Page` (those are shared) |
| `features/<ctx>/components/` | UI specific to that context | import another feature |
| `components/ui/` | dumb, reusable primitives | know about MediFlow domain concepts |
| `lib/api.ts` | the single `fetch` wrapper: base URL, JWT header, envelope unwrap, error mapping | contain any domain knowledge |

## The API contract

`lib/api.ts` is the **only** module in the codebase that calls `fetch`. It:

1. targets same-origin `/api/*` (Next rewrites to the gateway — no CORS, no hard-coded host),
2. attaches `Authorization: Bearer <token>`,
3. unwraps the standard envelope (`05`) and returns `data`,
4. throws `ApiRequestError` carrying `status` and the machine-readable `error.code`.

A feature's `api.ts` layers types on top:

```ts
// features/patient/api.ts
import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { PatientDTO, CreatePatientRequest } from "./types";

export const patientApi = {
  search: (keyword?: string, page = 0, size = 20) =>
    api.get<PageResult<PatientDTO>>("/api/v1/patients", { keyword, page, size }),

  getById: (id: string) => api.get<PatientDTO>(`/api/v1/patients/${id}`),

  create: (body: CreatePatientRequest) => api.post<PatientDTO>("/api/v1/patients", body),
};
```

Never call the gateway path from a component. Components call `patientApi.*`.

## Types mirror the backend, exactly

- **Shared** (`lib/types.ts`): `ApiResponse<T>`, `ApiError`, `PageResult<T>` — the envelope from `05`. Defined once.
- **Per feature** (`features/<ctx>/types.ts`): the DTOs of that context, in **Vietnamese camelCase**, field-for-field identical to the Java records — `hoTen`, `maBenhNhan`, `ngaySinh`.

When a backend DTO changes, the matching `features/<ctx>/types.ts` changes **in the same PR**. A
frontend type that has drifted from its DTO is a bug that TypeScript cannot catch for you.

## Server vs client components

App Router defaults to server components. Use them for read-only rendering. Reach for
`"use client"` only when the file needs state, effects, or event handlers — which, given we fetch
from the browser with a JWT in `localStorage`, is most interactive pages today.

Keep `"use client"` as **low in the tree as possible**: a client `PatientTable` inside a server page
beats marking the whole page client.

## Auth & roles

- `lib/auth.ts` owns `login` / `logout` / `getToken` / `getRole`, talking to the gateway's `/api/v1/auth/*`.
- `app/(dashboard)/layout.tsx` is the single auth guard — unauthenticated users get redirected once, from one place, not from every page.
- `lib/roles.ts` mirrors `backend/common/src/main/java/com/mediflow/common/security/Roles.java`. Keep them in sync.
- On `401`/`403` from `ApiRequestError`, redirect to `/login`.

> **Hiding a button is UX, not security.** Every rule in `07-security-rbac.md` is enforced by the
> backend. The frontend may hide what a role can't use, but must never be the thing that stops them.
>
> The JWT currently lives in `localStorage` — convenient for this starter, but readable by any XSS.
> For anything real, move to httpOnly cookies.

## Styling

- Tailwind utility classes in the markup. No CSS-in-JS, no per-component `.css` file.
- `globals.css` holds the Tailwind entry and design tokens (colors, spacing scale) **only**.
- Repeated utility clusters become a component in `components/ui/`, not a `@apply` soup.
- This is clinical software: prioritise **legibility and contrast** over decoration. Dense tables,
  readable numbers, unambiguous status colours. A lab value that is hard to read is a safety issue,
  not a style preference.

## Adding a feature — the checklist

1. `src/features/<context>/` with `api.ts`, `types.ts`, `components/`.
2. Types copied field-for-field from the service's DTO records (VN camelCase).
3. `api.ts` built on `lib/api.ts`, paths from `05-api-conventions.md`.
4. Route under `app/(dashboard)/<resource>/page.tsx`, thin — compose feature components.
5. Handle all three states explicitly: **loading**, **error** (`ApiRequestError.message`), **empty**.
6. Shared UI extracted to `components/ui/` rather than copy-pasted.
7. No import from another feature.
8. `pnpm typecheck && pnpm lint` clean.

## Commands

```bash
pnpm install     # first time. Node 20+; `corepack enable pnpm` if pnpm is missing
pnpm dev         # http://localhost:3000 — gateway must be running on :8080
pnpm build       # production build (runs tsc + eslint)
pnpm typecheck
pnpm lint
```

Full-stack order: `docker compose up -d` → `eureka-server` → `gateway` → the service(s) you need → `pnpm dev`.

## Current state (be honest about it)

The code in `frontend/src/` today is a **demo predating this blueprint**: three routes (`/`,
`/login`, `/patients`), all DTOs crammed into `lib/types.ts`, no `features/` directory. It does not
follow the tree above.

The folders exist (each with a `.gitkeep`) and this document is the target. Migrate the patient demo
into `features/patient/` as the first worked example, then build the rest against it.
