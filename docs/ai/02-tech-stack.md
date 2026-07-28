# 02 — Tech Stack

> **The parent `pom.xml` is the source of truth for versions.** This file mirrors it in prose. If they disagree, the POM wins — and fixing this file is part of the same PR.
> Child modules **never** declare a version number; they inherit from `<dependencyManagement>`.

## Decisions (locked by the team)

| Area | Choice |
|------|--------|
| Architecture | **Microservices + Clean Architecture** (`03`, `04`) |
| Backend | **Java 21 + Spring Boot** |
| Database | **PostgreSQL**, one database per service |
| Frontend | **Next.js + TypeScript + Tailwind CSS** |
| Messaging | **RabbitMQ** (topic exchange, event-driven) |
| Discovery | **Netflix Eureka** |

Everything below is the concrete realisation of those six lines.

## Backend — pinned versions

Verified against `pom.xml` at the repo root:

| Dependency | Version | Where pinned |
|------------|---------|--------------|
| Java | **21** (LTS) | `<java.version>` / `maven.compiler.release` |
| Spring Boot | **3.3.5** | parent `spring-boot-starter-parent` |
| Spring Cloud | **2023.0.3** | `<spring-cloud.version>` → BOM import |
| MapStruct | **1.5.5.Final** | `<mapstruct.version>` |
| Lombok | **1.18.34** | `<lombok.version>` |
| lombok-mapstruct-binding | **0.2.0** | `<lombok-mapstruct-binding.version>` — lets the two annotation processors cooperate |
| jjwt (JWT) | **0.12.6** | `<jjwt.version>` — `api` compile, `impl`+`jackson` runtime |
| springdoc-openapi | **2.6.0** | `<springdoc.version>` — servlet (webmvc-ui) flavour |
| PostgreSQL driver, Flyway, Testcontainers, Jackson, JUnit 5, Mockito, AssertJ | managed by the Spring Boot BOM | — |

Build: **Maven multi-module**, single parent at the repo root, one module per service. Packaging: Spring Boot fat JAR via `spring-boot-maven-plugin` (Lombok excluded from the repackaged jar).

## Which library is allowed in which layer

This is the part that keeps clean architecture honest. A dependency being *in the POM* does not make it legal *everywhere* in the module.

| Layer | May use | Must never import |
|-------|---------|-------------------|
| `domain/` | plain Java, `java.time`, `java.util`, Lombok (compile-time only), `common` (dependency-free) | Spring anything, `jakarta.persistence`, `jakarta.validation`, Jackson, AMQP |
| `application/` | `domain`, `common`, MapStruct annotations, `jakarta.validation` on DTOs, `@Service`/`@Transactional` | `org.springframework.data.*`, `jakarta.persistence`, `org.springframework.amqp`, `org.springframework.web`, anything under `infrastructure` |
| `infrastructure/` | everything | — |

The three concessions in the `application` row (MapStruct, `jakarta.validation`, `@Service`/`@Transactional`) are deliberate and justified in `04`. Nothing else gets added to that list without changing `04` first.

## Per-service dependencies

Every business service gets the same set:

- `spring-boot-starter-web` — REST *(the gateway uses `spring-cloud-starter-gateway` / WebFlux instead)*
- `spring-boot-starter-data-jpa` + `postgresql` driver + `flyway-core` + `flyway-database-postgresql`
- `spring-boot-starter-validation` — Jakarta Bean Validation
- `spring-boot-starter-security` + `jjwt` — verify the JWT again at the service boundary
- `spring-boot-starter-amqp` — RabbitMQ
- `spring-cloud-starter-netflix-eureka-client` — register with Eureka
- `spring-boot-starter-actuator` — health, metrics
- `springdoc-openapi-starter-webmvc-ui` — Swagger UI at `/swagger-ui.html`
- `mapstruct` + `lombok`
- `common`

Added **only where the service actually calls another service synchronously**:

- `spring-cloud-starter-openfeign` — declarative REST client, resolved through Eureka
- `spring-cloud-starter-circuitbreaker-resilience4j` — timeout + circuit breaker, so a downstream outage degrades one service instead of cascading

