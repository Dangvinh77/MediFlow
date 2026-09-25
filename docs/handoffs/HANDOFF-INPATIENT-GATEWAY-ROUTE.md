# HANDOFF-INPATIENT-GATEWAY-ROUTE — Route the planned Inpatient API

- **Status:** OPEN
- **Owner who must act:** Hoàng Anh (Gateway)
- **Producer:** `inpatient-service` (planned REST API under `/api/v1/inpatient/**`)
- **Consumer:** `gateway`
- **Source:** [`docs/ai/services/inpatient.md`](../ai/services/inpatient.md) and the Gateway route alignment section in [`docs/ai/services/gateway.md`](../ai/services/gateway.md)

## Required behavior

Route `/api/v1/inpatient/**` to `lb://inpatient-service` through Eureka and preserve the request
path. The route must use the Gateway's existing JWT authorization and correlation propagation.
This foundation exposes no business controller, so the route is not considered live until the
Gateway change and its route test are merged.

## Reason

The Inpatient foundation is owned by Vinh and does not modify Gateway production source. The
planned public prefix is already recorded in the Inpatient service design; this handoff tracks the
remaining change in the Gateway-owned module without defining any business DTO or payload.

## Acceptance criteria

- Gateway has a route predicate for `/api/v1/inpatient/**` with URI `lb://inpatient-service`.
- A Gateway route test asserts the route id, path predicate and Eureka service URI.
- The Gateway continues to enforce its existing authenticated route policy and forwards correlation
  metadata.
- The route is only called live after Inpatient endpoints exist and health, discovery, RBAC,
  downstream JWT and correlation checks pass.
