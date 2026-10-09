# CONTRACT-IDENTITY-LOOKUP-01 — Stable identity, lookup and Gateway routing

- **Status:** `PRODUCER-READY / CONSUMER-FIXTURES-PENDING`; Organization staff/department/room
  lookup and the additive job-title projection are implemented and contract-tested. Gateway account
  verification, Patient existence/read, the Clinical Patient consumer and the Inpatient Gateway
  route are implemented. Surgery has a gated route and additive room/capability authority; full
  runtime activation, adoption of master's generic room/job-title fixtures and several consumer
  claim migrations remain open. Explicit Surgery capability authority is retained separately.
- **Producer owners:** Organization, Patient, Gateway — Hoàng Anh
- **Consumers:** Clinical, Lab, Pharmacy, Billing, Notification, Inpatient, Surgery
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Identity ownership

- Patient owns `patientId` and demographics. Insurance-summary and emergency-contact fields are not
  part of the locked V1 schema; they require a separate additive contract with exact field definitions.
- Organization owns `staffId`, `departmentId`, roles, staff eligibility and department membership.
- Gateway owns public authentication, JWT issuance and routing. It does not own patient/staff data.
- Operational services store bare UUID references and snapshots needed for audit. They do not create
  substitute identities or foreign keys into another database.

## Synchronous lookup contract

REST is used only when an answer is required to finish the current request. Every lookup uses the
shared `ApiResponse` envelope, short timeout, bounded retry/circuit breaker and correlation ID.

| Lookup | Producer | Required result |
|---|---|---|
| patient existence/read | Patient | distinguish exists, confirmed absence, unavailable/malformed |
| staff eligibility | Organization | exists, role/job eligibility, authoritative departmentId |
| department read | Organization | exists, active status, type/name needed for display/audit |
| account verification | Organization | accountId, role, optional staffId/departmentId/patientId |

## Phase 1 endpoint lock

The following paths are the additive service-to-service contracts for this phase. They must be
authenticated with a short-lived JWT carrying `type=service`, `role=SYSTEM`, a service subject and
the request correlation ID. Human access/refresh tokens are not accepted.

### Patient

- `GET /api/v1/patients/{id}` remains the human/public read endpoint and returns the locked Patient
  DTO. It is not a service-auth bypass.
- `GET /api/v1/patients/{id}/exists` is the minimal service-only lookup and returns
  `ApiResponse<PatientLookupDTO>` with `{ exists, patientId }`. `exists=false` is confirmed absence;
  timeout, 5xx, circuit-open or malformed envelopes are upstream unavailable.
  `PatientLookupDTO` is an internal lookup projection, not the public Vietnamese Patient DTO.

### Organization

- `GET /api/v1/org/staff/{id}/lookup` returns
  `ApiResponse<StaffIdentityLookupDTO>` with
  `{ exists, active, jobTitle, departmentId, eligibleTeamRoles }`.
  `eligibleTeamRoles` is additive and is always an array. For an absent or inactive staff row it is
  empty. For an active row the V1 mapping is:

  | Organization `jobTitle` | `eligibleTeamRoles` |
  |---|---|
  | `DOCTOR` | `PRIMARY_SURGEON`, `ASSISTANT_SURGEON`, `ANESTHESIOLOGIST` |
  | `NURSE` | `OR_NURSE` |
  | `TECHNICIAN`, `PHARMACIST`, `CASHIER`, `MANAGER`, `ADMINISTRATIVE` | empty |

- `GET /api/v1/org/rooms/{id}/lookup` returns
  `ApiResponse<RoomLookupDTO>` with
  `{ exists, active, roomId, departmentId, roomName, roomType }`.
  `roomId` always echoes the requested UUID, including confirmed absence. `roomType` is an
  Organization-owned string; the first locked value is `OPERATING_ROOM`. A missing row is
  `exists=false`; an inactive row is `exists=true, active=false`; persistence/dependency failure
  is `503 ORG_LOOKUP_UNAVAILABLE` and must not be converted to absence.
- `GET /api/v1/org/departments/{id}/lookup` returns
  `ApiResponse<DepartmentLookupDTO>` with `{ exists, active, departmentId, departmentName,
  departmentType }`.
- Existing `GET /api/v1/org/staff/{id}/exists` is retained unchanged for Clinical compatibility; it
  remains the doctor-eligibility contract and is not reinterpreted as the generic staff lookup.
  Clinical must continue using this compatibility shape until Vinh's consumer fixture explicitly
  adopts `eligibleTeamRoles`; the additive field must not silently change the existing Clinical
  decoder or doctor-eligibility semantics.

