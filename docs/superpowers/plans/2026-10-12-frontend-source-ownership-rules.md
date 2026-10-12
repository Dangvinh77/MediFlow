# Frontend Source Ownership Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put documentation-only ownership rules beside every current frontend feature, route, and
shared source area so an AI agent can determine its writable scope before editing.

**Architecture:** Preserve the bounded-context Next.js tree and add layered `AGENTS.md` metadata.
`docs/ai/15-frontend-ownership.md` remains canonical; source-local files narrow effective scope and
point to the correct contract and service guide. Vinh coordinates shared frontend paths, while
bounded task delegation and handoffs permit intentional temporary cross-owner work.

**Tech Stack:** Markdown, AGENTS.md nearest-file precedence, Next.js 16 source layout, Git

**Spec:** `docs/superpowers/specs/2026-10-12-frontend-source-ownership-rules-design.md`

## Global Constraints

- Keep source folders organized by bounded context and Next.js responsibility.
- Never add developer-named runtime folders, types, components, APIs, or comments.
- Add documentation only; do not change TypeScript, TSX, CSS, manifests, lockfiles, or config.
- Do not add CODEOWNERS, path-blocking scripts, or CI enforcement.
- Vinh coordinates shared frontend scope but has no default authority over another owner's context.
- Delegation must name the original owner, delegated developer, exact scope, and bounded result.
- Delegation expires with the task or PR and never widens through subagent assignment.
- Preserve the unrelated `docs/documentation_docx/` directory.

## Review Focus

- Ambiguous actor: Git identity, branch name, prior edits, and subagent assignment are not authority.
- Broad delegation: an authorized path does not authorize its siblings or future tasks.
- Nearest-file loss: leaf rules retain parent architecture, contract, and delegation rules.
- Cross-context composition: every participating scope must be authorized; features never import
  one another.
- Wrong registry: frontend ownership dependencies use `frontend/docs/handoffs/`; missing backend
  contracts use `docs/handoffs/`.

---

### Task 1: Update canonical policy and entry points

**Files:**
- Modify: `docs/ai/12-frontend.md`
- Modify: `docs/ai/15-frontend-ownership.md`
- Modify: `frontend/AGENTS.md`
- Modify: `frontend/docs/services/README.md`

**Interfaces:**
- Consumes: approved design and current owner matrix.
- Produces: canonical policy referenced by every source-local rule.

- [ ] **Step 1: Add the source preflight pointer**

Add this to `docs/ai/12-frontend.md` after its ownership/workboard pointer:

```markdown
Before editing anything under `frontend/src/`, read
[`frontend/src/AGENTS.md`](../../frontend/src/AGENTS.md) and the nearest nested `AGENTS.md` for
every intended path. A source-local rule narrows effective write scope; it never replaces this
blueprint or the canonical ownership policy.
```

- [ ] **Step 2: Define shared coordination**

Replace the shared-scope section in `docs/ai/15-frontend-ownership.md` with rules stating:

- Vinh (`Dangvinh77` / `Harori`) coordinates app shell, dashboard shell, global loading/styles,
  login/auth/session, `src/components/**`, `src/lib/**`, `src/test/**`, manifests, lockfiles, and
  frontend-wide configuration.
- This grants default write authority only for shared frontend scope and Vinh-owned contexts.
- A non-Vinh developer needs a human task naming exact shared paths and a bounded deliverable.
- Vinh does not gain default authority over another owner's feature or route.

- [ ] **Step 3: Define work classifications**

Replace `IMPLEMENT versus HANDOFF` with:

```markdown
- **IMPLEMENT:** the acting developer owns every target, or Vinh is changing shared scope.
- **DELEGATED:** the human task names the canonical owner, delegated developer, exact context or
  shared paths, and bounded deliverable. Authority expires with the task or PR.
- **VERIFY-CONTRACT:** live controller, DTO, roles, errors, feature flag, or Gateway route must be
  confirmed read-only before editing.
- **HANDOFF:** another owner must supply a concrete action, contract, or decision.
- **BLOCKED:** safe work cannot continue without authority or without guessing a contract.
```

Below the list, state that Git metadata is not authorization, subagents cannot widen delegated
scope, committed public contracts may be inspected read-only, frontend handoffs use
`frontend/docs/handoffs/`, and backend-contract handoffs use `docs/handoffs/`.

- [ ] **Step 4: Require local rules from both entry indexes**

Update `frontend/AGENTS.md` to require this read order:

```markdown
Read `docs/ai/12-frontend.md`, `docs/ai/15-frontend-ownership.md`, `src/AGENTS.md`, and the nearest
nested `AGENTS.md` for every intended path. Then read the workboard, matching service guide, and
live backend contract.
```

Update `frontend/docs/services/README.md` to require both
`src/features/<context>/AGENTS.md` and `src/app/(dashboard)/<route>/AGENTS.md`.

- [ ] **Step 5: Verify and commit Task 1**

Run:

```powershell
rg -n "src/AGENTS.md|DELEGATED|coordinates frontend shared scope|frontend/docs/handoffs" docs/ai/12-frontend.md docs/ai/15-frontend-ownership.md frontend/AGENTS.md frontend/docs/services/README.md
git diff --check
git add -- docs/ai/12-frontend.md docs/ai/15-frontend-ownership.md frontend/AGENTS.md frontend/docs/services/README.md
git commit -m "docs(frontend): define delegated ownership policy"
```

Expected: all policy concepts are present and the existing context matrix is unchanged.

---

### Task 2: Add source-wide and shared rules

**Files:**
- Create: `frontend/src/AGENTS.md`
- Create: `frontend/src/features/AGENTS.md`
- Create: `frontend/src/app/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/AGENTS.md`
- Create: `frontend/src/components/AGENTS.md`
- Create: `frontend/src/lib/AGENTS.md`
- Create: `frontend/src/test/AGENTS.md`

**Interfaces:**
- Consumes: Task 1 classifications and coordinator policy.
- Produces: inherited rules that leaf files narrow without replacing canonical policy.

- [ ] **Step 1: Create the source preflight**

Create `frontend/src/AGENTS.md` with this decision flow:

```markdown
# AGENTS.md — frontend source

Parent `frontend/AGENTS.md`, `docs/ai/12-frontend.md`, and
`docs/ai/15-frontend-ownership.md` remain mandatory.

## Before any edit

1. List intended paths and classify each as context-owned or shared.
2. Read the nearest nested `AGENTS.md` for every path.
3. Identify the acting developer from an explicit human task, issue, workboard assignment, or plan.
4. Never infer authority from Git identity, branch name, prior edits, or subagent assignment.
5. Verify the service guide and live backend controller, DTO, security, tests, and Gateway contract.
6. Classify work as IMPLEMENT, DELEGATED, VERIFY-CONTRACT, HANDOFF, or BLOCKED.
7. Compare every changed file with authorized paths before completion.

If actor or scope is ambiguous, stop and ask.

## Boundaries

- A feature and matching route are one ownership unit.
- A feature never imports another feature.
- Cross-context composition belongs in app routes and requires every participating scope.
- Feature APIs use `src/lib/api.ts`; only `src/lib/api.ts` calls `fetch`.
- DTOs mirror live backend wire contracts exactly.
- No opportunistic refactor outside authorized paths.
```

- [ ] **Step 2: Create feature and app parent rules**

Create `frontend/src/features/AGENTS.md` requiring one bounded context per folder, no sibling feature
imports, exact DTO mirroring, context-local code by default, and Vinh coordination before shared
extraction.

Create `frontend/src/app/AGENTS.md` assigning Vinh the app shell, login, global loading/style, and
auth-facing routing. Route pages stay thin and never fetch directly.

Create `frontend/src/app/(dashboard)/AGENTS.md` assigning Vinh the dashboard layout, shared
navigation, and shared route infrastructure while allowing direct route-root rules to own only their
subtrees.

- [ ] **Step 3: Create shared path rules**

Create these exact responsibilities:

| File | Vinh-coordinated responsibility | Forbidden content |
|---|---|---|
| `src/components/AGENTS.md` | feature-agnostic UI, layout, auth components | context DTOs, API calls, business rules |
| `src/lib/AGENTS.md` | API wrapper, auth/session, envelopes, roles, format, validation | context endpoints, DTOs, domain presentation |
| `src/test/AGENTS.md` | shared Vitest setup and cross-feature harness utilities | behavior belonging beside one feature |

Each file states that non-Vinh work needs explicit paths and a bounded deliverable.

- [ ] **Step 4: Verify and commit Task 2**

Run:

