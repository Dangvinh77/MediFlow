# Frontend Service Base Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every MediFlow bounded context a consistent authenticated read-only or lookup base page backed by current gateway contracts, while preserving the existing Pharmacy implementation.

**Architecture:** Thin App Router pages compose a shared role gate and one bounded-context component. Each feature owns exact DTOs and a typed API facade built on `src/lib/api.ts`; shared UI covers navigation, async states, pagination, formatting, and UUID validation. Patient remains explicitly spec-backed because its backend controller is absent.

**Tech Stack:** Next.js 16 App Router, React 19, TypeScript 5, Tailwind CSS 4, native fetch through the existing API wrapper, existing `@mediflow/loader` workspace package.

---

## Guardrails and contract snapshot

- Read `docs/superpowers/specs/2026-09-20-frontend-service-base-pages-design.md` before implementation.
- Do not modify:
  - `frontend/src/features/pharmacy/**`
  - `frontend/src/app/(dashboard)/pharmacy/**`
  - `frontend/docs/pharmacy-frontend-implementation-plan.md`
- Do not add dependencies, backend code, raw `fetch`, hard-coded service ports, mutation workflows, charts, or cross-feature imports.
- Patient's `/v1/patients` contract comes from `backend/patient-service/patient.http` and `docs/eproject_general_plan/backend-spec/02-patient.md`; both must remain labeled as non-live until a controller exists.
- Before each commit, inspect `git diff --name-only` and keep changes inside that task's files.
- Commit with the current human Git identity only. Do not add `Co-Authored-By` or AI trailers.

### Live read contracts used

| Context | Method and feature path | Roles | Response |
|---|---|---|---|
| Organization | `GET /v1/org/departments?activeOnly=true` | ADMIN, MANAGER, DOCTOR, NURSE | `DepartmentResponse[]` |
| Organization | `GET /v1/org/staff?page=0&size=20` | ADMIN, MANAGER, DOCTOR, NURSE | `PageResult<StaffResponse>` |
| Appointment | `GET /v1/appointments?departmentId&appointmentDate&page&size` | ADMIN, MANAGER, DOCTOR, NURSE | `PageResult<AppointmentDTO>` |
| Medical record | `GET /v1/records/patient/{patientId}` | ADMIN, DOCTOR, NURSE | `MedicalRecordDTO[]` |
| Lab | `GET /v1/lab?departmentId&status&page&size` | ADMIN, MANAGER, LAB_TECH | `PageResult<LabTestDTO>` |
| Billing | `GET /v1/billing/patient/{patientId}?page=0&size=20` | ADMIN, CASHIER | `PageResult<InvoiceDTO>` |
| Notification | `GET /v1/notifications/patient/{patientId}?page=0&size=20` | ADMIN, NURSE, PATIENT | `PageResult<NotificationDTO>` |
| Report | `GET /v1/reports/daily?date&departmentId` | ADMIN, MANAGER | `DailyReportDTO` |

## File map

### Shared files

- Create `frontend/src/components/auth/RoleGate.tsx`: role-filtered rendering and forbidden state.
- Create `frontend/src/components/ui/AsyncState.tsx`: loading, error/retry, empty, and idle messages.
- Create `frontend/src/lib/format.ts`: `LocalDate`, instant, and decimal display helpers.
- Create `frontend/src/lib/validation.ts`: UUID validation.
- Modify `frontend/src/components/layout/DashboardHeader.tsx`: complete role-aware responsive navigation.
- Modify `frontend/src/components/ui/Pagination.tsx`: optional total count and accessible loading state.

### Feature files

```text
frontend/src/features/organization/{api.ts,types.ts,components/DepartmentList.tsx,components/StaffTable.tsx}
frontend/src/features/billing/{api.ts,types.ts,components/InvoiceLookup.tsx}
frontend/src/features/notification/{api.ts,types.ts,components/NotificationLookup.tsx}
frontend/src/features/report/{api.ts,types.ts,components/DailyReportView.tsx}
```

Existing Patient, Appointment, Medical Record, and Lab files are refined in place. New routes are:

```text
frontend/src/app/(dashboard)/organization/page.tsx
frontend/src/app/(dashboard)/billing/page.tsx
frontend/src/app/(dashboard)/notifications/page.tsx
frontend/src/app/(dashboard)/reports/page.tsx
```

---

### Task 1: Add the shared page-state and role-aware shell

