# 09 — gateway — Care & Finance V2 target

**Owner:** Hoàng Anh (`TranHoangAnh94`)

**Module:** `backend/gateway`

**Base path:** `/api/v1`

**Status:** implementation-ready route target; Surgery route remains disabled until service health passes

## 1. Sources and boundary

- [`mediflow-care-finance-redesign.html`](../../../architecture/mediflow-care-finance-redesign.html)
- [`CONTRACT-IDENTITY-LOOKUP-01`](../../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
- [`HANDOFF-INPATIENT-GATEWAY-ROUTE`](../../../handoffs/HANDOFF-INPATIENT-GATEWAY-ROUTE.md)
- [CURRENT Gateway spec](../09-gateway.md)

Gateway authenticates, authorizes, routes and propagates identity/correlation. It does not calculate
clearance, settlement, admission state, surgery readiness or financial totals.

## 1.1 V1 compatibility decisions

The current public `/api/v1` contract keeps the already deployed trust-boundary headers
`X-User-Id`, `X-User-Role`, `X-Patient-Id`, `X-Staff-Id`, `X-Department-Id` and
`X-Correlation-Id`. The proposed `X-Account-Id`/`X-Role` names are reserved for a separately versioned
contract; they must not replace the V1 names without a consumer migration and fixture update.

The current Gateway transport error code `GATEWAY_UPSTREAM_UNAVAILABLE` is also retained for V1.
`DOWNSTREAM_UNAVAILABLE` is descriptive terminology in the target design, not a silent wire-code
rename. Any new error code requires a versioned contract and regression fixtures.

## 2. Additive route configuration

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: inpatient-service
          uri: lb://inpatient-service
          predicates:
            - Path=/api/v1/inpatient/**
        - id: surgery-service
          uri: lb://surgery-service
          predicates:
            - Path=/api/v1/surgery/**
```

The Inpatient route becomes active only after Eureka health and downstream auth tests pass. Surgery
route configuration may merge disabled behind `mediflow.routes.surgery.enabled=false`; it cannot be
advertised as live before the module is registered and healthy.

## 3. Public role matrix

| Path | Methods | Allowed roles |
|---|---|---|
| `/api/v1/inpatient/**` | GET | ADMIN, MANAGER, DOCTOR, NURSE |
| `/api/v1/inpatient/**` | POST, PUT | ADMIN, DOCTOR, NURSE according to downstream endpoint |
| `/api/v1/surgery/**` | GET | ADMIN, MANAGER, DOCTOR, NURSE |
| `/api/v1/surgery/**` | POST, PUT | ADMIN, DOCTOR; MANAGER only for schedule operations |
| `/api/v1/org/**/lookup`, `/api/v1/patients/*/exists` | all | internal service token only |

Gateway coarse authorization never replaces endpoint-level `@PreAuthorize` in downstream services.

## 4. Identity propagation

Human access tokens use:

```text
sub=accountId
type=access
role=<one canonical role>
patientId? staffId? departmentId?
```

Gateway removes client-supplied identity headers and writes trusted V1 headers `X-User-Id`,
`X-User-Role`, `X-Patient-Id`, `X-Staff-Id`, `X-Department-Id` only from verified claims. A missing
claim remains missing; Gateway never derives a business ID from `sub`.

Service-to-service tokens use `type=service`, `role=SYSTEM` and service subject. Internal lookup
paths reject human tokens even when routed inside the network.

## 5. Correlation and failure behavior

- Accept a valid incoming `X-Correlation-Id` or generate one UUID.
- Propagate it downstream and return it in all success/error responses.
- Discovery unavailable/timeout maps to `503 GATEWAY_UPSTREAM_UNAVAILABLE` in V1.
- Authentication failure returns `401`; authenticated but forbidden returns `403`.
- Gateway never converts a downstream business `409/422` to `500`.
- Request/response logs exclude JWTs and medical/financial payload bodies.

## 6. Filter order

```text
correlation → rate limit → JWT verification → identity-header sanitization/propagation
→ route authorization → downstream routing → common error mapping
```

Reactive filters must not call blocking repositories or use `block()`.

## 7. Required tests

| Rule | Required test |
|---|---|
| Inpatient route resolves through discovery | `inpatientRoute_healthyService_forwards` |
| Surgery disabled before health gate | `surgeryRoute_disabled_returns404` |
| patient cannot access staff inpatient board | `inpatientRoute_patientRole_forbidden` |
| internal lookup rejects human token | `identityLookup_accessToken_forbidden` |
| supplied identity headers overwritten | `identityHeaders_spoofed_valuesRemoved` |
| `sub` not reused as staff/patient | `identityHeaders_missingExplicitClaim_staysMissing` |
| correlation survives success/error | `correlation_propagatedBothDirections` |
| downstream 422 preserved | `businessError_unprocessableEntity_preserved` |
| route remains nonblocking | `careFinanceRoutes_noBlockingCall` |

## 8. Rollout and Definition of Done

1. Add route configuration behind independent feature flags.
2. Add coarse RBAC and internal-path deny rules.
3. Verify Eureka discovery, downstream JWT verification and correlation under Docker Compose.
4. Enable Inpatient; enable Surgery only after its module is healthy.

Done means browser/mobile clients call only same-origin `/api/*`, both target services are protected
twice (Gateway and downstream), and no domain decision exists in Gateway.
