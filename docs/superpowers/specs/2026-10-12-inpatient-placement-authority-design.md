# Inpatient Placement Authority Design

**Date:** 2026-10-12  
**Owner:** Vinh (`Dangvinh77` / `Harori`)  
**Consumers:** Huy / Pharmacy and Report  
**Status:** Approved direction; written-spec review pending

## Goal

Make Inpatient the authoritative producer for the current admission placement, immutable bed-use
intervals, and bed-capacity changes needed by Pharmacy and Report. Pharmacy remains responsible for
the final create/dispense authorization, and Report remains responsible for KPI calculation and
publication.

This change must not edit Pharmacy or Report production code, activate held integrations, infer an
admission by patient, or let another service query the Inpatient database.

## Current state and gap

Inpatient already persists each bed assignment with `assignedAt`, optional `releasedAt`, actor and
reason. A transfer releases the old assignment and creates a new one atomically. The existing
service-only `/admissions/{id}/lookup` exposes the admission-owning department and admission row
revision; its contract explicitly excludes current placement.

Core V1 currently publishes only the initial bed/department snapshot in `admission.started`.
Consumers therefore cannot safely determine the current ward after transfer, reconstruct bed-use
intervals, or determine historical staffed/available capacity. Pharmacy has admission lifecycle
evidence but lacks fresh placement authority. Report has completed-stay duration but must not infer
occupancy from the initial bed or active-admission count.

## Approaches considered

### 1. REST lookup only

Add a current-placement endpoint and make both consumers call it. This gives Pharmacy fresh state,
but Report cannot deterministically replay historical occupancy, and polling creates avoidable
cross-service coupling. Rejected.

### 2. Events only

Publish placement and capacity events. This supports Report replay and local Pharmacy projections,
but an asynchronous projection alone is not a lease and cannot prove freshness immediately before a
stock mutation. Rejected.

### 3. Service lookup plus immutable events — selected

Add a distinct service-only placement lookup for Pharmacy and publish versioned placement/capacity
facts for durable consumer projections and replay. The lookup and events share the same source
identity and revisions. This preserves the existing admission lookup semantics and follows the
project rule that cross-service reads use REST while cross-service reactions use events.

## Contract boundaries

### Existing admission medication window

The medication window opens only at `admission.started` and closes at
`discharge.medically.approved`. Administrative `admission.closed` is a terminal safeguard for
out-of-order delivery, not the normal eligibility end. A close must never reopen eligibility, and a
late start must never override a known medical discharge or close.

Inpatient supplies evidence; it does not grant Pharmacy permission. Pharmacy must combine:

- the open admission medication window;
- a fresh current placement matching the prescription context;
- independent prescriber and order authority;
- financial and stock rules;
- a post-lock recheck that fails closed on missing, stale or unavailable evidence.

### Current-placement lookup

Add a separate internal endpoint:

```text
GET /api/v1/inpatient/admissions/{admissionId}/placement-lookup
```

It accepts only a signed short-lived `type=service`, `role=SYSTEM` credential, is blocked from public
Gateway access, propagates correlation ID, and returns a minimal English projection:

```json
{
  "exists": true,
  "admissionId": "uuid",
  "patientId": "uuid",
  "admissionStatus": "ADMITTED",
  "medicationWindowOpen": true,
  "placementRevision": 3,
  "placement": {
    "assignmentId": "uuid",
    "bedId": "uuid",
    "departmentId": "uuid",
    "wardCode": "CARDIOLOGY-A",
    "assignedAt": "2026-10-12T03:00:00Z"
  },
  "observedAt": "2026-10-12T03:00:01Z"
}
```

`departmentId` inside `placement` comes from the current bed, not the admission-owning department.
An existing admission without an active assignment returns `placement: null`; this is distinct from
an absent admission and from upstream failure. Absence is a successful response with `exists=false`;
storage or mapping failure returns the stable unavailable error. The response is evidence, not a
distributed lease or sufficient dispense permission.

The existing `/admissions/{id}/lookup` remains byte- and meaning-compatible.

### Placement event

Publish one immutable `admission.placement.changed` V1 fact per successful assignment mutation. Its
business identity is `(admissionId, placementRevision)`. `placementRevision` is an admission-scoped,
strictly increasing source revision stored by Inpatient; it is not the envelope version or the JPA
row version.

The payload contains:

- exact `admissionId`, `patientId`, `placementRevision`, `changeType`, `changedAt`, actor and optional
  reason;
- `previousPlacement`, nullable, with exact assignment/bed/department/ward/start/end facts;
- `currentPlacement`, nullable, with exact assignment/bed/department/ward/start facts.

`changeType` is one of `ASSIGNED`, `TRANSFERRED`, or `RELEASED`. Transfer is one atomic fact carrying
both the closed old interval and the new current placement. Assignment has no previous placement;
release has no current placement. Cancellation-triggered release uses `RELEASED` and retains the
actual release reason. Same business identity plus identical bytes is idempotent; changed bytes are
a contract conflict.

