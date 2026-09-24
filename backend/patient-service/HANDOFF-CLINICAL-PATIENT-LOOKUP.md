# HANDOFF — Patient lookup for Clinical

> **Mandatory for coding agents:** read this file before adding or changing patient read/existence
> APIs or service-to-service security in `patient-service`.
>
> **Status (2026-09-24): still open.** The module still has no Java patient domain/controller
> implementation. Clinical's resilient Feign adapter is ready, but live appointment/record
> creation and the frontend post-login patient landing remain blocked until this producer contract
> exists.

- **Producer / owner:** Patient — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked consumers:** Clinical patient validation and the frontend Patient list/landing route

## Current gap

The Patient module currently has only its application bootstrap and configuration; it exposes no
patient read or existence endpoint. Clinical therefore cannot distinguish a missing patient from an
unavailable Patient service.

## Required contract

Provide both contracts required by the current consumers:

- `GET /api/v1/patients/{id}` or an explicit `GET /api/v1/patients/{id}/exists` for Clinical;
- `GET /api/v1/patients?page&size&keyword` returning
  `ApiResponse<PageResult<PatientDTO>>` for the frontend Patient list through Gateway.

Use an agreed service-to-service authentication mechanism for Clinical. A confirmed absence may be
HTTP 404 or `exists=false`, but timeout/5xx/circuit-open must never be represented as absence.

## Acceptance criteria

- The endpoint returns a stable UUID-based contract through the gateway/service network.
- Clinical can authenticate without forwarding a human user's authorization decision.
- Contract tests cover existing, missing, unavailable, malformed, and additive-field responses.
- Login followed by redirect to `/patients` returns a successful empty or populated page instead of
  `503`, and the frontend verifies empty/error/retry states through Gateway.
- Notify the Clinical owner when the contract is merged so its Feign adapter can be finalized.
