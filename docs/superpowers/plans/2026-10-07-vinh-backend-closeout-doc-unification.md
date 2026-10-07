# Vinh Backend Closeout Documentation Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Clinical, Lab and Inpatient one canonical implementation path, retain only actionable cross-service blockers, and document why Vinh's backend is locally complete but not yet safe to activate.

**Architecture:** Consolidate Clinical and Lab compatibility plus Care-Finance material into their top-level service specifications, retain redirect stubs for old V2 links, and keep Inpatient's existing V2 file as its sole canonical specification. Rewrite active handoffs as small owner-to-owner contracts and add the missing Notification projection handoff without changing production source or feature flags.

**Tech Stack:** Markdown, PowerShell link validation, Git, Maven test evidence already captured in the approved design.

**Spec:** `docs/superpowers/specs/2026-10-07-vinh-backend-closeout-doc-unification-design.md`

## Global Constraints

- Production changes remain limited to Vinh-owned services, but this plan changes documentation only.
- Do not enable Clinical, Lab or Inpatient Care-Finance flags.
- Durable payload definitions remain under `docs/handoffs/care-finance/`; service specs link to them instead of copying them.
- A handoff contains current owner work and acceptance criteria only; Git history retains completed progress.
- Clinical and Lab keep compatibility behavior until broker-backed Docker acceptance succeeds.
- Every commit has exactly one allowed human author and no co-author or AI trailer.

## Review Focus

- Old links to V2 Clinical/Lab must still resolve and lead readers to the canonical file.
- A reader must not mistake `care-finance-v2/10-inpatient.md` for an obsolete duplicate.
- Handoffs must not claim a runtime contract is complete based only on decoder or held-fixture tests.
- Notification must receive only approved privacy-safe projections, without clinical free text in templates.
- The final diff must contain no Java, YAML feature-flag, migration or runtime configuration change.

---

### Task 1: Canonical backend specification index

**Files:**
- Modify: `docs/eproject_general_plan/backend-spec/README.md`

**Interfaces:**
- Consumes: canonical-file decisions from the approved design.
- Produces: a matrix mapping each service to exactly one implementation entry point and rollout state.

- [ ] **Step 1: Capture the failing invariant**

Run:

```powershell
$text = Get-Content -Raw docs/eproject_general_plan/backend-spec/README.md
if ($text -notmatch 'Canonical implementation entry point') { throw 'canonical matrix missing' }
```

Expected: FAIL with `canonical matrix missing`.

- [ ] **Step 2: Add the canonical matrix**

Replace the separate CURRENT/V2 authority wording with a matrix that names top-level `03-clinical.md` and `04-lab.md`, `care-finance-v2/10-inpatient.md`, and the existing owner specs for all other contexts. State `COMPATIBILITY_LIVE`, `FEATURE_GATED`, `EXTERNAL_BLOCKED`, or `DRAFT` explicitly.

- [ ] **Step 3: Verify the invariant**

Run the Step 1 command again.

Expected: PASS with no output.

- [ ] **Step 4: Commit**

```powershell
git add .changelog/entries.jsonl docs/eproject_general_plan/backend-spec/README.md docs/superpowers/plans/2026-10-07-vinh-backend-closeout-doc-unification.md
git commit -m "docs(backend): define canonical specification entry points"
```

### Task 2: Consolidate Clinical and Lab specifications

**Files:**
- Modify: `docs/eproject_general_plan/backend-spec/03-clinical.md`
- Modify: `docs/eproject_general_plan/backend-spec/04-lab.md`
- Modify: `docs/eproject_general_plan/backend-spec/care-finance-v2/03-clinical.md`
- Modify: `docs/eproject_general_plan/backend-spec/care-finance-v2/04-lab.md`
- Modify: `docs/eproject_general_plan/backend-spec/care-finance-v2/README.md`

**Interfaces:**
- Consumes: canonical paths from Task 1 and durable care-finance contract links.
- Produces: combined Clinical/Lab specs plus compatibility redirects at the old V2 paths.

