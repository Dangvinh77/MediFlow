---
name: new-microservice
description: Scaffold a new Spring Boot microservice module that conforms exactly to the project blueprint. Tool-agnostic — usable by Codex, Codex, or Cursor. Use when creating any new *-service module in this monorepo.
---

# Scaffold a microservice (blueprint-conformant)

This skill produces one new Maven module that matches `docs/ai/04-microservice-blueprint.md` exactly. Read that file and the target `docs/ai/services/<service>.md` first — they are authoritative.

## Inputs
- `<service>`: kebab name, e.g. `patient`, `medical-record`. Module dir = `backend/<service>-service/`. Base package = `com.mediflow.<serviceCamel>`. The root `pom.xml` module path must use `backend/<service>-service`.
- The design doc: `docs/ai/services/<service>.md` (and its `docs/eproject_general_plan/*.html` source).

## Steps (do them in order)

1. **Module & POM.** Create `backend/<service>-service/pom.xml` as a child of the root parent POM. No version numbers — inherit from `<dependencyManagement>`. Add the module to the root `pom.xml` `<modules>` as `backend/<service>-service`. Dependencies per `docs/ai/02-tech-stack.md` (web, data-jpa + driver, validation, security+jwt, amqp, eureka-client, actuator, mapstruct, lombok, flyway, springdoc; openfeign only if it calls other services).

2. **Package tree** under `src/main/java/com/mediflow/<serviceCamel>/` — three layers plus two outer rings, dependencies inward only (`application → domain`; driving adapters `web`/`messaging` call `application`; `infrastructure` implements `application`'s out-ports):
   - `domain/model/`, `domain/exception/`
   - `application/port/in/`, `application/port/out/`, `application/dto/request/`, `application/dto/response/`, `application/mapper/`, `application/service/`
   - `web/` — DRIVING adapter (HTTP): controllers + `GlobalExceptionHandler`, calls `application/port/in`
   - `messaging/consumer/` — DRIVING adapter (event consumers): `@RabbitListener`, calls `application/port/in`
   - `infrastructure/persistence/`, `infrastructure/messaging/payload/`, `infrastructure/messaging/` (publisher adapter), `infrastructure/client/`, `infrastructure/security/`, `infrastructure/config/` — DRIVEN adapters implementing `application/port/out`
   - plus `<Service>Application.java` at the base package.

3. **application.yml** (`src/main/resources/`): server port, `spring.application.name: <service>-service`, datasource, JPA (`ddl-auto: validate`), Flyway, RabbitMQ, Eureka client, actuator health, springdoc. Keep secrets out — use env placeholders; local overrides in `application-local.yml` (gitignored).

4. **Schema.** `src/main/resources/db/migration/V1__init.sql` creating the design-doc tables in **Vietnamese snake_case**.

5. **Domain model** (`domain/model/`). Plain Java — **no Spring, no Jakarta Persistence, no I/O**. VN camelCase fields, `UUID` ids, `BigDecimal` money, enums for states. Invariants of the object itself (e.g. "ngày sinh không ở tương lai") are enforced *here*, in factories and behaviour methods — not in a setter, not in the controller. Domain exceptions in `domain/exception/`, extending the `common` bases.

6. **Ports** (`application/port/`). `in/` — one interface per use case (`CreateXxxUseCase`, ...). `out/` — what the service needs from the world (`XxxRepositoryPort`, `XxxEventPublisherPort`). Ports use domain types and `common`'s `PageQuery`/`PageResult` — **never** `Pageable`, `Page`, or any JPA/AMQP type.

7. **DTOs** (`application/dto/`). `CreateXxxRequest` / `UpdateXxxRequest` and `XxxDTO` as Java **records** with Bean Validation; VN camelCase fields. MapStruct mapper in `application/mapper/` converts domain model ↔ DTO.

8. **Application service** (`application/service/`). Implements the in-ports, depends only on out-ports, enforces **every business rule** listed in the service doc that needs more than one object (uniqueness, cross-aggregate checks, orchestration). Constructor injection. `@Service`/`@Transactional` allowed; nothing else framework-y.

9. **Persistence adapter** (`infrastructure/persistence/`). `XxxJpaEntity` (explicit `@Table`/`@Column(name=...)`, `@Enumerated(STRING)`, `@CreationTimestamp`/`@UpdateTimestamp`, no `@Data`, no cross-service relations) + `XxxJpaRepository` (Spring Data) + `XxxPersistenceMapper` (entity ↔ domain model) + `XxxPersistenceAdapter` implementing the repository port. The JPA entity holds **no** business rules.

10. **Web adapter** (`web/`). Thin controllers, one per resource; `/api/v1/...` paths; `@Valid` requests; standard `ApiResponse` envelope; `@PreAuthorize` with the exact roles from the service doc. Plus one `@RestControllerAdvice GlobalExceptionHandler` mapping domain exceptions to the error envelope. Controllers are the *outer ring* — they call in-ports only, never infrastructure/persistence/messaging. **Every endpoint you ship must also have a matching request in `backend/<service>/<service>.http`** — an endpoint is not done without its `.http` request (see `docs/ai/05-api-conventions.md`, "Every endpoint ships a `.http` request").

11. **Messaging adapters.** Publisher adapter implementing the event-publisher port in `infrastructure/messaging/` (publish after commit); `@RabbitListener` consumers in `messaging/consumer/` (idempotent, dedupe on `eventId`); event records in `infrastructure/messaging/payload/`. Match the catalog in `docs/ai/06-events-rabbitmq.md`.

12. **Config & security** (`infrastructure/config/`, `infrastructure/security/`). `SecurityConfig` (JWT verify, `@EnableMethodSecurity`, stateless, default deny), `RabbitConfig` (exchange/queues/bindings/DLX), `OpenApiConfig`, `JwtAuthFilter` + `JwtProperties`.

13. **Tests** per `docs/ai/09-testing.md`: domain unit (pure, no Spring), application unit (mock the out-ports), `@WebMvcTest` (controller/validation/roles), `@DataJpaTest`+Testcontainers (adapter), `@SpringBootTest`+Testcontainers (integration incl. events).

14. **Module README.md** linking `docs/ai/services/<service>.md`.

15. **Verify.** `mvn -pl backend/<service>-service -am -q -DskipTests install`, then report against the blueprint's Definition-of-Done checklist.

## Guardrails
- Do not invent structure or names — copy the blueprint.
- **Check the dependency rule before reporting done:** no `jakarta.persistence`, `org.springframework.data`, `org.springframework.amqp` or `org.springframework.web` import may appear under `domain/` or `application/`. See `04` for the three allowed concessions.
- If the design doc and these rules disagree, the `docs/eproject_general_plan/*.html` design doc is authoritative for *what*; `docs/ai/*` is authoritative for *how*.
- Never hard-code hosts (use Eureka names), secrets, or cross-service DB access.
