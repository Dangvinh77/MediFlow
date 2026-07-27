# 01 — Architecture

There are **two** architectures in this project and they answer different questions. Keep them straight:

1. **Between services** — microservices: who owns what data, and how they talk. *This decides deployment.*
2. **Inside one service** — clean architecture: how a single module is layered. *This decides code structure.*

`04` covers (2) in full. This file covers (1), then shows where the two meet.

---

## 1. System topology

```
                         ┌──────────────┐
        Client  ───────► │   Gateway    │  JWT verify, RBAC, rate-limit, route
        (Next.js)        └──────┬───────┘  :8080
                                │ (resolves lb://<name> via Eureka)
     ┌───────────┬──────────────┼──────────────┬───────────┬───────────┐
     ▼           ▼              ▼              ▼           ▼           ▼
organization  patient      clinical          lab      pharmacy     billing ...
  :8089       :8081         :8082           :8084      :8085        :8086
  └ reference data ┘        └────────── departments ──────────┘
     │           │              │              │           │           │
     └───────────┴──────────────┴──────┬───────┴───────────┴───────────┘
                                       │  publish / subscribe
                                ┌──────▼───────┐
                                │   RabbitMQ   │  topic exchange `mediflow.events`
                                └──────────────┘
             ┌──────────────┐
             │    Eureka    │  :8761 — every service registers; gateway resolves names
             └──────────────┘
```

### Services and their addresses

Services fall into three groups. The grouping is not decoration — it is the answer to "why does this
service exist?", and every service must have one.

| Group | Service | Port | Database | Base path | Role |
|-------|---------|------|----------|-----------|------|
| infra | `eureka-server` | 8761 | — | — | service registry |
| infra | `gateway` | 8080 | — | — | single entry point, JWT, routing |
| **reference** | `organization-service` | 8089 | `mediflow_organization` | `/api/v1/org` | departments, staff, accounts — **owns `ma_khoa`** |
| **reference** | `patient-service` | 8081 | `mediflow_patient` | `/api/v1/patients` | master patient index |
| **department** | `clinical-service` | 8082 | `mediflow_clinical` | `/api/v1/appointments`, `/api/v1/records` | Khoa Khám bệnh — appointments, records, diagnoses |
| **department** | `lab-service` | 8084 | `mediflow_lab` | `/api/v1/lab` | Khoa Xét nghiệm |
| **department** | `pharmacy-service` | 8085 | `mediflow_pharmacy` | `/api/v1/pharmacy` | Khoa Dược — **saga participant** |
| **department** | `billing-service` | 8086 | `mediflow_billing` | `/api/v1/billing` | Phòng Viện phí — **saga orchestrator** |
| support | `notification-service` | 8087 | `mediflow_notification` | `/api/v1/notifications` | notification history |
| support | `report-service` | 8088 | `mediflow_report` | `/api/v1/reports` | read model built from events |

**Reference services** hold data everyone else points at; they are read far more than written, and
that is correct for their role, not a sign of an anemic service. **Department services** each map to
a real ward or office and own a workflow. **Support services** are technical, not organisational —
do not present them as departments.

### The department dimension

`organization-service` owns `KHOA`, and every operational table carries a `ma_khoa` **bare UUID**:
`LICH_HEN`, `HO_SO_BA`, `XET_NGHIEM`, `BAN_KE_CP`, `VIEN_PHI`. Domain events carry `maKhoa` too.

This is what makes the system *departmental* rather than merely *modular*. It is what lets the system
answer: revenue per department, who ordered which test, which doctor belongs where, and transferring
staff between departments.

---

## 2. Two — and only two — inter-service communication patterns

**1. Synchronous REST — "I need the answer to finish this request."**

- Used only to *read* another context at request time. Examples: appointment → patient, medical-record → patient ("does this patient exist?").
- Address the service **by name through Eureka** (`lb://patient-service`), never a host.
- Must be **resilient**: short timeout + circuit breaker + a fallback that degrades gracefully. A downstream outage must not cascade.

**2. Asynchronous events — "Something happened that others may care about."**

