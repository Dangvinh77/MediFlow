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
| Vinh (`Dangvinh77` / `Harori`) | appointment, medical-record, lab | `/appointments`, `/records`, `/lab` | `features/appointment`, `features/medical-record`, `features/lab` |
| Huy (`LQHuy0210`) | pharmacy, report | `/pharmacy`, `/reports` | `features/pharmacy`, `features/report` |
| Hoàng Anh (`TranHoangAnh94`) | organization, patient; Gateway-facing identity liaison | `/organization`, `/patients` | `features/organization`, `features/patient`; identity integration in shared files below |
| Lộc (`locgit-89`) | billing, notification | `/billing`, `/notifications` | `features/billing`, `features/notification` |

Hoàng Anh owns the Gateway identity contract liaison lane. The login and session implementation is
shared integration scope: `src/app/login/**`, `src/lib/auth.ts`, `src/lib/session.ts`, and
`src/components/auth/**` are not exclusive to that owner. Changes there require an explicit shared
task scope and must preserve all route consumers.

## Shared integration scope

The following paths are shared and may be changed only when the task explicitly assigns the shared
work:

- root app shell and layout: `src/app/layout.tsx`, `src/app/loading.tsx`, `src/app/globals.css`,
  `src/app/page.tsx`, and dashboard layout/shared route infrastructure;
- login/session integration: `src/app/login/**`, `src/lib/auth.ts`, `src/lib/session.ts`, and
  `src/components/auth/**`;
- shared UI and layout components under `src/components/**`;
- generic libraries under `src/lib/**`, including `api.ts`, `types.ts`, `format.ts`, and
  `validation.ts` (feature-specific code remains with its context);
- package manifests, lockfiles, Next/Tailwind/TypeScript configuration, and other frontend config.

Subagents inherit the same boundary. Delegation cannot grant a foreign feature or shared path write
authority. A feature may read another feature to understand a contract or composition point, but it
must not import from another feature. Move reusable code up to shared components or `lib/`; route
composition belongs in `app/`.

## IMPLEMENT versus HANDOFF

- **IMPLEMENT** means the task is inside the assigned context and its live backend contract is
  available. Keep changes bounded to that feature and route.
- **HANDOFF** means the task needs another owner's backend endpoint, DTO, event, identity claim, or
  contract decision. Record the producer, consumer, exact missing contract, acceptance criteria, and
  compatibility expectation under `docs/`; do not edit the producer or invent a temporary field.
- **VERIFY-CONTRACT** means the route or feature exists, but the next change must first be checked
  against the current owner backend controller/DTO/tests.
- **BLOCKED** means implementation cannot proceed until the named contract or decision arrives.

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
[`frontend/docs/frontend-workboard.md`](../../frontend/docs/frontend-workboard.md).