All lookup responses use the shared `ApiResponse` envelope and preserve `X-Correlation-Id`.
Gateway must treat the `/lookup` and `/exists` paths above as internal-only and must not expose them
as public human routes.

HTTP 404 or `exists=false` means confirmed absence only. Timeout, circuit-open, 5xx and malformed
envelopes map to upstream unavailable; consumer must not turn them into “not found”.

### Pharmacy necessary identity preflight — 2026-10-09

The independently default-off `care-finance-v2 AND pharmacy.identity.enabled` consumer reads Patient
`/exists` and Organization's generic staff and department `/lookup` endpoints before any receipt or
stock transaction. It supplies current descriptive facts to the internal context-checked creation
caller: exact patient existence, active DOCTOR job, matching active department. It does not derive
prescribing permission from `eligibleTeamRoles`, replace the separate doctor-eligibility endpoint,
or assert a license/medication order. Patient/Organization production code is unchanged.

Canonical IDs, explicit boolean/null shapes, bounded strict JSON and header/envelope correlation
are validated. Confirmed missing/ineligible facts reject the command; HTTP failure (including 404),
malformed envelopes and circuit-open are unavailable. The explicit `exists=false` shape is required
for confirmed absence in this consumer. No failure falls back to an unverified writer.

`checkedAt` is the local **start** of the three remote reads, not an invented producer observation or
revision. Age <=30s / future skew <=5s are necessary bounds, rechecked after network calls and
mutation-lock waits, including replay. This is not a state lease across services. Short-lived
Pharmacy SYSTEM JWTs carry no human identity claims. The checked caller requires both Clinical
context and current identity ports/proofs; a missing provider prevents startup when it is enabled.

Patient and generic staff consumer tests read existing producer fixture files. The department reply
is a local contract example, not a new producer-approved fixture or clinical-policy approval. Full
order/finance/admission authority and public/held activation remain open.
[Verification](../../superpowers/plans/2026-10-09-pharmacy-report-v2-priority.md).

## Additive Surgery authority V1 (2026-10-05)

Organization owns the operating-room **reference catalog**, not occupancy/reservations (Surgery)
or inpatient beds (Inpatient). No room or staff capability is seeded or inferred from a UUID,
specialization text, account role or job title.

- ADMIN creates `POST /api/v1/org/operating-rooms` and revises
  `PUT /api/v1/org/operating-rooms/{id}` with `expectedRevision`. A room has a unique uppercase
  `roomCode`, `roomName`, a real CLINICAL `departmentId`, `active` and revision starting at 1.
- ADMIN records an explicit qualification decision using
  `PUT /api/v1/org/staff/{id}/surgery-roles/{teamRole}` with `expectedRevision` (0 for first grant),
  `active`, finite `validFrom`/`validUntil` and `reason`. Supported roles: PRIMARY_SURGEON,
  ASSISTANT_SURGEON, ANESTHESIOLOGIST, OR_NURSE. Doctor roles require active licensed DOCTOR;
  OR_NURSE requires active NURSE. These are necessary, **not sufficient**, conditions: an
  explicit ADMIN grant is always required. The grant is scoped to the department at approval;
  transferring staff invalidates that grant until a new decision is recorded.
- Every authority mutation stores the verified ADMIN account ID, reason, correlation, revision
  and a durable `organization.surgery.authority.changed` V1 outbox event atomically. The envelope
  is the canonical event envelope; payload is `{referenceKind, referenceId, teamRole, revision,
  actorAccountId, reason}`. It is an invalidation hint, not authorization; no clinical/PII payload.
  The serialized V1 `teamRole` field is present: null for ROOM, a supported role for STAFF_CAPABILITY.
  Delivery is separately gated, disabled by default. Existing identity events are unchanged.
- Service-only `GET /api/v1/org/operating-rooms/{id}/lookup` returns
  `{exists, active, roomId, departmentId, sourceRevision, observedAt}`. `active` requires both the
  room and its current owning department active, with department type CLINICAL.
- Service-only `GET /api/v1/org/staff/{id}/surgery-eligibility?teamRole=...&startsAt=...&endsAt=...`
  returns `{exists, eligible, staffId, teamRole, departmentId, sourceRevision, observedAt,
  startsAt, endsAt}`. `exists` means the staff exists, not that a grant exists. Eligible requires
  active staff/department, matching current department, compatible job title/license, an active
  explicit grant and coverage of the **whole half-open requested interval**. Invalid intervals
  return 400; a missing grant or inactive/expired/revoked authority returns confirmed ineligible.