**Files:**
- Create: `frontend/src/components/auth/RoleGate.tsx`
- Create: `frontend/src/components/ui/AsyncState.tsx`
- Create: `frontend/src/lib/format.ts`
- Create: `frontend/src/lib/validation.ts`
- Modify: `frontend/src/components/layout/DashboardHeader.tsx`
- Modify: `frontend/src/components/ui/Pagination.tsx`

- [ ] **Step 1: Add `RoleGate` without changing backend authorization semantics**

Expose this contract:

```ts
interface RoleGateProps {
  allowed: readonly Role[];
  children: ReactNode;
}
```

The client component reads `getRole()`. If the role is allowed, render `children`; otherwise render a bordered `role="alert"` section saying the account cannot access the page. Do not redirect a known but disallowed role to login, and do not mount `children` in that branch.

- [ ] **Step 2: Add a shared async state component**

Use a discriminated union so impossible combinations cannot be passed:

```ts
type AsyncStateProps =
  | { kind: "loading"; message: string }
  | { kind: "idle" | "empty"; message: string }
  | { kind: "error"; message: string; correlationId?: string | null; onRetry: () => void };
```

Loading uses `MediFlowLoader` inside `role="status"`/`aria-busy="true"`; error uses `role="alert"`, keeps the message, conditionally shows correlation ID, and supplies a “Thử lại” button. Keep all classes semantic (`surface`, `border`, `danger`, `muted-foreground`).

- [ ] **Step 3: Add display and validation helpers**

`formatLocalDate("2026-09-20")` returns `20/09/2026` without constructing a timezone-shifted date. `formatInstant` uses `Intl.DateTimeFormat("vi-VN")`. `formatDecimal` accepts the JSON decimal string and uses `Intl.NumberFormat("vi-VN", { maximumFractionDigits: 20 })`; it must not add a currency. `isUuid` validates the canonical 8-4-4-4-12 shape case-insensitively.

- [ ] **Step 4: Replace the header's fixed navigation with the role matrix**

Use exactly these links:

```ts
const navigation = [
  { href: "/organization", label: "Tổ chức", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/patients", label: "Bệnh nhân", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/appointments", label: "Lịch hẹn", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/records", label: "Hồ sơ", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/lab", label: "Xét nghiệm", roles: ["ADMIN", "MANAGER", "LAB_TECH"] },
  { href: "/pharmacy", label: "Dược", roles: ["ADMIN", "DOCTOR", "PHARMACIST"] },
  { href: "/billing", label: "Viện phí", roles: ["ADMIN", "CASHIER"] },
  { href: "/notifications", label: "Thông báo", roles: ["ADMIN", "NURSE", "PATIENT"] },
  { href: "/reports", label: "Báo cáo", roles: ["ADMIN", "MANAGER"] },
] satisfies readonly NavigationItem[];
```

Filter with `item.roles.includes(role)`. Keep Pharmacy as one normal shared navigation entry and do not import any Pharmacy module. Add a mobile menu button with `aria-expanded` and `aria-controls`; close it after a link is selected. Keep desktop navigation visible at `lg` and make mobile controls at least 48px high.

- [ ] **Step 5: Extend pagination without breaking existing callers**

Add optional `totalElements?: number` and render “Trang X/Y · N kết quả” when present. Preserve zero-based `page`, disable boundary/loading buttons, and set `aria-disabled` through the native `disabled` state.

- [ ] **Step 6: Validate and commit**

Run:

```powershell
cd frontend
pnpm lint
pnpm exec next typegen
```

Expected: both exit `0`. Typecheck may still show the known stale `.next/dev/types` reference until the final clean verification.

Commit:

```text
feat(frontend): add shared service page foundation
```

---

### Task 2: Add the Organization base page

**Files:**
- Create: `frontend/src/features/organization/types.ts`
- Create: `frontend/src/features/organization/api.ts`
- Create: `frontend/src/features/organization/components/DepartmentList.tsx`
- Create: `frontend/src/features/organization/components/StaffTable.tsx`
- Create: `frontend/src/app/(dashboard)/organization/page.tsx`
- Delete: `frontend/src/features/organization/components/.gitkeep`

- [ ] **Step 1: Mirror the live response DTOs**

Copy every field from `DepartmentResponse`, `StaffResponse`, `DepartmentType`, and `JobTitle`. Nullable Java fields must be `string | null`; timestamps remain strings. Do not add Account DTOs because no account read endpoint exists.

- [ ] **Step 2: Implement the bounded-context API facade**

```ts
export const organizationApi = {
  departments: (activeOnly = true) =>
    api.get<DepartmentDTO[]>(`/v1/org/departments?activeOnly=${activeOnly}`),
  staff: (page = 0, size = 20) =>
    api.get<PageResult<StaffDTO>>(`/v1/org/staff?page=${page}&size=${size}`),
};
```