The aggregate mutation, assignment persistence, placement revision increment, admission history and
outbox append commit in one transaction. No event is emitted for an idempotent same-bed request that
does not mutate state.

### Capacity event

Publish `bed.capacity.changed` V1 when a bed first enters the catalog or when an administrative bed
update changes its capacity membership or capacity dimensions. Its business identity is
`(bedId, capacityRevision)`, where `capacityRevision` is a strictly increasing Inpatient source
revision.

The payload contains exact bed, department and ward identity, `active`, operational status,
`capacityEligible`, `effectiveAt`, actor, and source revision. `capacityEligible` means the bed is
active and not `OUT_OF_SERVICE`. Ordinary assignment transitions between `AVAILABLE` and `OCCUPIED`
do not change staffed capacity and therefore do not publish a capacity change. If a later design
distinguishes licensed, staffed and available capacity, it requires a new additive field or version;
Report must not guess it from occupancy.

The bed update and outbox append commit atomically. A no-op update emits no fact.

## Persistence

Add an admission-scoped placement revision initialized to zero and incremented once for every
assignment, transfer or release. Existing assignment rows remain the durable interval history; they
are never overwritten except for the single ACTIVE → RELEASED close of that interval.

Add a bed capacity revision and append-only capacity history containing the exact effective snapshot
that was published. This makes producer replay possible without reconstructing historical capacity
from the current bed row. Existing rows receive one deployment-time baseline snapshot during
migration; the migration does not fabricate past capacity before that recorded instant.

Outbox retention must preserve unaccepted placement/capacity facts. Producer gates remain disabled
by default until Huy's consumers pass the checked-in same-byte fixtures.

## Report semantics supplied by Inpatient

Inpatient supplies source facts only:

- completed medical length of stay remains `admittedAt → approvedAt` from the existing lifecycle
  events;
- bed occupancy intervals come from assignment start/end facts;
- the occupancy denominator comes from capacity-eligible intervals;
- administrative close is not medical discharge, bed release, or an occupancy end;
- missing placement/capacity history is unavailable, never zero.

Report owns timezone bucketing, census cut-off, rounding, aggregation, publication and coverage
metadata. This change does not implement or activate those calculations in Inpatient.

## Failure and ordering rules

- Pharmacy lookup fails closed on unavailable, stale, mismatched or missing current placement.
- Consumers may receive revisions out of order; the greatest contiguous accepted source revision is
  current, while gaps remain explicit pending/unavailable state.
- A release received before its assignment fact is retained as a gap, not converted to a zero-length
  interval.
- Duplicate envelope delivery has no second effect. Same source key with changed payload is a
  permanent contract conflict.
- Correlation ID is preserved from the command into the outbox envelope and audit history.
- Existing admission start/discharge/close facts and their singleton identities remain unchanged.

## Security and privacy

The placement lookup is service-only and returns no diagnosis, treatment narrative, staff profile or
financial details. Events contain only the identifiers and timestamps required for authorization and
operational reporting. Human roles cannot call the lookup, and a SYSTEM token cannot call human bed
commands.

## Testing and acceptance

Implementation follows test-first development and must prove:

1. assign, transfer, release and cancellation release create the exact placement revisions and
   immutable interval facts;
2. transfer atomically closes the old interval and creates the new current placement;
3. idempotent same-bed requests do not emit duplicate facts;
4. current-placement lookup distinguishes absent admission, no placement and unavailable storage;
5. lookup returns the bed's current department and never silently substitutes the admission-owning
   department;
6. medical discharge closes medication eligibility immediately even if a bed remains assigned;
7. capacity create/update facts and history use exact source revisions, while AVAILABLE ↔ OCCUPIED
   assignment changes do not alter capacity;
8. outbox, aggregate and history rollback together on persistence failure;
9. service-token RBAC, correlation propagation, malformed input and stable errors are covered;
10. producer fixtures deserialize exactly and document duplicate, conflict and out-of-order behavior;
11. PostgreSQL tests prove revision and interval constraints under concurrency;
12. existing Inpatient tests remain green and all feature flags default off.

Contract status becomes `PRODUCER_READY` only after producer DTOs, fixtures, migration, contract
tests and broker-backed producer acceptance pass. It becomes `IMPLEMENTED` only after Huy confirms
Pharmacy and Report consume the same fixture bytes. Until then, the existing active handoff remains
registered with the precise consumer work and acceptance criteria.

## Documentation changes in the implementation PR

Update together with code:

- `docs/handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md` for the lookup, medication boundary
  and placement fact consumed by Pharmacy;
- `docs/handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md` for placement/capacity facts and Report
  source semantics;
- `docs/handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md` and registry status for Huy's remaining
  consumer work;
- `docs/ai/services/inpatient.md` and the implementation-ready Inpatient backend spec;
- checked-in canonical JSON fixtures under the Inpatient test resources.

No Pharmacy or Report production source is modified without a separate explicit task-scoped owner
override.