- [ ] **Step 1: Capture duplicate-authority failures**

Run:

```powershell
$clinical = Get-Content -Raw docs/eproject_general_plan/backend-spec/03-clinical.md
$lab = Get-Content -Raw docs/eproject_general_plan/backend-spec/04-lab.md
if ($clinical -notmatch 'FEATURE_GATED' -or $lab -notmatch 'FEATURE_GATED') { throw 'rollout states missing' }
```

Expected: FAIL with `rollout states missing`.

- [ ] **Step 2: Merge target behavior into each canonical spec**

Add a rollout table and the implemented additive schema, commands, invariants, event links, external blockers, migration rule and test evidence. Preserve the compatibility sections and avoid copying canonical JSON payload bodies.

- [ ] **Step 3: Replace the two V2 specs with redirects**

Each redirect identifies the new canonical path, explains that the old path remains link-compatible, and contains no DDL, algorithm, DTO or payload duplication. Update the V2 README accordingly.

- [ ] **Step 4: Verify consolidation**

Run:

```powershell
$redirects = @(
  'docs/eproject_general_plan/backend-spec/care-finance-v2/03-clinical.md',
  'docs/eproject_general_plan/backend-spec/care-finance-v2/04-lab.md'
)
foreach ($file in $redirects) {
  $text = Get-Content -Raw $file
  if ($text -notmatch 'canonical' -or (Get-Content $file).Count -gt 30) { throw "invalid redirect: $file" }
}
```

Expected: PASS with no output.

- [ ] **Step 5: Commit**

```powershell
git add .changelog/entries.jsonl docs/eproject_general_plan/backend-spec
git commit -m "docs(vinh): consolidate clinical and lab specifications"
```

### Task 3: Clarify Inpatient and service implementation status

**Files:**
- Modify: `docs/eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md`
- Modify: `docs/ai/services/clinical.md`
- Modify: `docs/ai/services/lab.md`
- Modify: `docs/ai/services/inpatient.md`

**Interfaces:**
- Consumes: the canonical matrix and consolidated Vinh specifications.
- Produces: one status vocabulary shared by implementation specs and AI service guidance.

- [ ] **Step 1: Capture missing closeout status**

Run:

```powershell
$files = @('docs/ai/services/clinical.md','docs/ai/services/lab.md','docs/ai/services/inpatient.md')
foreach ($file in $files) {
  if ((Get-Content -Raw $file) -notmatch 'locally complete') { throw "closeout status missing: $file" }
}
```

Expected: FAIL on at least one file.

- [ ] **Step 2: Add status and activation gates**

Mark Inpatient Core V1 as implemented and its external messaging as gated. In all three service documents, link the one canonical implementation spec, name the flags that remain disabled, and list the exact external acceptance evidence required before activation.

- [ ] **Step 3: Verify status and source scope**

Run the Step 1 command again, then:

```powershell
git diff --name-only --diff-filter=ACMRT | Where-Object { $_ -match '\.(java|yml|yaml|sql)$' } | ForEach-Object { throw "production file changed: $_" }
```

Expected: both commands PASS with no output.

- [ ] **Step 4: Commit**

```powershell
git add .changelog/entries.jsonl docs/eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md docs/ai/services/clinical.md docs/ai/services/lab.md docs/ai/services/inpatient.md
git commit -m "docs(vinh): record backend activation gates"
```

### Task 4: Reduce retained handoffs to current blockers

**Files:**
- Modify: `backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md`
- Modify: `backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md`
- Modify: `docs/handoffs/HANDOFF-VINH-GATEWAY-CARE-ROLES.md`
- Modify: `docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md`
- Modify: `docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md`

**Interfaces:**
- Consumes: verified source state in the approved design and durable event contracts.
- Produces: five concise handoffs with producer, consumer, owner actions and measurable acceptance criteria.

- [ ] **Step 1: Capture historical-noise failures**

Run:

