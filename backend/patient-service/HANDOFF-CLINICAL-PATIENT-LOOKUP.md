# HANDOFF — Patient lookup for Clinical

> **Mandatory for coding agents:** read this file before adding or changing patient read/existence
> APIs or service-to-service security in `patient-service`.

- **Producer / owner:** Patient — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked Clinical work:** live patient validation when creating appointments and records

## Current gap

The Patient module currently has only its application bootstrap and configuration; it exposes no
patient read or existence endpoint. Clinical therefore cannot distinguish a missing patient from an
unavailable Patient service.

## Required contract

Provide either `GET /api/v1/patients/{id}` or an explicit
`GET /api/v1/patients/{id}/exists`, using the shared `ApiResponse` envelope and an agreed
service-to-service authentication mechanism. A confirmed absence may be HTTP 404 or `exists=false`,
but timeout/5xx/circuit-open must never be represented as absence.

## Acceptance criteria

- The endpoint returns a stable UUID-based contract through the gateway/service network.
- Clinical can authenticate without forwarding a human user's authorization decision.
- Contract tests cover existing, missing, unavailable, malformed, and additive-field responses.
- Notify the Clinical owner when the contract is merged so its Feign adapter can be finalized.

