# Frontend Source Ownership Rules Design

**Date:** 2026-10-12

**Status:** Approved design

**Scope:** `frontend/`, especially `frontend/src/`

## Goal

Make frontend ownership visible at the point where an AI agent edits code. An agent entering any
feature, route, or shared source directory must be able to determine the canonical owner, writable
scope, contract source, delegation requirements, and handoff route without reorganizing the Next.js
application around developer names.

The rules are documentation-enforced. They guide agents and reviewers but do not add a fixed CI path
gate, because a developer may be explicitly delegated a bounded task from another owner.

## Non-goals

- Do not create developer-named source folders such as `features/vinh/` or `features/huy/`.
- Do not change existing imports, routes, components, or runtime behavior.
- Do not add CODEOWNERS or a script that rejects foreign paths automatically.
- Do not turn temporary delegation into permanent ownership.
- Do not use handoffs for ordinary work that can be completed inside one authorized scope.

## Architectural principle

The source tree remains organized by bounded context and Next.js responsibility:

```text
frontend/src/
├── app/                    # routing and composition
├── features/<context>/     # one backend bounded context per folder
├── components/             # shared feature-agnostic UI
├── lib/                    # shared generic infrastructure
└── test/                   # shared test harness
```

Ownership is metadata expressed through nested `AGENTS.md` files. It never appears in folder names,
component names, types, APIs, or source-code comments.

## Ownership matrix

| Canonical owner | Context-owned feature folders | Context-owned route roots |
|---|---|---|
| Vinh (`Dangvinh77` / `Harori`) | `appointment`, `medical-record`, `lab`, `inpatient` | `appointments`, `records`, `lab`, `inpatient` |
| Huy (`LQHuy0210`) | `pharmacy`, `report`; future `surgery` | `pharmacy`, `reports`; future `surgery` |
| Hoàng Anh (`TranHoangAnh94`) | `organization`, `patient` | `organization`, `patients` |
| Lộc (`locgit-89`) | `billing`, `notification` | `billing`, `notifications` |

The feature and its matching route root form one ownership unit. A context owner may change both as
part of a bounded context task. Nested route directories inherit the route-root owner.

## Shared-scope coordinator

Vinh coordinates frontend shared scope. This is an explicit frontend exception to the general rule
that coordination does not expand feature ownership.

The coordinated shared scope includes:

- `frontend/src/lib/**`;
- `frontend/src/components/**`;
- `frontend/src/test/**` and shared test infrastructure;
- root app shell, dashboard shell, global loading, global styles, login, auth, and session paths;
- frontend manifests, lockfiles, Next.js, Tailwind, TypeScript, ESLint, Vitest, and other frontend
  configuration.

Vinh's coordinator role grants default write authority only to these shared paths and Vinh-owned
contexts. It does not grant default write authority to another developer's feature or route.

Another developer may change an exact shared path only when the human task explicitly delegates
that shared work. A feature need does not silently grant shared-scope access.

## Layered `AGENTS.md` structure

The effective rule is placed close to the files it governs:

```text
frontend/AGENTS.md
frontend/src/AGENTS.md
frontend/src/features/AGENTS.md
frontend/src/features/<existing-context>/AGENTS.md
frontend/src/app/AGENTS.md
frontend/src/app/(dashboard)/AGENTS.md
frontend/src/app/(dashboard)/<existing-route-root>/AGENTS.md
frontend/src/components/AGENTS.md
frontend/src/lib/AGENTS.md
frontend/src/test/AGENTS.md
```

Planned contexts such as Surgery receive nested rules when their source or route folder is actually
scaffolded. Empty source directories are not created only to hold ownership metadata.

### Rule responsibilities

- `frontend/AGENTS.md` remains the frontend entry point and mandates the source preflight.
- `frontend/src/AGENTS.md` contains the complete decision flow, delegation semantics, handoff
  routing, and the non-negotiable architecture rules an agent must not lose through nearest-file
  precedence.
- `frontend/src/features/AGENTS.md` defines feature-wide rules: one bounded context per folder,
  feature APIs through `src/lib/api.ts`, exact DTO mirroring, and no feature-to-feature imports.
- Each `features/<context>/AGENTS.md` states only the canonical owner, exact feature scope, matching
  route root, backend contract authority, local guide, and verification expectations.
- `frontend/src/app/AGENTS.md` and `frontend/src/app/(dashboard)/AGENTS.md` protect shared shell/auth
  paths under Vinh's coordination while allowing more specific route-root rules to take precedence.
- Each route-root `AGENTS.md` states its canonical owner, matching feature, thin-route responsibility,
  and the prohibition on absorbing another context's business logic.
- `components/AGENTS.md`, `lib/AGENTS.md`, and `test/AGENTS.md` mark those paths as Vinh-coordinated
  shared scope and define what belongs there.

Every nested file must explicitly say that parent rules and the canonical documents continue to
apply. Nearest-file precedence may narrow a writable scope; it must not erase the architecture,
contract, delegation, or verification rules.

## Mandatory edit preflight

Before editing anything under `frontend/src`, an agent must perform this decision flow:

1. List the intended files and map each file to a context-owned or shared scope.
2. Read `frontend/src/AGENTS.md` and the nearest nested `AGENTS.md` for every intended path.
3. Identify the acting developer from an explicit human task, issue, workboard assignment, or
   approved plan. Git identity, branch name, prior edits, and subagent assignment are not authority.
