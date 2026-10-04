# Inpatient Service

Inpatient owns admissions, beds and assignments, append-oriented treatment entries, external-order
references, medical discharge and administrative close. Core V1 is implemented on port `8090`
with PostgreSQL/Flyway/JPA, transactional outbox, guarded RabbitMQ consumers, Eureka registration,
stateless JWT security and canonical `X-Correlation-Id` propagation.

Gateway routes `/api/v1/inpatient/**` to `lb://inpatient-service`. Cross-service RabbitMQ
integrations remain disabled until the named producer and consumer owners pass canonical same-byte
fixtures; a live HTTP route does not activate those contracts.

## Run and verify

Provide the same HS256 signing secret used by Gateway (`MEDIFLOW_JWT_SECRET`, at least 32 UTF-8
bytes), start PostgreSQL, RabbitMQ and Eureka, then run:

```bash
mvn -pl backend/inpatient-service -am spring-boot:run
mvn -q -pl backend/inpatient-service -am test
```

The service uses the dedicated `mediflow_inpatient` database. The Compose service is registered in
the root `docker-compose.yml`; the test profile disables external infrastructure.

## Design and contracts

- Service responsibilities and endpoint/state rules:
  [`docs/ai/services/inpatient.md`](../../docs/ai/services/inpatient.md)
- Implementation-ready backend spec:
  [`10-inpatient.md`](../../docs/eproject_general_plan/backend-spec/care-finance-v2/10-inpatient.md)
- Care-finance contracts:
  [`docs/ai/16-care-finance-integration-contracts.md`](../../docs/ai/16-care-finance-integration-contracts.md)
- Active external blockers: [handoff registry](../../docs/handoffs/README.md)
- Billing producer work:
  [Inpatient deposit/settlement handoff](../billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md)
