# Frontend Service Base Pages Design

**Date:** 2026-09-20  
**Branch inspected:** `codex/frontend-service-base-pages`  
**Scope:** authenticated web base pages for Organization, Patient, Clinical, Lab, Pharmacy, Billing, Notification, and Report.

## 1. Purpose

MediFlow needs a consistent, usable entry page for every bounded context without trying to finish each service's full workflow in one change. This design adds one contract-backed read flow per service, standardizes the existing Patient/Clinical/Lab pages, and makes the dashboard navigation match backend roles.

The pages are foundations. They expose the smallest useful read capability that the current controllers support and leave create/update/delete, lifecycle transitions, dialogs, detail routes, charts, and cross-service enrichment to later tasks.

## 2. Sources of truth and inspected state

The design follows, in order:

1. live controller and DTO source under `backend/*-service/src/main/java`;
2. `docs/ai/12-frontend.md` and `docs/ai/05-api-conventions.md`;
3. `docs/DESIGN.md` and the semantic tokens already implemented in `frontend/src/app/globals.css`;
4. backend specs only when live source is absent, which currently applies to Patient.

The requested path `docs/design/DESIGN.md` does not exist. The repository-wide design system is `docs/DESIGN.md`.

### Current frontend inventory

| Context | Current route and feature state | Contract state | Decision |
|---|---|---|---|
| Organization | `features/organization/components/.gitkeep`; no route | Live department and staff read controllers | Add `/organization` with active departments and paged staff |
| Patient | `/patients`, API/types/table exist | `patient-service` has no controller or DTO source; `patient.http` explicitly marks requests DEMO | Keep the spec-backed list, add keyword/state/role consistency, and document the backend blocker |
| Appointment | `/appointments`, typed paged table exists | Live and aligned with `AppointmentController`/`AppointmentDTO` | Standardize labels, status badges, validation, states, and access |
| Medical record | `/records`, patient UUID lookup exists | Live and aligned with `MedicalRecordController`/DTOs | Standardize validation, states, dates, and access |
| Lab | `/lab`, typed paged queue exists | Live and aligned with `LabController`/DTOs | Standardize labels, independent clinical/payment status, states, and access |
| Pharmacy | Deep route tree, APIs, permissions, components, and Huy's implementation plan exist | Live | Preserve all Pharmacy route and feature files exactly |
| Billing | Empty feature folder; no route | Live invoice-by-patient endpoint and DTOs | Add `/billing` with patient UUID lookup and paged invoices |
| Notification | Empty feature folder; no route | Live notification-by-patient endpoint and DTO | Add `/notifications` with patient UUID lookup and pagination |
| Report | Empty feature folder; no route | Three live read endpoints | Add `/reports` with the daily report query only |

The current frontend baseline has a clean Git worktree. `pnpm lint` passes. `pnpm typecheck` and `pnpm build` are blocked by a stale generated `.next/dev/types/validator.ts` reference to the old `src/app/patients/page.tsx`; a clean generated-types directory is required before final validation.

## 3. Options considered

### A. Contract-driven vertical slices — selected

Each service gets one real read flow: exact TypeScript DTOs, one feature API facade, one focused client component, and one thin route. Shared components cover access, async states, pagination, formatting, and responsive navigation.

This gives every context a working entry point while keeping the change reviewable and avoiding invented endpoints.

### B. Static service landing cards

This would create every route quickly, but most pages would not prove API wiring or state handling. It would postpone the main architectural risks and would not satisfy the gateway/API requirement.

### C. Full CRUD and workflow coverage

This would include staff/account administration, appointment transitions, lab results, billing payment, notification sending, and all report variants. It is too broad, mixes several owners' business workflows, and duplicates the separate Pharmacy plan.

## 4. Architecture

The existing feature-based boundary remains unchanged:

```text
app route
  -> shared RoleGate
  -> feature component
  -> feature api.ts
  -> lib/api.ts
  -> same-origin /api rewrite
  -> gateway
  -> owning service
```