- Whenever a service changes its own state, it publishes. Subscribers are decoupled and must be **idempotent** (dedupe on `eventId` — redelivery is normal, not exceptional).
- **This is the default.** Prefer an event over REST for anything that is not a same-request read.

**Rule of thumb:** *"Do I need the answer to finish this request?"* → REST. *"Did something happen others care about?"* → event.

Never use an event to fetch data, and never use REST to announce a state change.

---

## 3. Where the two architectures meet

The distributed concerns above are **infrastructure**. Clean architecture says the business code must not know about them. So each one enters through a port:

| Distributed concern | Port (`application/port/out`) | Adapter (`infrastructure/`) |
|---------------------|-------------------------------|------------------------------|
| Read another service over REST | `PatientLookupPort` | `client/PatientFeignClient` + fallback |
| Publish a domain event | `XxxEventPublisherPort` | `messaging/XxxEventPublisherAdapter` |
| Persist state | `XxxRepositoryPort` | `persistence/XxxPersistenceAdapter` |
| React to another service's event | *(an in-port use case)* | `messaging/consumer/XxxEventConsumer` |

Consequences worth internalising:

- The application layer never imports Feign, RabbitTemplate, or a JPA repository. It calls `patientLookup.exists(id)` — it does not know whether that is HTTP, a cache, or a stub.
- An event consumer is a **driving adapter**, exactly like a REST controller. It parses the message, calls a use case, acks. No business logic lives in a `@RabbitListener`.
- Swapping RabbitMQ for Kafka, or Feign for `RestClient`, touches `infrastructure/` only. That is the test of whether the layering is real.

---

## 4. Data ownership

- **Each service owns its own database schema.** No shared tables, no cross-service SQL joins, no foreign keys across boundaries.
- An id referencing another context (e.g. `ma_benh_nhan` inside `LICH_HEN`) is a **bare `UUID`** — a reference, not a DB foreign key, and not a JPA `@ManyToOne`.
- **`report-service` holds a read model.** It never queries another service's database; it builds its aggregates purely from the events it subscribes to. If it ever needs a JDBC URL pointing at another service's DB, the design has been violated.

Why this matters more than it looks: the moment two services share a table, they must deploy together, and you no longer have microservices — you have a distributed monolith with extra latency.

---

## 5. Saga — the distributed transaction

There is no `@Transactional` across services. `billing-service` is the **orchestrator** for `prescribe → dispense → pay`:

```
pharmacy: prescription.created ──► billing: create invoice
                                        │
                                   PUT /invoices/{id}/pay
                                        │
                                   payment.completed ──► pharmacy: dispense (CHO_XUAT → DA_XUAT)
                                        │                        │
                                        │                   out of stock / expired
                                        │                        │
                                        ◄──── failure event ─────┘
                                   payment.failed ──► notification + compensation
```

Every participant must be **idempotent** and must handle compensation. Details in `06` and `services/billing.md`.

---

## 6. Cross-cutting concerns

| Concern | Where it lives | Status |
|---------|----------------|--------|
| AuthN entry | gateway verifies JWT once | stub auth — no user store yet |
| AuthZ | each service re-verifies the JWT and enforces `@PreAuthorize` roles (`07`) | specified |
| Service discovery | Eureka | wired |
| Config | externalised via env vars; never hard-code hosts or secrets | wired |
| Correlation / tracing | propagate `X-Correlation-Id`, include it in logs and events | **specified, not implemented** |
| Idempotency | consumers dedupe on `eventId`; PUT endpoints idempotent by design | specified |
| Rate limiting | gateway, 100 req/min per IP (`07`) | **not implemented — needs Redis** |

Rows marked *not implemented* are real gaps, not oversights in the doc. Treat them as a backlog.

---

## 7. Why "defense in depth" on auth

The gateway validates the JWT, and then **every service validates it again**. That is not redundant. Services are reachable on their own ports (8081–8088) and register in Eureka; anything inside the network can call them directly, bypassing the gateway entirely. A service that trusts a request merely because it arrived is a service with no authorization at all.
