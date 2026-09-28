# CONTRACT-IDENTITY-LOOKUP-01 — Stable identity, lookup and Gateway routing

- **Status:** `PARTIAL / PHASE-1-LOCKED`; Organization staff lookup, Gateway account verification
  and the Patient existence/read producer slice are implemented. End-to-end Patient acceptance,
  Gateway routes and several consumer claim migrations remain open.
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
  `ApiResponse<StaffIdentityLookupDTO>` with `{ exists, active, jobTitle, departmentId }`.
- `GET /api/v1/org/departments/{id}/lookup` returns
  `ApiResponse<DepartmentLookupDTO>` with `{ exists, active, departmentId, departmentName,
  departmentType }`.
- Existing `GET /api/v1/org/staff/{id}/exists` is retained unchanged for Clinical compatibility; it
  remains the doctor-eligibility contract and is not reinterpreted as the generic staff lookup.

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
  already distinguishes confirmed absence from malformed/unavailable responses.
- Clinical signs its Organization lookup credential with `type=service`, `role=SYSTEM`, a
  `clinical-service` subject and a 60-second lifetime while preserving the request correlation ID.
- Gateway verifies accounts through Organization, issues typed access/refresh tokens and carries
  optional `staffId`, `departmentId` and `patientId` claims.
- Pharmacy consumes the explicit `staffId` claim and never treats `sub` as a staff identity.

Open work is listed only in the [active handoff registry](../README.md), including Patient
end-to-end/shared-fixture acceptance and consumer JWT claim migrations.

## Planned Inpatient/Surgery routing

Inpatient Core V1 now has business APIs and guarded integrations, while its Gateway route remains
open in [`HANDOFF-INPATIENT-GATEWAY-ROUTE`](../HANDOFF-INPATIENT-GATEWAY-ROUTE.md).
Surgery has a bootable foundation and initial domain core but no business API/schema/events yet.
Gateway routes for `/api/v1/inpatient/**` and
`/api/v1/surgery/**` use service discovery and the same auth/correlation policies when implemented;
neither route is live before health and authorization tests pass.

## Acceptance criteria

- A service token missing `type=service` or `role=SYSTEM` is rejected.
- A patient/staff lookup can distinguish absence from outage.
- Consumers use returned canonical IDs and never search by name or infer from JWT subject.
- Inpatient/Surgery route tests prove public role authorization and downstream service auth before
  registry status changes to `IMPLEMENTED`.