- `app/(dashboard)/**/page.tsx` owns metadata, page title, route composition, and the allowed role list.
- `features/<context>/types.ts` mirrors only that service's wire DTOs.
- `features/<context>/api.ts` builds only that context's `/v1/*` paths and calls `api.get`.
- `features/<context>/components/*` owns filters, request lifecycle, tables, and retry.
- `components/auth/RoleGate.tsx` prevents a disallowed role from mounting a data-fetching child and shows a forbidden message. It is UX only; controllers remain authoritative.
- `components/ui/AsyncState.tsx` renders accessible loading, error/retry, and empty states with the existing MediFlow loader and semantic colors.
- `lib/format.ts` handles display-only dates and decimal values. Payload strings remain unchanged.
- `lib/validation.ts` supplies UUID validation for lookup/filter forms.

No feature imports another feature. Organization may combine departments and staff because they belong to the same bounded context. No page calls `fetch`, a service port, or a foreign feature API.

## 5. Route, role, and contract matrix

| Web route | Roles shown/allowed by UI | Live request used by the base page | Result |
|---|---|---|---|
| `/organization` | `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE` | `GET /v1/org/departments?activeOnly=true`; `GET /v1/org/staff?page&size` | `DepartmentResponse[]`; `PageResult<StaffResponse>` |
| `/patients` | `ADMIN`, `DOCTOR`, `NURSE` | `GET /v1/patients?keyword&page&size` | Spec-backed `PageResult<PatientDTO>`; backend not live |
| `/appointments` | `ADMIN`, `MANAGER`, `DOCTOR`, `NURSE` | `GET /v1/appointments?departmentId&appointmentDate&page&size` | `PageResult<AppointmentDTO>` |
| `/records` | `ADMIN`, `DOCTOR`, `NURSE` | `GET /v1/records/patient/{patientId}` | `MedicalRecordDTO[]` |
| `/lab` | `ADMIN`, `MANAGER`, `LAB_TECH` | `GET /v1/lab?departmentId&status&page&size` | `PageResult<LabTestDTO>` |
| `/pharmacy` | `ADMIN`, `DOCTOR`, `PHARMACIST` | Existing Pharmacy requests | Existing behavior |
| `/billing` | `ADMIN`, `CASHIER` | `GET /v1/billing/patient/{patientId}?page&size` | `PageResult<InvoiceDTO>` |
| `/notifications` | `ADMIN`, `NURSE`, `PATIENT` | `GET /v1/notifications/patient/{patientId}?page&size` | `PageResult<NotificationDTO>` |
| `/reports` | `ADMIN`, `MANAGER` | `GET /v1/reports/daily?date&departmentId` | `DailyReportDTO` |

The Lab page deliberately exposes the queue endpoint only to its controller roles. Doctor/Nurse detail and patient-specific Lab reads remain future workflows. The Report page starts with the daily endpoint; monthly and top-medicine reports remain future extensions. Billing payment and Notification send are mutations and remain out of scope.

### Navigation behavior

`DashboardHeader` uses the matrix above as a single role-to-link source. Desktop shows the permitted links in a wrapping navigation area. Below the desktop breakpoint it shows a keyboard-operable menu button with `aria-expanded`, a named navigation region, 48px touch targets, and the same filtered links. The role name, theme control, and logout remain visible.

Direct URLs still render `RoleGate`. A disallowed role sees a forbidden state and no feature component is mounted, so no avoidable request is sent. A backend `403` remains possible and is rendered as a permission error rather than a login failure. A backend `401` clears/ends the invalid session and returns to login through the existing auth boundary behavior.

## 6. Feature designs

### Organization

The route displays two independent sections:

- active departments, showing name, abbreviation, type, location, and active status;
- paged staff, showing full name, job title, specialization, department UUID, and active status.

Each section owns its loading/error/empty state so one failed request does not hide the other. The page does not create accounts, list accounts (no live GET endpoint exists), edit staff, or translate department UUIDs through another service.

Exact response fields:

```ts
type DepartmentType = "CLINICAL" | "PARACLINICAL" | "ADMINISTRATIVE";
type JobTitle = "DOCTOR" | "NURSE" | "TECHNICIAN" | "PHARMACIST" | "CASHIER" | "MANAGER" | "ADMINISTRATIVE";

interface DepartmentDTO {
  departmentId: string;
  departmentName: string;
  abbreviation: string;
  departmentType: DepartmentType;
  departmentHeadId: string | null;
  location: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface StaffDTO {
  staffId: string;
  fullName: string;
  departmentId: string;
  jobTitle: JobTitle;
  specialization: string | null;
  licenseNumber: string | null;
  phoneNumber: string | null;
  email: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
```

