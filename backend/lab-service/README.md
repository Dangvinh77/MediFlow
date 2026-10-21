# lab-service

## Product scope — 2026-10-17

Part of **MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management**. Lab owns outpatient test requests, processing and results; laboratory lifecycle and payment authority remain separate.

Canonical [system design](../../docs/plans/01-system-design.md), [contracts](../../docs/plans/03-contracts.md) and [acceptance runbook](../../docs/plans/06-acceptance-runbook.md) define current scope. Existing APIs, feature flags and historical test counts below do not certify full workflow release acceptance.


Lab tests and results (`LAB_TEST`, `LAB_RESULT`).

Reference: [`docs/ai/services/lab.md`](../../docs/ai/services/lab.md) · [business flows](../../docs/plans/02-business-flows.md) · [Vinh handoff/evidence](../../docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md).

- **Port:** 8084 · **Base path:** `/api/v1/lab` · **DB:** `mediflow_lab` (PostgreSQL)
- **Owns tables:** `LAB_TEST`, `LAB_RESULT`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`, dependencies inward only.

## Status

The domain, application, persistence, HTTP controller, JWT security and error handling layers are
in place:
`LabTest` owns its `LabResult` values, the application service handles creation, results,
lifecycle and explicit payment updates, and published events carry the standard envelope. Flyway
and JPA adapters persist aggregate results, use locked mutation reads and provide an insert-only
processed-event ledger.
The controller exposes the six specified endpoints with validation, role declarations, standard
response envelopes and web-slice coverage.
Bearer tokens are verified locally using the gateway's shared HS256 secret; invalid or missing
tokens and forbidden roles return the common API error envelope.
Domain and validation failures are mapped to the specified HTTP statuses with stable error codes.

The RabbitMQ publisher sends JSON events to the durable shared exchange only after transaction
commit. Inbound consumers use explicit producer IDs; Billing supplies `labTestIds` and Clinical
supplies record identifiers. Cross-service fields and remaining target workflow work are recorded
in the [event catalog](../../docs/ai/06-events-rabbitmq.md), the
[care-finance contracts](../../docs/handoffs/care-finance/README.md), and the
[active handoff registry](../../docs/handoffs/README.md).

Integration adapters are organized under:

```
infrastructure/client   messaging/consumer
```

## Run locally

1. Create the database once: `CREATE DATABASE mediflow_lab;`
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ must be running on localhost:5672 (or set `MEDIFLOW_RABBIT_*`).

```bash
mvn -pl backend/lab-service -am spring-boot:run
```

Swagger UI: http://localhost:8084/swagger-ui.html

## Events

- **Publish:** `lab.request.created`, `lab.request.cancelled`, `lab.result.created`

### Cancellation audit and Billing adjustment

The V1 cancellation command verifies the supplied actor against the authenticated staff identity.
It stores `cancelledBy`, `cancellationReason` and `cancelledAt` with the test and appends
`lab.request.cancelled` version 1 in the same transaction. The response exposes these additive
audit fields. Historical canceled rows retain null audit fields; migration V5 invents no history.
Repeating the same actor/reason retains the original timestamp and emits no second fact;
conflicting retries and cancellation of completed tests are rejected.

Before rollout, review historical V1 cancellations in the Lab-owned store:

```sql
SELECT test_id, source_order_id, care_episode_id, updated_at
FROM lab_test
WHERE care_contract_version = 1 AND status = 'CANCELLED' AND cancelled_by IS NULL;
```

Reconcile these exact source IDs through Billing's APIs/approved exports. Do not join another
context's database, infer an actor from a patient, regenerate historical facts, or mark money
refunded solely because the Lab status is canceled. New guards protect future commands; they
do not reconstruct cancellation evidence that was never recorded.

The exact source identity, pricing snapshot and immutable cancellation audit are locked by
`src/test/resources/contracts/lab-request-cancelled-v1/lab.request.cancelled.v1.json` on both
producer and Billing consumer. Billing applies cancellation before any subsequent collection,
retains cancellation arriving before creation, and records the remaining refundable obligation
for paid charges. This fact does not assert cash was returned; a cashier records an actual
full or partial refund against the original payment and the approved amount.

### Current Billing execution authority

`MEDIFLOW_CARE_FINANCE_V2` defaults to `false`. With the feature enabled, starting a normal test
requires an exact local LAB_TEST grant and a successful Billing
`POST /api/v1/billing/financial-clearances/{clearanceId}/execution-starts`; the test UUID is both
the stable operation ID and the source ID. Billing serializes this admission against refunds and
rechecks current ledger eligibility, including when replaying the same start after a local retry.
If Billing has revoked the grant, the target identity differs, or Billing is unavailable, Lab
fails closed. A validated, explicitly audited emergency override follows its own path and does not
depend on a clearance snapshot.

Every normal results write also calls Billing's current
`GET /api/v1/billing/financial-clearances/{clearanceId}/lookup`. Lab requires `eligible=true`,
`sourceType=LAB_TEST`, and `sourceId` equal to that exact test, along with matching invoice,
account, patient, outpatient episode, purpose and grant timing. The cached local grant alone cannot
authorize a start or results change. A successful start is a durable admission; if execution is
abandoned, the audited `lab.request.cancelled` fact is the evidence Billing needs to review any
subsequent refund. Billing can accept an approved full or partial refund up to the remaining net
obligation; the cancellation fact itself does not represent returned cash.

The Lab producer V1 grant example is
`src/test/resources/contracts/financial.clearance.granted.v1.json`; Billing's corresponding
single-target LAB_TEST ledger example is
`billing-service/src/test/resources/contracts/ledger-v1/clearance-lab.json`. The V1 cancellation
fixture is mirrored at
`src/test/resources/contracts/lab-request-cancelled-v1/lab.request.cancelled.v1.json` and
`billing-service/src/test/resources/contracts/lab-request-cancelled-v1/lab.request.cancelled.v1.json`.
- **Subscribe:** `medicalrecord.created` (idempotently acknowledged; no test is auto-created because
  the canonical Clinical payload does not contain an explicit lab order), `payment.completed`

Topic exchange `mediflow.events`; see [`docs/ai/06-events-rabbitmq.md`](../../docs/ai/06-events-rabbitmq.md). Consumers must be idempotent (dedupe on `eventId`).

`lab.result.created` uses `labId` as its canonical test identifier (sourced from the API's
`testId`) and includes `labType` and `performedDate` for billing, notification and reporting.
The current `payment.completed` producer does not provide a lab/test id, so the future consumer
must not infer one from `invoiceId` or `recordId`; `ReactToClinicalUseCase` and
`ProcessedEventPort` are declared for that later adapter work.

## Tests

### Outbox failure and recovery

Flyway V4 adds delivery scheduling and quarantine without changing existing event IDs, payloads
or published history. The dispatcher excludes future retries and quarantined rows from its
100-row batch. Defaults: five failed send attempts total, exponential waits of 1, 2, 4, 8 seconds
(capped at 60 seconds when a larger retry budget is configured). Configure using
`MEDIFLOW_LAB_OUTBOX_MAX_RETRIES`, `MEDIFLOW_LAB_OUTBOX_INITIAL_BACKOFF_MS` and
`MEDIFLOW_LAB_OUTBOX_MAX_BACKOFF_MS`. Historical rows already at the configured limit are
quarantined without another send. A NACK, return or missing confirm never marks a row published.

Monitor `quarantined_at IS NOT NULL` and the redacted `last_failure_code`; quarantine emits an
error containing event ID/type and retry count, without patient data. After fixing the binding
or broker problem, record the incident and snapshot the selected row before an operator replay.
On the Lab-owned database only, replay exactly the reviewed unpublished event in a transaction:

```sql
BEGIN;
SELECT event_id, event_type, retry_count, last_failure_code
FROM lab_outbox_event WHERE event_id = :reviewed_event_id FOR UPDATE;
UPDATE lab_outbox_event
SET retry_count = 0, next_attempt_at = NULL, quarantined_at = NULL, last_failure_code = NULL
WHERE event_id = :reviewed_event_id AND published_at IS NULL AND quarantined_at IS NOT NULL;
-- Require exactly one affected row; otherwise ROLLBACK and investigate.
COMMIT;
```

Do not regenerate identity/payload/correlation or reset published history/inbox dedupe. A crash
after broker acceptance but before the DB mark can redeliver the same event; consumers must
remain idempotent. Replay renews the bounded retry budget and retains the original event.

```bash
mvn -pl backend/lab-service test        # unit/contract tests + PostgreSQL slices when Docker is available
mvn -pl backend/lab-service verify
```
