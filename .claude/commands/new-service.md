---
description: Scaffold a new Spring Boot microservice module from the mandatory blueprint. Usage: /new-service <name>
argument-hint: <service-name e.g. patient>
---

Scaffold a new microservice module named `$ARGUMENTS` following the project blueprint.

Do this:
1. Read `docs/ai/04-microservice-blueprint.md` and `docs/ai/services/$ARGUMENTS.md` (if it exists). If no service doc exists, ask the user which design doc in `EProject/*.html` this maps to.
2. Invoke the `.claude/skills/new-microservice/SKILL.md` steps (or the `spring-boot-engineer` agent) to generate `backend/$ARGUMENTS-service/` with the exact clean-architecture package tree: `domain/{model,exception}`, `application/{port/in,port/out,dto/request,dto/response,mapper,service}`, `infrastructure/{web,persistence,messaging/payload,messaging/consumer,client,security,config}`. Dependencies point inward only.
3. Generate: `pom.xml` (child, no versions — inherits parent), `Application` class, `application.yml` (port, DB, Eureka, RabbitMQ, actuator, app name `$ARGUMENTS-service`), Flyway `V1__init.sql` from the design doc tables (VN snake_case), entities + repositories + DTO records + MapStruct mapper, service interface+impl enforcing all business rules, controllers with `@PreAuthorize`, event publishers/consumers, `SecurityConfig`, `RabbitConfig`, `GlobalExceptionHandler`, and a module `README.md` linking `docs/ai/services/$ARGUMENTS.md`.
4. Register the module in the parent `pom.xml` `<modules>`.
5. Add unit + slice + integration tests per `docs/ai/09-testing.md`.
6. Run `mvn -pl backend/$ARGUMENTS-service -am -q -DskipTests install` to verify it compiles, then report what was created against the blueprint checklist.

Follow every rule in `docs/ai/` — do not invent structure.
