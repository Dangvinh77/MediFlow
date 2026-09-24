# CONTRACT-IDENTITY-LOOKUP-01 — Stable identity, lookup and Gateway routing

- **Status:** `PARTIAL`; current Clinical lookups/auth are tracked by existing handoffs
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

Existing detailed handoffs remain authoritative for implemented paths:

- [Clinical service token type](../../../backend/clinical-service/HANDOFF-CLINICAL-SERVICE-TOKEN-TYPE.md)
- [Organization staff lookup](../../../backend/organization-service/HANDOFF-CLINICAL-STAFF-LOOKUP.md)
- [Patient lookup](../../../backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md)
- [Gateway account verification](../../../backend/gateway/HANDOFF-ORGANIZATION-ACCOUNT-VERIFY.md)
- [Notification patient claim](../../../backend/notification-service/HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md)

## Planned Inpatient/Surgery routing

When modules are scaffolded, Gateway adds `/api/v1/inpatient/**` and `/api/v1/surgery/**` using
service discovery and the same auth/correlation policies. Ports, module registration and Compose
configuration are decided in the scaffold PR; docs must not claim routes are live before health and
authorization tests pass.

## Acceptance criteria

- A service token missing `type=service` or `role=SYSTEM` is rejected.
- A patient/staff lookup can distinguish absence from outage.
- Consumers use returned canonical IDs and never search by name or infer from JWT subject.
- Inpatient/Surgery route tests prove public role authorization and downstream service auth before
  registry status changes to `IMPLEMENTED`.