```powershell
$files = @(
  'backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md',
  'backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md',
  'docs/handoffs/HANDOFF-VINH-GATEWAY-CARE-ROLES.md',
  'docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md',
  'docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md'
)
foreach ($file in $files) {
  if ((Get-Content $file).Count -gt 140) { throw "handoff still contains history: $file" }
}
```

Expected: FAIL for at least the Surgery or Huy handoff.

- [ ] **Step 2: Rewrite each handoff**

Keep only current contract direction, missing owner work, exact canonical contract/fixture links, feature-gate rule and acceptance checklist. Remove dated progress diaries, completed rows and obsolete scope grants.

- [ ] **Step 3: Verify handoff shape**

Run Step 1 again and verify each document contains `Producer`, `Consumer`, `Owner actions` and `Acceptance criteria` headings.

Expected: PASS with no output.

- [ ] **Step 4: Commit**

```powershell
git add .changelog/entries.jsonl backend/billing-service/HANDOFF-*.md docs/handoffs/HANDOFF-VINH-GATEWAY-CARE-ROLES.md docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md
git commit -m "docs(handoffs): retain only active integration blockers"
```

### Task 5: Register Notification care projections

**Files:**
- Create: `docs/handoffs/HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md`
- Modify: `docs/handoffs/README.md`
- Modify: `docs/handoffs/care-finance/README.md`
- Modify: `backend/notification-service/AGENTS.md`

**Interfaces:**
- Consumes: approved Clinical completion, admission, top-up, settlement and Surgery event contracts.
- Produces: a registered Lộc-owned Notification blocker and mandatory local reading link.

- [ ] **Step 1: Capture the missing registry entry**

Run:

```powershell
$registry = Get-Content -Raw docs/handoffs/README.md
if ($registry -notmatch 'Notification care projections') { throw 'notification handoff missing' }
```

Expected: FAIL with `notification handoff missing`.

- [ ] **Step 2: Create the handoff and update registries**

Name each producer and consumer, require exact versioned bindings/decoders, idempotent intent persistence, privacy-safe templates and broker-backed tests. Link it from the active registry, care-finance registry and Notification `AGENTS.md`.

- [ ] **Step 3: Verify registration**

Run Step 1 again and confirm all three links resolve to the new document.

Expected: PASS with no output.

- [ ] **Step 4: Commit**

```powershell
git add .changelog/entries.jsonl docs/handoffs backend/notification-service/AGENTS.md
git commit -m "docs(notification): register care projection handoff"
```

### Task 6: Repository documentation verification

**Files:**
- Modify only if verification exposes a broken link or stale authority statement in the files already listed above.

**Interfaces:**
- Consumes: all prior task outputs.
- Produces: a clean documentation-only branch ready for review.

- [ ] **Step 1: Validate relative Markdown links in changed documents**

Run a PowerShell validator that extracts non-HTTP Markdown targets from every changed `.md` file, strips fragments, resolves them relative to the source file, and fails when the target does not exist.

Expected: PASS with no missing targets.

- [ ] **Step 2: Validate policy and scope**

Run:

```powershell
git diff --check master...HEAD
git diff --name-only master...HEAD | Where-Object { $_ -match '\.(java|yml|yaml|sql)$' } | ForEach-Object { throw "unexpected runtime change: $_" }
git log --format='%H%n%an <%ae>%n%(trailers:key=Co-Authored-By)' master..HEAD
```

Expected: no whitespace errors, no runtime files, one allowed human author per commit, and no co-author trailers.

- [ ] **Step 3: Compare final docs with the approved design**

Confirm all seven validation points in the design document are satisfied and that Docker smoke remains explicitly pending.

- [ ] **Step 4: Commit verification fixes if needed**

```powershell
git add .changelog/entries.jsonl docs backend/*/HANDOFF-*.md backend/notification-service/AGENTS.md
git commit -m "docs(vinh): finalize backend closeout guidance"
```

Skip this commit when verification requires no content fix.

