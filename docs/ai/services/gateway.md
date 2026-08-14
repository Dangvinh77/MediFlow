# Service: gateway

**Source of truth:** `docs/eproject_general_plan/gateway.html`  
**Module:** `backend/gateway/` · **Base path:** `/api/v1/**` · **Technology:** Spring Cloud Gateway (WebFlux)

## Bounded Context

Owns:

- Authentication using JWT
- Authorization using RBAC
- Routing requests to target services
- Rate limiting
- Centralized request logging
- Correlation ID propagation

Does NOT own:

- Patient data
- Medical records
- Appointments
- Laboratory data
- Medication data
- Billing data
- Organizational data

The Gateway is the single entry point for external API requests. It does not own or persist business data.

## Responsibilities

The Gateway is responsible for:

1. Receiving external client requests.
2. Authenticating users.
3. Validating JWT access tokens.
4. Enforcing route-level authorization.
5. Applying rate limits.
6. Resolving target services through Eureka.
7. Forwarding requests to downstream services.
8. Propagating authenticated identity and tracing information.
9. Returning downstream responses to clients.

The Gateway must not implement business logic owned by downstream services.

## Data

The Gateway has **no business database**.

It may maintain runtime route configuration containing:

| Field           | Type      | Description                       |
| --------------- | --------- | --------------------------------- |
| `route_id`      | String    | Unique route identifier           |
| `path`          | String    | External API path pattern         |
| `service_name`  | String    | Eureka service name               |
| `method`        | ENUM      | HTTP method allowed by the route  |
| `roles_allowed` | Set<ENUM> | Roles allowed to access the route |

### HTTP Method Enum

Supported HTTP methods:

```text
GET
POST
PUT
PATCH
DELETE
OPTIONS
```

### Role Enum

The Gateway must use the same role values as:

```text
backend/common/security/Roles.java
```

The canonical role values are:

```text
ADMIN
DOCTOR
NURSE
PHARMACIST
CASHIER
LAB_TECH
MANAGER
PATIENT
SYSTEM
```

These values must remain synchronized across:

- Gateway authorization
- Organization Service
- JWT `role` claim
- Route configuration
- Spring Security configuration
- Downstream service authorization

The Gateway must not introduce a different role naming scheme.

## Gateway Endpoints

These endpoints belong to the Gateway itself.

| Method | Path                   | Request                  | Response                              | Authentication |
| ------ | ---------------------- | ------------------------ | ------------------------------------- | -------------- |
| POST   | `/api/v1/auth/login`   | `{ username, password }` | `{ accessToken, refreshToken, role }` | Public         |
| POST   | `/api/v1/auth/refresh` | `{ refreshToken }`       | `{ accessToken }`                     | Public         |
| GET    | `/actuator/health`     | -                        | Health status                         | Internal       |

## Login Flow

The Gateway does not access the Organization Service database directly.

Authentication flow:

```text
Client
   │
   │ POST /api/v1/auth/login
   │ { username, password }
   ▼
Gateway
   │
   │ POST /api/v1/org/accounts/verify
   ▼
Organization Service
   │
   │ Verify username
   │ Verify BCrypt password hash
   │ Check account status
   ▼
Organization Service
   │
   │ {
   │   accountId,
   │   staffId,
   │   departmentId,
   │   role
   │ }
   ▼
Gateway
   │
   │ Mint JWT
   ▼
Client
   │
   │ {
   │   accessToken,
   │   refreshToken,
   │   role
   │ }
```

The Gateway must never:

- Read the `TAI_KHOAN` table directly.
- Access the Organization Service database.
- Store user passwords.
- Store plaintext passwords.
- Implement password verification itself.

Password verification belongs to the Organization Service.

## JWT

The Gateway validates JWT access tokens before routing protected requests.

A valid access token must:

- Be correctly signed.
- Be unexpired.
- Contain a `role` claim.
- Contain the required identity information.
- Use the configured signing key or public key.

Expected claims include:

```text
sub
role
departmentId
exp
```

Where:

| Claim          | Description                           |
| -------------- | ------------------------------------- |
| `sub`          | Authenticated user/account identifier |
| `role`         | User role                             |
| `departmentId` | Department identifier when applicable |
| `exp`          | Token expiration timestamp            |

For new English naming conventions, internal Java objects and API models should use:

```text
userId
role
departmentId
expirationTime
```

However, JWT claim compatibility must be preserved across the existing system. If the current security contract uses `departmentId`, it must not be renamed independently in the Gateway without updating all consumers.

