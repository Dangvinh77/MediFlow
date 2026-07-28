# clinical-service

**Khoa Khám bệnh** — the outpatient examination workflow end to end: booking an appointment, the examination, the medical record and its diagnoses.

Reference: [`docs/ai/services/clinical.md`](../docs/ai/services/clinical.md) · design doc [`EProject/clinical-service.html`](../docs/eproject_general_plan/clinical-service.html).

- **Port:** 8082 · **Base paths:** `/api/v1/appointments`, `/api/v1/records` · **DB:** `mediflow_clinical`
- **Owns tables:** `LICH_HEN`, `HO_SO_BA`, `CHUAN_DOAN`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md).

## Why appointments and records are one service

They are **one department's single workflow**: book, examine, record. Separating them would buy
nothing but distributed-systems overhead:

- `HO_SO_BA.ma_lich_hen` points straight at `LICH_HEN`. In one service that is a real foreign key; split apart it becomes a bare UUID across a network boundary.
- Setting an appointment to `DA_DEN` when its record is created would need a published event — a message broker and eventual consistency doing the work of what is logically **one transaction**: the patient showed up and was examined.

Kept together, that is a local transaction. The two URL prefixes remain distinct, so the public API
is exactly what `05-api-conventions.md` specifies.

## Status

**Skeleton only.** Module, dependencies, config and the mandated package layout are in place — no business code yet.

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

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and which department are they in?

Both go through Feign with a 2s connect / 3s read timeout and a circuit breaker. A downstream outage
must degrade this service, never cascade.

## Tests

```bash
mvn -pl backend/clinical-service test        # unit (domain + application, no Spring)
mvn -pl backend/clinical-service verify      # + integration (Testcontainers, needs Docker)
```