Today that means **`clinical-service`** only — it REST-checks that a patient exists (`patient-service`) and that a doctor exists in the right department (`organization-service`). Its `application.yml` carries the matching `feign.client.config.default` timeouts and `resilience4j.circuitbreaker` settings.

Added only to **`notification-service`**: `spring-boot-starter-mail` (autoconfigures a `JavaMailSender` only when `spring.mail.*` is present, so it stays inert until configured).

## Databases

- **PostgreSQL**, one database per service — `mediflow_organization`, `mediflow_patient`, `mediflow_clinical`, `mediflow_lab`, `mediflow_pharmacy`, `mediflow_billing`, `mediflow_notification`, `mediflow_report`.
- No shared tables, no cross-database joins, no foreign keys across service boundaries (`01`, `08`).
- Types: PK = `UUID`, money = `DECIMAL(15,2)` ↔ `BigDecimal`, timestamps = `TIMESTAMPTZ` ↔ `Instant`.
- **Flyway** owns the schema: `src/main/resources/db/migration/V<n>__<desc>.sql`, append-only. `ddl-auto` is `validate` — never `update` outside local experimentation.
- Credentials come from `MEDIFLOW_DB_USER` / `MEDIFLOW_DB_PASSWORD` (dev default `postgres`/`postgres`). Real values go in a gitignored `application-local.yml` or the environment — never in git.

## Messaging

- **RabbitMQ**, one durable topic exchange `mediflow.events`; routing keys are dot.case event names; each consumer owns a durable queue plus a DLX/DLQ for poison messages. Full rules in `06`.
- JSON payloads via `Jackson2JsonMessageConverter`.
- Connection from `MEDIFLOW_RABBIT_HOST` / `_USER` / `_PASSWORD` (dev default `localhost` / `guest` / `guest`).

## Service discovery & gateway

- **Eureka server** on `8761`; every service registers itself and is addressed as `lb://<service-name>` — never a hard-coded host.
- **Spring Cloud Gateway** on `8080` is the single entry point: verifies the JWT once, then routes by path (`05`). Rate limiting (100 req/min per IP, per `07`) needs Redis and is **not** wired yet.

## Frontend

Verified against `frontend/package.json`:

| Dependency | Version |
|------------|---------|
| Next.js | **16.2.11** (App Router) |
| React / React DOM | **19.2.4** |
| Tailwind CSS | **v4** (via `@tailwindcss/postcss`) |
| TypeScript | **5.x** |
| ESLint | **9.x** + `eslint-config-next` |
| Package manager | **pnpm** |

The browser calls same-origin `/api/*`; Next rewrites to the gateway (`GATEWAY_URL`, default `http://localhost:8080`). Conventions in `12`.

## Testing

- `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ) + `spring-security-test`
- **Testcontainers** (`postgresql`, `rabbitmq`, `junit-jupiter`) + `spring-boot-testcontainers` for integration tests — **requires Docker running**
- Layer-by-layer strategy in `09`. The payoff of clean architecture shows up here: domain and application tests need **no Spring context at all**.

## Shared module (`common`)

Deliberately **dependency-free pure Java** — so both the servlet services and the WebFlux gateway can depend on it without dragging in conflicting web stacks. That constraint is load-bearing; do not add a starter to `backend/common/pom.xml`.

Holds only what is genuinely generic: `ApiResponse` envelope + error types, framework-free pagination (`PageQuery` / `PageResult`), base exceptions, JWT claim names and role constants. **Business logic never goes in `common`.**

## Not yet wired (known gaps)

Be honest about these rather than discovering them late:

- ~~`PageQuery` / `PageResult`~~ — done, in `common/api/`.
- Gateway rate limiting (needs Redis).
- Distributed tracing — `X-Correlation-Id` is specified in `01` but no propagation filter exists yet.
- Gateway auth is a **stub**; there is no user store.

## Upgrade policy

Change a version in the **parent POM and this file in the same PR**. Spring Boot and Spring Cloud move together — check the compatibility matrix before bumping either, and run `mvn -q -DskipTests install` across all modules before pushing.