```powershell
$required = @('frontend/src/AGENTS.md','frontend/src/features/AGENTS.md','frontend/src/app/AGENTS.md','frontend/src/app/(dashboard)/AGENTS.md','frontend/src/components/AGENTS.md','frontend/src/lib/AGENTS.md','frontend/src/test/AGENTS.md')
$missing = $required | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { throw "Missing source rules: $($missing -join ', ')" }
rg -n "Parent|Vinh|DELEGATED|direct.*fetch|feature.*import" frontend/src --glob AGENTS.md
git diff --check
git add -- frontend/src/AGENTS.md frontend/src/features/AGENTS.md frontend/src/app/AGENTS.md 'frontend/src/app/(dashboard)/AGENTS.md' frontend/src/components/AGENTS.md frontend/src/lib/AGENTS.md frontend/src/test/AGENTS.md
git commit -m "docs(frontend): guard shared source scope"
```

Expected: all seven files exist and preserve inheritance, coordination, and boundaries.

---

### Task 3: Add feature owner rules

**Files:**
- Create: `frontend/src/features/appointment/AGENTS.md`
- Create: `frontend/src/features/medical-record/AGENTS.md`
- Create: `frontend/src/features/lab/AGENTS.md`
- Create: `frontend/src/features/inpatient/AGENTS.md`
- Create: `frontend/src/features/pharmacy/AGENTS.md`
- Create: `frontend/src/features/report/AGENTS.md`
- Create: `frontend/src/features/organization/AGENTS.md`
- Create: `frontend/src/features/patient/AGENTS.md`
- Create: `frontend/src/features/billing/AGENTS.md`
- Create: `frontend/src/features/notification/AGENTS.md`

**Interfaces:**
- Consumes: source parent rules and service guides.
- Produces: exact owner, route, guide, and backend mapping at every feature boundary.

- [ ] **Step 1: Use one complete leaf format**

Every file contains:

```markdown
# AGENTS.md — <context label> frontend feature

Parent `frontend/src/AGENTS.md`, `frontend/src/features/AGENTS.md`,
`docs/ai/12-frontend.md`, and `docs/ai/15-frontend-ownership.md` remain mandatory.

- Canonical owner: <owner>.
- Writable feature scope: `frontend/src/features/<feature>/**`.
- Matching route scope: `frontend/src/app/(dashboard)/<route>/**`.
- Service guide: `frontend/docs/services/<guide>.md`.
- Backend contract authority: `backend/<service>/` controllers, DTOs, security, and tests.

The owner may IMPLEMENT inside this feature and route. Anyone else needs a human DELEGATED task
naming this context or exact paths and bounded deliverable. Read foreign contexts only to verify
public contracts. Never edit them, import another feature, infer wire fields, or widen delegation.
Use the frontend handoff registry for a frontend dependency and the repository registry for a
missing backend contract.

Verify changed scope, then run `pnpm typecheck` and `pnpm lint` when TypeScript changed.
```

- [ ] **Step 2: Apply this exact mapping**

| Feature | Label | Owner | Route | Guide | Backend |
|---|---|---|---|---|---|
| `appointment` | Clinical appointment | Vinh (`Dangvinh77` / `Harori`) | `appointments` | `clinical.md` | `clinical-service` |
| `medical-record` | Clinical medical record | Vinh (`Dangvinh77` / `Harori`) | `records` | `clinical.md` | `clinical-service` |
| `lab` | Lab | Vinh (`Dangvinh77` / `Harori`) | `lab` | `lab.md` | `lab-service` |
| `inpatient` | Inpatient | Vinh (`Dangvinh77` / `Harori`) | `inpatient` | `inpatient.md` | `inpatient-service` |
| `pharmacy` | Pharmacy | Huy (`LQHuy0210`) | `pharmacy` | `pharmacy.md` | `pharmacy-service` |
| `report` | Report | Huy (`LQHuy0210`) | `reports` | `report.md` | `report-service` |
| `organization` | Organization | Hoàng Anh (`TranHoangAnh94`) | `organization` | `organization.md` | `organization-service` |
| `patient` | Patient | Hoàng Anh (`TranHoangAnh94`) | `patients` | `patient.md` | `patient-service` |
| `billing` | Billing | Lộc (`locgit-89`) | `billing` | `billing.md` | `billing-service` |
| `notification` | Notification | Lộc (`locgit-89`) | `notifications` | `notification.md` | `notification-service` |

- [ ] **Step 3: Verify and commit Task 3**

Run:

```powershell
$features = Get-ChildItem -LiteralPath 'frontend/src/features' -Directory
$missing = $features | Where-Object { -not (Test-Path -LiteralPath (Join-Path $_.FullName 'AGENTS.md')) }
if ($missing) { throw "Feature rules missing: $($missing.Name -join ', ')" }
if ($features.Count -ne 10) { throw 'Review new or removed feature contexts' }
rg -n "Canonical owner:|Writable feature scope:|Matching route scope:|Backend contract authority:" frontend/src/features --glob AGENTS.md
git diff --check
git add -- frontend/src/features/*/AGENTS.md
git commit -m "docs(frontend): assign feature context owners"
```

Expected: ten current feature folders and ten fully populated leaf rules.

---

### Task 4: Add dashboard route owner rules

**Files:**
- Create: `frontend/src/app/(dashboard)/appointments/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/records/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/lab/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/inpatient/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/pharmacy/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/reports/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/organization/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/patients/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/billing/AGENTS.md`
- Create: `frontend/src/app/(dashboard)/notifications/AGENTS.md`

**Interfaces:**
- Consumes: Task 3 feature-owner mapping.
- Produces: route ownership inherited by every nested page.

- [ ] **Step 1: Use one complete route format**

Every route-root file contains:

```markdown
# AGENTS.md — <route label> route