- `sourceRevision` is nullable for absent records/grants; otherwise a decimal string describing
  **only the room/grant revision**, not a global staff/department version. `observedAt` is producer
  time. These lookups are fresh request-time decisions, not a distributed lease. Consumers must
  revalidate at schedule confirmation/start and must not reuse a draft snapshot as permission.
- Absent references return 200, `exists=false`, `active/eligible=false`; room echoes `roomId`
  and has other descriptive fields null. Missing staff echoes staff/role/interval, has department
  and revision null. All success/error envelopes and headers preserve correlation. Outages are
  503, never confirmed absence. Gateway never exposes these two lookup routes publicly.

Surgery is the initial consumer; generic staff/department lookup DTOs used by Clinical/Inpatient
retain master's additive `eligibleTeamRoles` projection. That descriptive job-title mapping is
not sufficient authority for scheduling: Surgery requires the explicit interval-scoped grant
above. Generic `room` and revisioned `operating_room` catalogs coexist without inferred UUID
mapping; Surgery uses the explicit authority endpoint, not a generic room as a permission fallback.
Canonical fixtures live in Organization test resources; Surgery reads the same
bytes. This extension does not declare a required surgical team composition, medical checklist,
consent policy or legal credential verification policy; those remain separate clinical policies.

Consumer freshness: observations older than 30 seconds or more than 5 seconds ahead are rejected
as upstream unavailable. A 404 on an additive authority endpoint means producer deployment is
unconfirmed, not absence (these endpoints use 200 + exists=false). No grant/revision is inferred
from the generic staff lookup. Room/grant revisions do not version unrelated identity rows.

Surgery draft scheduling retains the producer's room `departmentId` and capability `departmentId`.
Both must equal the case department before case/resource locks or writes. Missing relationship data
is upstream-unavailable (503), not permission; foreign department is 422 with
`SURGERY_ROOM_DEPARTMENT_MISMATCH` or `SURGERY_STAFF_DEPARTMENT_MISMATCH`. No implicit
cross-department exception is granted by ADMIN or by a generic DOCTOR job title. A future explicit
cross-department policy requires its own authority contract, not omission of this comparison.

### Surgery authority-change consumption slice (2026-10-06, gated local implementation)

The existing Organization V1 event is an invalidation hint only. Surgery validates the complete
envelope/payload (ROOM with null teamRole, STAFF_CAPABILITY with an exact supported role,
positive revision, canonical IDs, recorded ADMIN actor/reason). Each reference/role/revision is
immutable: semantic duplicate delivery with a new event ID creates no new effects; different
content at the same revision conflicts. Envelope retry is byte-preserving.

Intake atomically commits its inbox entry, immutable source hint and durable jobs for currently
READY/SCHEDULED snapshots whose current schedule uses the exact room or staff/role. ACK means
these jobs are durably stored, not that every case has already changed. A bounded worker processes
each job in an independent transaction: case lock first, job lock second, then re-read state and
exact pinned snapshot/schedule revision. The job conservatively invalidates the pre-start decision
through the shared protocol; it never grants eligibility, creates READY, starts surgery or changes
IN_PROGRESS/terminal/replacement winners. No REST lookup is performed under a resource lock.

Repeated/late hints may require a fresh pre-start assessment; arrival ordering/timestamps are not
distributed locks or permission proofs. Completed/superseded jobs are never reopened. Failures
retain jobs with bounded durable backoff. The future READY/finalize/START orchestration must still
read current authority and reconcile source revisions after waits; this intake does not close that
race window or prove a full distributed lifecycle. Invalidation notification wire remains open.
An additional Organization-authority consumer flag defaults OFF, alongside business/consumer flags.

Producer fixtures are `organization-service/src/test/resources/contracts/surgery-authority-v1/`
`event.room.changed.json` and `event.staff.changed.json`; producer tests capture actual application
events and normalize only the random event ID for fixture comparison. Surgery reads these same
files directly, keeps raw inbox bytes, rejects duplicate keys/trailing tokens/future skew over 5s,
and uses a dedicated queue/DLQ. Actor identity is recorded producer evidence, not a consumer-side
claim that the actor's current role has been independently reauthorized. Long-term Organization
ownership is unchanged; no production producer wire is changed by this slice.
See the [execution ledger](../../superpowers/plans/2026-10-05-huy-50-task-execution.md) for actual test
results and remaining activation criteria; local PG/MQ tests are not multi-service lifecycle E2E.

