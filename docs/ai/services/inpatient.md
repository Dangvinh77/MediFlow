# Service: inpatient (planned)

**Status:** approved bounded context, module not scaffolded
**Owner:** Vinh (`Dangvinh77` / `Harori`)
**Source of truth:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
**Planned module:** `backend/inpatient-service/` · **Port:** 8090 · **Database:** `mediflow_inpatient` · **Base path:** `/api/v1/inpatient`

## Bounded context

Owns admissions, beds, bed assignments, treatment entries, references to external clinical orders,
discharge summaries, admission state history and administrative close.

Does not own patient/staff/department identities, outpatient records, Lab tests, prescriptions,
surgery cases or money. Those remain bare UUID references and event snapshots.

## Logical data model

- `ADMISSION`: admissionId, patientId, referralRecordId/requestId, departmentId, priority,
  emergency flag, status and timestamps.
- `BED`: bedId, departmentId/ward reference, code, type and availability state.
- `BED_ASSIGNMENT`: assignmentId, admissionId, bedId, start/end timestamps, assignment status.
- `TREATMENT_ENTRY`: append-oriented clinical note/action with author, time and correction reference.
- `CLINICAL_ORDER_REF`: admissionId + external order type/id for Lab, Pharmacy or Surgery.
- `DISCHARGE_SUMMARY`: diagnosis/outcome/instructions, approver and medical approval time.
- `ADMISSION_STATUS_HISTORY`: old/new state, actor, reason, time and correlation.

Physical DDL and Vietnamese/English naming mapping are defined in the future implementation-ready
spec. No other service may create these tables temporarily.

## State machine

```text
REQUESTED → AWAITING_BED → AWAITING_DEPOSIT → READY → ADMITTED
          → MEDICALLY_DISCHARGED → CLOSED
```

`CANCELLED` is allowed before ADMITTED under an explicit reason/policy. Emergency override may
bypass the financial wait but must record audit and create a Billing receivable. It does not bypass
the need for a bed assignment unless the future spec defines an emergency holding location.

## Planned endpoints

| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/v1/inpatient/admissions` | ADMIN, DOCTOR, NURSE | create from exact admission request/referral |
| GET | `/api/v1/inpatient/admissions/{id}` | ADMIN, DOCTOR, NURSE | read admission |
| GET | `/api/v1/inpatient/admissions` | ADMIN, MANAGER, DOCTOR, NURSE | filter by department/status/date |
| PUT | `/api/v1/inpatient/admissions/{id}/bed` | ADMIN, NURSE | assign/transfer bed |
| POST | `/api/v1/inpatient/admissions/{id}/treatments` | ADMIN, DOCTOR, NURSE | append treatment entry |
| POST | `/api/v1/inpatient/admissions/{id}/medical-discharge` | ADMIN, DOCTOR | approve medical discharge |
| POST | `/api/v1/inpatient/admissions/{id}/close` | ADMIN, CASHIER | administrative close after settlement/override |

Exact request/response DTOs require a dedicated implementation spec before coding.

## Events

**Publish:** `admission.deposit.requested`, `admission.started`,
`discharge.medically.approved`, `admission.closed`.

**Subscribe:** `admission.requested`, `financial.clearance.granted` for ADMISSION_DEPOSIT,
`surgery.ready`, `surgery.completed`, `surgery.cancelled`, `settlement.completed`, and explicit
Lab/Pharmacy completion facts needed for the admission timeline.

## Business rules

1. One `admissionRequestId` creates at most one admission.
2. A bed has at most one active assignment; transfer closes the old assignment before the new one.
3. Normal admission requires an active bed assignment and matching deposit clearance.
4. Emergency admission records actor/reason/time/episode and leaves a Billing receivable.
5. Treatment history is append-oriented; corrections reference prior entries instead of overwrite.
6. Medical discharge requires an approved summary but does not close the admission.
7. Administrative close requires `settlement.completed` for this admission or an approved debt/
   emergency override ID.
8. External order/case IDs are never inferred from patient or “latest record”.

## Mandatory handoffs

- [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-INPATIENT-SURGERY-01`](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)

## Scaffold/implementation gate

Before production code: write an implementation-ready spec; scaffold with the project skill; add
root Maven module, Flyway DB, Eureka registration, Gateway route, Compose service, nested `AGENTS.md`,
contract fixtures and tests. Contract status stays `DESIGN_READY` until dependent producers and
consumers share passing fixtures.