### Patient

The existing list gains a labeled keyword filter and consistent pagination/state handling. `PatientDTO` remains the exact record from `docs/eproject_general_plan/backend-spec/02-patient.md` because no live Java DTO exists. The UI does not add detail/create/update/delete behavior.

If the missing service returns `404`, `502`, or is unavailable, the page shows the API error with retry and preserves the keyword. The product UI does not claim data exists when the backend does not.

### Clinical: appointments and medical records

The Appointment list keeps the live department/date filters. Status values render as Vietnamese labels through `StatusBadge`: `PENDING` warning, `ARRIVED` info, `CANCELLED` neutral; unknown runtime values render “Không xác định”. Dates display as `dd/MM/yyyy`, time remains `HH:mm`.

Medical Records remains an explicit patient UUID lookup. The form validates UUID before requesting and preserves the input after error. Results show examination date, record/doctor IDs, symptoms, and diagnoses. No create/update/diagnosis action is added.

### Lab

The existing queue keeps department and status filters. Lifecycle status and payment are separate badges; a completed test is never interpreted as a normal result. The page does not create requests, record results, transition status, or infer abnormal values from text/reference ranges.

### Pharmacy

All files under these paths are protected:

```text
frontend/src/features/pharmacy/**
frontend/src/app/(dashboard)/pharmacy/**
frontend/docs/pharmacy-frontend-implementation-plan.md
```

This work only retains/adds the Pharmacy entry in shared dashboard navigation. It does not refactor Pharmacy to the new shared state component, change its permissions, routes, types, presentation helpers, or placeholders.

### Billing

The page asks for a patient UUID and returns that patient's invoices with pagination. Rows show created date, invoice ID, exact decimal `totalAmount`, paid state, payment method, and saga status. The backend DTO carries no currency code, so the base page does not invent one. Fee details and payment actions remain future work.

```ts
type PaymentMethod = "CASH" | "TRANSFER" | "INSURANCE";
type SagaStatus = "NONE" | "AWAITING_PAYMENT" | "PAID" | "AWAITING_DISPENSE" | "COMPLETED" | "REFUNDED";
type FeeType = "EXAM" | "LAB" | "DRUG" | "SERVICE";

interface InvoiceDTO {
  invoiceId: string;
  patientId: string;
  createdDate: string;
  totalAmount: string;
  isPaid: boolean;
  paymentMethod: PaymentMethod | null;
  prescriptionId: string | null;
  sagaStatus: SagaStatus;
  paidAt: string | null;
  fees: FeeDTO[];
}
```

JSON `BigDecimal` values are modeled as `string` to avoid arithmetic or precision loss in this read-only page.

### Notification

The page asks for a patient UUID and renders a paged list with title, channel, delivery status, created/sent times, and failure reason. It never renders `recipientAddress` or retry count because `NotificationDTO` intentionally excludes them.

For `PATIENT`, the gateway login response does not expose account/patient ID separately and current frontend session code does not decode JWT subject. The base page therefore keeps an explicit patient UUID input; backend ownership enforcement rejects another patient's ID. Automatic “my notifications” requires a separately agreed identity contract/utility.

```ts
type NotificationChannel = "EMAIL" | "SMS" | "IN_APP";
type NotificationStatus = "PENDING" | "SENT" | "FAILED";

interface NotificationDTO {
  notificationId: string;
  patientId: string;
  title: string;
  content: string;
  channel: NotificationChannel;
  status: NotificationStatus;
  failureReason: string | null;
  createdAt: string;
  sentAt: string | null;
}
```

### Report

The base page displays a daily operational report using a required date and optional department UUID. It shows visit, lab, prescription counts and the exact decimal revenue. Empty/zero is a valid report and is rendered as zero metrics, not an empty state.

```ts
interface DailyReportDTO {
  reportDate: string;
  departmentId: string | null;
  visitCount: number;
  labCount: number;
  prescriptionCount: number;
  revenue: string;
}
```

