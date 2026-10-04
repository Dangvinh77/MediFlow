# HANDOFF-INPATIENT-GATEWAY-ROUTE — Route the planned Inpatient API

- **Status:** OPEN — route definition, coarse RBAC rules, and local route tests are implemented;
  live Eureka health/discovery, downstream JWT, and real-controller smoke evidence remain.
- **Owner who must act:** Hoàng Anh (Gateway)
- **Producer:** `inpatient-service` (implemented REST API under `/api/v1/inpatient/**`)
- **Consumer:** `gateway`
- **Source:** [`docs/ai/services/inpatient.md`](../ai/services/inpatient.md) and the Gateway route alignment section in [`docs/ai/services/gateway.md`](../ai/services/gateway.md)

## Required behavior

Route `/api/v1/inpatient/**` to `lb://inpatient-service` through Eureka and preserve the request
path. The route must use the Gateway's existing JWT authorization and correlation propagation.
Inpatient now exposes business controllers. The Gateway route and route-level rules are present,
but the route is not marked live until the integration gate proves discovery, health, downstream
JWT, and correlation through the real Inpatient controller.

## Reason

The Inpatient service is owned by Vinh and does not modify Gateway production source. Its public
prefix is already implemented and recorded in the service design; this handoff tracks the remaining
Gateway-owned exposure without redefining any business DTO or payload.

## Acceptance criteria

- Gateway has a route predicate for `/api/v1/inpatient/**` with URI `lb://inpatient-service`.
- A Gateway route test asserts the route id, path predicate and Eureka service URI.
- Gateway rules mirror the endpoint roles declared by Inpatient and keep service-only paths
  inaccessible to human callers.
- Health, discovery, allowed/denied role, downstream JWT and correlation checks pass through the
  real Inpatient controller before this handoff is removed.

## Phase 7 evidence (2026-10-04)

- `GatewayRouteDefinitionTest` verifies the `inpatient-service` route id, `lb://inpatient-service`
  URI, path preservation, and that the Surgery prefix does not match this route.
- `GatewayWebTest` verifies the Inpatient GET/POST/PUT coarse role matrix and correlation/error
  propagation when discovery is unavailable.
- The route remains `OPEN`: no live Eureka + Inpatient deployment was available in this test run,
  so the final health/discovery/downstream-JWT acceptance criteria cannot be truthfully closed.
