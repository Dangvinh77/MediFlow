# organization-service

Departments, staff and accounts — the organisational backbone the rest of the system references.

Reference: [`docs/ai/services/organization.md`](../docs/ai/services/organization.md) · design doc [`EProject/organization-service.html`](../docs/eproject_general_plan/organization-service.html).

- **Port:** 8089 · **Base path:** `/api/v1/org` · **DB:** `mediflow_organization` (PostgreSQL)
- **Owns tables:** `DEPARTMENT`, `STAFF`, `ACCOUNT`
- **Architecture:** clean architecture per [`docs/ai/04-microservice-blueprint.md`](../docs/ai/04-microservice-blueprint.md) — `infrastructure → application → domain`.

## Why it exists

It owns the three things every other service points at:

1. **Departments** — `DEPARTMENT`, and the `department_id` carried by every operational table and every domain event. This is the dimension that makes the system _departmental_ rather than merely modular.
2. **Staff** — `STAFF` provides the `staff_id` referenced from `APPOINTMENT`, `MEDICAL_RECORD` and `PRESCRIPTION`, three bounded contexts that must not own staff identity themselves.
3. **Accounts** — `ACCOUNT` is what the gateway checks before issuing a JWT. The gateway holds no user data of its own.

## Data ownership

The Organization Service owns:

### `DEPARTMENT`

| Column               | Type         | Description                                  |
| -------------------- | ------------ | -------------------------------------------- |
| `department_id`      | UUID         | Primary key                                  |
| `department_name`    | VARCHAR(100) | Department name                              |
| `abbreviation`       | VARCHAR(20)  | Unique department abbreviation               |
| `department_type`    | ENUM         | `CLINICAL`, `PARACLINICAL`, `ADMINISTRATIVE` |
| `department_head_id` | UUID         | References `STAFF.staff_id`, nullable        |
| `location`           | VARCHAR(255) | Department location                          |
| `is_active`          | BOOLEAN      | Whether the department is active             |
| `created_at`         | TIMESTAMPTZ  | Creation timestamp                           |
| `updated_at`         | TIMESTAMPTZ  | Last update timestamp                        |

### `STAFF`

| Column           | Type         | Description                                                                           |
| ---------------- | ------------ | ------------------------------------------------------------------------------------- |
| `staff_id`       | UUID         | Primary key                                                                           |
| `full_name`      | VARCHAR(100) | Staff member's full name                                                              |
| `department_id`  | UUID         | Foreign key to `DEPARTMENT`                                                           |
| `job_title`      | ENUM         | `DOCTOR`, `NURSE`, `TECHNICIAN`, `PHARMACIST`, `CASHIER`, `MANAGER`, `ADMINISTRATIVE` |
| `specialization` | VARCHAR(100) | Optional specialization                                                               |
| `license_number` | VARCHAR(50)  | Optional professional/practising license                                              |
| `phone_number`   | VARCHAR(15)  | Phone number                                                                          |
| `email`          | VARCHAR(100) | Email address                                                                         |
| `status`         | ENUM         | `ACTIVE`, `INACTIVE`                                                                  |
| `created_at`     | TIMESTAMPTZ  | Creation timestamp                                                                    |
| `updated_at`     | TIMESTAMPTZ  | Last update timestamp                                                                 |

### `ACCOUNT`

| Column          | Type         | Description                                                                                     |
| --------------- | ------------ | ----------------------------------------------------------------------------------------------- |
| `account_id`    | UUID         | Primary key                                                                                     |
| `username`      | VARCHAR(50)  | Unique username                                                                                 |
| `password_hash` | VARCHAR(255) | BCrypt password hash                                                                            |
| `staff_id`      | UUID         | Foreign key to `STAFF`, nullable                                                                |
| `role`          | ENUM         | `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, `SYSTEM` |
| `is_active`     | BOOLEAN      | Whether the account can log in                                                                  |
| `last_login_at` | TIMESTAMPTZ  | Nullable                                                                                        |
| `created_at`    | TIMESTAMPTZ  | Creation timestamp                                                                              |
| `updated_at`    | TIMESTAMPTZ  | Last update timestamp                                                                           |

## Enum definitions

The following enum values are the single source of truth for the Organization Service.

### `DepartmentType`

```text
CLINICAL
PARACLINICAL
ADMINISTRATIVE
```

### `JobTitle`

```text
DOCTOR
NURSE
TECHNICIAN
PHARMACIST
CASHIER
MANAGER
ADMINISTRATIVE
```

### `StaffStatus`

```text
ACTIVE
INACTIVE
```

### `Role`

```text
ADMIN
DOCTOR
NURSE
PHARMACIST
CASHIER
LAB_TECH
MANAGER
PATIENT
SYSTEM
```

`Role` must remain exactly synchronized with `backend/common/security/Roles.java`.

`TECHNICIAN` and `LAB_TECH` intentionally represent different concepts:

- `TECHNICIAN` is a staff `JobTitle`.
- `LAB_TECH` is an authorization `Role`.

## Status

**Skeleton only.** Module, dependencies, configuration and the mandated package layout are in place — no business code yet. Build it against the _Definition of Done_ in the blueprint and the rules in the service documentation.

## Run locally

1. `CREATE DATABASE mediflow_organization;` (or `docker compose up -d`, which creates it).
2. Start `eureka-server` on port `8761`, then `gateway` on port `8080`, then this service.
3. RabbitMQ runs on `localhost:5672`.

```bash
mvn -pl backend/organization-service -am spring-boot:run
```

Swagger UI:

`http://localhost:8089/swagger-ui.html`

