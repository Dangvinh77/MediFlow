# Surgery Service

**Owner:** Huy (`LQHuy0210`)
**Coordinates:** port `8091`, database `mediflow_surgery`, base path `/api/v1/surgery`
**Design:** [service boundary](../../docs/ai/services/surgery.md) · [Care–Finance V2 candidate](../../docs/eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md)

This module contains the platform foundation, Surgery domain models and a first persistence/reliability
slice: V1 Flyway schema, case JPA restore/history, draft schedule and resource reservation adapters,
checklist/consent/result persistence ports and adapters, schedule-history readback, durable command
receipts, inbox/outbox storage, and the service-auth Patient existence lookup. The newest child
persistence slices have not yet been re-verified against PostgreSQL. It has
no business API, Rabbit publisher/consumer binding, or placeholder-success endpoint. The feature flag
remains disabled.

Cross-service episode/referral, billing, clearance, room/team and outcome contracts remain tracked in
the [Surgery decision handoff](../../docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md).
Cross-service producers/consumers and clinical/Organization contracts remain gated by owner fixtures.
Root module registration, DB/Compose wiring and the Gateway route remain with their owners in the
[bootstrap handoff](../../docs/handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md).

Run local unit/context tests with `mvn -f backend/surgery-service/pom.xml test`. On Docker Desktop 29
with this project's Testcontainers 1.19.8, use
`mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` to run PostgreSQL containers;
without a compatible Docker daemon, container tests are skipped. The root Maven reactor will include
this service after the shared owner registers it in `pom.xml`.