- [ ] **Step 3: Implement independent department and staff sections**

`DepartmentList` loads active departments once and displays department name, abbreviation, translated type, location, and active badge. `StaffTable` loads a page and displays name, translated job title, specialization, department UUID, and active badge with shared pagination.

Each section must independently render loading, API error with correlation ID/retry, empty, and success. A failed department request must not hide staff and vice versa.

- [ ] **Step 4: Compose the thin route**

Set metadata title `Tổ chức | MediFlow`. Wrap both components in `PageShell` and one `RoleGate` allowed for `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE`. The route contains no request logic.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint`, `pnpm exec next typegen`, and `pnpm typecheck`. Expected: new Organization files have no diagnostics; if only the pre-existing stale `/patients` generated-type error remains, record it and continue to the final clean gate.

Commit:

```text
feat(frontend): add organization base page
```

---

### Task 3: Standardize the spec-backed Patient base page

**Files:**
- Modify: `frontend/src/app/(dashboard)/patients/page.tsx`
- Modify: `frontend/src/features/patient/components/PatientTable.tsx`
- Review only: `frontend/src/features/patient/api.ts`
- Review only: `frontend/src/features/patient/types.ts`

- [ ] **Step 1: Keep the existing documented wire contract unchanged**

Confirm `PatientDTO` still matches `backend-spec/02-patient.md`: `maBenhNhan`, `hoTen`, `ngaySinh`, `gioiTinh`, `soCmnd`, nullable contact/BHYT fields, and timestamps. Do not claim it is verified against live Java code.

- [ ] **Step 2: Add a labeled keyword search**

Use the existing `patientApi.search({ keyword, page, size })`. Separate draft `keyword` from `activeKeyword`; submit/reset goes to page zero, pagination retains `activeKeyword`, and retry repeats the last applied request.

- [ ] **Step 3: Use shared states and formatting**

Replace plain loading/error/empty paragraphs with `AsyncState`. Show correlation ID from `ApiRequestError`. Use `formatLocalDate` for date of birth and pass `totalElements` to `Pagination`. Preserve the search field when the absent service yields an error.

- [ ] **Step 4: Add page-level access**

Wrap `PatientTable` in `RoleGate` for `ADMIN`, `DOCTOR`, `NURSE`. Do not add create/edit/delete actions.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint` and `pnpm exec next typegen`.

Commit:

```text
refactor(frontend): standardize patient base page
```

---

### Task 4: Standardize Appointment and Medical Record base pages

**Files:**
- Modify: `frontend/src/app/(dashboard)/appointments/page.tsx`
- Modify: `frontend/src/app/(dashboard)/records/page.tsx`
- Modify: `frontend/src/features/appointment/components/AppointmentTable.tsx`
- Modify: `frontend/src/features/medical-record/components/MedicalRecordTable.tsx`
- Review only: `frontend/src/features/appointment/{api.ts,types.ts}`
- Review only: `frontend/src/features/medical-record/{api.ts,types.ts}`

- [ ] **Step 1: Preserve the current live API calls**

Do not add endpoints. Appointment remains paged search by optional department/date; Medical Record remains `GET /v1/records/patient/{patientId}`.

- [ ] **Step 2: Harden Appointment filters and display**

Validate non-empty department ID as UUID before calling. Keep draft/applied filters, reset page on submit, retry the applied request, use `formatLocalDate`, and pass total results to pagination. Map statuses:

```ts
PENDING   -> { label: "Chờ tiếp nhận", tone: "warning" }
ARRIVED   -> { label: "Đã đến", tone: "info" }
CANCELLED -> { label: "Đã hủy", tone: "neutral" }
```

Unknown values display “Không xác định” with neutral tone. Use `AsyncState` for loading/error/empty.

- [ ] **Step 3: Harden Medical Record lookup**

Keep the idle state before first search. Reject invalid UUID locally with a visible form error and no API call. Preserve the patient ID after API errors, show correlation ID/retry, format examination date, and retain all diagnosis text without truncating it into a tooltip.

- [ ] **Step 4: Apply route role gates**

