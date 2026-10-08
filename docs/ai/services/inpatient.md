# Service: inpatient

**Status:** locally complete for Core V1; cross-service activation remains feature-gated and
externally blocked. The sole implementation authority is
[`backend-spec/care-finance-v2/10-inpatient.md`](../../eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md).
**Owner:** Vinh (`Dangvinh77` / `Harori`)
**Source of truth:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)
**Module:** `backend/inpatient-service/` · **Port:** 8090 · **Database:** `mediflow_inpatient` · **Base path:** `/api/v1/inpatient` (Gateway route live)

Core V1 now contains the implementation-ready DDL, admission/bed/treatment/discharge APIs, domain
state machine, outbox publisher and guarded consumers from
[`10-inpatient.md`](../../eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md).
Producer and consumer activation remain off until the named service owners pass the canonical
same-byte fixtures. Gateway route, coarse RBAC and route tests landed in `262d610`; that transport
progress does not activate the disabled RabbitMQ integrations. Inpatient business contracts remain
`DESIGN_READY`; the shared identity contract retains its separately tracked `PARTIAL` status.

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

Physical DDL and Vietnamese/English naming mapping are implemented from the Inpatient backend spec.
No other service may create or query these tables.

## State machine

```text
REQUESTED → AWAITING_BED → AWAITING_DEPOSIT → READY → ADMITTED
          → MEDICALLY_DISCHARGED → CLOSED
```

`CANCELLED` is allowed before ADMITTED under an explicit reason/policy. Emergency override may
bypass the financial wait but must record audit and create a Billing receivable. It does not bypass
the need for a bed assignment unless the future spec defines an emergency holding location.

## Core V1 endpoints

Additive internal authority: `GET /api/v1/inpatient/admissions/{id}/lookup` uses only a short-lived
SYSTEM service credential (max 60s), preserves correlation and returns minimal exact admission
identity/status/revision. It is not the human admission DTO or bed-placement/referral authority.
The precise fields and absence/outage/eligibility rules live in CONTRACT-INPATIENT-SURGERY-01.
Accepting a service credential grants no human care-command role. Runtime event flags stay off.

| Method | Path | Roles | Purpose |
|---|---|---|---|
| POST | `/api/v1/inpatient/admissions` | ADMIN, DOCTOR | create from exact admission request/referral |
| GET | `/api/v1/inpatient/admissions/{id}` | ADMIN, DOCTOR, NURSE, CASHIER | read admission |
| GET | `/api/v1/inpatient/admissions` | ADMIN, MANAGER, DOCTOR, NURSE, CASHIER | filter by department/status/date |
| PUT | `/api/v1/inpatient/admissions/{id}/bed` | ADMIN, NURSE | assign bed |
| PUT | `/api/v1/inpatient/admissions/{id}/bed/transfer` | ADMIN, NURSE | transfer bed |
| PUT | `/api/v1/inpatient/admissions/{id}/bed/release` | ADMIN, NURSE | release bed |
| POST | `/api/v1/inpatient/admissions/{id}/admit` | ADMIN, DOCTOR, NURSE | admit after bed and financial gates |
| POST | `/api/v1/inpatient/admissions/{id}/treatments` | ADMIN, DOCTOR, NURSE | append treatment entry |
| POST | `/api/v1/inpatient/admissions/{id}/treatments/{entryId}/corrections` | ADMIN, DOCTOR, NURSE | append treatment correction |
| POST | `/api/v1/inpatient/admissions/{id}/order-references` | ADMIN, DOCTOR, NURSE | register a clinical order reference |
| POST | `/api/v1/inpatient/admissions/{id}/medical-discharge` | ADMIN, DOCTOR | approve medical discharge |
| POST | `/api/v1/inpatient/admissions/{id}/close` | ADMIN, CASHIER | administrative close after settlement/override |
| POST | `/api/v1/inpatient/admissions/{id}/cancel` | ADMIN, DOCTOR | cancel before admission under policy |
| POST | `/api/v1/inpatient/beds` | ADMIN, MANAGER | create bed |
| PUT | `/api/v1/inpatient/beds/{id}` | ADMIN, MANAGER | update bed |
| GET | `/api/v1/inpatient/beds` | ADMIN, MANAGER, DOCTOR, NURSE | filter beds by department/ward/status |

Exact request/response DTOs are defined by the implementation-ready Inpatient backend spec and
implemented in the service controller.

## Events

**Publish:** `admission.deposit.requested`, `admission.started`,
`discharge.medically.approved`, `admission.closed`.

Start, medical discharge and close are immutable singleton operations keyed by routing key plus
`admissionId`; their implicit business operation revision is `1`. Envelope `version` is schema-only.
`discharge.medically.approved` ends normal Pharmacy admission-medication eligibility, while
`admission.closed` remains the later administrative fact.

**Subscribe:** `admission.requested`, `financial.clearance.granted` for ADMISSION_DEPOSIT,
`surgery.case.created`, `surgery.ready`, `surgery.completed`, `surgery.cancelled`,
`settlement.completed`, and explicit Lab/Pharmacy completion facts needed for the admission timeline.
The Surgery consumer stores semantic receipts, retains event-first terminal outcomes until the exact
case reference exists, requires the same case/request/admission/patient/department identity on every
outcome, rejects changed redeliveries and ignores valid outpatient facts without inventing an admission.

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
9. `admission.started` carries the initial bed/department snapshot only. Transfer, release and
   capacity have no approved wire fact, so consumers cannot use the start payload as current
   placement after a transfer.

## Mandatory handoffs

- [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md)
- [`CONTRACT-INPATIENT-SURGERY-01`](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
- [`CONTRACT-SURGERY-BILLING-01`](../../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
- [`HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT`](../../../backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md)

## Integration activation gate

Core business DDL, APIs, events and tests are implemented. Keep Inpatient RabbitMQ producer and
consumer flags disabled until dependent producers and consumers share passing fixtures. Do not add
bed-transfer/release/capacity or Surgery wire events before their canonical fields and routing keys
are approved. The public route is available, but financial and Surgery event activation still wait
for the active handoffs above. Contract status stays `DESIGN_READY` until those integration gates
pass.

Keep `mediflow.features.inpatient-event-producers=false` and
`mediflow.features.inpatient-event-consumers=false` until Billing, Surgery, Pharmacy, Report and
Notification satisfy their registered handoffs and the admission/deposit/discharge/settlement
Docker slices pass through RabbitMQ.