## Endpoints

| Method | Path                                                | Roles                         |
| ------ | --------------------------------------------------- | ----------------------------- |
| GET    | `/api/v1/org/departments`                           | ADMIN, MANAGER, DOCTOR, NURSE |
| GET    | `/api/v1/org/departments/{id}`                      | ADMIN, MANAGER, DOCTOR, NURSE |
| POST   | `/api/v1/org/departments`                           | ADMIN                         |
| PUT    | `/api/v1/org/departments/{id}`                      | ADMIN                         |
| GET    | `/api/v1/org/staff?departmentId&jobTitle&page&size` | ADMIN, MANAGER, DOCTOR, NURSE |
| GET    | `/api/v1/org/staff/{id}`                            | ADMIN, MANAGER, DOCTOR, NURSE |
| POST   | `/api/v1/org/staff`                                 | ADMIN                         |
| PUT    | `/api/v1/org/staff/{id}`                            | ADMIN                         |
| PUT    | `/api/v1/org/staff/{id}/department`                 | ADMIN                         |
| GET    | `/api/v1/org/staff/{id}/exists`                     | SYSTEM                        |
| POST   | `/api/v1/org/accounts`                              | ADMIN                         |
| PUT    | `/api/v1/org/accounts/{id}/status`                  | ADMIN                         |
| POST   | `/api/v1/org/accounts/verify`                       | SYSTEM                        |

### Department transfer

`PUT /api/v1/org/staff/{id}/department`

Request:

```json
{
  "departmentId": "..."
}
```

The operation updates the existing staff member's `department_id` and publishes `staff.department.changed`. It never deletes and recreates the staff member.

### Account status

`PUT /api/v1/org/accounts/{id}/status`

Request:

```json
{
  "isActive": true
}
```

### Account verification

`POST /api/v1/org/accounts/verify`

Request:

```json
{
  "username": "...",
  "password": "..."
}
```

Successful response:

```json
{
  "accountId": "...",
  "staffId": "...",
  "departmentId": "...",
  "role": "DOCTOR"
}
```

The gateway posts credentials to this endpoint. The Organization Service verifies the account and BCrypt password hash, then returns the authenticated identity. The gateway uses the result to mint the JWT.

**The gateway must never read the `ACCOUNT` table directly.** That would violate the service boundary through cross-service database access.

## Events

### Publish

- `department.created`
- `staff.created`
- `staff.department.changed`

### `department.created`

```json
{
  "departmentId": "...",
  "departmentName": "...",
  "departmentType": "CLINICAL"
}
```

### `staff.created`

```json
{
  "staffId": "...",
  "fullName": "...",
  "departmentId": "...",
  "jobTitle": "DOCTOR"
}
```

### `staff.department.changed`

```json
{
  "staffId": "...",
  "oldDepartmentId": "...",
  "newDepartmentId": "..."
}
```

### Subscribe

None. This is reference data; it drives other contexts rather than reacting to them.

## Business rules

1. `username` is unique.
2. Passwords are stored as **BCrypt hashes**, never plaintext, and never logged.
3. A `STAFF` member must belong to exactly one active `DEPARTMENT`.
4. `job_title = DOCTOR` requires a non-empty `license_number`.
5. `department_head_id`, if set, must reference a `STAFF` member of the same department.
6. A department with active staff cannot be deactivated.
7. Staff transfer updates the existing `STAFF` record and publishes `staff.department.changed`; it never deletes and recreates the staff member.
8. `role = PATIENT` accounts must have `staff_id = null`, because patients are not staff.
9. Deactivating an account (`is_active = false`) prevents future logins.
10. Existing JWTs expire naturally according to the gateway's JWT configuration.
11. `Role` must remain synchronized with `backend/common/security/Roles.java`.
12. No other service may directly access the Organization Service database.

## Two integration points to get right

### Account verification

**`POST /api/v1/org/accounts/verify`**

The gateway posts credentials here and mints the JWT from the result.

Flow:

```text
Client
  │
  │ username + password
  ▼
Gateway
  │
  │ POST /api/v1/org/accounts/verify
  ▼
Organization Service
  │
  ├── find ACCOUNT by username
  ├── verify BCrypt password
  ├── verify account is active
  └── return accountId, staffId, departmentId, role
  │
  ▼
Gateway
  │
  └── mint JWT
```