Parent `frontend/src/AGENTS.md`, `frontend/src/app/AGENTS.md`,
`frontend/src/app/(dashboard)/AGENTS.md`, `docs/ai/12-frontend.md`, and
`docs/ai/15-frontend-ownership.md` remain mandatory.

- Canonical owner: <owner>.
- Writable route scope: `frontend/src/app/(dashboard)/<route>/**`.
- Matching feature: `frontend/src/features/<feature>/**`.
- Service guide: `frontend/docs/services/<guide>.md`.

Keep this route thin: read params, compose authorized feature components, and own page-level
loading/error state. Do not add business logic, direct fetch, foreign-feature imports, or shared
shell changes. Another developer needs an explicit human DELEGATED task. Cross-context composition
requires authority for every participating context.
```

- [ ] **Step 2: Apply this exact mapping**

| Route | Feature | Owner | Guide |
|---|---|---|---|
| `appointments` | `appointment` | Vinh (`Dangvinh77` / `Harori`) | `clinical.md` |
| `records` | `medical-record` | Vinh (`Dangvinh77` / `Harori`) | `clinical.md` |
| `lab` | `lab` | Vinh (`Dangvinh77` / `Harori`) | `lab.md` |
| `inpatient` | `inpatient` | Vinh (`Dangvinh77` / `Harori`) | `inpatient.md` |
| `pharmacy` | `pharmacy` | Huy (`LQHuy0210`) | `pharmacy.md` |
| `reports` | `report` | Huy (`LQHuy0210`) | `report.md` |
| `organization` | `organization` | Hoàng Anh (`TranHoangAnh94`) | `organization.md` |
| `patients` | `patient` | Hoàng Anh (`TranHoangAnh94`) | `patient.md` |
| `billing` | `billing` | Lộc (`locgit-89`) | `billing.md` |
| `notifications` | `notification` | Lộc (`locgit-89`) | `notification.md` |

- [ ] **Step 3: Verify and commit Task 4**

Run:

```powershell
$dashboard = 'frontend/src/app/(dashboard)'
$routes = Get-ChildItem -LiteralPath $dashboard -Directory
$missing = $routes | Where-Object { -not (Test-Path -LiteralPath (Join-Path $_.FullName 'AGENTS.md')) }
if ($missing) { throw "Route rules missing: $($missing.Name -join ', ')" }
if ($routes.Count -ne 10) { throw 'Review new or removed route roots' }
rg -n "Canonical owner:|Writable route scope:|Matching feature:|Cross-context composition" $dashboard --glob AGENTS.md
git diff --check
git add -- 'frontend/src/app/(dashboard)/*/AGENTS.md'
git commit -m "docs(frontend): assign dashboard route owners"
```

Expected: ten route roots and ten rules; deeper pages inherit without duplicates.

---

### Task 5: Add the frontend handoff registry

**Files:**
- Create: `frontend/docs/handoffs/README.md`

**Interfaces:**
- Consumes: Task 1 handoff routing policy.
- Produces: one registry for active frontend-only ownership, composition, and shared blockers.

- [ ] **Step 1: Create the registry**

Use this complete structure:

```markdown
# Active frontend handoffs

This registry contains only active frontend-to-frontend ownership, composition, or shared-scope
dependencies. Missing backend endpoints, DTOs, roles, events, identity claims, or Gateway contracts
belong in `docs/handoffs/README.md`.

## Active handoffs

None.

## When to create one

