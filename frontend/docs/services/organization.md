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

`/organization` loads all departments and paged staff as independent read sections. Detail routes
exist for both resources. `ADMIN` can create/update departments, create/update/transfer staff, and
use `/organization/accounts` to create or lock/unlock an account. Service-only lookup and credential
verification endpoints remain outside the browser UI.

## Owner queue

- `FE-ORG-01` — `DONE`: department detail/create/update with exact roles and fields.
- `FE-ORG-02` — `DONE`: staff detail/create/update/department transfer.
- `FE-ORG-03` — `DONE`: account create/status commands; SYSTEM account creation remains backend-only.
- `FE-ORG-04` — `DONE`: component coverage for independent department/staff failures, inactive
  department display, staff pagination and same-page retry, admin action visibility, account commands,
  department/staff create forms, failed form-load retry, and profile-save/department-transfer partial
  success. Department and staff forms stop submission when required source data fails to load.

## Handoffs and blockers

- Signed `staffId` and account-to-staff mapping are Gateway/Organization contracts. Acceptance:
  stable documented identity semantics usable by Pharmacy and other staff-scoped consumers.
- Clinical and Lab may request staff/department lookup contracts. Implement them in Organization;
  consumers must not query Organization storage or copy its entities.
- Any shared login/session change requires an explicit shared task and smoke tests for all roles.

## Contract and verification gate

Confirm live department, staff, and account controller DTOs and role annotations. Keep the two base
reads independently recoverable so one failure does not hide the other. Department transfer follows
staff profile update as a separate command; a transfer refusal is now surfaced as partial success,
the saved profile is refreshed, and retry safely attempts the transfer again.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Organization tests when available.
Smoke test department/staff partial failure, pagination, empty state, and denied roles.
