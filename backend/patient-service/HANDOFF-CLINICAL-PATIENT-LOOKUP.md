# HANDOFF — Patient lookup for Clinical

> **Mandatory for coding agents:** read this file before adding or changing patient read/existence
> APIs or service-to-service security in `patient-service`.
>
> **Status (2026-09-26): producer read/lookup slice implemented; end-to-end acceptance remains.**
> Patient now exposes the locked read, list and service-only existence endpoints with Flyway/JPA,
> correlation propagation and access/service JWT validation. Clinical's consumer contract is ready;
> the remaining step is to run the gateway/frontend end-to-end fixture before closing this handoff.

- **Producer / owner:** Patient — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked consumers:** Clinical patient validation and the frontend Patient list/landing route

## Current gap

The producer-side slice is available. End-to-end gateway routing, frontend empty/error/retry states,
and the shared producer/consumer fixture still need to be verified before this handoff is closed.

## Required contract

Patient must provide both contracts required by the current consumers:

- `GET /api/v1/patients/{id}/exists` returning `ApiResponse<PatientLookupDTO>` with
  `{exists, patientId}` for Clinical;
- `GET /api/v1/patients?page&size&keyword` returning
  `ApiResponse<PageResult<PatientDTO>>` for the frontend Patient list through Gateway.

Accept the locked short-lived JWT with `type=service`, `role=SYSTEM`, service subject and propagated
`X-Correlation-Id`. A confirmed absence may be HTTP 404 or `exists=false`, but timeout/5xx/
circuit-open must never be represented as absence.

## Acceptance criteria

- The endpoint returns a stable UUID-based contract through the gateway/service network.
- Patient accepts Clinical's service JWT without forwarding a human authorization decision.
- Producer contract tests cover existing, missing, malformed token and correlation propagation;
  Clinical already covers existing, missing, unavailable, malformed and additive-field responses.
- Login followed by redirect to `/patients` returns a successful empty or populated page instead of
  `503`, and the frontend verifies empty/error/retry states through Gateway.
- Notify the Clinical owner when the producer endpoint is merged so end-to-end fixtures can run and
  this handoff can be removed.
