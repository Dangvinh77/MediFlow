# Handoff — Clinical service token must declare `type=service`

- **Producer of contract:** Gateway / shared security contract
- **Consumer to update:** Clinical Service (`clinical-service`)
- **Owner:** Vinh (`Dangvinh77` / `Harori`)

Phase 0 makes service authentication explicit. Organization now accepts a SYSTEM request only when
the signed JWT contains both `role=SYSTEM` and `type=service`. A token with only `role=SYSTEM`, or
an access/refresh token, is rejected.

## Required Clinical change

Update Clinical's service-token creation/forwarding interceptor to add:

```java
claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE)
```

The token must continue to use the shared secret, a service subject (for example
`clinical-service`), `role=SYSTEM`, a short expiry, and the propagated correlation ID. Do not use
an account login token for service-to-service calls.

## Acceptance tests

- Clinical → Organization staff lookup succeeds with `type=service`, `role=SYSTEM`.
- A Clinical token missing `type` is rejected by Organization.
- A Clinical token with `type=refresh` is rejected by Organization.
- The correlation ID is still propagated unchanged.

This handoff intentionally does not modify Clinical source code because that module is owned by a
different developer.

