# Inpatient Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a bootable, secure Inpatient service shell on port 8090 without implementing admission behavior.

**Architecture:** Register a Spring Boot Maven module using existing service conventions. Keep business packages empty; external systems are configured for normal runs and disabled only in a test profile. Record the Gateway route as an owner handoff.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring Cloud Eureka, PostgreSQL, Flyway, JPA, RabbitMQ, JJWT, Actuator, springdoc, Maven, JUnit 5, MockMvc.

---

### Task 1: Add test-first smoke, security, and configuration coverage

**Files:**
- Modify: `pom.xml`
- Create: `backend/inpatient-service/pom.xml`
- Create: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/InpatientServiceSmokeTest.java`
- Create: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/security/JwtAuthFilterTest.java`
- Create: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/security/JwtPropertiesTest.java`
- Create: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/config/InpatientConfigurationTest.java`
- Create: `backend/inpatient-service/src/test/resources/application-test.yml`

- [x] Assert the test profile starts the full web application without PostgreSQL, RabbitMQ, or Eureka.
- [x] Assert `/actuator/health`, `/actuator/info`, and Swagger/OpenAPI assets are public.
- [x] Assert an unmapped planned API path returns 401 without a bearer token; a valid signed token passes security but still returns 404 because no endpoint exists.
- [x] Assert valid JWTs authenticate, while expired or wrongly signed tokens do not.
- [x] Assert the JWT secret rejects blank/short values and the production YAML requires `MEDIFLOW_JWT_SECRET`.
- [x] Assert production YAML names port 8090, `inpatient-service`, and `mediflow_inpatient`.
- [x] Run `mvn -q -pl backend/inpatient-service -am test`; first result was RED at test compilation because the application/security classes did not exist yet.

### Task 2: Create the bootable Maven and security shell

**Files:**
- Create: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/InpatientServiceApplication.java`
- Create: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/security/JwtProperties.java`
- Create: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/security/JwtAuthFilter.java`
- Create: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/config/SecurityConfig.java`
- Create: `backend/inpatient-service/src/main/java/com/mediflow/inpatient/infrastructure/config/OpenApiConfig.java`
- Create: `backend/inpatient-service/src/main/resources/application.yml`
- Create: `backend/inpatient-service/src/main/resources/db/migration/.gitkeep`

- [x] Configure the expected port, service identity, datasource, JPA validation, empty Flyway location, RabbitMQ, Eureka, Actuator, springdoc, and required external JWT secret.
- [x] Add stateless JWT verification, minimum secret-length validation, public health/info/Swagger matchers, and authenticated-by-default security.
- [x] Run the service tests and confirm GREEN without external infrastructure.

### Task 3: Wire shared development setup and document the incomplete boundary

**Files:**
- Modify: `scripts/init-databases.sql`
- Modify: `backend/Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/ai/00-project-overview.md`
- Modify: `docs/ai/02-tech-stack.md`
- Modify: `docs/ai/services/README.md`
- Modify: `docs/ai/services/inpatient.md`
- Create: `backend/inpatient-service/AGENTS.md`
- Create: `backend/inpatient-service/README.md`
- Create: `docs/handoffs/HANDOFF-INPATIENT-GATEWAY-ROUTE.md`
- Modify: `docs/handoffs/README.md`

- [x] Add the empty dedicated database and Compose service on port 8090 with healthy PostgreSQL, RabbitMQ, and Eureka dependencies and the development JWT secret matching the existing Compose pattern.
- [x] Add the module POM to the Docker build cache-copy list.
- [x] Mark Inpatient as a preliminary foundation only; retain all business contract statuses as `DESIGN_READY` and document that the future implementation-ready spec gates business DDL/API/events.
- [x] Register an active Gateway-owner handoff for the already documented `/api/v1/inpatient/**` → `lb://inpatient-service` route; do not edit Gateway source.

### Task 4: Verify the foundation

**Files:** none

- [x] Run `mvn -q -pl backend/inpatient-service -am test`.
- [x] Run `mvn -q -DskipTests package` from the repository root.
- [x] Run `rg -n "^(import jakarta\\.persistence|import org\\.springframework\\.data|import org\\.springframework\\.amqp|import org\\.springframework\\.web)" backend/inpatient-service/src/main/java/com/mediflow/inpatient/domain backend/inpatient-service/src/main/java/com/mediflow/inpatient/application`; expect no matches.
- [x] Run `docker compose config`; it resolves successfully.
- [x] Review `git diff` to confirm no Gateway or other service production source changed and no business DDL, endpoint, DTO, aggregate, or event was introduced.
