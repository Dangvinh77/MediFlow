# HANDOFF — Organization account verification for Gateway

> **Mandatory for coding agents:** read this file before replacing Gateway demo authentication or
> changing Organization's account verification contract.

- **Producer:** `organization-service` — Hoàng Anh (`TranHoangAnh94`)
- **Consumer:** `gateway` — Hoàng Anh (`TranHoangAnh94`)
- **Implemented producer endpoint:** `POST /api/v1/org/accounts/verify`
- **Gateway implementation:** PR2 replaces the hard-coded `DEMO_USERS` flow with a reactive
  Organization call; PR3 keeps that integration and consumes the standardized `ApiResponse`
  envelope. Deployment still requires `MEDIFLOW_JWT_SECRET` in both services.

## Available wire contract

The internal request is:

```json
{
  "username": "admin",
  "password": "password"
}
```

The successful response is:

```json
{
  "success": true,
  "data": {
    "accountId": "00000000-0000-0000-0000-000000000001",
    "staffId": "00000000-0000-0000-0000-000000000002",
    "departmentId": "00000000-0000-0000-0000-000000000003",
    "patientId": null,
    "role": "ADMIN"
  },
  "error": null,
  "timestamp": "2026-09-18T00:00:00Z",
  "correlationId": "00000000-0000-0000-0000-000000000010"
}
```

Organization accepts only a signed JWT carrying `type=service` and `role=SYSTEM`. Both services must
use the same externally supplied `MEDIFLOW_JWT_SECRET`. Invalid username, password, or inactive account must be
reported as invalid credentials without revealing which value failed. Organization returns the
internal `422 AUTH_INVALID_CREDENTIALS` contract; Gateway maps it to public HTTP `401`.

## Required Gateway work

1. Replace `AuthController.DEMO_USERS` and plaintext comparison with a non-blocking call to
   Organization through service discovery. Do not access Organization's database.
2. Authenticate the internal request with a short-lived service JWT whose subject identifies the
   Gateway and whose role is `SYSTEM`; never reuse the login caller's credentials.
3. Mint access and refresh JWTs from the returned `accountId`, `role`, and optional
   `staffId`/`departmentId`/`patientId` according to the Gateway JWT contract. `sub` is always
   `accountId`; `patientId` is never inferred from `sub`.
4. Preserve or create `X-Correlation-Id` on the internal call.
5. Map confirmed invalid credentials to HTTP 401. Map timeout, circuit-open, malformed response,
   and Organization 5xx to an upstream/service-unavailable response, not HTTP 401.
6. Remove hard-coded usernames and plaintext passwords from production source after the real flow
   is enabled.

## Acceptance criteria

- Login succeeds using an active Organization account and BCrypt password verification.
- Unknown, wrong-password, inactive-account, and unavailable-Organization cases are distinct in tests.
- Gateway stays reactive; the Organization client does not block a Netty event-loop thread.
- Service JWT signature, role, subject, expiry, and correlation propagation have contract tests.
- No password, JWT, or signing secret is logged.
- `auth.http` and Organization's HTTP collection match the live contract.

## Gateway implementation status (PR2 + PR3)

- Gateway calls Organization through a load-balanced `WebClient`.
- The internal request uses a short-lived JWT with `type=service`, `sub=gateway`, and `role=SYSTEM`.
- Human login credentials are sent only in the verification request body; the caller's bearer token
  is never forwarded to Organization.
- Organization's confirmed invalid credentials return internal `422 AUTH_INVALID_CREDENTIALS`; the
  Gateway maps that result to public `401 AUTH_INVALID_CREDENTIALS`.
- Organization/network failures return `503 AUTH_UPSTREAM_UNAVAILABLE`.
- Access and refresh tokens use `type=access|refresh`, `sub=accountId`, and include optional
  `staffId`, `departmentId`, and `patientId` claims when present.
- `X-Correlation-Id` is preserved or generated and propagated to Organization.