4. Compare the acting developer with the canonical owner or shared coordinator.
5. Verify the live backend controller/DTO/Gateway contract and the context's frontend service guide.
6. Classify the work as `IMPLEMENT`, `DELEGATED`, `HANDOFF`, `VERIFY-CONTRACT`, or `BLOCKED`.
7. Edit only the paths allowed by that classification and inspect the final changed-file list before
   reporting completion.

If the acting developer or delegated paths are ambiguous, the agent must stop and ask for a precise
assignment rather than infer permission.

## Work classifications

### `IMPLEMENT`

The acting developer is the canonical owner of every context-owned target, or Vinh is changing an
authorized shared path. The live contract exists and the work stays inside the declared paths.

### `DELEGATED`

A human task explicitly names:

- the canonical owner;
- the delegated developer;
- the exact context or shared paths;
- the bounded deliverable.

The delegation lasts only for that task or PR. It does not change the ownership matrix, authorize
neighboring paths, or pass automatically to a subagent. A subagent may work only inside the same
explicitly delegated paths and cannot widen them.

A one-task delegation needs no permanent document. If the work spans tasks or PRs, or another person
must resume it later, record it in a frontend handoff.

### `VERIFY-CONTRACT`

The source exists, but the next edit depends on confirming the current backend controller, DTO,
roles, error codes, feature flags, or Gateway route. Investigation remains read-only until the
contract is verified.

### `HANDOFF`

Use a handoff only when one authorized scope cannot finish without a concrete action, contract, or
decision from another owner. A handoff is not a general permission slip and is not required for
ordinary same-scope work.

### `BLOCKED`

The required authority or contract is missing and no safe implementation can proceed without
guessing. Do not invent temporary DTO fields, route paths, roles, identifiers, or cross-feature
coupling.

## Working from another developer's premise

A context owner may build inside their own feature and route using another context's completed work
only when the premise is a committed and verifiable public contract: a live Gateway endpoint,
backend DTO, documented error/role contract, or stable shared API.

This permits read-only inspection of another owner's source, but it does not permit:

- editing the producer's feature or route;
- importing directly from another feature;
- depending on uncommitted implementation details;
- copying guessed fields or business rules into the consumer.

If the premise is missing, unstable, or requires producer work, create a handoff instead.

## Handoff placement and lifecycle

### Frontend-to-frontend handoff

Place frontend ownership, composition, or shared-component handoffs under:

```text
frontend/docs/handoffs/
├── README.md
└── HANDOFF-<PRODUCER>-<CONSUMER>-<SLUG>.md
```

The registry contains only active frontend handoffs. Each handoff names:

- producer owner and consumer owner;
- affected context and exact paths;
- required action or decision;
- verified current contract or baseline;
- acceptance criteria;
- compatibility expectations;
- who resumes work after acceptance.

### Backend-contract handoff

If frontend work is blocked by a backend endpoint, DTO, role, event, identity claim, or Gateway
contract, use the existing repository registry at `docs/handoffs/README.md` and follow its lifecycle.
Do not duplicate the same blocker in both registries; the frontend workboard may link to the canonical
backend handoff.

### Completion

When acceptance criteria pass, move durable rules into the canonical frontend blueprint, ownership
document, service guide, or backend contract document. Remove the completed handoff from the active
registry in the same PR. Git history keeps the transfer record.

## Conflict-prevention rules

- Do not perform opportunistic refactors in a foreign or shared path.
- Do not mix multiple owners' contexts in one task unless the task explicitly delegates each scope.
- Do not move domain-specific code into shared scope merely to bypass ownership.
- Do not copy another feature's component or DTO to avoid a handoff.
- A feature never imports another feature. Cross-context composition remains in `app/` and must be
  authorized for every participating route/context scope.
- If reusable code is needed but shared scope was not assigned, keep a context-specific abstraction
  local when safe or hand the shared extraction to Vinh; do not silently edit shared files.
- Review the changed-file list against the declared scope before running final verification.

## Canonical documentation changes

Implementation updates the following documentation without duplicating mutable state unnecessarily:

- `docs/ai/15-frontend-ownership.md`: canonical ownership, Vinh's shared coordinator role,
  delegation semantics, classification rules, and handoff routing;
- `docs/ai/12-frontend.md`: pointer to the mandatory source preflight and nested rules;
- `frontend/AGENTS.md`: mandatory read order and source-rule entry point;
- `frontend/docs/services/README.md`: pointer from each context guide to its nested source rules;
- `frontend/docs/handoffs/README.md`: active frontend handoff registry and template.

The dated workboard remains a current-state inventory. It does not own permanent authorization rules.

## Verification

Because this change adds documentation only, runtime frontend tests are not required. Verification
must establish that:

1. every existing feature folder has exactly one nested owner rule;
2. every existing dashboard route root has exactly one nested owner rule;
3. feature and route mappings match the canonical matrix;
4. shared source paths consistently name Vinh as coordinator;
5. every nested file inherits the parent and canonical rules;
6. no developer name was introduced into runtime source structure or identifiers;
7. no existing source code or runtime configuration changed;
8. Markdown links resolve and `git diff --check` is clean.

The final review must inspect the changed-file list manually. There is intentionally no permanent CI
path gate.
