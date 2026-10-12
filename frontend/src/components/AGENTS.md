# AGENTS.md — shared components

Parent [`frontend/src/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../../docs/ai/15-frontend-ownership.md) remain mandatory.

Vinh (`Dangvinh77` / `Harori`) coordinates this shared scope. A non-Vinh developer needs an explicit
human `DELEGATED` task naming the exact component paths and bounded deliverable.

This directory contains feature-agnostic UI primitives, layout, theme, and auth components. It must
not contain context DTOs, context API calls, or feature business rules. Keep domain-specific UI in
its owning feature.
