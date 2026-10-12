# AGENTS.md — dashboard routes

Parent [`frontend/src/AGENTS.md`](../../AGENTS.md),
[`frontend/src/app/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../../../docs/ai/15-frontend-ownership.md) remain mandatory.

Vinh (`Dangvinh77` / `Harori`) coordinates the dashboard layout, shared navigation, and shared route
infrastructure. Adding navigation or changing the dashboard shell is shared work; a non-Vinh
developer needs an explicit human `DELEGATED` task naming the exact paths and deliverable.

Each direct context route folder has its own nested owner rule. That rule changes ownership only for
its route subtree and does not authorize dashboard shell or sibling route changes.