- Appointment: `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE`.
- Medical Record: `ADMIN`, `DOCTOR`, `NURSE`.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint` and `pnpm exec next typegen`.

Commit:

```text
refactor(frontend): standardize clinical base pages
```

---

### Task 5: Standardize the Lab base page

**Files:**
- Modify: `frontend/src/app/(dashboard)/lab/page.tsx`
- Modify: `frontend/src/features/lab/components/LabTable.tsx`
- Review only: `frontend/src/features/lab/{api.ts,types.ts}`

- [ ] **Step 1: Keep the queue contract and roles exact**

Retain `GET /v1/lab?departmentId&status&page&size` and gate the route to `ADMIN`, `MANAGER`, `LAB_TECH`. Do not expose the queue to Doctor/Nurse merely because they can read individual tests through different endpoints.

- [ ] **Step 2: Standardize filters and request states**

Validate non-empty department UUID, keep draft/applied filters, reset to page zero on submit, and retry with the applied filter. Use `AsyncState` and pass `totalElements` to pagination.

- [ ] **Step 3: Render independent status meanings**

Use Vietnamese lifecycle badges (`PENDING` warning, `IN_PROGRESS` info, `COMPLETED` success, `CANCELLED` neutral) and a separate paid/unpaid badge. Do not infer whether a result is clinically normal.

- [ ] **Step 4: Validate and commit**

Run `pnpm lint` and `pnpm exec next typegen`.

Commit:

```text
refactor(frontend): standardize lab base page
```

---

### Task 6: Add the Billing invoice lookup base page

**Files:**
- Create: `frontend/src/features/billing/types.ts`
- Create: `frontend/src/features/billing/api.ts`
- Create: `frontend/src/features/billing/components/InvoiceLookup.tsx`
- Create: `frontend/src/app/(dashboard)/billing/page.tsx`
- Delete: `frontend/src/features/billing/components/.gitkeep`

- [ ] **Step 1: Mirror Billing response fields and enums**

Define `FeeDTO`, `InvoiceDTO`, `FeeType`, `PaymentMethod`, and `SagaStatus` exactly from the Java records. Model `BigDecimal` JSON fields as `string`; use `isPaid` exactly as named in the DTO contract. Nullable payment/prescription/paid timestamp fields use `| null`.

- [ ] **Step 2: Implement only the patient invoice query**

```ts
export const billingApi = {
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<InvoiceDTO>>(
      `/v1/billing/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
};
```

- [ ] **Step 3: Build the lookup component**

Start idle. Require a valid patient UUID, preserve it across errors, and render invoices with date, invoice ID, `formatDecimal(totalAmount)`, paid badge, translated payment method, and translated saga status. Do not add fee expansion, create invoice, payment, or revenue queries.

- [ ] **Step 4: Compose and gate the route**

Use `PageShell` and `RoleGate` for `ADMIN`, `CASHIER`; set metadata `Viện phí | MediFlow`.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint`, `pnpm exec next typegen`, and `pnpm typecheck` with the same stale-generated-type caveat until the final clean gate.

Commit:

```text
feat(frontend): add billing invoice lookup
```

---

### Task 7: Add the Notification lookup base page

**Files:**
- Create: `frontend/src/features/notification/types.ts`
- Create: `frontend/src/features/notification/api.ts`
- Create: `frontend/src/features/notification/components/NotificationLookup.tsx`
- Create: `frontend/src/app/(dashboard)/notifications/page.tsx`
- Delete: `frontend/src/features/notification/components/.gitkeep`

- [ ] **Step 1: Mirror the live DTO and enums**

Define `NotificationDTO`, `NotificationChannel`, and `NotificationStatus` from live Java source. Include no `recipientAddress` or `retryCount` because the response intentionally omits them.

- [ ] **Step 2: Add the paged patient query**

```ts
export const notificationApi = {
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<NotificationDTO>>(
      `/v1/notifications/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
};
```

- [ ] **Step 3: Build the lookup component**

Validate the patient UUID before calling. Render title, channel label, delivery status badge, created/sent times, and failure reason. Keep content readable in a wrapping cell. Handle idle/loading/error/empty/success and retry the last lookup.

For `PATIENT`, do not prefill or decode a token subject in this task. Backend ownership remains authoritative when the typed ID is submitted.

- [ ] **Step 4: Compose and gate the route**

Use `RoleGate` for `ADMIN`, `NURSE`, `PATIENT`; set metadata `Thông báo | MediFlow`. Do not add notification sending.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint` and `pnpm exec next typegen`.

Commit:

```text
feat(frontend): add notification lookup page
```

---

### Task 8: Add the daily Report base page

**Files:**
- Create: `frontend/src/features/report/types.ts`
- Create: `frontend/src/features/report/api.ts`
- Create: `frontend/src/features/report/components/DailyReportView.tsx`
- Create: `frontend/src/app/(dashboard)/reports/page.tsx`
- Delete: `frontend/src/features/report/components/.gitkeep`

- [ ] **Step 1: Mirror the daily DTO**

Define `DailyReportDTO` with `reportDate`, nullable `departmentId`, numeric visit/lab/prescription counts, and string revenue.

- [ ] **Step 2: Implement the exact daily query**

```ts
export const reportApi = {
  daily: (date: string, departmentId?: string) => {
    const query = new URLSearchParams({ date });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<DailyReportDTO>(`/v1/reports/daily?${query}`);
  },
};
```

- [ ] **Step 3: Build the daily report view**

Require a date, validate optional department UUID, and submit explicitly. Render report date/scope and four labeled metrics: visits, lab tests, prescriptions, and exact decimal revenue. A valid all-zero report is success, not empty. Preserve filters on error and offer retry.

- [ ] **Step 4: Compose and gate the route**

Use `RoleGate` for `ADMIN`, `MANAGER`; set metadata `Báo cáo | MediFlow`. Do not add monthly/top-medicine queries, charts, export, or a currency label.

- [ ] **Step 5: Validate and commit**

Run `pnpm lint`, `pnpm exec next typegen`, and `pnpm typecheck` with the same stale-generated-type caveat until the final clean gate.

Commit:

```text
feat(frontend): add daily report base page
```

---

### Task 9: Run the clean quality gate and Pharmacy safety review

**Files:**
- Review all frontend changes against the branch base.
- Do not modify Pharmacy files to satisfy this task.

- [ ] **Step 1: Verify the protected Pharmacy paths have no diff**

Run from repository root:

```powershell
git diff --name-only -- 'frontend/src/features/pharmacy/**' 'frontend/src/app/(dashboard)/pharmacy/**' 'frontend/docs/pharmacy-frontend-implementation-plan.md'
```

Expected: no output.

- [ ] **Step 2: Regenerate Next artifacts from an exact, verified directory**

Stop any frontend dev server. From `frontend/`, resolve and remove only the ignored `.next` build directory:

```powershell
$frontendRoot = (Resolve-Path '.').Path
$nextBuildDir = Join-Path $frontendRoot '.next'
if ((Split-Path $nextBuildDir -Parent) -ne $frontendRoot) { throw 'Unsafe .next path' }
if (Test-Path -LiteralPath $nextBuildDir) { Remove-Item -LiteralPath $nextBuildDir -Recurse -Force }
pnpm exec next typegen
```

Expected: type generation exits `0` and contains no stale `src/app/patients/page.tsx` import.

- [ ] **Step 3: Run the required frontend gates**

```powershell
pnpm typecheck
pnpm lint
pnpm build
```

Expected: all exit `0`.

- [ ] **Step 4: Run architecture scans**

From repository root:

```powershell
rg -n "fetch\(" frontend/src --glob '*.ts' --glob '*.tsx'
rg -n "localhost:808[1-8]|http://.*:808[1-8]" frontend/src
rg -n 'from "@/features/' frontend/src/features
git diff --check
```

Expected:

- `fetch(` appears only in `frontend/src/lib/api.ts`;
- no direct business-service port appears;
- no feature imports another feature;
- `git diff --check` prints nothing.

- [ ] **Step 5: Perform the manual state/role/responsive matrix**

With gateway and available services running, verify:

1. ADMIN sees all service links; each new live page reaches the gateway and shows loading then success/empty.
2. MANAGER sees Organization, Appointments, Lab, Reports; it does not see Billing/Notifications/Pharmacy.
3. CASHIER sees Billing only among business links; LAB_TECH sees Lab; PATIENT sees Notifications; PHARMACIST retains the existing Pharmacy flow.
4. A direct disallowed route shows the forbidden state and does not issue the feature request.
5. Invalid UUID stays client-side; valid lookup errors preserve input and show retry/correlation ID when present.
6. Tables scroll inside their container at 320px width; mobile navigation opens/closes by keyboard; focus remains visible.
7. Patient displays the real unavailable/error state until its backend is restored; do not mark Patient integration as passed.

- [ ] **Step 6: Commit final integration fixes only if this task changed shared non-Pharmacy files**

If verification required a legitimate source fix, commit it separately:

```text
fix(frontend): complete service page integration
```

If no source fix was needed, do not create an empty commit.

## Completion report

The implementer must report:

- changed files grouped by shared/context;
- exact endpoints and DTO source used;
- typecheck/lint/build and scan results;
- manual role/state/responsive results;
- confirmation that protected Pharmacy paths have no diff;
- Patient backend, Notification identity, and currency-contract blockers that remain.
