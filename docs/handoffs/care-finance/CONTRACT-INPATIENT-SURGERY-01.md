# CONTRACT-INPATIENT-SURGERY-01 — Admission, surgery and inpatient medication

- **Status:** `DESIGN_READY`; Inpatient Core V1 and both implementation specs exist, while live cross-service activation remains blocked on approved shared fixtures and the remaining Surgery business API/event implementation
- **Owners:** Inpatient/Clinical — Vinh; Surgery/Pharmacy — Huy
- **Consumers involved:** Billing, Notification, Report
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Boundary

Clinical decides that outpatient care requires admission and publishes a referral. Inpatient owns
the admission, bed assignment, treatment log and discharge. Surgery owns the case, readiness,
schedule, team, consent and result. Pharmacy owns medication and stock. No service imports or joins
another service's aggregate.

## `admission.requested`

Producer: Clinical. Consumer: Inpatient; Billing may project episode intent.

Required payload: `admissionRequestId`, `recordId`, `patientId`, `departmentId`, `requestedBy`,
`diagnosisSummary`, `priority`, `emergency`, `requestedAt`.

Inpatient deduplicates by `admissionRequestId`, creates at most one admission, and records the
returned `admissionId` in `admission.started`. It must not search by patient to guess an active
admission.

## Inpatient lifecycle events

| Event | Required fields | Main consumers |
|---|---|---|
| `admission.deposit.requested` | admissionId, patientId, episode fields, suggestedAmount, reason | Billing, Notification |
| `admission.started` | admissionId, patientId, bedId, departmentId, admittedAt, emergency | Billing, Report, Notification |
| `discharge.medically.approved` | admissionId, patientId, summaryId, approvedBy, approvedAt | Billing, Pharmacy |
| `admission.closed` | admissionId, settlementId or approved override ID, closedAt | Report, Notification |

`admission.started` requires an active bed assignment and deposit clearance unless an audited
emergency override exists. `admission.closed` requires medical discharge plus settlement/debt
resolution.

Core V1 permits each start, medical discharge and administrative close transition once. Start and
close are immutable singleton operations keyed by routing key plus `admissionId`; their implicit
business operation revision is `1`, independently of envelope schema `version`. A consumer may hold
an out-of-order close until the exact matching start arrives, but a late start never reopens it.

`discharge.medically.approved` ends normal admission medication eligibility: the care-finance design
freezes normal charge intake at medical discharge, and Pharmacy must not create or dispense a new
admission medication charge after the exact discharge fact. Administrative close remains later and
separate. A future explicitly approved late clinical adjustment policy may add an exception; none is
defined in V1.

## Surgery request and result

- `surgery.requested`: producer Clinical or Inpatient; payload includes `surgeryRequestId`, exact
  `recordId` or `admissionId`, `patientId`, `procedureCode`, indication, priority, requesting doctor
  and department.
- `surgery.ready`: producer Surgery; includes `surgeryCaseId`, `scheduleId`,
  `readinessSnapshotId`, `readyAt` and whether emergency override was used.
- `surgery.completed`: producer Surgery; includes `surgeryCaseId`, `admissionId`, `resultId`,
  performed item/price codes, startedAt, completedAt and complications summary.
- `surgery.cancelled`: producer Surgery; includes case/admission IDs, cancellation stage, reason,
  cancelledBy and cancelledAt. Billing decides financial adjustment under the billing handoff.

Readiness is the conjunction of valid indication, complete mandatory pre-op checks, active consent,
team assignment, room/time confirmation and surgery financial clearance. No consumer may set READY
from a payment event alone.

## Inpatient medication

Pharmacy adds `careContext = OUTPATIENT | ADMISSION` and an exact `admissionId` when the context is
ADMISSION. Inpatient medication is posted to the admission account and may be dispensed under the
admission policy; it must not reuse the outpatient prescription clearance blindly. Pharmacy events
retain `prescriptionId`, `patientId`, `departmentId`, item snapshots and correlation.

The active medication window begins at the exact `admission.started` fact and ends at the exact
`discharge.medically.approved` fact, not at administrative close. Bed transfer effects are not
defined by these lifecycle events: the start bed/department is an immutable admission snapshot, not
fresh placement authority. Cross-department transfer eligibility remains blocked until an approved
transfer/release contract exists.

## Acceptance criteria

- One referral creates at most one admission under redelivery/concurrency.
- A paid deposit without a bed does not produce `admission.started`.
- Surgery cannot become READY with missing consent, checklist, team, room/time or clearance.
- Duplicate `surgery.completed` does not duplicate admission notes, charges or reports.
- Pharmacy never chooses an admission by patient ID; inpatient prescriptions carry `admissionId`.
- Medical discharge and administrative close remain separate states.
- Medication creation/dispense after medical discharge is rejected unless a future approved late
  adjustment contract explicitly permits it.