## Service authentication

Internal lookups require a short-lived signed JWT with `type=service`, `role=SYSTEM`, service
subject and correlation propagation. Human access/refresh tokens are not reused for service calls.
Human access tokens use `sub=accountId` and explicit `patientId`, `staffId`, `departmentId` claims;
no service may interpret `sub` as a patient or staff ID.

### Gateway V1 compatibility lock

Gateway V1 continues to sanitize and emit the existing trusted headers `X-User-Id`, `X-User-Role`,
`X-Patient-Id`, `X-Staff-Id`, `X-Department-Id` and `X-Correlation-Id`. The proposed
`X-Account-Id`/`X-Role` names are reserved for a separately versioned migration and must not be
introduced as a silent replacement. Likewise, `GATEWAY_UPSTREAM_UNAVAILABLE` remains the V1
transport-unavailable error code; a new `DOWNSTREAM_UNAVAILABLE` code requires a versioned fixture
change across consumers.

Implemented baseline:

- Organization returns `ApiResponse<StaffLookupDTO>` with `exists`, `eligibleDoctor` and
  authoritative `departmentId`; Clinical already projects the three states and preserves outages.
- Patient exposes the locked service-only existence lookup and human read/list contracts; Clinical
  distinguishes confirmed absence from malformed/unavailable responses and deserializes the
  producer's canonical `patient.lookup.exists.json` fixture.
- Clinical signs its Organization lookup credential with `type=service`, `role=SYSTEM`, a
  `clinical-service` subject and a 60-second lifetime while preserving the request correlation ID.
- Gateway verifies accounts through Organization, issues typed access/refresh tokens and carries
  optional `staffId`, `departmentId` and `patientId` claims.
- Pharmacy consumes the explicit `staffId` claim and never treats `sub` as a staff identity.

Organization producer evidence for this additive slice is in
`backend/organization-service/src/test/resources/contracts/organization.room.lookup.json` and
`organization.staff.lookup.json`; the Organization contract tests deserialize those exact
envelopes. Surgery and Clinical owners must copy the fixtures into their consumer tests and
confirm role semantics before this contract can move to `IMPLEMENTED`.

Open work is listed only in the [active handoff registry](../README.md), including Surgery routing
and consumer JWT claim migrations. The former Patient handoff was retired after the
producer endpoint, service auth, Gateway internal-only rule and Clinical same-fixture test landed.

## Inpatient and planned Surgery routing

Inpatient Core V1 has business APIs and guarded integrations. Gateway now routes
`/api/v1/inpatient/**` to `lb://inpatient-service`, applies the endpoint role matrix, preserves
correlation and has route/authorization tests (`262d610`). This closes the former Inpatient route
handoff; it does not enable any cross-service RabbitMQ flag.
Surgery has feature-gated pre-op and pre-start cancellation business APIs. The Gateway route uses
`lb://surgery-service` and preserves `/api/v1/surgery/**` without rewriting. It is registered only
when `mediflow.routes.surgery.enabled=true` (default false). POST `/cases/{id}/preop` and `/cancel`
allow ADMIN/DOCTOR; exact GET `/cases` and `/cases/{id}` allow ADMIN/MANAGER/DOCTOR/NURSE;
PUT `/cases/{id}/schedule` allows ADMIN/MANAGER/DOCTOR. Other methods/paths remain default-deny.
Surgery read scope: ADMIN/MANAGER are cross-department; DOCTOR/NURSE require signed staffId plus
fresh active Organization staff lookup and use its current department. Caller filters and JWT
department do not grant object scope. Foreign detail returns opaque 404; foreign filter is empty;
unavailable/stale authority is 503. This is read policy, not eligibility for a clinical command.
Service and route activation
are independent gates. Registration does not enable business events or cross-service workflow.

Report's existing `/api/v1/reports/operations/daily` and `/operations/surgery` GET routes allow
ADMIN, MANAGER and DOCTOR. Other Report routes retain ADMIN/MANAGER. Gateway and downstream
tests enforce these endpoint-specific roles; no DOCTOR permission is granted to legacy revenue.

## Acceptance criteria

- A service token missing `type=service` or `role=SYSTEM` is rejected.
- A patient/staff lookup can distinguish absence from outage.
- Consumers use returned canonical IDs and never search by name or infer from JWT subject.
- Surgery route tests prove public role authorization and downstream service auth before its
  routing status changes to `IMPLEMENTED`.
