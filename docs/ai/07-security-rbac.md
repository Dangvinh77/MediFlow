# 07 — Security & RBAC

## Authentication flow

1. Client calls `POST /api/v1/auth/login` on the **gateway** with `{ username, password }`.
2. Gateway (or an auth component) verifies credentials and issues a **JWT access token** + **refresh token**. The access token carries claims: `sub` (user id), `role`, `exp`.
3. Client sends `Authorization: Bearer <token>` on every subsequent request.
4. Gateway validates the token **once** (signature + expiry + role claim) before routing.
5. Downstream services **re-verify** the JWT (defense in depth) and enforce endpoint-level roles. Never trust a request just because it came through the gateway.

`POST /api/v1/auth/refresh` exchanges a valid refresh token for a new access token.

## Authorization (RBAC)

- Roles: `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, `SYSTEM`.
- **Every endpoint declares its allowed roles.** Use method security:
  ```java
  @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
  @PostMapping
  public ApiResponse<PatientDTO> create(@Valid @RequestBody CreatePatientRequest req) { ... }
  ```
- Role → endpoint matrix comes straight from each service's design doc (`services/*.md`, "API Endpoints" table). Keep them identical.
- Only these are unauthenticated: gateway `/api/v1/auth/login`, `/api/v1/auth/refresh`, and `/actuator/health`.

## Rules

- **Rate limiting** at the gateway: max 100 requests/minute per client IP (design doc). Use the gateway's request-rate limiter.
- **Least privilege:** if a role isn't listed for an endpoint, it is denied. Default deny.
- **JWT secret / keys** come from externalized config / env vars — never hard-coded, never committed. Local dev uses a throwaway secret in `application-local.yml` (gitignored).
- **Do not log** tokens, passwords, or full PII (CMND/CCCD, BHYT, phone) at INFO. Mask when logging is necessary.
- `SYSTEM` role is for service-to-service / event-triggered actions (e.g. notification sending), not for human users.
- `PATIENT` role can only read its **own** resources — enforce ownership checks in the service layer, not just the role.

## Where security config lives

- `config/SecurityConfig.java` in every service: JWT resource-server / filter, `@EnableMethodSecurity`, stateless sessions, permit-list for actuator health + swagger (dev), deny everything else.
- Shared claim names & role constants live in `common` so all services agree.
