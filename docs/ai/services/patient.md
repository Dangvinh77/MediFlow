# Service: patient

**Source of truth:** `docs/eproject_general_plan/patient-service.html` and the locked cross-service
contracts under `docs/handoffs/care-finance/`. The current module is a skeleton; this document is the
target contract, not a claim that the implementation already exists.
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
| `gender`                  | VARCHAR(1)         | `M`, `F`                          |
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
M
F
```

The persistence value is English-named (`gender`) while the public wire value is the locked `M`/`F`
contract. Do not introduce `MALE`/`FEMALE` aliases.

## API Endpoints

| Method | Path                                 | Request                | Response           | Roles                |
| ------ | ------------------------------------ | ---------------------- | ------------------ | -------------------- |
| GET    | `/api/v1/patients/{id}`              | -                      | `PatientDTO`       | ADMIN, DOCTOR, NURSE |
| GET    | `/api/v1/patients/{id}/exists`       | -                      | `PatientLookupDTO` | SYSTEM service token only |
| GET    | `/api/v1/patients?page&size&keyword` | -                      | `Page<PatientDTO>` | ADMIN, DOCTOR, NURSE |
| POST   | `/api/v1/patients`                   | `CreatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| PUT    | `/api/v1/patients/{id}`              | `UpdatePatientRequest` | `PatientDTO`       | ADMIN, NURSE         |
| DELETE | `/api/v1/patients/{id}`              | -                      | `204 No Content`   | ADMIN                |

`GET /api/v1/patients/{id}/exists` is an internal lookup. Its response is intentionally minimal:

```json
{
  "exists": true,
  "patientId": "550e8400-e29b-41d4-a716-446655440000"
}
```

The endpoint accepts only a short-lived service JWT (`type=service`, `role=SYSTEM`) and is not a
replacement for the human-facing list or mutation endpoints.

### DTO fields

Public request/response DTOs use Vietnamese `camelCase` names to match the existing frontend contract:

```text
maBenhNhan
hoTen
ngaySinh
gioiTinh
soCmnd
diaChi
soDienThoai
email
bhytSo
createdAt
updatedAt
```

Example:

```json
{
  "maBenhNhan": "550e8400-e29b-41d4-a716-446655440000",
  "hoTen": "Nguyen Van A",
  "ngaySinh": "1990-01-15",
  "gioiTinh": "M",
  "soCmnd": "001234567890",
  "diaChi": "Ho Chi Minh City",
  "soDienThoai": "0901234567",
  "email": "patient@example.com",
  "bhytSo": "01-12345678-9",
  "createdAt": "2026-08-13T10:00:00Z",
  "updatedAt": "2026-08-13T10:00:00Z"
}
```

## Events

### Publish

The Patient Service publishes the following domain events. `patient.created` is intentionally kept in
the flat compatibility shape consumed by Notification. A versioned envelope is a separate contract
change and requires producer and consumer fixtures/tests before rollout; see
[`CONTRACT-PATIENT-NOTIFICATION-01`](../../handoffs/care-finance/CONTRACT-PATIENT-NOTIFICATION-01.md).

#### `patient.created`

```json
{
  "eventId": "...",
  "occurredAt": "...",
  "correlationId": "...",
  "patientId": "...",
  "hoTen": "...",
  "email": "...",
  "sdt": "..."
}
```

Published when a patient is successfully created.

#### `patient.updated`

```json
{
  "eventId": "...",
  "occurredAt": "...",
  "correlationId": "...",
  "patientId": "...",
  "hoTen": "...",
  "email": "...",
  "sdt": "...",
  "diaChi": "..."
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

Use English class/domain names. Public request/response fields remain Vietnamese as listed above:

```text
Patient
Gender

PatientDTO
CreatePatientRequest
UpdatePatientRequest
```

Public DTO/request fields:

```text
maBenhNhan
hoTen
ngaySinh
gioiTinh
soCmnd
diaChi
soDienThoai
email
bhytSo
createdAt
updatedAt
```

### JSON / API

Use the locked Vietnamese `camelCase` wire contract:

```json
{
  "maBenhNhan": "...",
  "hoTen": "...",
  "ngaySinh": "1990-01-15",
  "gioiTinh": "M",
  "soCmnd": "...",
  "diaChi": "...",
  "soDienThoai": "...",
  "email": "...",
  "bhytSo": "..."
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
- [ ] `Gender` uses exactly `M` and `F`.
- [ ] DTOs and request objects use Vietnamese camelCase fields.
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

## Care-finance integration alignment

Patient owns the canonical patient identity. Insurance-summary and emergency-contact fields are not
part of the locked V1 schema; add them only through a separate additive contract and migration after
their exact field definitions are agreed. Patient does not calculate insurance benefit, patient
liability, deposits or settlement; those financial results belong to Billing.

- Clinical, Lab, Pharmacy, Inpatient and Surgery store only bare `patientId` references and permitted
  event snapshots. They never join or query the Patient database.
- `GET /api/v1/patients/{id}/exists` is service-only (`type=service`, `role=SYSTEM`) and returns a
  minimal existence result. Human/public reads use `GET /api/v1/patients/{id}`. Both contracts must
  distinguish confirmed absence from upstream outage/malformed response.
- Human JWT uses explicit `patientId`; `sub` is `accountId` and must not be reinterpreted.
- Insurance fields are source inputs. Billing publishes the approved/reconciled financial amounts.
- `payment.completed` log-only behavior remains compatibility behavior and does not mutate patient
  profile or mean the complete care episode is settled.

Mandatory handoff: [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
The current Clinical lookup remains tracked in
[`HANDOFF-CLINICAL-PATIENT-LOOKUP`](../../../backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md).
