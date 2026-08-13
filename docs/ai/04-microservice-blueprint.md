# 04 — Microservice Blueprint (MANDATORY)

> Every business service is a copy of this shape. When you scaffold a new service or add a feature, follow this layout **exactly**. Consistency here is what lets both humans and AI navigate any service instantly.
>
> The architecture is **Clean Architecture**.
>
> ✅ **`backend/patient-service/` is the reference implementation.** It follows this document exactly —
> 4 files in `domain/`, 11 in `application/`, 15 in `infrastructure/`, 30 passing tests, and a
> verified dependency rule (`domain/` imports nothing but `java.*` and `common`; `application/`
> uses only `@Service` and `@Transactional` from Spring). When something here is ambiguous, open
> that module and copy what it does.

## The one rule everything else serves

```
infrastructure  ───►  application  ───►  domain
```

Dependencies point **inward only**. Concretely:

- `domain` imports **nothing** from `application` or `infrastructure`, and **no framework** — no Spring, no Jakarta Persistence, no Spring Data. Plain Java (+ Lombok, which is compile-time only, and `common`, which is dependency-free).
- `application` imports `domain`. It may **not** import `infrastructure`, `org.springframework.data.*`, or anything JPA/AMQP/HTTP.
- `infrastructure` may import both. This is where every framework annotation lives.

If you ever need an outward dependency, you invert it: `application` declares an **interface** (a *port*) and `infrastructure` writes the **implementation** (an *adapter*).

> **How to check yourself:** if a file under `domain/` or `application/` has an `import jakarta.persistence`, `import org.springframework.data`, `import org.springframework.amqp`, or `import org.springframework.web`, the layering is broken. The only Spring imports allowed in `application` are `@Service`/`@Transactional` on the application service and MapStruct's `componentModel = SPRING` (a pragmatic concession so Spring can wire the beans — documented below).

## Maven layout (monorepo)

```
MediFlow/                       <- repo root, parent POM
├── pom.xml                     <- <packaging>pom</packaging>, dependencyManagement, <modules>
├── backend/                    <- ALL Java microservices + shared lib + infra
│   ├── common/                 <- shared thin lib (envelope, pagination, base exceptions, security constants)
│   ├── eureka-server/          <- service registry
│   ├── gateway/                <- Spring Cloud Gateway (special, see services/gateway.md)
│   ├── organization-service/   <- reference: departments, staff, accounts
│   ├── patient-service/        <- reference: master patient index
│   ├── clinical-service/       <- Khoa Khám bệnh (appointments + records)
│   ├── lab-service/
│   ├── pharmacy-service/
│   ├── billing-service/
│   ├── notification-service/
│   └── report-service/
├── frontend/                   <- Next.js web client
└── ...

## Package layout inside every `*-service`

Base package: `com.mediflow.<service>`.

```
backend/patient-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/mediflow/patient/
    │   │   ├── PatientServiceApplication.java
    │   │   │
    │   │   ├── domain/                     # PURE JAVA — no framework, no I/O
    │   │   │   ├── model/                  # rich domain model + enums
    │   │   │   │   ├── Patient.java         #   business rules live INSIDE the model
    │   │   │   │   └── GioiTinh.java
    │   │   │   └── exception/              # domain exceptions (extend common bases)
    │   │   │       ├── PatientNotFoundException.java
    │   │   │       └── InvalidPatientDataException.java
    │   │   │
    │   │   ├── application/                # use cases — orchestration, no framework detail
    │   │   │   ├── port/
    │   │   │   │   ├── in/                 # what the outside may ASK this service to do
    │   │   │   │   │   ├── CreatePatientUseCase.java
    │   │   │   │   │   ├── UpdatePatientUseCase.java
    │   │   │   │   │   ├── GetPatientUseCase.java
    │   │   │   │   │   └── DeletePatientUseCase.java
    │   │   │   │   └── out/                # what this service NEEDS from the outside
    │   │   │   │       ├── PatientRepositoryPort.java
    │   │   │   │       └── PatientEventPublisherPort.java
    │   │   │   ├── dto/
    │   │   │   │   ├── request/            # CreateXxxRequest, UpdateXxxRequest (records)
    │   │   │   │   └── response/           # XxxDTO (records)
    │   │   │   ├── mapper/                 # domain model <-> DTO (MapStruct)
    │   │   │   └── service/                # PatientApplicationService implements the in-ports
    │   │   │
    │   │   ├── web/                        # DRIVING adapter (HTTP) — outer ring, calls application
    │   │   │   ├── PatientController.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── messaging/                  # DRIVING adapter (event consumer) — calls application
    │   │   │   └── consumer/               #   @RabbitListener handlers (idempotent)
    │   │   ├── infrastructure/             # DRIVEN adapters — application calls OUT
    │   │   │   ├── persistence/            #   driven (DB) — implements PatientRepositoryPort
    │   │   │   │   ├── PatientJpaEntity.java
    │   │   │   │   ├── PatientJpaRepository.java
    │   │   │   │   ├── PatientPersistenceMapper.java
    │   │   │   │   └── PatientPersistenceAdapter.java
    │   │   │   ├── messaging/              #   driven (RabbitMQ) — publisher + payload
    │   │   │   │   ├── PatientEventPublisherAdapter.java
    │   │   │   │   └── payload/            #     event records (XxxEvent)
    │   │   │   ├── client/                 #   driven (REST to other services) — Feign + fallback
    │   │   │   ├── security/               #   JwtAuthFilter, JwtProperties
    │   │   │   └── config/                 #   SecurityConfig, RabbitConfig, OpenApiConfig
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/               # Flyway V1__init.sql, ...
    └── test/
        └── java/com/mediflow/patient/      # mirrors main packages