Create a handoff only when one authorized frontend scope cannot finish without a concrete action or
decision from another owner. A one-task explicit delegation does not need a handoff.

## Required template

# HANDOFF: <producer context> to <consumer context> — <outcome>

- Status: ACTIVE
- Producer owner: <human owner and Git identity>
- Consumer owner: <human owner and Git identity>
- Affected contexts: <exact contexts>
- Affected paths: <exact paths>
- Verified baseline: <commit and public contract>
- Required action or decision: <concrete missing result>
- Acceptance criteria: <observable completion conditions>
- Compatibility expectation: <what existing consumers must retain>
- Resume owner: <who continues after acceptance>

## Lifecycle

1. Register only active blockers.
2. Do not use a handoff as broad write permission.
3. Link a backend-contract handoff instead of duplicating it here.
4. After acceptance, move durable rules to canonical docs and delete the completed handoff entry and
   file in the same PR.
```

The angle-bracket fields are a copyable template, not unfinished requirements.

- [ ] **Step 2: Verify and commit Task 5**

Run:

```powershell
rg -n "frontend-to-frontend|docs/handoffs/README.md|one-task explicit delegation|Acceptance criteria|Lifecycle" frontend/docs/handoffs/README.md docs/ai/15-frontend-ownership.md frontend/src/AGENTS.md
git diff --check
git add -- frontend/docs/handoffs/README.md
git commit -m "docs(frontend): add handoff registry"
```

Expected: frontend-only and backend-contract blockers have one destination each.

---

### Task 6: Run the final ownership audit

**Files:**
- Verify only; no planned source changes.

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: evidence that all current boundaries are covered and runtime code is untouched.

- [ ] **Step 1: Check coverage counts**

Run:

```powershell
$featureDirs = Get-ChildItem -LiteralPath 'frontend/src/features' -Directory
$routeDirs = Get-ChildItem -LiteralPath 'frontend/src/app/(dashboard)' -Directory
$featureRules = $featureDirs | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'AGENTS.md') }
$routeRules = $routeDirs | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'AGENTS.md') }
if ($featureRules.Count -ne $featureDirs.Count) { throw 'Incomplete feature ownership coverage' }
if ($routeRules.Count -ne $routeDirs.Count) { throw 'Incomplete route ownership coverage' }
"$($featureRules.Count) feature rules; $($routeRules.Count) route rules"
```

Expected: `10 feature rules; 10 route rules`.

- [ ] **Step 2: Check leaf vocabulary**

Run:

```powershell
$leafRules = @((Get-ChildItem -LiteralPath 'frontend/src/features' -Filter 'AGENTS.md' -Recurse),(Get-ChildItem -LiteralPath 'frontend/src/app/(dashboard)' -Filter 'AGENTS.md' -Recurse))
$requiredPatterns = @('Parent','Canonical owner:','scope:','DELEGATED')
foreach ($pattern in $requiredPatterns) {
  $missing = $leafRules | Where-Object { -not (Select-String -LiteralPath $_.FullName -Pattern $pattern -Quiet) }
  if ($missing) { throw "Rules missing '$pattern': $($missing.FullName -join ', ')" }
}
```

Expected: all twenty leaf rules preserve inheritance, owner, scope, and delegation.

- [ ] **Step 3: Check structure and diff**

Run:

```powershell
$forbidden = Get-ChildItem -LiteralPath 'frontend/src' -Directory -Recurse | Where-Object { $_.Name -match '^(vinh|harori|huy|hoang-?anh|loc|lqhuy0210|tranhoanganh94|locgit-89)$' }
if ($forbidden) { throw "Developer-named source folders: $($forbidden.FullName -join ', ')" }
git diff --name-status HEAD~5..HEAD
git diff --check HEAD~5..HEAD
```

Manually confirm the five implementation commits contain only `AGENTS.md` files, the four canonical
and index documents, `frontend/docs/handoffs/README.md`, and repository-managed changelog entries.
No `.ts`, `.tsx`, `.css`, package, lock, or config file may appear.

- [ ] **Step 4: Validate cited paths**

For every feature leaf, use `Test-Path -LiteralPath` to confirm its feature folder, route root,
service guide, and backend service exist. For every route leaf, confirm its route root, feature, and
guide. Surgery is excluded because no empty source folders are created.

- [ ] **Step 5: Report final evidence**

Report ten feature rules, ten route rules, Vinh as shared coordinator, no runtime/config changes, no
CI path gate, all five work classifications, both handoff destinations, and confirmation that
`docs/documentation_docx/` was untouched.
