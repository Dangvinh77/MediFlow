# Clinical/Lab Web Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add read-only Clinical/Lab web screens whose TypeScript contracts match the implemented backend DTOs.

**Architecture:** Each bounded context owns its DTOs, gateway API wrapper, and table component. Thin App Router pages compose those components under a shared authenticated dashboard layout. Shared documentation describes field naming as a per-service wire contract.

**Tech Stack:** Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, existing native-fetch wrapper.

---

### Task 1: Correct the shared naming guidance

**Files:**
- Modify: `docs/ai/05-api-conventions.md`
- Modify: `docs/ai/12-frontend.md`
- Modify: `frontend/AGENTS.md`
- Modify: `mobile/AGENTS.md`
- Modify: `frontend/src/lib/types.ts`

- [ ] State that consumers copy the exact Java/wire field names for each service.
- [ ] Keep Patient examples in Vietnamese camelCase and document Clinical/Lab as English camelCase.
- [ ] Export `PageResult<T>` as the canonical page type while retaining `Page<T>` as a compatibility alias.
- [ ] Run `pnpm typecheck` and expect exit code 0.
- [ ] Commit with `docs(api): align frontend field naming contracts`.

### Task 2: Add the authenticated dashboard shell

**Files:**
- Create: `frontend/src/app/(dashboard)/layout.tsx`
- Modify: `frontend/src/app/page.tsx`

- [ ] Create a client layout that checks `isAuthenticated()`, redirects to `/login`, and renders navigation for `/appointments`, `/records`, and `/lab` plus logout.
- [ ] Add matching links on the home page so the new routes are discoverable.
- [ ] Run `pnpm typecheck` and expect exit code 0.
- [ ] Commit with `feat(frontend): add clinical lab dashboard shell`.

### Task 3: Add typed Clinical/Lab features

**Files:**
- Create: `frontend/src/features/appointment/types.ts`
- Create: `frontend/src/features/appointment/api.ts`
- Create: `frontend/src/features/appointment/components/AppointmentTable.tsx`
- Create: `frontend/src/features/medical-record/types.ts`
- Create: `frontend/src/features/medical-record/api.ts`
- Create: `frontend/src/features/medical-record/components/MedicalRecordTable.tsx`
- Create: `frontend/src/features/lab/types.ts`
- Create: `frontend/src/features/lab/api.ts`
- Create: `frontend/src/features/lab/components/LabTable.tsx`
- Create: `frontend/src/app/(dashboard)/appointments/page.tsx`
- Create: `frontend/src/app/(dashboard)/records/page.tsx`
- Create: `frontend/src/app/(dashboard)/lab/page.tsx`

- [ ] Copy response fields exactly from `AppointmentDTO`, `MedicalRecordDTO`, `DiagnosisDTO`, `LabTestDTO`, and `LabResultDTO`.
- [ ] Implement `appointmentApi.search(page, size)` with `/v1/appointments`.
- [ ] Implement `medicalRecordApi.byPatient(patientId)` with `/v1/records/patient/{patientId}`.
- [ ] Implement `labApi.search(page, size)` with `/v1/lab`.
- [ ] Keep `fetch` usage inside `src/lib/api.ts`; feature API files call only `api.get`.
- [ ] Render loading, error, and empty states in each feature component.
- [ ] Keep route pages thin: each page returns its feature table component.
- [ ] Run `pnpm typecheck` and expect exit code 0.
- [ ] Run `pnpm lint` and expect exit code 0.
- [ ] Commit with `feat(frontend): add clinical lab read views`.

### Task 4: Verify and publish

**Files:**
- Review all files changed from `origin/master`.

- [ ] Run `pnpm build` and expect all routes to compile successfully.
- [ ] Run `git diff --check` and expect no output.
- [ ] Review gateway paths, feature boundaries, exact DTO names, auth behavior, and mobile exclusion.
- [ ] Push `codex/clinical-lab-web-foundation`.
- [ ] Open a focused pull request targeting `master` with validation results and current backend blockers.
