# clinical-service

**Khoa Khám bệnh** — the outpatient examination workflow end to end: booking an appointment, the examination, the medical record and its diagnoses.

Reference: [service rules](../../docs/ai/services/clinical.md) · [design](../../docs/eproject_general_plan/clinical-service.html) · [implementation spec](../../docs/eproject_general_plan/backend-spec/03-clinical.md).

- **Port:** 8082 · **Base paths:** `/api/v1/appointments`, `/api/v1/records` · **DB:** `mediflow_clinical`
- **Owns tables:** `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS`, `ATTACHED_RESULT`
- **Architecture:** clean architecture per [blueprint](../../docs/ai/04-microservice-blueprint.md).

## Why appointments and records are one service

They are **one department's single workflow**: book, examine, record. Separating them would buy
nothing but distributed-systems overhead:

- `MEDICAL_RECORD.appointment_id` points straight at `APPOINTMENT`. In one service that is a real foreign key; split apart it becomes a bare UUID across a network boundary.
- Setting an appointment to `ARRIVED` when its record is created belongs in **one local transaction**: the patient showed up and was examined.

Kept together, that is a local transaction. The two URL prefixes remain distinct, so the public API
is exactly what `05-api-conventions.md` specifies.

## Status

**Application, persistence, HTTP API, JWT security and error handling implemented.** Rich domain
models enforce
appointment dates/hours and transitions, required references, diagnosis names/ICD codes and the
nonempty diagnosis aggregate. Application services coordinate remote validation,
record/appointment transactions and published events. Flyway and JPA adapters persist both
aggregates with locked mutation reads and database uniqueness for pending daily appointments and
appointment-linked records. `AppointmentController` and `MedicalRecordController` expose all
eleven specified endpoints with validation, role declarations, standard response envelopes and
web-slice coverage.
Bearer tokens are verified locally using the gateway's shared HS256 secret; invalid or missing
tokens and forbidden roles return the common API error envelope.
Domain, validation and upstream failures are mapped to the specified 400/404/409/422/503 statuses
with stable error codes.

Published records live in `application/event`, following billing/pharmacy, to keep publisher ports independent of infrastructure. JSON compatibility notes and the remaining producer gaps are in [the contract handoff](../../docs/eproject_general_plan/backend-spec/clinical-lab-contract-handoff.md).

The RabbitMQ publisher sends all four domain events as JSON to the durable shared exchange only
after transaction commit. External Lab and Pharmacy references are stored separately with an
idempotent composite key instead of altering clinical text. The `lab.result.created` consumer uses
a durable queue, dead-letter queue and processed-event ledger. Feign clients and the
`prescription.filled` consumer remain follow-up work. The `.http` requests match both controllers.

## Run locally

1. `CREATE DATABASE mediflow_clinical;` (or `docker compose up -d`)
2. Start `eureka-server` (8761), then `gateway` (8080), then this service.
3. RabbitMQ on localhost:5672.

```bash
mvn -pl backend/clinical-service -am spring-boot:run
```

Swagger UI: http://localhost:8082/swagger-ui.html

## Events

- **Publish:** `appointment.created`, `appointment.status.changed`, `medicalrecord.created`, `diagnosis.added`
- **Subscribe:** `lab.result.created` (attach result to the record), `prescription.filled` (attach prescription info)

## Cross-service reads (resilient)

Clinical resolves patient existence through `patient-service` and doctor department membership
through `organization-service` using short-timeout Feign clients. Confirmed 404 responses become
domain lookup misses; transport failures, circuit-open responses and invalid envelopes become
`UPSTREAM_UNAVAILABLE` (503). The caller's bearer token is forwarded to these internal reads.

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and which department are they in?

Future adapters must use Feign with a 2s connect / 3s read timeout and a circuit breaker. A confirmed miss returns false/empty; an outage throws `UpstreamUnavailableException` (`UPSTREAM_UNAVAILABLE`, future HTTP 503 mapping). Returning false from every fallback would incorrectly report a missing patient or doctor.

## Tests

```bash
mvn -pl backend/clinical-service -am test    # unit/contract tests + PostgreSQL slices when Docker is available
mvn -pl backend/clinical-service -am verify
```
