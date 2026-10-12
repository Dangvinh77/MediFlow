# AGENTS.md — shared libraries

Parent [`frontend/src/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md) remain mandatory.

Vinh (`Dangvinh77` / `Harori`) coordinates this shared scope. A non-Vinh developer needs an explicit
human `DELEGATED` task naming the exact library paths and bounded deliverable.

This directory contains the generic API wrapper, auth/session helpers, shared envelopes, roles,
formatting, and generic validation. It must not contain context endpoints, context DTOs, or
domain-specific presentation. `api.ts` is the only frontend module allowed to call `fetch`.
