# AGENTS.md — frontend source

Parent [`frontend/AGENTS.md`](../AGENTS.md),
[`docs/ai/12-frontend.md`](../../docs/ai/12-frontend.md), and
[`docs/ai/15-frontend-ownership.md`](../../docs/ai/15-frontend-ownership.md) remain mandatory.
Nearest-file precedence narrows ownership; it never removes architecture, contract, delegation,
handoff, or verification rules.

## Before any edit

1. List every intended path and classify it as context-owned or shared.
2. Read the nearest nested `AGENTS.md` for every intended path.
3. Identify the acting developer from an explicit human task, issue, workboard assignment, or
   approved plan.
4. Never infer authority from Git identity, a branch name, prior edits, or subagent assignment.
5. Verify the matching service guide and live backend controller, DTO, security, tests, and Gateway
   contract.
6. Classify the work as `IMPLEMENT`, `DELEGATED`, `VERIFY-CONTRACT`, `HANDOFF`, or `BLOCKED`.
7. Before completion, compare every changed file with the authorized paths.

If the actor or scope is ambiguous, stop and ask. Do not infer permission.

## Boundaries

- A feature folder and its matching route root form one context ownership unit.
- A feature never imports another feature.
- Cross-context composition belongs in `app/` and requires authority for every participating scope.
- Feature APIs use `src/lib/api.ts`; only `src/lib/api.ts` calls `fetch`.
- DTOs mirror live backend wire contracts exactly.
- Do not perform opportunistic refactors outside authorized paths.
- Vinh coordinates shared frontend scope; another developer needs an explicit human `DELEGATED`
  task naming exact shared paths and a bounded deliverable.

Frontend ownership, composition, and shared-scope dependencies use
[`frontend/docs/handoffs/`](../docs/handoffs/). Missing backend contracts use
[`docs/handoffs/`](../../docs/handoffs/README.md). Do not duplicate a blocker.
