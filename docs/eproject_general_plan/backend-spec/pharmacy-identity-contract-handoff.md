# Pharmacy identity claim handoff — 2026-09-13

## Finding

`pharmacy-service` now treats JWT `sub` as `accountId` only. It never compares that value with a
prescription `doctorId` or stores it as a staff identity. Staff-owned operations require a separate
signed `staffId` claim; until that claim is supplied, pharmacy fails closed with a stable forbidden
error.

## Evidence checked

- `backend/gateway/.../JwtTokenService.java` currently emits `sub`, `role` and `cid` only.
- `backend/gateway/.../AuthController.java` demo users contain one `userId`, not a staff mapping.
- `backend/organization-service` models `Account.accountId`, `Account.staffId` and `Role` as distinct
  values, confirming that UUIDs must not be conflated.

## Required producer contract

Gateway/Organization owners must agree and sign the following additive claims for access and refresh
tokens:

| Claim | Type | Meaning |
|---|---|---|
| `sub` | UUID string | authenticated account id (`accountId`) |
| `staffId` | UUID string or absent | linked active staff id; required for staff-owned actions |
| `role` | string | account role (`ADMIN`, `DOCTOR`, `PHARMACIST`, …) |
| `cid` | UUID string | request correlation id |

`staffId` must be absent for `PATIENT` accounts and must be present for staff roles. `SYSTEM` may omit
it. Pharmacy validates the UUID shape and rejects malformed signed claims before creating the security
context.

## Pharmacy implementation boundary

- `ActorIdentity` carries `accountId`, optional `staffId` and `role` through application commands.
- Doctor prescription ownership compares `staffId` to `doctorId`; an account id is never used for
  that comparison.
- Admin audit actions retain `accountId` when no staff id exists; staff-backed actions use `staffId`.
- No Gateway or Organization production file was changed in this task.

## Acceptance tests for the producer follow-up

1. Token with account A and staff S yields both ids in pharmacy authentication.
2. Token with malformed `staffId` is unauthenticated.
3. Doctor token without `staffId` cannot create/cancel a prescription.
4. Doctor token with staff S can operate only on prescriptions owned by S.
5. Admin token with account A can override ownership while audit keeps A (or an explicitly agreed
   admin staff id).

Until these producer tests and the signed claim contract are merged, T03 remains partially blocked
for end-to-end login flows. Pharmacy unit and web-slice tests cover the fail-closed behavior locally.
