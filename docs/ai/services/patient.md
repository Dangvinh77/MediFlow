# Service: patient

**Source of truth:** `docs/eproject_general_plan/patient-service.html`  
**Module:** `backend/patient-service/` · **Base path:** `/api/v1/patients` · **DB table:** `PATIENT`

## Bounded context

Owns: patient demographics and patient records, including health insurance information.

Does NOT own: appointments, medical records, billing, drugs.

The Patient Service is the source of truth for patient identity and demographic information. Other services reference patients using the bare `patient_id` UUID and must not create cross-service database relationships.

## Data — `PATIENT`

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

#### `Gender`

```text
MALE
FEMALE
```

The Patient Service must use these enum values consistently across the database, Java domain model, API DTOs, JSON payloads, and events.

## API Endpoints

| Method | Path                                 | Request                | Response           | Roles                |
| ------ | ------------------------------------ | ---------------------- | ------------------ | -------------------- |
| GET    | `/api/v1/patients/{id}`              | -                      | `PatientDTO`       | ADMIN, DOCTOR, NURSE |
| GET    | `/api/v1/patients?page&size&keyword` | -                      | `Page<PatientDTO>` | ADMIN, DOCTOR, NURSE |
| POST   | `/api/v1/patients`                   | `CreatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| PUT    | `/api/v1/patients/{id}`              | `UpdatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| DELETE | `/api/v1/patients/{id}`              | -                      | `204 No Content`   | ADMIN                |

### DTO fields

Use English camelCase names:

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

### Publish

The Patient Service publishes the following domain events.

#### `patient.created`

```json
{
  "patientId": "...",
  "fullName": "...",
  "email": "...",
  "phoneNumber": "..."
}
```

Published when a patient is successfully created.

#### `patient.updated`

```json
{
  "patientId": "...",
  "fullName": "...",
  "email": "...",
  "phoneNumber": "...",
  "address": "..."
}
```

Published when patient demographic information is successfully updated.

### Subscribe

#### `payment.completed`

Source: Billing Service.

The Patient Service does not change patient data in response to this event. It only logs the event.

## Business rules

1. `identity_number` must be unique.
2. `email`, when provided, must use a valid email format.
3. `health_insurance_number`, when provided, must match the format `XX-XXXXXXXX-X`.
4. `date_of_birth` must not be in the future.
5. `phone_number` must contain digits only and must contain at least 10 digits.
6. `patient_id` is generated as a UUID and is immutable.
7. Other services must reference a patient using the bare `patient_id` UUID.
8. No other service may directly access the Patient Service database.
9. Patient data must not be duplicated as an owned entity in other bounded contexts.

## Cross-service references

Other services may store:

```text
patient_id
```

as a bare UUID.

They must not use:

- JPA relationships to `PATIENT`
- Cross-service database joins
- Direct access to the Patient Service database

When another service needs to validate that a patient exists, it should use the Patient Service's internal REST API according to the system's standard resilient cross-service validation pattern.

## Naming convention

All technical names use English terminology.

### Database

Use `snake_case`:

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

Table:

```text
PATIENT
```

### Java

Use English domain names:

```text
Patient
Gender

PatientDTO
CreatePatientRequest
UpdatePatientRequest
```

Fields:

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
  "dateOfBirth": "1990-01-15",
  "gender": "MALE",
  "identityNumber": "...",
  "address": "...",
  "phoneNumber": "...",
  "email": "...",
  "healthInsuranceNumber": "..."
}
```

## Architecture

The Patient Service follows the project's clean architecture:

```text
infrastructure
      │
      ▼
application
      │
      ▼
domain
```

### Domain

Contains:

- `Patient` entity
- `Gender` enum
- Business rules
- Domain events

### Application

Contains:

- Patient use cases
- Application services
- Ports
- Transaction orchestration

### Infrastructure

Contains:

- REST controllers
- JPA repositories
- PostgreSQL configuration
- Event publishing and consumption
- External service adapters

Dependencies must point inward:

```text
infrastructure → application → domain
```

The domain layer must not depend on Spring, JPA, RabbitMQ, HTTP, or other infrastructure concerns.

## Tests

Unit tests:

```bash
mvn -pl backend/patient-service test
```

These cover domain and application logic without requiring Spring infrastructure.

Integration verification:

```bash
mvn -pl backend/patient-service verify
```

Integration tests should use Testcontainers where database or messaging infrastructure is required.

## Definition of Done

The service is complete when:

- [ ] `PATIENT` is implemented.
- [ ] All database columns use English naming.
- [ ] Java domain classes and fields use English names.
- [ ] `Gender` uses exactly `MALE` and `FEMALE`.
- [ ] DTOs and request objects use English camelCase fields.
- [ ] Patient CRUD endpoints are implemented.
- [ ] `identity_number` uniqueness is enforced.
- [ ] Email validation is implemented.
- [ ] Health insurance number validation is implemented.
- [ ] Date of birth cannot be in the future.
- [ ] Phone number validation is implemented.
- [ ] `patient.created` is published after successful creation.
- [ ] `patient.updated` is published after successful update.
- [ ] `payment.completed` is consumed with log-only behavior.
- [ ] Other services reference patients using bare `patient_id` UUIDs.
- [ ] No cross-service database access exists.
- [ ] Unit tests pass.
- [ ] Integration tests pass.
