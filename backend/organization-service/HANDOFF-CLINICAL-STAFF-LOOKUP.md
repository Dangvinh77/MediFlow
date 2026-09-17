# HANDOFF — Organization staff lookup for Clinical

> **Mandatory for coding agents:** read this file before changing the staff lookup API,
> staff roles, or service-to-service security in `organization-service`.

- **Producer / owner:** Organization — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked Clinical work:** reliable patient appointment and medical-record validation

## Current gap

`GET /api/v1/org/staff/{id}/exists` currently returns a bare
`StaffExistsResponse`. Clinical follows the shared API convention and expects
`ApiResponse<StaffExistsResponse>`. The response also proves only staff existence and a department;
it does not prove that the staff member is eligible to act as a doctor. Organization security must
also accept an agreed service credential for this call.

## Required contract

Return the shared `ApiResponse` envelope with data that distinguishes all three outcomes:

- staff does not exist;
- staff exists but is not an eligible doctor;
- eligible doctor exists, with its authoritative `departmentId`.

Transport failure, timeout, circuit-open, invalid envelope, or HTTP 5xx must remain distinguishable
from a confirmed negative lookup.

## Acceptance criteria

- Controller and OpenAPI/contract documentation expose the same envelope and fields.
- A service-authenticated Clinical request is authorized without using a human user's credentials.
- Contract tests cover missing staff, non-doctor staff, eligible doctor, and additive JSON fields.
- Notify the Clinical owner when the contract is merged so its Feign projection can be enabled.
