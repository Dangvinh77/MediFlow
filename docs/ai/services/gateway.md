# Service: gateway

**Source of truth:** `EProject/gateway.html`
**Module:** `backend/gateway/` · **Base path:** `/api/v1/**` (routes everything) · **Tech:** Spring Cloud Gateway (WebFlux)

## Bounded context
Owns: authentication (JWT), authorization (RBAC), routing to target services, rate limiting, centralized logging.
Does NOT own: any medical/business data.

## Data
No business DB. Only route config (`route_id`, `path`, `service_name`, `method`, `roles_allowed`) + service list from Eureka.

## Endpoints (gateway's own)
| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/login` `{username,password}` → `{accessToken,refreshToken,role}` | Public |
| POST | `/api/v1/auth/refresh` `{refreshToken}` → `{accessToken}` | Public |
| GET | `/actuator/health` | Internal |

## Rules
- JWT must be valid (unexpired) and contain a `role` claim.
- Every request except login/refresh needs `Authorization: Bearer <token>`.
- Rate limit: 100 req/min per client IP.
- Only route if the target service is registered in Eureka.

## Notes for implementers
- WebFlux, not MVC — use reactive gateway filters. Validate JWT in a global filter; propagate claims (user id, role, correlation id) as headers to downstream services.
- Downstream services still re-verify (see `07-security-rbac.md`).
