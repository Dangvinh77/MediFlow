# HANDOFF — Patient lookup for Clinical

> **Mandatory for coding agents:** read this file before adding or changing patient read/existence
> APIs or service-to-service security in `patient-service`.
>
> **Status (2026-09-26): producer still open; Clinical consumer complete.** Clinical now calls the
> locked service-only `/{id}/exists` path with a short-lived service JWT, validates the standard
> envelope/canonical patient ID and preserves outage-vs-absence semantics. Patient still has no
> Java lookup controller, so live appointment/record creation and the frontend patient landing
> remain blocked until the producer contracts exist.

- **Producer / owner:** Patient — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked consumers:** Clinical patient validation and the frontend Patient list/landing route

## Current gap

The Patient module exposes no Java patient read or existence controller. Clinical's consumer can
distinguish a confirmed miss from an unavailable/malformed producer response, but every live lookup
still fails until Patient implements the endpoint and accepts the locked service credential.

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
