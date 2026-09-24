# Handoff — Notification must use the `patientId` JWT claim

- **Status (2026-09-24): OPEN in Notification.** Gateway already issues typed human tokens with
  `sub=accountId` and an explicit `patientId`; Notification still reads `sub` as the patient ID.
- **Producer of contract:** Gateway / Organization
- **Consumer:** Notification Service
- **Reason:** Phase 0 standardizes `sub=accountId` for every human JWT.

Notification currently treats a PATIENT token's `sub` as the patient identifier. That assumption
is no longer valid: a PATIENT token has `type=access`, `sub=accountId`, and an explicit
`patientId` claim. Staff tokens may have `staffId` and `departmentId` but do not get a patient
identity implicitly.

## Required consumer change

Update Notification's JWT filter/controller boundary so that:

1. `type=access` is required for human API calls.
2. A PATIENT caller reads `patientId` from the JWT claim.
3. Missing or malformed `patientId` on a PATIENT token is rejected with the normal unauthorized or
   forbidden contract; do not fall back to `sub`.
4. Staff authorization continues to use the staff role and does not treat `sub` as a patient ID.

The event consumer contract remains unchanged and is independent of JWT authorization:
`patient.created` contains `eventId`, `occurredAt`, `correlationId`, `patientId`, `hoTen`, `email`,
and `sdt`.

## Acceptance tests

- PATIENT `sub=accountId`, `patientId=P` can read `/notifications/patient/P`.
- The same token is forbidden for `/notifications/patient/Q`.
- PATIENT token without `patientId` is rejected.
- A refresh token and a service token cannot access the human Notification endpoints.
- A staff token is not interpreted as a patient token.

Complete this handoff before enabling patient-facing Notification routes in a shared environment.
