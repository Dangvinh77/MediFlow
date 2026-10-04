# HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP — Shared build, database and Gateway wiring

- **Status:** OPEN — shared reactor, database and Compose registration completed and verified on
  2026-10-02; Huy must expose a real business API before Hoàng Anh adds Gateway routing.
- **Requester / Surgery owner:** Huy (`LQHuy0210`).
- **Owners who must still act:** Huy for the Surgery business endpoint/role matrix, then Hoàng Anh
  for `backend/gateway/`. Vinh's shared-build integration work is complete.
- **Producer:** implemented local `backend/surgery-service/` foundation (`surgery-service`, port
  `8091`, database `mediflow_surgery`), now registered in the shared reactor and local runtime.
- **Consumers:** Maven reactor, local PostgreSQL/Compose environment and Gateway clients.
- **Sources:** [Surgery service contract](../ai/services/surgery.md), [Surgery backend-spec draft](../eproject_general_plan/backend-spec/10-surgery.md), [microservice blueprint](../ai/04-microservice-blueprint.md), [Gateway design](../ai/services/gateway.md).

## Requested owner actions and acceptance evidence

| Scope / owner | Required change | Evidence before marking integrated |
|---|---|---|
| Root `pom.xml` / shared integrator — **COMPLETE** | Register `backend/surgery-service` in `<modules>` once the module exists; do not alter dependency policy for other modules. | Reactor recognizes `-pl backend/surgery-service -am`; the 147-test Surgery suite and full 1,601-test reactor pass without failures, errors or skips. |
| `scripts/init-databases.sql` / shared integrator — **COMPLETE** | Provision dedicated `mediflow_surgery` DB with the same least-privilege/config convention as existing services. | A fresh PostgreSQL data directory created all ten service databases, including `mediflow_surgery`. Restarting with the existing data directory skipped initialization and retained it. Flyway then applied Surgery V1 independently. |
| `docker-compose.yml` / shared integrator — **COMPLETE** | Add Surgery service with port `8091`, its DB/Rabbit/Eureka/JWT environment, dependency/health conventions and no committed secret. | `docker compose config -q` passes. The service became healthy on `8091`, registered in Eureka as `SURGERY-SERVICE`/`UP`, applied Flyway V1 and opened JDBC connections only to `mediflow_surgery`. This is platform evidence, not a business-workflow claim. |
| Surgery business API / Huy, then `backend/gateway/src/main/resources/application.yml` / Hoàng Anh | Huy first supplies a real controller endpoint and exact role matrix. Hoàng Anh then routes `Path=/api/v1/surgery/**` to `lb://surgery-service`, preserving the full path. Retain existing Gateway JWT and correlation behavior. | Surgery web test proves downstream authorization. Gateway route test then asserts route id, predicate, Eureka URI and path preservation; integration smoke checks denied/allowed roles, downstream token re-verification and correlation. |

## Integration sequence and boundary

1. Huy has supplied the blueprint-conformant module and V1 schema plus policy-independent
   application/persistence slices. This satisfies the module prerequisite, not the unapproved
   cross-service business contracts or a live business API.
2. The shared integrator and Gateway owner make their scoped changes in their own review path, with
   the tests above. Huy does not edit these shared/Gateway production files through this handoff.
3. Test local module, reactor, fresh/existing DB bootstrap and Compose/Eureka first. Add the Gateway
   route only with a real business endpoint and end-to-end security/correlation smoke; health alone
   is not a live workflow claim.

## Existing PostgreSQL volume operation

The init script runs only when PostgreSQL creates a data directory. For an existing local volume,
check before creating the Surgery database; do not remove the volume to replay initialization:

```bash
if ! docker compose exec -T postgres psql -U postgres -d postgres -Atqc \
  "SELECT 1 FROM pg_database WHERE datname = 'mediflow_surgery'" | grep -qx 1; then
  docker compose exec -T postgres createdb -U postgres mediflow_surgery
fi
```

Starting `surgery-service` after that lets its own Flyway migration create and version the schema.

This handoff does not approve `surgery.requested` producer semantics, a new charge-source event, room reservation, clinical clearance or any business DTO. Those remain in [Surgery implementation decisions](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) and the relevant canonical care-finance contracts. Keep this handoff active only for the remaining Gateway action; when its evidence lands, move the lasting route fact to the Gateway/service docs and remove this handoff per the registry lifecycle.
