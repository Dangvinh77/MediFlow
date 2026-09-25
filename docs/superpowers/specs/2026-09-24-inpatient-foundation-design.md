# Inpatient Service Preliminary Foundation

## Status and scope

This change creates a bootable Spring Boot shell for the approved Inpatient bounded context. It is
not a business implementation. The `CONTRACT-INPATIENT-SURGERY-01` and
`CONTRACT-CARE-BILLING-01` statuses remain `DESIGN_READY`.

The service will use port `8090`, service name `inpatient-service`, database
`mediflow_inpatient`, and the planned API prefix `/api/v1/inpatient`. It will have no admission
controller, domain aggregate, business DTO, business table or migration, event publisher,
consumer, or Rabbit binding. Business DDL, API shapes, and events wait for the future
implementation-ready Inpatient spec; no missing identifier or contract field will be inferred.

## Runtime design

The module follows the existing Maven/Spring Boot service conventions and the package boundaries
in `docs/ai/04-microservice-blueprint.md`. `application.yml` binds PostgreSQL, Flyway, JPA,
RabbitMQ, Eureka, Actuator and springdoc. Flyway remains enabled and owns the empty migration
directory; Hibernate uses `ddl-auto: validate`. The application will not require tables or declare
Rabbit topology until a business spec defines them.

Security is stateless and verifies gateway-signed HS256 bearer JWTs using the externally supplied
`MEDIFLOW_JWT_SECRET`. The secret must contain at least 32 UTF-8 bytes and has no committed
fallback. Only actuator health/info and Swagger/OpenAPI assets are public; every other request is
authenticated, with method security enabled for future endpoint role declarations. No HTTP
business endpoint is added.

A test profile disables database, Flyway, RabbitMQ and Eureka auto-configuration so a full web
application smoke test can start without external infrastructure. Production configuration keeps
those integrations enabled. Docker Compose provides the planned database and service container.

## Integration boundary

No Gateway production source changes are in scope. The Gateway service design already calls for
`/api/v1/inpatient/**` to route to `inpatient-service`; an active handoff will record that route and
its verification as the remaining work for the Gateway owner. The route is not treated as live
until the Gateway change and route test land.

The service metadata will say that the foundation exists while the business context remains
planned. A future implementation must first add the implementation-ready Inpatient spec and pass
the care-finance contract gate before adding DDL, endpoints, DTOs, or events.

## Verification

Tests will exercise no-infrastructure application startup, public health/info and Swagger access,
authentication of otherwise-unmapped paths, JWT signature/expiry behavior, required secret
validation, and configured port/database identity. Repository-level checks will run the requested
module tests, compile/package where appropriate, inspect clean-architecture boundaries, and
validate Compose configuration when Docker Compose is available.
