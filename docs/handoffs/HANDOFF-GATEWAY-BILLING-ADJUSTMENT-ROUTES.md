# HANDOFF — Gateway allowlist missing for two live Billing read endpoints

**Owner:** Hoàng Anh / Gateway
**Status:** Open
**Reason:** `RouteAuthorizationFilter` is default-deny. Two live, always-reachable Billing
controllers have no matching rule, so a request that already passes Billing's own
`@PreAuthorize` is still rejected at the Gateway boundary.

**Found 2026-10-15** while building the frontend for `backend/billing-service/.../web/
PrescriptionAdjustmentController.java` (`GET /api/v1/billing/prescription-adjustments/{id}/
refunds-due`, roles `ADMIN`/`CASHIER`) and `backend/billing-service/.../web/
SurgeryCancellationController.java` (`GET /api/v1/billing/surgery-cancellations/{id}/
refunds-due`, roles presumably `ADMIN`/`CASHIER` — verify against its live `@PreAuthorize`).
`backend/gateway/src/main/java/com/mediflow/gateway/filter/RouteAuthorizationFilter.java` has
rules for every other live Billing path (`invoices/**`, `patient/**`, `revenue`,
`admin/outbox/**`) but none for `prescription-adjustments/**` or `surgery-cancellations/**`, so
both currently return the filter's default deny regardless of caller role.

## Required change

Add two `rule(...)` entries in `RouteAuthorizationFilter` mirroring the existing Billing rules'
style, matching each controller's exact live path/method/role set:

```
rule("/api/v1/billing/prescription-adjustments/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
rule("/api/v1/billing/surgery-cancellations/**", HttpMethod.GET, Roles.ADMIN, Roles.CASHIER),
```

Verify the exact role set against each controller's live `@PreAuthorize` before adding — this
handoff only reports the missing rule, not a verified final role list for the Surgery one.

## Acceptance

- `GatewayWebTest` (or equivalent) covers allowed role, denied role, wrong method, and
  extra/nested path for both new rules, matching the existing Billing route tests' style.
- No other Billing rule is widened or narrowed.
- `mvn -q -pl backend/gateway -am test` passes.

## Why this matters now

`prescription-adjustments` is a live MVP path — Rx compensation (`mediflow.billing.ledger.enabled`
+ `mediflow.billing.prescription-charge-consumer.enabled`) is already turned on in
`docker-compose.yml`. The frontend queue item `FE-BILLING-07` in
`frontend/docs/services/billing.md` stays `BLOCKED` on the prescription-adjustments half until
this is fixed; the Surgery-cancellation half stays out of MVP scope regardless (Surgery is
deferred), so only the prescription-adjustments rule is needed to unblock FE-BILLING-07's first
half.
