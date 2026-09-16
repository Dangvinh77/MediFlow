# HANDOFF — Report JWT access/refresh token contract

## Owner

Gateway/security owner (Report service consumes the contract but does not issue tokens).

## Finding

`report-service` verifies HS256 signature, expiry, subject and role, and now rejects an explicit
`type` claim other than `access`. During migration it still accepts legacy tokens that omit
`type`; because the gateway currently issues access and refresh tokens with the same claims and
signing key, those legacy refresh tokens can still be used as bearer tokens against report endpoints.

## Required contract change

1. Add the shared `type` token-use claim to the common JWT contract (matching the gateway spec).
2. Gateway must issue `type=access` and `type=refresh` values explicitly and preserve the distinction in
   every token-refresh flow.
3. Report (and other resource services) must accept only `type=access`; refresh endpoints remain
   the only consumers of `type=refresh`.
4. Add gateway and resource-service tests proving a refresh token receives HTTP 401 while an access
   token with the same subject/role is accepted.

## Scope boundary

This handoff is intentionally not implemented in `backend/gateway` or `backend/common` from the
Report task. Those modules belong to another owner and changing them without a task-scoped
override would violate the repository ownership rules.
