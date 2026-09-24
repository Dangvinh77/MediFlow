# CONTRACT-IDENTITY-LOOKUP-01 — Stable identity, lookup and Gateway routing

- **Status:** `PARTIAL`; Organization staff lookup and Gateway account verification are implemented,
  while Patient lookup and several consumer claim migrations remain open
- **Producer owners:** Organization, Patient, Gateway — Hoàng Anh
- **Consumers:** Clinical, Lab, Pharmacy, Billing, Notification, Inpatient, Surgery
- **Source:** [`mediflow-care-finance-redesign.html`](../../architecture/mediflow-care-finance-redesign.html)

## Identity ownership

- Patient owns `patientId`, demographics, insurance summary input and emergency contact.
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

HTTP 404 or `exists=false` means confirmed absence only. Timeout, circuit-open, 5xx and malformed
envelopes map to upstream unavailable; consumer must not turn them into “not found”.

## Service authentication

Internal lookups require a short-lived signed JWT with `type=service`, `role=SYSTEM`, service
subject and correlation propagation. Human access/refresh tokens are not reused for service calls.
Human access tokens use `sub=accountId` and explicit `patientId`, `staffId`, `departmentId` claims;
no service may interpret `sub` as a patient or staff ID.

Implemented baseline:

- Organization returns `ApiResponse<StaffLookupDTO>` with `exists`, `eligibleDoctor` and
  authoritative `departmentId`; Clinical already projects the three states and preserves outages.
- Clinical signs its Organization lookup credential with `type=service`, `role=SYSTEM`, a
  `clinical-service` subject and a 60-second lifetime while preserving the request correlation ID.
- Gateway verifies accounts through Organization, issues typed access/refresh tokens and carries
  optional `staffId`, `departmentId` and `patientId` claims.
- Pharmacy consumes the explicit `staffId` claim and never treats `sub` as a staff identity.

Open work is listed only in the [active handoff registry](../README.md), including Patient
read/existence APIs and consumer JWT claim migrations.

## Planned Inpatient/Surgery routing

The Inpatient foundation now has its module, port, and Compose configuration, while its Gateway
route remains open in [`HANDOFF-INPATIENT-GATEWAY-ROUTE`](../HANDOFF-INPATIENT-GATEWAY-ROUTE.md).
Surgery remains unscaffolded. Gateway routes for `/api/v1/inpatient/**` and
`/api/v1/surgery/**` use service discovery and the same auth/correlation policies when implemented;
neither route is live before health and authorization tests pass.

## Acceptance criteria

- A service token missing `type=service` or `role=SYSTEM` is rejected.
- A patient/staff lookup can distinguish absence from outage.
- Consumers use returned canonical IDs and never search by name or infer from JWT subject.
- Inpatient/Surgery route tests prove public role authorization and downstream service auth before
  registry status changes to `IMPLEMENTED`.
