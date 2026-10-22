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

### Personal profile split — 2026-10-22

`/organization` gives ADMIN the existing department/staff/account management workspace;
other human roles see their own profile. `/organization/profile` is available through the
shared header (explicit user delegation for `DashboardHeader.tsx` and its test).
MANAGER/DOCTOR/NURSE retain the public work directory at `/organization/directory`.
Personal profiles are read-only and link to the existing `/organization/security` password form.

No new HTTP endpoint or wire field: the profile selects `staffId` from the access token and
calls existing GET `/api/v1/org/staff/{id}`. Core verifies the token locally and returns private
fields only for ADMIN or the signed owner. PHARMACIST/CASHIER/LAB_TECH may read their own
detail, without list, foreign detail or write authority. Directory responses remain redacted
for non-ADMIN. JWT parsing in the UI selects a URL; it never grants authority.
Unlinked accounts show an explicit empty state without querying the directory. The app route
composes the separate Patient profile; no cross-feature imports or cross-context DB reads.
Profile workspaces remount by session/identity and ignore stale request completion.

Verification for this split: Organization/Patient/profile/header 177/177 tests in 20 files
(`--maxWorkers=2`), typecheck, lint and production build pass. Core/Gateway unit/web checks
and packaging pass; Docker-dependent Core acceptance remains unavailable. See the dated
workboard entry for the transient parallel-test timeout and exact verification limits.

Confirm live department, staff, and account controller DTOs and role annotations. Keep the two base
reads independently recoverable so one failure does not hide the other. Department transfer follows
staff profile update as a separate command; a transfer refusal is now surfaced as partial success,
the saved profile is refreshed, and retry safely attempts the transfer again.

Run `pnpm typecheck`, `pnpm lint`, `pnpm build`, and focused Organization tests when available.
Smoke test department/staff partial failure, pagination, empty state, and denied roles.
