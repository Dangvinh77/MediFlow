# MediFlow Frontend Contract Workspaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the MediFlow web frontend for every currently supported bounded-context workflow while keeping blocked cross-service capabilities visibly unavailable instead of inventing contracts.

**Architecture:** Keep route pages thin and compose context-owned components from `frontend/src/features/<context>`. Every request goes through `frontend/src/lib/api.ts`; feature DTOs mirror live Java response/request records exactly. Shared shell and UI primitives provide consistent navigation, filters, forms, async states, responsive behavior, and role-aware affordances without becoming a security boundary.

**Tech Stack:** Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, pnpm, Spring Boot gateway contracts.

---

### Task 1: Shared clinical workspace shell

**Files:**
- Modify: `frontend/src/app/globals.css`
- Modify: `frontend/src/app/(dashboard)/layout.tsx`
- Modify: `frontend/src/components/layout/DashboardHeader.tsx`
- Modify: `frontend/src/components/layout/PageShell.tsx`
- Create: `frontend/src/components/layout/DashboardSidebar.tsx`
- Create: `frontend/src/components/ui/Button.tsx`
- Create: `frontend/src/components/ui/Field.tsx`
- Create: `frontend/src/components/ui/DataTableShell.tsx`

- [x] Replace the wrapping header-only layout with a responsive sidebar workspace and compact utility header.
- [x] Keep navigation filtered by the signed role and add the live Inpatient destination.
- [x] Implement shared button, field, and table-shell primitives using the tokens in `docs/DESIGN.md`.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit as `feat(frontend): establish shared clinical workspace`.

### Task 2: Vinh-owned Inpatient workspace

**Files:**
- Create: `frontend/src/features/inpatient/types.ts`
- Create: `frontend/src/features/inpatient/api.ts`
- Create: `frontend/src/features/inpatient/presentation.ts`
- Create: `frontend/src/features/inpatient/components/AdmissionTable.tsx`
- Create: `frontend/src/features/inpatient/components/AdmissionDetail.tsx`
- Create: `frontend/src/features/inpatient/components/BedTable.tsx`
- Create: `frontend/src/app/(dashboard)/inpatient/page.tsx`
- Create: `frontend/src/app/(dashboard)/inpatient/[admissionId]/page.tsx`
- Create: `frontend/src/app/(dashboard)/inpatient/beds/page.tsx`

- [x] Mirror the live admission search/detail and bed response records from Inpatient Java DTOs.
- [x] Add typed API calls for `GET /v1/inpatient/admissions`, `GET /v1/inpatient/admissions/{id}`, and `GET /v1/inpatient/beds`.
- [x] Render explicit loading, empty, error, retry, pagination, status, and UUID reference states.
- [x] Keep deposit, settlement, and Surgery controls absent while their handoffs remain active.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit as `feat(inpatient-ui): add admission and bed workspaces`.

### Task 3: Complete live Clinical and Lab commands

**Files:**
- Modify: `frontend/src/features/appointment/api.ts`
- Modify: `frontend/src/features/appointment/types.ts`
- Modify: `frontend/src/features/appointment/components/AppointmentDetail.tsx`
- Modify: `frontend/src/features/medical-record/api.ts`
- Modify: `frontend/src/features/medical-record/types.ts`
- Modify: `frontend/src/features/medical-record/components/MedicalRecordDetail.tsx`
- Modify: `frontend/src/features/lab/api.ts`
- Modify: `frontend/src/features/lab/types.ts`
- Modify: `frontend/src/features/lab/components/LabDetail.tsx`

- [x] Add the always-on Lab start, result, and cancel commands.
- [ ] Add check-in, examination start, record completion, and admission referral after `care-finance-v2` is enabled in the service and Docker runtime.
- [x] Gate affordances by backend roles and current lifecycle status while treating 403 as authoritative.
- [x] Keep financial clearance activation unavailable until Billing and Gateway handoffs pass.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit the available batch as `feat(lab-ui): complete live laboratory commands`.

### Task 4: Hoang Anh organization and patient administration

**Files:**
- Extend: `frontend/src/features/organization/**`
- Extend: `frontend/src/app/(dashboard)/organization/**`
- Extend: `frontend/src/features/patient/**`
- Extend: `frontend/src/app/(dashboard)/patients/**`

- [x] Mirror live patient DTOs and request validation.
- [x] Mirror live department, staff, and account DTOs and request validation.
- [x] Add department/staff detail and supported create/update flows.
- [x] Add patient detail plus create/update/delete with explicit destructive confirmation.
- [x] Preserve service-only lookup endpoints as non-UI APIs.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit the Patient batch as `feat(patient-ui): add patient administration workflow`.
- [x] Commit the Organization batch as `feat(organization-ui): add workforce administration`.

### Task 5: Loc billing and notification operations

**Files:**
- Extend: `frontend/src/features/billing/**`
- Extend: `frontend/src/app/(dashboard)/billing/**`
- Extend: `frontend/src/features/notification/**`
- Extend: `frontend/src/app/(dashboard)/notifications/**`

- [x] Add invoice detail, supported invoice creation/payment actions, and exact money/status presentation.
- [x] Add notification detail and manual send using privacy-safe response fields.
- [x] Do not expose unavailable refund/read-state controls or infer patient identity.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit as `feat(frontend): add billing and notification operations`.

### Task 6: Huy pharmacy completion and reporting

**Files:**
- Harden: `frontend/src/features/pharmacy/**`
- Harden: `frontend/src/app/(dashboard)/pharmacy/**`
- Extend: `frontend/src/features/report/**`
- Extend: `frontend/src/app/(dashboard)/reports/**`

- [x] Preserve the existing complete Pharmacy workflows and their explicit error/empty/terminal states.
- [x] Add monthly and top-medicine report views from always-on endpoints.
- [ ] Add operations-daily and surgery-operation reports after `care-finance-v2` is enabled in Report runtime.
- [x] Keep Surgery UI disabled until the Gateway default-off route and production authority handoff close.
- [x] Verify with `pnpm typecheck`, `pnpm lint`, and `pnpm build`.
- [x] Commit as `feat(report-ui): add monthly and medicine reports`.

### Task 7: Contract registry and release evidence

**Files:**
- Modify: `frontend/docs/frontend-workboard.md`
- Modify: `frontend/docs/services/*.md`

- [x] Record each shipped route and remaining external blocker against backend baseline `e7dab08`.
- [x] Run a final raw-fetch scan; only `frontend/src/lib/api.ts` calls `fetch`.
- [ ] Run `pnpm typecheck`, `pnpm lint`, and `pnpm build` from a clean tree.
- [x] Review the branch diff for feature-to-feature imports, guessed DTO fields, and role drift.

