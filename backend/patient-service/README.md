# patient-service

Master patient index (`PATIENT`) — the system's authoritative source for patient identity and demographic information.

Reference: [`docs/ai/services/patient.md`](../docs/ai/services/patient.md) · design doc [`EProject/patient-service.html`](../docs/eproject_general_plan/patient-service.html) · implementation spec [`EProject/backend-spec/02-patient.md`](../docs/eproject_general_plan/backend-spec/02-patient.md).

> ✅ **This is the project's reference implementation.** When implementing another service and the blueprint is unclear, use this module as the implementation reference.
>
> | Layer             | Files | Allowed imports                                                            |
> | ----------------- | ----: | -------------------------------------------------------------------------- |
> | `domain/`         |     4 | only `java.*` + `common` — **no framework dependencies**                   |
> | `application/`    |    11 | additionally `@Service`, `@Transactional`, MapStruct, `jakarta.validation` |
> | `infrastructure/` |    15 | everything                                                                 |
>
> 30 tests pass, including domain tests that run **without a Spring context**.

## Key implementation patterns

- **`domain/model/Patient`** — has no setters. All state changes go through `update()`, so domain invariants cannot be bypassed.
- **`PatientEventPublisherAdapter`** — publishes **after the transaction commits**, using `TransactionSynchronization`. Publishing directly inside the transaction could notify other services about a patient that was subsequently removed by a rollback.
- **`PatientRepositoryPort`** — contains no `Pageable`, `Page`, or JPA types. The conversion between `PageQuery` and `Pageable` is fully contained inside `PatientPersistenceAdapter`.
- **Two-layer validation is intentional:** Bean Validation on DTOs provides HTTP 400 responses with field-level details; domain invariants produce HTTP 422 responses and protect the rules from **every caller**, including event consumers and tests.

## Service information

- **Port:** 8081
- **Base path:** `/api/v1/patients`
- **Database:** `mediflow_patient` (PostgreSQL)
- **Owns table:** `PATIENT`
- Built according to the mandatory blueprint: [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md).

## Data model

The service owns the `PATIENT` table.

| Column                    | Type               | Description                       |
| ------------------------- | ------------------ | --------------------------------- |
| `patient_id`              | UUID PK            | Patient identifier                |
| `full_name`               | VARCHAR(100)       | Patient full name                 |
| `date_of_birth`           | DATE               | Patient date of birth             |
| `gender`                  | ENUM               | `MALE`, `FEMALE`                  |
| `identity_number`         | VARCHAR(20) UNIQUE | National identity document number |
| `address`                 | VARCHAR(255)       | Patient address                   |
| `phone_number`            | VARCHAR(15)        | Patient phone number              |
| `email`                   | VARCHAR(100)       | Patient email                     |
| `health_insurance_number` | VARCHAR(20) NULL   | Health insurance number           |
| `created_at`              | TIMESTAMPTZ        | Creation timestamp                |
| `updated_at`              | TIMESTAMPTZ        | Last update timestamp             |

### Enum

`Gender` is defined as:

```text
MALE
FEMALE
```

These values must remain consistent across:

- Database enum
- Java domain model
- DTOs
- API JSON
- Domain events

## API

**Base path:** `/api/v1/patients`

| Method | Path                                 | Request                | Response           | Roles                |
| ------ | ------------------------------------ | ---------------------- | ------------------ | -------------------- |
| GET    | `/api/v1/patients/{id}`              | -                      | `PatientDTO`       | ADMIN, DOCTOR, NURSE |
| GET    | `/api/v1/patients?page&size&keyword` | -                      | `Page<PatientDTO>` | ADMIN, DOCTOR, NURSE |
| POST   | `/api/v1/patients`                   | `CreatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| PUT    | `/api/v1/patients/{id}`              | `UpdatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| DELETE | `/api/v1/patients/{id}`              | -                      | `204 No Content`   | ADMIN                |

### DTO fields

All DTO fields use English `camelCase`:

```text
patientId
fullName
dateOfBirth
gender
identityNumber
address
phoneNumber
email
healthInsuranceNumber
createdAt
updatedAt
```

Example:

