# Vinh Record and Lab Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add contract-aligned, read-only detail pages for Clinical medical records and Lab tests.

**Architecture:** Each feature owns its typed gateway call and client detail component. Thin App Router pages validate UUIDs before rendering. Existing list components add links only where the currently aligned Gateway roles can follow them.

**Tech Stack:** Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, existing MediFlow API/auth/UI helpers.

---

### Task 1: Align wire types and APIs

**Files:**
- Modify: `frontend/src/features/medical-record/types.ts`
- Modify: `frontend/src/features/medical-record/api.ts`
- Modify: `frontend/src/features/lab/types.ts`
- Modify: `frontend/src/features/lab/api.ts`

- [ ] Add `MedicalRecordStatus`, `RecordDisposition`, and the four live completion fields to `MedicalRecordDTO`.
- [ ] Add `careContractVersion`, source/episode/clearance fields, and `resultVersion` to `LabTestDTO` with backend nullability.
- [ ] Add encoded `getById` calls for `/v1/records/{id}` and `/v1/lab/{id}`.
- [ ] Run `pnpm typecheck`; existing consumers must still compile against the additive types.

### Task 2: Medical-record detail slice

**Files:**
- Create: `frontend/src/features/medical-record/components/MedicalRecordDetail.tsx`
- Create: `frontend/src/app/(dashboard)/records/[recordId]/page.tsx`
- Modify: `frontend/src/features/medical-record/components/MedicalRecordTable.tsx`

- [ ] Fetch through `medicalRecordApi.getById`; redirect on 401, show a distinct 404 state, retry other failures with correlation ID, and cancel stale effect updates.
- [ ] Render exact IDs, status/disposition, symptoms, dates, diagnoses, appointment reference and completion information without cross-feature requests.
- [ ] Validate the route UUID before any API call; gate ADMIN/DOCTOR/NURSE.
- [ ] Link record IDs from the patient result table.
- [ ] Run `pnpm typecheck` and `pnpm lint`.

### Task 3: Lab detail slice

**Files:**
- Create: `frontend/src/features/lab/components/LabDetail.tsx`
- Create: `frontend/src/app/(dashboard)/lab/[testId]/page.tsx`
- Modify: `frontend/src/features/lab/components/LabTable.tsx`

- [ ] Fetch through `labApi.getById` with the same 401/404/retry/stale-effect behavior.
- [ ] Render lifecycle and payment as separate badges; show exact episode, source order, clearance, results, reference ranges, conclusion and audit dates.
- [ ] Validate the route UUID before any request. Until the Gateway handoff closes, gate detail to ADMIN/DOCTOR/NURSE and show list links only for those roles.
- [ ] Preserve nulls as unavailable and never derive abnormality from `COMPLETED` or compare textual result values.
- [ ] Run `pnpm typecheck`, `pnpm lint`, and `pnpm build`; then run `git diff --check`.

### Task 4: Documentation and delivery

**Files:**
- Modify: `frontend/docs/frontend-workboard.md`
- Modify: `frontend/docs/services/clinical.md`
- Modify: `frontend/docs/services/lab.md`

- [ ] Mark only the two detail slices complete; leave mutations and the Gateway LAB_TECH handoff open.
- [ ] Commit with the configured Harori identity and no co-author trailer.
- [ ] Push `codex/vinh-record-lab-details`, open a focused PR, enable auto-merge, wait for required checks, sync master, and remove the merged branch.
