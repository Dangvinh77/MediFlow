---
paths:
  - "frontend/**/*.{ts,tsx,css}"
  - "frontend/*.{ts,mjs,json}"
---

# Frontend rules (loaded only when touching `frontend/`)

Authoritative text: [`docs/ai/12-frontend.md`](../../docs/ai/12-frontend.md). Summary below.

**Stack:** Next.js 16 App Router · TypeScript · Tailwind v4 · pnpm. No component library, no
data-fetching library — those were deliberate team decisions, not omissions.

**Structure is feature-based:**
```
src/app/          routing only; pages thin
src/features/     one folder per bounded context, mirroring the backend services
src/components/   shared, feature-agnostic UI
src/lib/          api.ts (the only fetch wrapper), auth.ts, shared envelope types
```
A feature never imports another feature. Shared code moves up to `components/` or `lib/`.

**Non-negotiables:**
- Gateway only, via same-origin `/api/*`. Never a service port (`:8081`…).
- All HTTP through `src/lib/api.ts`. No raw `fetch` in components.
- Per-feature DTOs live in `features/<ctx>/types.ts`, Vietnamese camelCase, mirroring the backend.
- Tailwind utilities only — no CSS-in-JS, no per-component stylesheet.
- Role-based UI hiding is UX; the backend enforces authorization.

**Do not apply the Java rules here.** No `domain/application/infrastructure`, no ports or adapters
in TypeScript. Only the wire contract crosses: Vietnamese camelCase fields and the `ApiResponse`
envelope.
