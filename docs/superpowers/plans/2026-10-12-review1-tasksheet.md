# Review 1 Tasksheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Completed steps use checked boxes for tracking.

**Goal:** Create a source-backed English Review 1 task sheet that records delivered frontend work and assigns future frontend functions to the correct MediFlow developers.

**Architecture:** One standalone Markdown document contains a short evidence note and one HTML table with the approved two-level header. Current rows come from live Next.js routes, feature folders, frontend guides, and Git author dates; future rows follow the canonical ownership matrix and name contract blockers explicitly.

**Tech Stack:** Markdown, HTML table markup, Git history, PowerShell validation.

**Execution status:** Completed on 2026-10-12, including the pre-merge review follow-up that aligned
planned Surgery ownership and its frontend service guide with the project-level ownership rules.

---

## File Structure

- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md`
  - Owns the complete Review 1 task sheet, evidence rules, status legend, and grouped member rows.
- Modify: `docs/ai/15-frontend-ownership.md`
  - Canonical developer-to-context ownership.
- Modify: `frontend/docs/services/README.md`
  - Register the planned Surgery frontend guide.
- Create: `frontend/docs/services/surgery.md`
  - Record the planned Surgery scope, owner queue, and activation blockers.
- Reference only: `frontend/docs/frontend-workboard.md`
  - Current shipped, in-progress, unavailable, and next-task state.
- Reference only: `frontend/docs/services/*.md`
  - Context-specific queues, blockers, route baselines, and contract gates.

### Task 1: Collect Reproducible Source and Git Evidence

**Files:**
- Read: `frontend/src/app/(dashboard)/**/page.tsx`
- Read: `frontend/src/features/**`
- Read: `frontend/docs/frontend-workboard.md`
- Read: `frontend/docs/services/*.md`
- Read: `docs/ai/15-frontend-ownership.md`

- [x] **Step 1: List the current frontend route files**

Run:

```powershell
rg --files frontend/src/app frontend/src/features | Sort-Object
```

Expected: route and feature files for appointment, medical record, Laboratory, Inpatient,
Organization, Patient, Pharmacy, Billing, Notification, and Report; no Surgery route.

- [x] **Step 2: Extract first author date and distinct active dates per owned context**

Run:

```powershell
$groups=[ordered]@{
  Appointment=@('frontend/src/features/appointment','frontend/src/app/(dashboard)/appointments')
  MedicalRecord=@('frontend/src/features/medical-record','frontend/src/app/(dashboard)/records')
  Laboratory=@('frontend/src/features/lab','frontend/src/app/(dashboard)/lab')
  Inpatient=@('frontend/src/features/inpatient','frontend/src/app/(dashboard)/inpatient')
  Pharmacy=@('frontend/src/features/pharmacy','frontend/src/app/(dashboard)/pharmacy')
  Report=@('frontend/src/features/report','frontend/src/app/(dashboard)/reports')
  Organization=@('frontend/src/features/organization','frontend/src/app/(dashboard)/organization')
  Patient=@('frontend/src/features/patient','frontend/src/app/(dashboard)/patients')
  Billing=@('frontend/src/features/billing','frontend/src/app/(dashboard)/billing')
  Notification=@('frontend/src/features/notification','frontend/src/app/(dashboard)/notifications')
}
foreach($name in $groups.Keys){
  $rows=@(git log '--format=%ad|%an' --date=short -- $groups[$name])
  $dates=@($rows | ForEach-Object { ($_ -split '\|',2)[0] } | Sort-Object -Unique)
  [pscustomobject]@{
    Context=$name
    ActualStart=if($dates){$dates[0]}else{'—'}
    ActualDays=$dates.Count
  }
}
```

Expected: ten context rows with reproducible start dates and distinct active-day counts. Use the
earliest date as `Actual Start Date`; do not use elapsed calendar days.

- [x] **Step 3: Build the current/future task inventory**

Record rows under these owner groups:

- Vinh: appointment, medical record, Laboratory, Inpatient, focused tests, clearance-enabled
  Clinical commands, Clinical-to-Laboratory request handoff, and Inpatient bed placement,
  admit/cancel, treatment correction, order-reference, discharge, close, and bed administration.
- Huy: Pharmacy, Report, focused tests, operational reports, export contract, Surgery workspace.
- Hoàng Anh: Organization, Patient, focused tests, lookup selectors, identity mapping, patient
  self-service.
- Lộc: Billing, Notification, focused tests, payments/refunds/settlement/clearance, revenue,
  Billing outbox replay, prescription/Surgery refund-due reads, notification read/retry, and
  authenticated patient notification access.

Expected: every delivered route is represented and every future function stays with its owner.

### Task 2: Create the Word-Friendly Task Sheet

**Files:**
- Create: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md`

- [x] **Step 1: Add the document title, evidence note, and status legend**

The introduction must state:

- preparation date: `2026-10-12`;
- delivered dates come from Git author history;
- `Actual Days` counts distinct activity dates;
- future work begins on or after `2026-10-12`;
- statuses are `DONE`, `IN PROGRESS`, `PLANNED`, `VERIFY CONTRACT`, and `BLOCKED`.

- [x] **Step 2: Add the approved two-level HTML header**

Use this exact structure:

```html
<thead>
  <tr>
    <th rowspan="2">No.</th>
    <th rowspan="2">Member</th>
    <th rowspan="2">Contents</th>
    <th rowspan="2">Page Name</th>
    <th>Activity Plan</th>
    <th colspan="3">Plan Preparation Date</th>
  </tr>
  <tr>
    <th>Planned Start Date</th>
    <th>Actual Start Date</th>
    <th>Actual Days</th>
    <th>Status</th>
  </tr>
</thead>
```

- [x] **Step 3: Add rows grouped by member**

For each row:

- use an existing route for delivered work;
- use `Planned: /path` when the route does not exist;
- use ISO dates (`YYYY-MM-DD`);
- use `—` for unavailable actual values;
- describe the exact dependency in `Contents` when status is `BLOCKED` or `VERIFY CONTRACT`;
- schedule future tasks in a dependency-aware sequence beginning `2026-10-12`.

- [x] **Step 4: Review ownership and claims**

Confirm that Surgery is assigned to Huy, Inpatient to Vinh, identity liaison to Hoàng Anh, and
Billing/Notification to Lộc. Confirm no blocked feature is worded as delivered.

- [x] **Step 5: Commit the task sheet**

Run:

```powershell
git add docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md
git commit -m "docs(review1): add source-based frontend task sheet"
```

Expected: one new Markdown deliverable committed.

### Task 3: Validate the Deliverable

**Files:**
- Verify: `docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md`

- [x] **Step 1: Run structural checks**

Run:

```powershell
$path='docs/monitor_proj_progress/01-giai-doan-1-khoi-dong-dac-ta/review-1/Tasksheet_review1.md'
$text=Get-Content -LiteralPath $path -Raw
foreach($member in 'Vinh','Huy','Hoàng Anh','Lộc') {
  if($text -notmatch [regex]::Escape($member)){ throw "Missing member: $member" }
}
foreach($status in 'DONE','IN PROGRESS','PLANNED','VERIFY CONTRACT','BLOCKED') {
  if($text -notmatch [regex]::Escape($status)){ throw "Missing status: $status" }
}
if($text -notmatch 'rowspan="2"'){ throw 'Missing two-level row header' }
if($text -notmatch 'colspan="3"'){ throw 'Missing Plan Preparation Date group' }
if($text -match '\bV\d+\b|Care[-.]Finance'){ throw 'Internal version terminology found' }
'TASKSHEET_STRUCTURE_OK'
```

Expected: `TASKSHEET_STRUCTURE_OK`.

- [x] **Step 2: Verify the change is documentation-only and whitespace-clean**

Run:

```powershell
git diff --check HEAD^..HEAD
git diff --name-only HEAD^..HEAD
```

Expected: no whitespace errors and only Markdown documentation changes on the completed branch.

- [x] **Step 3: Inspect final Git state**

Run:

```powershell
git status --short
```

Expected: clean worktree, excluding any explicitly preserved user-owned untracked files outside the
isolated worktree.
