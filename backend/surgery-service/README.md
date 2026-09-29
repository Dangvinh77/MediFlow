# Surgery Service

**Owner:** Huy (`LQHuy0210`)
**Coordinates:** port `8091`, database `mediflow_surgery`, base path `/api/v1/surgery`
**Design:** [service boundary](../../docs/ai/services/surgery.md) · [Care–Finance V2 candidate](../../docs/eproject_general_plan/backend-spec/care-finance-v2/11-surgery.md)

This module contains the platform foundation, Surgery domain models and initial persistence/reliability
slices: V1 Flyway schema, case JPA restore/history, draft schedule and resource reservation adapters,
checklist/consent/result persistence ports and adapters, schedule-history readback, durable command
receipts, inbox/outbox storage, and the service-auth Patient existence lookup. A generic outbox dispatcher
and Rabbit publisher transport are present behind both the Surgery and producer feature flags. There is
not yet a Surgery business API, event-specific publisher, consumer binding, or placeholder-success endpoint;
the feature flags remain disabled.

Cross-service episode/referral, billing, clearance, room/team and outcome contracts remain tracked in
the [Surgery decision handoff](../../docs/handoffs/HANDOFF-SURGERY-IMPLEMENTATION-DECISIONS.md).
Cross-service producers/consumers and clinical/Organization contracts remain gated by owner fixtures.
Root module registration, DB/Compose wiring and the Gateway route remain with their owners in the
[bootstrap handoff](../../docs/handoffs/HANDOFF-SURGERY-FOUNDATION-BOOTSTRAP.md).

Run the module suite with `mvn -f backend/surgery-service/pom.xml test`. On Docker Desktop 29
with this project's Testcontainers 1.19.8, use
`mvn -f backend/surgery-service/pom.xml '-Dapi.version=1.44' test` to run PostgreSQL and RabbitMQ
containers; without a compatible Docker daemon, container tests may be skipped. On 2026-09-29 this
command passed 128 tests with no failures, errors, or skips, including PostgreSQL 16.14 and RabbitMQ
3.13-alpine Testcontainers. That is module-local evidence only; root reactor and Gateway integration
remain unverified until shared owners complete the registered bootstrap handoff.
