# Frontend Service: Organization

**Owner:** Hoàng Anh (`TranHoangAnh94`)  
**Backend:** `backend/organization-service/**`  
**Contract context:** [`docs/ai/services/organization.md`](../../../docs/ai/services/organization.md)

## Writable frontend scope

- `frontend/src/features/organization/**`
- `frontend/src/app/(dashboard)/organization/**`

Gateway identity is coordinated by the same owner, but login/session/auth files remain shared and
need an explicitly assigned shared task.

## Current UI baseline

`/organization` loads active departments and paged staff as independent read sections through
`GET /v1/org/departments` and `GET /v1/org/staff`. UI roles are `ADMIN`, `MANAGER`, `DOCTOR`, and
`NURSE`.

## Owner queue

- `FE-ORG-01` — `VERIFY-CONTRACT`: add department detail or one supported administration slice.
- `FE-ORG-02` — `VERIFY-CONTRACT`: add staff detail or one supported create/update slice.
- `FE-ORG-03` — `VERIFY-CONTRACT`: add account administration only when a live read/write contract
  and roles exist.
- `FE-ORG-04` — `IMPLEMENT`: test independent section failures, pagination, role gates, and retries
  after the shared harness exists.

## Handoffs and blockers

- Signed `staffId` and account-to-staff mapping are Gateway/Organization contracts. Acceptance:
  stable documented identity semantics usable by Pharmacy and other staff-scoped consumers.
- Clinical and Lab may request staff/department lookup contracts. Implement them in Organization;
  consumers must not query Organization storage or copy its entities.
- Any shared login/session change requires an explicit shared task and smoke tests for all roles.

## Contract and verification gate

Confirm live department, staff, and account controller DTOs and role annotations. Keep the two base
reads independently recoverable so one failure does not hide the other.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Organization tests when available.
Smoke test department/staff partial failure, pagination, empty state, and denied roles.
