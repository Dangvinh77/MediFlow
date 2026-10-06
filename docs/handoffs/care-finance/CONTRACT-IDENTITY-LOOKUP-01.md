# CONTRACT-IDENTITY-LOOKUP-01 — Stable identity, lookup and Gateway routing

- **Status:** `PRODUCER-READY / CONSUMER-FIXTURES-PENDING`; Organization staff/department/room
  lookup and the additive job-title projection are implemented and contract-tested. Gateway account
  verification, Patient existence/read, the Clinical Patient consumer and the Inpatient Gateway
  route are implemented. Surgery/Clinical consumer fixture adoption, Surgery routing and several
  consumer claim migrations remain open.
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
Surgery has a bootable foundation, V1 schema, persistence adapters and initial application services,
but no business API or published/subscribed business events yet.
The future `/api/v1/surgery/**` route must use service discovery and the same auth/correlation
policies, but should land only after Huy supplies a real business endpoint and authorization matrix.

## Acceptance criteria

- A service token missing `type=service` or `role=SYSTEM` is rejected.
- A patient/staff lookup can distinguish absence from outage.
- Consumers use returned canonical IDs and never search by name or infer from JWT subject.
- Surgery route tests prove public role authorization and downstream service auth before its
  routing status changes to `IMPLEMENTED`.