```

## Two models, on purpose

This is the part people get wrong, so it is spelled out:

| | `domain/model/Patient` | `infrastructure/persistence/PatientJpaEntity` |
|---|---|---|
| Purpose | express the business | map rows to objects |
| Annotations | none | `@Entity`, `@Table`, `@Column` |
| Knows about DB? | no | yes |
| Has business rules? | **yes** | no — it is a dumb data holder |
| Testable without Spring? | yes | no |

`PatientPersistenceMapper` converts between them. Yes, this is extra typing. It is what buys you a domain you can unit-test in milliseconds with zero Spring context, and a database you can swap without touching a business rule.

**Do not** put `@Entity` on a domain model. **Do not** put business rules in a JPA entity.

## Where each kind of logic goes

| Logic | Home | Why |
|-------|------|-----|
| "ngày sinh không được ở tương lai" | `domain/model` | invariant of the thing itself |
| "số CMND phải duy nhất" | `application/service` | needs to ask the repository — not knowable by one object alone |
| "chỉ ADMIN/NURSE được tạo" | `web` (`@PreAuthorize`) | a delivery concern, not a business rule |
| "lưu vào Postgres" | `infrastructure/persistence` | a detail |
| "phát event `patient.created`" | `application/service` calls the **port**; `infrastructure/messaging` does the AMQP | intent vs. mechanism |

## The request lifecycle

```
HTTP → Controller (validate DTO, @PreAuthorize)
     → in-port → ApplicationService
         → domain model enforces its invariants
         → out-port (RepositoryPort)        ← implemented by PersistenceAdapter → JPA → Postgres
         → out-port (EventPublisherPort)    ← implemented by MessagingAdapter   → RabbitMQ
     → DTO mapper → ApiResponse envelope → HTTP
```

## Pagination without Spring in the application layer

`org.springframework.data.domain.Page`/`Pageable` are infrastructure types — they must not appear in `application` or `domain`. Use the framework-free pair from `common`:

- `PageQuery(int page, int size)` — what the caller asks for
- `PageResult<T>(content, totalElements, totalPages, number, size)` — what comes back

`PageResult` serializes to the exact same JSON shape as a Spring `Page`, so the frontend contract does not change. The persistence adapter converts `PageQuery` → `Pageable` and `Page` → `PageResult`.

## Pragmatic concessions (deliberate, not accidents)

Purity is a means, not the goal. These three are allowed; everything else follows the rule strictly:

1. **`@Service` / `@Transactional` on the application service.** Keeps Spring wiring and transaction boundaries simple. The class stays otherwise framework-agnostic.
2. **MapStruct `componentModel = SPRING` on mappers.** The mapper *interface* is plain; only generated code touches Spring.
3. **`jakarta.validation` annotations on request DTOs.** These are a spec, not a framework, and they keep validation declarative at the edge.

## Definition of Done for a new service

- [ ] Module added to parent `pom.xml` `<modules>`; inherits versions (no version numbers in child POM).
- [ ] Package layout matches this blueprint exactly (`domain` / `application` / `infrastructure`).
- [ ] **Dependency rule verified:** no framework imports in `domain`; no `infrastructure` or Spring Data imports in `application`.
- [ ] `application.yml`: port, DB, Eureka client, RabbitMQ, actuator, app name `<service>-service`.
- [ ] Flyway `V1__init.sql` creates the tables from the design doc (VN snake_case).
- [ ] Domain model with its invariants; JPA entity + persistence mapper + adapter implementing the repository port.
- [ ] In-ports (one interface per use case) + application service implementing them, enforcing **all business rules** from the design doc.
- [ ] DTOs (records) in `application/dto`; MapStruct DTO mapper.
- [ ] Controller for every endpoint in the design doc, each with `@PreAuthorize` roles.
- [ ] Every endpoint shipped has a matching request in `backend/<service>/<service>.http` (see `05` "Every endpoint ships a `.http` request").
- [ ] Event publisher port + messaging adapter; consumers per the design doc's publish/subscribe tables (idempotent).
- [ ] Domain exceptions + `GlobalExceptionHandler`.
- [ ] `SecurityConfig` validating JWT + role checks; `RabbitConfig` for exchanges/queues.
- [ ] Tests: domain unit (no Spring), application unit (mock the ports), web slice (`@WebMvcTest`), integration (`@SpringBootTest` + Testcontainers).
- [ ] `README.md` in the module linking to `docs/ai/services/<service>.md`.

## Scaffolding

The seven service modules already exist as skeletons: `pom.xml`, `application.yml`, the `Application` class, a `README.md`, and the full package tree (each folder holds a `.gitkeep` until you fill it). Nothing else — no business code.

To add a *new* module beyond those, use **`/new-service <name>`** (Claude) or follow `.claude/skills/new-microservice/SKILL.md` together with this document. Both were updated to this layout.
