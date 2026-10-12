# 15 — Frontend Ownership and Work Routing

> Canonical frontend ownership rule for MediFlow. This document owns the developer-to-context
> matrix and the shared-scope rules; the other frontend entry points link here rather than copying
> this matrix.

## Read order

For frontend work, read the repository root [`AGENTS.md`](../../AGENTS.md), this file, the frontend
blueprint [`12-frontend.md`](12-frontend.md), and the current-state workboard
[`frontend/docs/frontend-workboard.md`](../../frontend/docs/frontend-workboard.md). Then verify the
live owner backend DTO/controller contract before changing a feature.

## Ownership matrix

Ownership follows bounded contexts. A route owner may implement the route composition and the
matching feature folder, subject to the shared-scope and contract rules below.

| Owner | Frontend contexts | Route paths | Feature paths |
|---|---|---|---|
| Vinh (`Dangvinh77` / `Harori`) | appointment, medical-record, lab, inpatient | `/appointments`, `/records`, `/lab`, `/inpatient` | `features/appointment`, `features/medical-record`, `features/lab`, `features/inpatient` |
| Huy (`LQHuy0210`) | pharmacy, report, planned surgery | `/pharmacy`, `/reports`, planned `/surgery` | `features/pharmacy`, `features/report`, planned `features/surgery` |
| Hoàng Anh (`TranHoangAnh94`) | organization, patient; Gateway-facing identity liaison | `/organization`, `/patients` | `features/organization`, `features/patient`; identity integration in shared files below |
| Lộc (`locgit-89`) | billing, notification | `/billing`, `/notifications` | `features/billing`, `features/notification` |

Hoàng Anh owns the Gateway identity contract liaison lane. The login and session implementation is
shared integration scope: `src/app/login/**`, `src/lib/auth.ts`, `src/lib/session.ts`, and
`src/components/auth/**` are not exclusive to that owner. Changes there require an explicit shared
task scope and must preserve all route consumers.

## Shared integration scope

Vinh (`Dangvinh77` / `Harori`) coordinates frontend shared scope:

- root app shell, dashboard shell, global loading, global styles, login, auth, and session paths;
- `src/components/**`, `src/lib/**`, and `src/test/**`;
- package manifests, lockfiles, Next/Tailwind/TypeScript/ESLint/Vitest configuration, and other
  frontend-wide configuration.

This coordination grants Vinh default write authority for shared frontend paths and Vinh-owned
contexts only. It does not grant default authority over another developer's feature or route.
Another developer may edit an exact shared path only when a human task explicitly delegates that
path and bounded deliverable.

A feature may read another feature to verify a public contract or composition point, but it must
not import from another feature. Move reusable code up to shared components or `lib/` only through
Vinh's coordination or an explicit shared-scope delegation; route composition belongs in `app/`.

## Work classifications

- **IMPLEMENT:** the acting developer owns every target, or Vinh is changing shared scope.
- **DELEGATED:** the human task names the canonical owner, delegated developer, exact context or
  shared paths, and bounded deliverable. Authority expires with the task or PR.
- **VERIFY-CONTRACT:** the live controller, DTO, roles, errors, feature flag, or Gateway route must
  be confirmed read-only before editing.
- **HANDOFF:** another owner must supply a concrete action, contract, or decision.
- **BLOCKED:** safe work cannot continue without missing authority or without guessing a contract.

Git identity, branch name, prior edits, and subagent assignment are not authorization. Delegation
does not change canonical ownership or authorize neighboring paths. A subagent inherits only the
paths authorized by the human task and cannot widen them. A committed public contract may be
consumed read-only from another context, but foreign source may not be edited or imported from
another feature.

Frontend ownership, composition, or shared-scope dependencies use the active registry under
[`frontend/docs/handoffs/`](../../frontend/docs/handoffs/). Missing backend endpoints, DTOs, roles,
events, identity claims, or Gateway contracts use [`docs/handoffs/`](../handoffs/README.md). Do not
duplicate one blocker in both registries.

Frontend DTOs mirror the live owner backend wire contract field-for-field. Do not infer endpoint
paths, response fields, roles, identifiers, or identity claims from a design sketch or from another
feature. All HTTP goes through `src/lib/api.ts` and the gateway same-origin `/api/*` boundary.

## Quota-aware model routing

Use the smallest model that can safely complete the bounded task and leave deterministic evidence:

- **`gpt-5.6-sol`, high:** exactly one audit/planning pass per epic; use it to settle scope, ownership,
  contract risks, and the task sequence.
- **`gpt-5.6-luna`, medium/high:** default implementation model for small bounded tasks and focused commits;
  use maximum effort only for a hard blocker.
- Run deterministic checks before review, such as path/contract checks, `pnpm typecheck`, and
  `pnpm lint` when the changed scope warrants them.
- **`gpt-6-astra`, low:** one final review after the deterministic checks are green; escalate only for
  high-risk contract, auth, or shared-scope changes.
- Reuse this workboard and codebase-memory queries for orientation. Do not reread the whole
  repository for every task.

The current route and next-task state is maintained in
[`frontend/docs/frontend-workboard.md`](../../frontend/docs/frontend-workboard.md). Detailed
effective scope, queues, and handoffs for each context are indexed in
[`frontend/docs/services/`](../../frontend/docs/services/README.md).