## Account Verification Contract

The Gateway authenticates users through the Organization Service endpoint:

```http
POST /api/v1/org/accounts/verify
```

Request:

```json
{
  "username": "admin",
  "password": "password"
}
```

Response:

```json
{
  "accountId": "uuid",
  "staffId": "uuid",
  "departmentId": "uuid",
  "role": "ADMIN"
}
```

The field names are part of the cross-service contract and must remain English.

## Authentication Rules

Every request must contain:

```http
Authorization: Bearer <token>
```

except:

```text
POST /api/v1/auth/login
POST /api/v1/auth/refresh
```

The Gateway must reject:

- Missing tokens
- Invalid tokens
- Expired tokens
- Malformed tokens
- Tokens without a valid `role` claim

Recommended response:

```http
401 Unauthorized
```

for authentication failures.

## Authorization Rules

Authentication and authorization are separate concerns.

### Authentication

Answers:

> Who is making this request?

The Gateway validates the JWT.

### Authorization

Answers:

> Is this role allowed to access this route?

The Gateway checks:

```text
JWT.role
      ↓
Route.roles_allowed
      ↓
Allow / Deny
```

A valid JWT does not automatically grant access to every route.

If the authenticated role is not allowed:

```http
403 Forbidden
```

must be returned.

## Route Configuration

Routes map external API paths to Eureka service names.

Example:

```yaml
routes:
  - route_id: patient-service
    path: /api/v1/patients/**
    service_name: PATIENT-SERVICE
    method:
      - GET
      - POST
      - PUT
      - DELETE
    roles_allowed:
      - ADMIN
      - DOCTOR
      - NURSE
```

The exact configuration format may vary, but the semantic fields must remain:

```text
route_id
path
service_name
method
roles_allowed
```

## Eureka Integration

The Gateway routes requests only to services registered in Eureka.

Example:

```text
Client
   │
   ▼
Gateway
   │
   │ Resolve PATIENT-SERVICE
   ▼
Eureka
   │
   │ Registered instance
   ▼
Patient Service
```

If the target service is not registered:

```text
Do not route the request.
```

The Gateway must not hard-code unavailable service instances as a fallback unless explicitly required by the deployment architecture.

## Rate Limiting

The Gateway applies:

```text
100 requests / minute / client IP
```

The rate limit applies to external client requests.

When the limit is exceeded, the Gateway should return:

```http
429 Too Many Requests
```

The rate-limiting implementation must be compatible with WebFlux and must not block reactive request processing.

## Request Headers

After successful JWT validation, the Gateway propagates authenticated identity information to downstream services.

Recommended headers:

```text
X-User-Id
X-User-Role
X-Department-Id
X-Correlation-Id
```

Example:

```http
X-User-Id: <user-id>
X-User-Role: DOCTOR
X-Department-Id: <department-id>
X-Correlation-Id: <correlation-id>
```

These headers are internal trust-boundary headers.

The Gateway must remove or overwrite client-supplied versions of these headers before forwarding the request so that clients cannot impersonate another user.

## Correlation ID

Every request should have a correlation ID.

If the client provides:

```http
X-Correlation-Id
```

the Gateway may preserve it according to the system tracing policy.

If none exists, the Gateway generates one.

The same correlation ID should be propagated to downstream services.

## Reactive Architecture

The Gateway uses:

```text
Spring Cloud Gateway
Spring WebFlux
Project Reactor
```

It must **not** use Spring MVC for request routing.

Use reactive components such as:

```text
GlobalFilter
GatewayFilter
ReactiveSecurityContext
Mono
Flux
```

Avoid blocking operations inside Gateway filters.

Blocking database calls, synchronous HTTP clients, or other blocking operations must not be introduced into the reactive request pipeline.

## Global Security Filter

JWT validation should be implemented through a global reactive security/filter mechanism.

Processing flow:

```text
Incoming Request
      │
      ▼
Correlation ID
      │
      ▼
Authentication Filter
      │
      ├── Public endpoint → continue
      │
      └── Protected endpoint
              │
              ▼
          Validate JWT
              │
              ├── Invalid → 401
              │
              ▼
          Extract role
              │
              ▼
          Route Authorization
              │
              ├── Forbidden → 403
              │
              ▼
          Rate Limit
              │
              ▼
          Eureka Route
              │
              ▼
          Downstream Service
```

## Downstream Security

The Gateway is **not the only security boundary**.

Downstream services must re-verify authorization according to:

```text
docs/ai/07-security-rbac.md
```

The architecture is:

```text
Client
   │
   ▼
Gateway
   │
   │ Authentication
   │ Route authorization
   ▼
Downstream Service
   │
   │ Re-verify authorization
   ▼
Business Logic
```

This prevents direct or misrouted internal requests from bypassing service-level authorization.

## Business Data Isolation

The Gateway must not own:

```text
PATIENT
STAFF
DEPARTMENT
APPOINTMENT
MEDICAL_RECORD
LAB_TEST
MEDICATION
BILLING
```

It only manages infrastructure/security concerns.

Business ownership remains with the corresponding bounded context.

## Architecture

The Gateway is implemented as a reactive edge service:

```text
Client
  │
  ▼
Gateway
  ├── Authentication
  ├── Authorization
  ├── Rate Limiting
  ├── Routing
  ├── Correlation ID
  └── Centralized Logging
          │
          ▼
     Eureka
          │
          ▼
 Downstream Services
```

Unlike ordinary business services, the Gateway does not follow the same persistence-oriented clean architecture because its primary responsibility is reactive request routing and security enforcement.

## Naming Convention

All technical names use English terminology.

### Configuration

Use:

```text
route_id
path
service_name
method
roles_allowed
```

### Java

Use English class and field names:

```text
RouteConfiguration
RouteDefinition
serviceName
rolesAllowed
routeId
```

### API JSON

Use English `camelCase`:

```json
{
  "username": "admin",
  "password": "password"
}
```

Login response:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "role": "ADMIN"
}
```

Refresh request:

```json
{
  "refreshToken": "..."
}
```

Refresh response:

```json
{
  "accessToken": "..."
}
```

## Error Handling

The Gateway should use standard HTTP status codes.

| Status | Meaning                                 |
| ------ | --------------------------------------- |
| `400`  | Invalid request                         |
| `401`  | Missing or invalid authentication       |
| `403`  | Authenticated but not authorized        |
| `404`  | Route/resource not found                |
| `429`  | Rate limit exceeded                     |
| `502`  | Downstream service unavailable          |
| `503`  | Gateway/service temporarily unavailable |

Errors should contain a consistent structure according to the project's global API error contract.

The Gateway must not expose internal stack traces or sensitive authentication information.

## Security Requirements

The Gateway must:

- Never log passwords.
- Never log JWT secrets.
- Never expose signing keys.
- Never store plaintext passwords.
- Never trust client-provided identity headers.
- Overwrite authenticated identity headers after JWT validation.
- Reject expired JWTs.
- Reject JWTs without a valid role.
- Apply route-level RBAC.
- Apply rate limiting.
- Route only to registered services.
- Use HTTPS in production.
- Propagate correlation IDs.
- Avoid leaking internal service information in error responses.

## Run Locally

Start Eureka:

```bash
mvn -pl backend/eureka-server -am spring-boot:run
```

Start the Gateway:

```bash
mvn -pl backend/gateway -am spring-boot:run
```

Default ports:

```text
Eureka: 8761
Gateway: 8080
```

The Gateway does not require a business database.

## Health Check

Gateway health endpoint:

```http
GET /actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

The health endpoint is intended for internal monitoring and infrastructure checks.

## Definition of Done

The Gateway is complete when:

- [ ] Spring Cloud Gateway WebFlux is used.
- [ ] Spring MVC is not used for Gateway routing.
- [ ] No business database is introduced.
- [ ] Eureka service discovery is configured.
- [ ] Only registered services can be routed to.
- [ ] `/api/v1/auth/login` is publicly accessible.
- [ ] `/api/v1/auth/refresh` is publicly accessible.
- [ ] Protected endpoints require a Bearer JWT.
- [ ] JWT signature is validated.
- [ ] JWT expiration is validated.
- [ ] JWT contains a valid `role` claim.
- [ ] Roles exactly match `Roles.java`.
- [ ] Route-level RBAC is enforced.
- [ ] `401 Unauthorized` is returned for authentication failures.
- [ ] `403 Forbidden` is returned for authorization failures.
- [ ] Rate limiting is set to 100 requests per minute per client IP.
- [ ] Authenticated identity headers are propagated.
- [ ] Client-supplied identity headers are overwritten.
- [ ] Correlation IDs are propagated.
- [ ] Downstream services re-verify authorization.
- [ ] Passwords and tokens are never logged.
- [ ] `/actuator/health` is available for internal monitoring.
- [ ] Reactive request processing contains no blocking operations.
