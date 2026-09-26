# HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP — Shared build, database and Gateway wiring

- **Status:** OPEN — request prepared by Huy; no shared implementation or live-route claim.
- **Requester / Surgery owner:** Huy (`LQHuy0210`).
- **Owners who must act:** repository/shared-build integrator (coordinate with Vinh) for root `pom.xml`, `scripts/init-databases.sql` and `docker-compose.yml`; Hoàng Anh for `backend/gateway/`. The shared integrator assignment needs explicit confirmation before editing shared production files.
- **Producer:** planned `backend/surgery-service/` (`surgery-service`, port `8091`, database `mediflow_surgery`).
- **Consumers:** Maven reactor, local PostgreSQL/Compose environment and Gateway clients.
- **Sources:** [Surgery service contract](../ai/services/surgery.md), [Surgery backend-spec draft](../eproject_general_plan/backend-spec/10-surgery.md), [microservice blueprint](../ai/04-microservice-blueprint.md), [Gateway design](../ai/services/gateway.md).

## Requested owner actions and acceptance evidence

| Scope / owner | Required change | Evidence before marking integrated |
|---|---|---|
| Root `pom.xml` / shared integrator | Register `backend/surgery-service` in `<modules>` once the module exists; do not alter dependency policy for other modules. | Reactor recognizes `-pl backend/surgery-service -am`; service tests and root reactor build run without a missing-module error. |
| `scripts/init-databases.sql` / shared integrator | Provision dedicated `mediflow_surgery` DB with the same least-privilege/config convention as existing services. | Fresh PostgreSQL data directory creates DB; an **existing** data directory is handled by an explicit, reviewed idempotent operational step. The init script alone does not rerun on an existing volume; do not delete volumes to make this test pass. Flyway migrates the service schema separately. |
| `docker-compose.yml` / shared integrator | Add Surgery service with port `8091`, its DB/Rabbit/Eureka/JWT environment, dependency/health conventions and no committed secret. | `docker compose config` validates; container starts, registers as `surgery-service`, passes health and connects only to its own DB. No claim that business workflows work from a healthy shell. |
| `backend/gateway/src/main/resources/application.yml` / Hoàng Anh | Route `Path=/api/v1/surgery/**` to `lb://surgery-service`, preserving the full path (no accidental prefix strip). Retain existing Gateway JWT and correlation behavior. | Gateway route test asserts route id, predicate, Eureka URI and path preservation; integration smoke checks auth denial/allowed role, downstream token re-verification and correlation when a real Surgery endpoint exists. |

## Integration sequence and boundary

1. Huy supplies a blueprint-conformant module and validated implementation-ready schema/contracts; the [H-01d draft](../eproject_general_plan/backend-spec/10-surgery.md) currently covers only policy-independent design. A bootable process alone is not an exposed business API.
2. The shared integrator and Gateway owner make their scoped changes in their own review path, with tests above. Huy does not edit these shared/Gateway production files through this handoff.
3. Test local module, reactor, fresh/existing DB bootstrap, Compose/Eureka and Gateway route as distinct acceptance states. Route configuration is not called **live** until a real endpoint and end-to-end security/correlation smoke pass.

This handoff does not approve `surgery.requested` producer semantics, a new charge-source event, room reservation, clinical clearance or any business DTO. Those remain in [Surgery implementation decisions](HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md) and the relevant canonical care-finance contracts. Once the requested changes and evidence land, move lasting route/bootstrap facts to the service/Gateway docs and remove this active handoff per the registry lifecycle.
