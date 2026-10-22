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

### Account management pages — 2026-10-22

User-assigned IMPLEMENT scope: Organization feature/routes, Core Organization and Gateway.
`/organization/accounts` is the searchable list; its heading has a Create action and each
row has a Detail link. `/organization/accounts/new` creates accounts; the ADMIN-only
`/organization/accounts/{accountId}` page fetches detail directly and owns password reset
and active/inactive controls. Loading, denied, empty/error and same-request retry states
remain explicit; workspaces remount by session and discard old completions.

The create form selects a staff or patient profile, never an editable role. Staff job title
maps DOCTOR→DOCTOR, NURSE→NURSE, TECHNICIAN→LAB_TECH, PHARMACIST→PHARMACIST,
CASHIER→CASHIER, MANAGER→MANAGER, ADMINISTRATIVE→ADMIN (explicit user decision).
Patient profiles use PATIENT. Staff lookup must finish successfully before submission.

The live Core contract adds ADMIN GET `/api/v1/org/accounts/{accountId}` (existing
AccountDTO fields) and optional `replaceActiveAccount` on creation/status requests.
409 `ACCOUNT_OWNER_ACTIVE` opens an explicit confirmation before retrying with true;
Core performs deactivation and creation/activation atomically. The browser never disables
another account with a separate command. Lost creation responses reuse the original key
and exact draft; confirmed replacement recovery survives remount without storing passwords.
An unknown status result requires a detail reload before another status command.

Personal profile no longer renders “Định danh tài khoản” or “Thông tin kỹ thuật”.

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


### Department and staff enrichment — 2026-10-22

User-approved expansion uses the existing Organization endpoints. Responses add nullable
DepartmentDTO description/phoneNumber/email and StaffDTO dateOfBirth/gender/address/hireDate/
qualification. Create/update requests send those new fields inside a complete optional
`profile` object. Omission/null preserves existing enrichment; `{}` clears it. Forms preserve
loaded values, normalize blank text to null and validate date order.

Department detail shows function, location, contact, the actual head name and members with
active/total counts. It reads all pages from the existing department-filtered staff API,
paginates display locally and offers Action/Detail links. Staff detail resolves the actual
unit name using the existing department endpoint. Both secondary reads have independent
retry and ignore stale sessions; the base detail stays usable when a secondary read fails.
The head picker permits active same-office staff for ADMINISTRATIVE and doctors for clinical
units. Titles use phòng/khoa according to the loaded type.

Staff detail exposes new private fields only for ADMIN; personal profile uses its existing
owner-authorized detail payload. Other directory roles see qualification but no birth date,
gender, address or hire date. No UUID/audit metadata section is reintroduced.

Deploy/restart updated Core (V11/V12 migrations) before the frontend. Core now fills empty new
profile fields on previously tracked seed masters exactly once, preserving nonempty edits.
A completion receipt prevents later restarts from refilling fields the user clears. Fresh seed
data has sample values. See Core README for transaction, adoption and date-conflict behavior.
