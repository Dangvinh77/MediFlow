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

### Surgery-owned outbound V1 decisions — 2026-10-07

Huy's producer DTOs/fixtures define one common wire for all consumers (no Java imports across
modules). Every outcome includes `surgeryCaseId`, `surgeryRequestId`, `patientId`, `departmentId`,
`careEpisodeType`, `careEpisodeId`, nullable `admissionId`/`recordId`, and `caseRevision`.

- READY also carries `scheduleId`, `scheduleRevision`, `roomId`, `plannedStartAt`, `plannedEndAt`,
  `readinessSnapshotId`, `readyAt`, `emergencyOverrideUsed=false`, `reservationConfirmed=false`.
  This is provisional readiness, NOT a finalized booking. Its semantic key is the snapshot UUID.
- COMPLETED carries persisted `resultId`, `sourceRevision=1`, actual `startedAt`/`completedAt`,
  `recordedAt`, `procedureCode`, `methodCode`, `outcomeCode`, nullable `complicationsCategory`,
  `complicationsSummary=null`, and performed `{performedItemId,itemCode,priceCode,quantity}`.
  No diagnosis, narrative or consent document is published. The result is immutable in V1.
- CANCELLED carries producer-owned `cancellationId`, `sourceRevision=1`, `cancellationStage`,
  `reason`, `reasonCode=PRE_START_CANCELLATION`, `cancelledBy` (human account UUID),
  optional `cancelledByStaffId`, and `cancelledAt`. The stage is derived from the last committed
  transition: REQUESTED → BEFORE_PREOP; PREOP/READY → AFTER_PREOP; SCHEDULED → BEFORE_START.
  Cancellation UUID is the Java name-UUID of UTF-8 `surgery.cancelled:<surgeryCaseId>`; this
  is Surgery's new operation identity, not an inferred external reference.

- `surgery.readiness.invalidated` identifies the exact prior `readinessSnapshotId`, `scheduleId`
  and `scheduleRevision`, with the same care identity, final committed `caseRevision`, structured
  `reasonCode` and `invalidatedAt`. It never claims that a new schedule is booked. Its semantic
  identity is the old readiness snapshot, not an external authority revision. Notification must
  suppress that snapshot's reminder; a later READY for a new snapshot can create a new provisional
  reminder. Terminal events dominate READY regardless of delivery order. Reasons are
  READINESS_EXPIRED, ORGANIZATION_AUTHORITY_CHANGED, FINANCIAL_CLEARANCE_CHANGED, CONSENT_CHANGED,
  CONSENT_REVOKED, CHECKLIST_CHANGED, SCHEDULE_REPLACED and READINESS_RECHECK_FAILED.

Inpatient now consumes the checked-in producer bytes for `surgery.case.created`, READY and terminal
facts. It classifies valid outpatient outcomes before requiring admission IDs, registers the exact
case reference, retains event-first outcomes durably, detects semantic duplicate/conflict and keeps
terminal state dominant over late READY. It persists `surgeryRequestId` with the case receipt and
rejects any outcome whose case/request/admission/patient/department identity differs. Broker-backed
Docker acceptance is still required, and
held producer rows do not activate this flow.

### Additive exact-admission authority lookup V1 (2026-10-05)

`GET /api/v1/inpatient/admissions/{id}/lookup` is service-only, requiring a signed short-lived
`type=service`, `role=SYSTEM` credential and correlation propagation. Human access/refresh tokens
cannot read it, and a service token cannot call existing human care commands. Gateway blocks the
lookup publicly. Existing Vietnamese human DTOs are unchanged; the minimal internal projection
uses the shared English care-finance identity vocabulary.

Payload: `{exists, admissionId, patientId, departmentId, sourceRecordId, status, eligible,
sourceRevision, observedAt}`. Absence is 200 with echoed admissionId, exists/eligible false and
remaining business fields null. Eligible requires status ADMITTED and no medical discharge/close/
cancellation timestamp; REQUESTED/READY are not medication or surgery eligibility. Persistence
failure is 503 `INPATIENT_LOOKUP_UNAVAILABLE`, never absence.

`sourceRevision` is the decimal string of the existing admission row's optimistic-lock `version`
(including initial 0), not an event envelope version or reconstructed history. It versions admission
state only, **not bed placement or clinical order references**. `departmentId` is the admission's
owning department, not the department of the most recent bed. Cross-department placement is not
authorized by this lookup. `sourceRecordId` is the admission referral's exact source record, not a
substitute for any Surgery request. Consumers compare exact admission/patient/department IDs and
may not claim that this proves the independently missing surgery-referral relationship.

Pharmacy's independently gated consumer-side read adapter is now implemented (2026-10-08), with
SYSTEM service credential, exact correlation/identity and strict source row revision (including 0).
It matches Surgery's <=30-second observation age / <=5-second future-skew boundary, bounded
timeouts, circuit breaker and unavailable fallback. It refuses a call inside a mutation transaction;
the necessary exact patient/department/current-state check must be repeated after lock waits.
It does not supply order/prescriber/current-placement permission or activate public admission
commands. Actual fixture-over-HTTP evidence and remaining gates are in
[Huy's follow-up](../../superpowers/plans/2026-10-08-huy-ready-task-completion.md).

Fresh request-time REST is not a distributed lease: Surgery/Pharmacy must revalidate at their own
mutation boundary and retain local revision/audit fences. A draft lookup cannot authorize a future
start/dispense. Surgery accepts observations at most 30 seconds old with at most 5 seconds future
clock skew; stale/malformed responses or an older producer's 404 fail as upstream unavailable.
No asynchronous start projection alone can authorize medication. Shared fixtures cover active,
medically discharged and missing states; runtime admission/medication activation remains off.

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