The gateway must **never** read `ACCOUNT` directly.

### Staff existence

**`GET /api/v1/org/staff/{id}/exists`**

`APPOINTMENT` and `MEDICAL_RECORD` use this endpoint to validate `staff_id`.

The lookup must be resilient with:

- Timeout
- Circuit breaker
- Fallback

This follows the same cross-service validation pattern used when operational services validate patient existence.

## Cross-service references

Other services must hold Organization Service identifiers as **bare UUIDs**.

Examples:

```text
department_id
staff_id
```

They must not use:

- JPA relationships to Organization Service entities
- Cross-service database joins
- Direct access to the `mediflow_organization` database

### Example: APPOINTMENT

The `APPOINTMENT` bounded context may contain:

```text
staff_id
department_id
```

It does not contain a JPA relationship such as:

```java
@ManyToOne
private Staff staff;
```

Instead, it stores the UUID and performs an internal REST lookup when staff existence needs to be validated.

### Example: MEDICAL_RECORD

The `MEDICAL_RECORD` bounded context may contain:

```text
staff_id
department_id
```

but does not own staff or department data.

### Example: PRESCRIPTION

The `PRESCRIPTION` bounded context may reference:

```text
staff_id
department_id
```

as bare UUIDs.

## Naming convention

All names in this service and all references documented here use English terminology.

### Database

Use `snake_case` for columns:

```text
department_id
department_name
abbreviation
department_type
department_head_id
location
is_active

staff_id
full_name
job_title
specialization
license_number
phone_number
status

account_id
username
password_hash
role
last_login_at

created_at
updated_at
```

Database table names:

```text
DEPARTMENT
STAFF
ACCOUNT
```

### Java

Use English domain names:

```text
Department
Staff
Account

DepartmentType
JobTitle
StaffStatus
Role
```

Fields:

```text
departmentId
departmentName
abbreviation
departmentType
departmentHeadId
location
isActive

staffId
fullName
jobTitle
specialization
licenseNumber
phoneNumber
status

accountId
username
passwordHash
role
lastLoginAt
```

### JSON / API

Use `camelCase`:

```json
{
  "departmentId": "...",
  "departmentName": "...",
  "departmentType": "CLINICAL",
  "departmentHeadId": "...",
  "staffId": "...",
  "fullName": "...",
  "jobTitle": "DOCTOR",
  "accountId": "...",
  "username": "...",
  "role": "DOCTOR",
  "isActive": true
}
```

## Related service naming

When this README refers to other bounded contexts, use their English names consistently:

| Context         | Table / Domain   |
| --------------- | ---------------- |
| Appointment     | `APPOINTMENT`    |
| Medical Record  | `MEDICAL_RECORD` |
| Prescription    | `PRESCRIPTION`   |
| Laboratory Test | `LAB_TEST`       |
| Billing         | `BILLING`        |
| Patient         | `PATIENT`        |

The Organization Service does not own these tables.

## Architecture

The service follows the project's clean architecture:

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

- Entities
- Value objects
- Domain enums
- Business rules
- Domain events

### Application

Contains:

- Use cases
- Application services
- Ports
- Transaction orchestration

### Infrastructure

Contains:

- REST controllers
- JPA repositories
- Database configuration
- RabbitMQ integration
- Security integration
- External service adapters

Dependencies must point inward:

```text
infrastructure → application → domain
```

The domain layer must not depend on Spring, JPA, RabbitMQ, HTTP, or other infrastructure concerns.

## Tests

Unit tests:

```bash
mvn -pl backend/organization-service test
```

These cover domain and application logic without requiring Spring infrastructure.

Integration verification:

```bash
mvn -pl backend/organization-service verify
```

Integration tests use Testcontainers and require Docker.

## Definition of Done

The service is complete when:

- [ ] `DEPARTMENT`, `STAFF`, and `ACCOUNT` are implemented.
- [ ] All database columns use the English naming convention.
- [ ] All Java domain classes and fields use English names.
- [ ] All enum values match the definitions in this README.
- [ ] `Role` matches `backend/common/security/Roles.java`.
- [ ] CRUD endpoints for departments are implemented.
- [ ] Staff lookup, creation, update, and department transfer are implemented.
- [ ] Staff existence lookup is implemented.
- [ ] Account creation and status management are implemented.
- [ ] Account verification uses BCrypt.
- [ ] Plaintext passwords are never stored or logged.
- [ ] Patient accounts have no `staff_id`.
- [ ] Department and staff domain events are published.
- [ ] No events are consumed by this service.
- [ ] Cross-service references use bare UUIDs.
- [ ] No cross-service database access exists.
- [ ] Staff existence validation supports timeout, circuit breaker, and fallback.
- [ ] Gateway authentication uses `/api/v1/org/accounts/verify`.
- [ ] Unit tests pass.
- [ ] Integration tests with Testcontainers pass.
- [ ] Swagger/OpenAPI documentation is available.
