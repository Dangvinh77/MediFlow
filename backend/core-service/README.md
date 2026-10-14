# Core Service

Core is the single deployment for the Organization and Patient bounded contexts. Their source,
business rules, wire DTOs, migrations, and PostgreSQL stores live in this module while retaining
their separate `com.mediflow.organization` and `com.mediflow.patient` packages. Existing API routes
are served behind `core-service` service discovery.

Organization and Patient use separate datasource URLs, entity managers, transaction managers,
and Flyway locations. Their schemas and data remain separate. Configure
`MEDIFLOW_ORGANIZATION_DB_*`, `MEDIFLOW_PATIENT_DB_*`, and `MEDIFLOW_JWT_SECRET` for a local run.
RabbitMQ and Eureka use the shared MediFlow settings.

Request examples are in `requests/organization.http` and `requests/patient.http`.
The Markdown files in `requests/` retain the original context documentation.