Monthly drilldown, top medicines, charts, export, and revenue currency assumptions remain out of scope.

## 7. State, errors, and data flow

Every feature component follows the same observable state model:

```text
idle (lookup pages only)
  -> loading
  -> success with rows/data
  -> empty (valid response with no rows)
  -> error with retry
```

- Initial list pages load once after mount.
- Lookup pages remain idle until a valid UUID/date form is submitted.
- Filter input and applied filter state are separate; changing a field does not request until submit.
- A new filter resets page to zero. Pagination retains applied filters.
- Retry repeats the last applied request.
- Requests disable their own submit/pagination controls to avoid duplicates.
- `ApiRequestError.message` is displayed without stack traces. Correlation ID is shown when present.
- `401` ends the unusable session. `403` remains on the page as a permission state.
- No mutation uses optimistic state because mutations are outside this scope.

## 8. Visual, responsive, and accessibility rules

- Reuse current semantic Tailwind colors and dark-mode tokens; do not add a per-service palette or component library.
- Keep tables in bounded `overflow-x-auto` regions with `min-width`; the page itself must not scroll horizontally.
- Filters stack under `md`/`lg` breakpoints and labels remain visible above controls.
- Inputs and buttons have visible focus styles and at least 40px desktop/48px mobile targets where practical.
- Loading uses `role="status"`, errors use `role="alert"`, and retry has a descriptive accessible name.
- Tables use `scope="col"`; identifiers may use monospace but core clinical values remain at readable sizes.
- Enum color is always paired with Vietnamese text.
- Dates are display-formatted without timezone conversion for `LocalDate` values.
- Null is rendered as “—” or a specific “Chưa có” label; zero remains zero.

The design uses the existing semantic tokens rather than retheming `globals.css`. `docs/DESIGN.md` currently describes target values that differ from some implemented tokens; reconciling that global palette is a separate design-system migration.

## 9. Non-goals

- No backend edits, new endpoints, fake data, or direct service-port calls.
- No cross-feature imports or client-side joins across bounded contexts.
- No new component/data-fetching/test dependency.
- No CRUD forms, lifecycle mutations, billing payment, notification sending, report charts/export, or account administration.
- No JWT decoding/session schema change.
- No Pharmacy feature or route refactor.
- No attempt to make the missing Patient backend appear live.

## 10. Acceptance criteria

1. Every bounded context has a discoverable authenticated base route or, for Pharmacy, its existing route remains intact.
2. Dashboard links are filtered by the live controller roles in the route matrix.
3. Every new request passes through its feature API and `src/lib/api.ts` to `/api/v1/*`.
4. New DTOs match live Java DTO fields and enum values exactly; Patient is explicitly marked spec-backed.
5. Each page has accessible loading, error/retry, empty/idle, and success states appropriate to its query.
6. Existing Patient/Clinical/Lab pages use consistent status, formatting, validation, and pagination behavior.
7. Mobile navigation and all tables/filters remain usable at narrow widths.
8. No file in the protected Pharmacy paths changes.
9. `pnpm typecheck`, `pnpm lint`, and `pnpm build` pass from a clean generated Next state.
10. Static scans find raw `fetch(` only in `src/lib/api.ts`, no hard-coded backend ports, and no cross-feature imports.

## 11. Risks and blockers

- **Patient backend missing:** the page can only retain the documented spec contract until `patient-service` is rebuilt and its controller/DTO are verified.
- **Notification patient identity:** login returns role and tokens, not a separate patient ID; the base page cannot safely prefill “my notifications” without decoding/agreed session claims.
- **Money currency absent:** Billing/Report DTOs expose decimal amounts without a currency field. The UI must not label them VND unless the contract is amended or the product decision is documented.
- **Stale Next generated types:** the current `.next/dev/types` directory references a removed route and breaks typecheck/build. Final verification must regenerate from a clean `.next` after any dev server is stopped.
- **Global design-token drift:** `docs/DESIGN.md` target colors/fonts do not exactly match current `globals.css`. This work uses the implemented semantic tokens and does not silently retheme Pharmacy or the rest of the app.