```json
{
  "patientId": "550e8400-e29b-41d4-a716-446655440000",
  "fullName": "Nguyen Van A",
  "dateOfBirth": "1990-01-15",
  "gender": "MALE",
  "identityNumber": "001234567890",
  "address": "Ho Chi Minh City",
  "phoneNumber": "0901234567",
  "email": "patient@example.com",
  "healthInsuranceNumber": "01-12345678-9",
  "createdAt": "2026-08-13T10:00:00Z",
  "updatedAt": "2026-08-13T10:00:00Z"
}
```

## Events

Publishes `patient.created` and `patient.updated` to the `mediflow.events` topic exchange.

See [`docs/ai/06-events-rabbitmq.md`](../docs/ai/06-events-rabbitmq.md).

### `patient.created`

```json
{
  "patientId": "...",
  "fullName": "...",
  "email": "...",
  "phoneNumber": "..."
}
```

Published after a patient is successfully created and the transaction commits.

### `patient.updated`

```json
{
  "patientId": "...",
  "fullName": "...",
  "email": "...",
  "phoneNumber": "...",
  "address": "..."
}
```

Published after patient demographic information is successfully updated and the transaction commits.

### Event consumption

The Patient Service subscribes to:

```text
payment.completed
```

This event is emitted by the Billing Service.

The Patient Service only logs the event and does not modify patient data.

## Business rules

1. `identity_number` must be unique.
2. `email`, when provided, must use a valid email format.
3. `health_insurance_number`, when provided, must match `XX-XXXXXXXX-X`.
4. `date_of_birth` must not be in the future.
5. `phone_number` must contain digits only and contain at least 10 digits.
6. `patient_id` is immutable after creation.
7. Patient state changes must go through domain methods; callers must not bypass domain invariants with setters.
8. Patient domain invariants must be enforced independently of DTO validation.
9. Other services must reference patients using the bare `patient_id` UUID.
10. No other service may directly access the Patient Service database.

## Validation

The service intentionally uses two validation layers.

### Application/API validation

DTOs use Bean Validation for request-level validation.

This layer is responsible for:

- Required fields
- Field format validation
- Request structure
- Returning HTTP `400 Bad Request`
- Returning field-level validation details

### Domain validation

The `Patient` domain model enforces business invariants independently of the API layer.

This protects the system when the domain is called by:

- Application services
- Event consumers
- Tests
- Other internal callers

Domain violations result in HTTP `422 Unprocessable Entity` when translated by the API layer.

## Transactional event publishing

Domain events must not be published directly before the transaction is committed.

`PatientEventPublisherAdapter` uses `TransactionSynchronization` to publish events after a successful transaction commit.

Flow:

```text
HTTP Request
    │
    ▼
Application Service
    │
    ├── Validate request
    ├── Execute domain operation
    ├── Persist Patient
    └── Register event for after-commit publication
             │
             ▼
       Transaction Commit
             │
             ▼
       RabbitMQ Event
```

This prevents other services from receiving an event for a patient change that was later rolled back.

## Repository architecture

`PatientRepositoryPort` belongs to the application/domain boundary and must remain free of infrastructure-specific types.

It must not expose:

```text
Pageable
Page
JPA Entity
JpaRepository
Spring Data
```

Pagination is represented using the application's own `PageQuery` abstraction.

The conversion:

```text
PageQuery → Pageable
```

belongs inside:

```text
PatientPersistenceAdapter
```

The persistence adapter is responsible for translating between the application port and Spring Data/JPA.

## Cross-service references

Other bounded contexts may store:

```text
patient_id
```

as a bare UUID.

They must not:

- Define a JPA relationship to `Patient`
- Join the Patient Service database
- Read the Patient Service database directly
- Recreate patient identity as an owned entity

When another service needs to validate patient existence, it should use the appropriate internal Patient Service API according to the system's resilient cross-service communication pattern.

## Naming convention

All technical names use English terminology.

### Database

Table:

```text
PATIENT
```

Columns:

```text
patient_id
full_name
date_of_birth
gender
identity_number
address
phone_number
email
health_insurance_number
created_at
updated_at
```

Use `snake_case` for database identifiers.

### Java

Domain classes:

```text
Patient
Gender
```

Application DTOs:

```text
PatientDTO
CreatePatientRequest
UpdatePatientRequest
```

Java fields:

```text
patientId
fullName
dateOfBirth
gender
identityNumber
address
phoneNumber
email
healthInsuranceNumber
createdAt
updatedAt
```

### JSON / API

Use `camelCase`:

```json
{
  "patientId": "...",
  "fullName": "...",
  "dateOfBirth": "...",
  "gender": "MALE",
  "identityNumber": "...",
  "address": "...",
  "phoneNumber": "...",
  "email": "...",
  "healthInsuranceNumber": "..."
}
```

### Events

Use English field names:

```text
patientId
fullName
email
phoneNumber
address
```

Event names:

```text
patient.created
patient.updated
```

## Architecture

The Patient Service follows the project's mandatory clean architecture:

```text
infrastructure
      │
      ▼
application
      │
      ▼
domain
```

### Domain layer

The domain layer contains:

- `Patient`
- `Gender`
- Domain invariants
- Domain methods
- Domain events

It must not depend on:

- Spring
- Spring Data
- JPA
- RabbitMQ
- HTTP
- Bean Validation
- Other infrastructure frameworks

Allowed imports are limited to:

```text
java.*
common
```

### Application layer

The application layer contains:

- Use cases
- Application services
- Repository ports
- Event publisher ports
- DTOs
- Mapping
- Transaction orchestration
- Bean Validation

It may use:

```text
@Service
@Transactional
MapStruct
jakarta.validation
```

### Infrastructure layer

The infrastructure layer contains:

- REST controllers
- Persistence adapters
- JPA entities
- Spring Data repositories
- RabbitMQ adapters
- Event consumers
- Configuration
- External integrations

The infrastructure layer may depend on all lower layers.

Dependencies must point inward:

```text
infrastructure → application → domain
```

## Project structure

The expected package structure follows the mandatory blueprint:

```text
patient-service/
└── src/
    ├── main/
    │   └── java/
    │       └── ...
    │           ├── domain/
    │           │   ├── model/
    │           │   │   └── Patient
    │           │   └── event/
    │           ├── application/
    │           │   ├── port/
    │           │   ├── service/
    │           │   └── dto/
    │           └── infrastructure/
    │               ├── web/
    │               ├── persistence/
    │               ├── messaging/
    │               └── config/
    └── test/
```

## Run locally

1. Create the database once (see the root `README.md`):

```sql
CREATE DATABASE mediflow_patient;
```

2. Start `eureka-server` on port `8761`.
3. Optionally start `gateway` on port `8080`.
4. Start the Patient Service on port `8081`.
5. RabbitMQ must be running on `localhost:5672`, or configure the `MEDIFLOW_RABBIT_*` environment variables.

Run:

```bash
mvn -pl backend/patient-service -am spring-boot:run
```

Swagger UI:

`http://localhost:8081/swagger-ui.html`

## Tests

Run unit tests:

```bash
mvn -pl backend/patient-service test
```

Run full verification:

```bash
mvn -pl backend/patient-service verify
```

`verify` includes integration tests using Testcontainers and therefore requires Docker.

The reference implementation currently has 30 passing tests, including domain tests that execute without a Spring context.

## Definition of Done

The Patient Service is complete when:

- [ ] `PATIENT` is implemented.
- [ ] All database identifiers use English `snake_case`.
- [ ] All Java/domain names use English terminology.
- [ ] `Gender` contains exactly `MALE` and `FEMALE`.
- [ ] DTO and API JSON fields use English `camelCase`.
- [ ] Patient CRUD endpoints are implemented.
- [ ] `identity_number` uniqueness is enforced.
- [ ] Email validation is implemented.
- [ ] `health_insurance_number` validation is implemented.
- [ ] `date_of_birth` cannot be in the future.
- [ ] `phone_number` validation is implemented.
- [ ] Domain invariants cannot be bypassed through setters.
- [ ] DTO validation returns appropriate HTTP 400 responses.
- [ ] Domain validation is preserved independently of DTO validation.
- [ ] `patient.created` is published after transaction commit.
- [ ] `patient.updated` is published after transaction commit.
- [ ] `payment.completed` is consumed with log-only behavior.
- [ ] `PatientRepositoryPort` contains no JPA or Spring Data types.
- [ ] Pagination conversion is isolated inside `PatientPersistenceAdapter`.
- [ ] Other services reference patients using bare `patient_id` UUIDs.
- [ ] No cross-service database access exists.
- [ ] Domain tests run without a Spring context.
- [ ] Unit tests pass.
- [ ] Integration tests pass with Testcontainers.
- [ ] Swagger/OpenAPI documentation is available.
