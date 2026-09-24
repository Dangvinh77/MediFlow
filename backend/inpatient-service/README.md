# Inpatient Service

This module is a **preliminary bootable foundation only** for the approved Inpatient bounded
context. It provides the Spring Boot runtime on port `8090`, PostgreSQL/Flyway/JPA configuration,
Eureka and RabbitMQ configuration, actuator health/info, OpenAPI assets, stateless JWT security and
canonical `X-Correlation-Id` propagation for every HTTP request.

There are no business endpoints, admission model, DTOs, business migrations, events, publishers or
consumers. Business DDL, API shapes and events require the future implementation-ready Inpatient
spec. The Inpatient business contracts remain `DESIGN_READY`; the shared identity contract has
its separately tracked `PARTIAL` status.

## Run

Provide the same HS256 signing secret used by Gateway (`MEDIFLOW_JWT_SECRET`, at least 32 UTF-8
bytes), start PostgreSQL, RabbitMQ and Eureka, then run:

```bash
mvn -pl backend/inpatient-service -am spring-boot:run
```

The service uses `mediflow_inpatient`. The Compose service is included in the root `docker-compose.yml`.
The test profile disables external infrastructure and is used by
`mvn -q -pl backend/inpatient-service -am test`.

## Design and contracts

- Service responsibilities and planned behavior: [`docs/ai/services/inpatient.md`](../../docs/ai/services/inpatient.md)
- Care-finance contracts: [`docs/ai/16-care-finance-integration-contracts.md`](../../docs/ai/16-care-finance-integration-contracts.md)
- Gateway route handoff: [`docs/handoffs/HANDOFF-INPATIENT-GATEWAY-ROUTE.md`](../../docs/handoffs/HANDOFF-INPATIENT-GATEWAY-ROUTE.md)
