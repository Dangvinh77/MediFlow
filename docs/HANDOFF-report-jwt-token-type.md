# HANDOFF — Report must require an access-token type

**Status (2026-09-24): OPEN in Report only.** Gateway/Common now issue and share explicit
`type=access|refresh|service` claims. The remaining gap is Report's legacy fallback that accepts a
signed token when `type` is absent.

## Owner

Report owner — Huy (`LQHuy0210`).

## Finding

`report-service` verifies HS256 signature, expiry, subject and role and rejects an explicit `type`
other than `access`. It still accepts legacy tokens that omit `type`; the shared contract no longer
needs that compatibility path.

## Required contract change

1. Read `JwtClaims.TYPE` and require the value `JwtClaims.ACCESS_TOKEN_TYPE`.
2. Remove the branch that accepts a missing token type.
3. Add tests proving missing, refresh and service token types are rejected while an access token
   with the same subject/role is accepted.

## Scope boundary

No Gateway/Common change is required unless Report exposes a new incompatibility in their existing
typed-token fixtures.
