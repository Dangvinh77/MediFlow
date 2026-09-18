# HANDOFF — Organization staff lookup for Clinical

> **Mandatory for coding agents:** read this file before changing the staff lookup API,
> staff roles, or service-to-service security in `organization-service`.

- **Producer / owner:** Organization — TranHoangAnh94
- **Consumer:** Clinical — Dangvinh77 / Harori
- **Blocked Clinical work:** reliable patient appointment and medical-record validation

## Contract status

`GET /api/v1/org/staff/{id}/exists` returns the shared
`ApiResponse<StaffLookupDTO>` envelope. The response distinguishes staff existence, doctor
eligibility, and the authoritative department. Organization accepts a signed service JWT carrying
the `SYSTEM` role for this call; human caller credentials are not accepted.

The remaining consumer work is on Clinical: project the envelope and propagate the service JWT.

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

## Organization implementation status

The Organization-side contract is implemented in PR1 and normalized under the PR3 API envelope.
Clinical must still update its Feign projection and service credential interceptor before
deployment. The service credential must be a JWT carrying the `SYSTEM` role; human caller Bearer
tokens are not accepted for this lookup.
