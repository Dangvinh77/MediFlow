# Organization Service

**Module:** `backend/organization-service/`  
**Base path:** `/api/v1/org`  
**Database tables:** `DEPARTMENT`, `STAFF`, `ACCOUNT`

**Source of truth:** `docs/eproject_general_plan/organization-service.html`

> **This service is the foundation the rest of the system references.** It owns the three things everything else points at:
>
> 1. **Departments** — `DEPARTMENT`, and `department_id` that every operational table and every domain event carries. Without it there is no departmental dimension at all.
> 2. **Staff** — `STAFF` provides the `staff_id` referenced by appointments, medical records, prescriptions, and other operational contexts.
> 3. **Accounts** — `ACCOUNT` is what the gateway checks before issuing a JWT.

## Bounded Context

Owns the hospital's **organizational structure** — which departments exist, who works in them, and how those people authenticate.

Does NOT own: patients, appointments, medical records, tests, drugs, billing, or payments. It answers _who_ and _where_, never _what happened to a patient_.

## Data

### `DEPARTMENT`

A hospital department or organizational unit.

| Column               | Type         | Constraints / Description                    |
| -------------------- | ------------ | -------------------------------------------- |
| `department_id`      | UUID         | Primary key                                  |
| `department_name`    | VARCHAR(100) | Department name                              |
| `abbreviation`       | VARCHAR(20)  | Unique abbreviation                          |
| `department_type`    | ENUM         | `CLINICAL`, `PARACLINICAL`, `ADMINISTRATIVE` |
| `department_head_id` | UUID         | References `STAFF.staff_id`, nullable        |
| `location`           | VARCHAR(255) | Department location                          |
| `is_active`          | BOOLEAN      | Whether the department is active             |
| `created_at`         | TIMESTAMPTZ  | Creation timestamp                           |
| `updated_at`         | TIMESTAMPTZ  | Last update timestamp                        |

### `STAFF`

A staff member such as a doctor, nurse, technician, pharmacist, cashier, manager, or administrative employee.

| Column           | Type         | Constraints / Description                       |
| ---------------- | ------------ | ----------------------------------------------- |
| `staff_id`       | UUID         | Primary key                                     |
| `full_name`      | VARCHAR(100) | Staff member's full name                        |
| `department_id`  | UUID         | Foreign key to `DEPARTMENT`, same service       |
| `job_title`      | ENUM         | See Staff Job Title enum below                  |
| `specialization` | VARCHAR(100) | Optional specialization                         |
| `license_number` | VARCHAR(50)  | Optional professional/practising license number |
| `phone_number`   | VARCHAR(15)  | Phone number                                    |
| `email`          | VARCHAR(100) | Email address                                   |
| `status`         | ENUM         | `ACTIVE`, `INACTIVE`                            |
| `created_at`     | TIMESTAMPTZ  | Creation timestamp                              |
| `updated_at`     | TIMESTAMPTZ  | Last update timestamp                           |

### `ACCOUNT`

A login account used for authentication.

| Column          | Type         | Constraints / Description        |
| --------------- | ------------ | -------------------------------- |
| `account_id`    | UUID         | Primary key                      |
| `username`      | VARCHAR(50)  | Unique username                  |
| `password_hash` | VARCHAR(255) | BCrypt password hash             |
| `staff_id`      | UUID         | Foreign key to `STAFF`, nullable |
| `role`          | ENUM         | See Account Role enum below      |
| `is_active`     | BOOLEAN      | Whether the account can log in   |
| `last_login_at` | TIMESTAMPTZ  | Nullable                         |
| `created_at`    | TIMESTAMPTZ  | Creation timestamp               |
| `updated_at`    | TIMESTAMPTZ  | Last update timestamp            |

`role` must mirror `backend/common/security/Roles.java` exactly. Keep both definitions synchronized.

## Enum Definitions

### Department Type — `department_type`

```text
CLINICAL
PARACLINICAL
ADMINISTRATIVE
```

### Staff Job Title — `job_title`

```text
DOCTOR
NURSE
TECHNICIAN
PHARMACIST
CASHIER
MANAGER
ADMINISTRATIVE
```

### Staff Status — `status`

```text
ACTIVE
INACTIVE
```

### Account Role — `role`

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

`TECHNICIAN` and `LAB_TECH` are intentionally different concepts:

- `TECHNICIAN` is a staff **job title**.
- `LAB_TECH` is a system **authorization role**.

They do not need to have identical names.

## Endpoints

| Method | Path                                                | Roles                                             |
| ------ | --------------------------------------------------- | ------------------------------------------------- |
| GET    | `/api/v1/org/departments`                           | ADMIN, MANAGER, DOCTOR, NURSE                     |
| GET    | `/api/v1/org/departments/{id}`                      | ADMIN, MANAGER, DOCTOR, NURSE                     |
| POST   | `/api/v1/org/departments`                           | ADMIN                                             |
| PUT    | `/api/v1/org/departments/{id}`                      | ADMIN                                             |
| GET    | `/api/v1/org/staff?departmentId&jobTitle&page&size` | ADMIN, MANAGER, DOCTOR, NURSE                     |
| GET    | `/api/v1/org/staff/{id}`                            | ADMIN, MANAGER, DOCTOR, NURSE                     |
| POST   | `/api/v1/org/staff`                                 | ADMIN                                             |
| PUT    | `/api/v1/org/staff/{id}`                            | ADMIN                                             |
| PUT    | `/api/v1/org/staff/{id}/department`                 | ADMIN                                             |
| GET    | `/api/v1/org/staff/{id}/exists`                     | SYSTEM _(internal lookup used by other services)_ |
| POST   | `/api/v1/org/accounts`                              | ADMIN                                             |
| PUT    | `/api/v1/org/accounts/{id}/status`                  | ADMIN                                             |
| POST   | `/api/v1/org/accounts/verify`                       | SYSTEM _(gateway only — never exposed publicly)_  |

### Department Transfer Request

`PUT /api/v1/org/staff/{id}/department`

Request body:

```json
{
  "departmentId": "..."
}
```

The operation transfers the existing staff member to another department. It must not delete and recreate the staff member.

### Account Status Request

`PUT /api/v1/org/accounts/{id}/status`

Request body:

```json
{
  "isActive": true
}
```

### Account Verification Request

`POST /api/v1/org/accounts/verify`

Request body:

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

`/accounts/verify` turns the gateway's stub login into real authentication: the gateway posts credentials, this service verifies the BCrypt password hash and account status, then returns the authenticated identity information. The gateway mints the JWT.

**The gateway never reads the `ACCOUNT` table directly.** Direct cross-service database access is prohibited.

## Events

### Published Events

#### `department.created`

Payload:

```json
{
  "departmentId": "...",
  "departmentName": "...",
  "departmentType": "CLINICAL"
}
```

#### `staff.created`

Payload:

```json
{
  "staffId": "...",
  "fullName": "...",
  "departmentId": "...",
  "jobTitle": "DOCTOR"
}
```

#### `staff.department.changed`

Payload:

```json
{
  "staffId": "...",
  "oldDepartmentId": "...",
  "newDepartmentId": "..."
}
```

This event represents a staff transfer between departments.

### Subscribed Events

None.

This service owns reference data and drives other contexts rather than reacting to events from them.

## Business Rules

1. `username` must be unique.

2. Passwords must be stored as **BCrypt hashes**. Plaintext passwords must never be stored or logged.

3. Every `STAFF` member must belong to exactly one active `DEPARTMENT`.

4. `job_title = DOCTOR` requires a non-empty `license_number`.

5. `department_head_id`, when provided, must reference a `STAFF` member belonging to the same department.

6. A department with active staff cannot be deactivated.

7. Staff transfers must update the existing `STAFF` record and publish `staff.department.changed`. The staff record must never be deleted and recreated for a transfer.

8. Accounts with `role = PATIENT` must have a null `staff_id`, because patients are not staff members.

9. Deactivating an account (`is_active = false`) must prevent future logins.

10. Existing JWTs do not need to be revoked by this service; they expire naturally according to the gateway's JWT configuration.

11. The `role` enum must remain exactly synchronized with `backend/common/security/Roles.java`.

12. No other service may directly access the Organization Service database.

## Why This Service Makes the Departmental Dimension Real

Once `department_id` exists, every other service can carry it, and questions that define a departmental system become answerable.

| Question                                             | Required Data                                          |
| ---------------------------------------------------- | ------------------------------------------------------ |
| Which department ordered this lab test?              | `LAB_TEST.department_id`                               |
| Revenue per department this month                    | `BILLING.department_id` + report aggregation           |
| Which doctors are in a department, and who leads it? | `STAFF.department_id`, `DEPARTMENT.department_head_id` |
| Move a doctor from one department to another         | `staff.department.changed`                             |
| Can this user perform this action?                   | `ACCOUNT.role`, resolved at login                      |

Before this service exists, these organizational relationships cannot be reliably resolved as a shared system-wide reference.

## Cross-Service Rules

- Other services store `department_id` and `staff_id` as **bare UUIDs**.
- Other services must not create JPA relationships to Organization Service entities.
- Other services must not perform cross-database joins with Organization Service tables.
- `appointment` and `medical-record` validate staff existence through:

  `/api/v1/org/staff/{id}/exists`

- These internal REST lookups should be resilient using timeout, circuit breaker, and fallback mechanisms.
- The gateway calls:

  `/api/v1/org/accounts/verify`

  during authentication.

- Cross-service communication must use REST APIs or domain events.
- Direct database access across service boundaries is prohibited.

## Naming Convention

All database, API, Java, DTO, JSON, event, and business-layer terminology must use English names.

### Database Naming

Use `snake_case` for database tables and columns.

| Concept                 | Database Name        |
| ----------------------- | -------------------- |
| Department              | `DEPARTMENT`         |
| Staff                   | `STAFF`              |
| Account                 | `ACCOUNT`            |
| Department ID           | `department_id`      |
| Department name         | `department_name`    |
| Department abbreviation | `abbreviation`       |
| Department type         | `department_type`    |
| Department head         | `department_head_id` |
| Staff ID                | `staff_id`           |
| Full name               | `full_name`          |
| Job title               | `job_title`          |
| Specialization          | `specialization`     |
| License number          | `license_number`     |
| Phone number            | `phone_number`       |
| Status                  | `status`             |
| Account ID              | `account_id`         |
| Username                | `username`           |
| Password hash           | `password_hash`      |
| Role                    | `role`               |
| Active flag             | `is_active`          |
| Last login              | `last_login_at`      |
| Creation time           | `created_at`         |
| Update time             | `updated_at`         |

### API / JSON Naming

Use `camelCase` for JSON request and response fields.

Examples:

```json
{
  "departmentId": "...",
  "departmentName": "...",
  "departmentType": "CLINICAL",
  "staffId": "...",
  "fullName": "...",
  "jobTitle": "DOCTOR",
  "accountId": "...",
  "username": "...",
  "role": "DOCTOR",
  "isActive": true
}
```

### Java Naming

Use English names consistently:

```text
Department
Staff
Account

DepartmentType
JobTitle
StaffStatus
Role

departmentId
departmentName
departmentType
departmentHeadId

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
isActive
lastLoginAt
```

## Enum Mapping Summary

| Database Field               | Java Enum        | Values                                                                                          |
| ---------------------------- | ---------------- | ----------------------------------------------------------------------------------------------- |
| `DEPARTMENT.department_type` | `DepartmentType` | `CLINICAL`, `PARACLINICAL`, `ADMINISTRATIVE`                                                    |
| `STAFF.job_title`            | `JobTitle`       | `DOCTOR`, `NURSE`, `TECHNICIAN`, `PHARMACIST`, `CASHIER`, `MANAGER`, `ADMINISTRATIVE`           |
| `STAFF.status`               | `StaffStatus`    | `ACTIVE`, `INACTIVE`                                                                            |
| `ACCOUNT.role`               | `Role`           | `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, `SYSTEM` |

## Architecture Responsibility

The Organization Service owns:

```text
Department
    ├── Department identity
    ├── Department type
    ├── Department status
    └── Department head

Staff
    ├── Staff identity
    ├── Department assignment
    ├── Job title
    ├── Professional information
    └── Employment status

Account
    ├── Username
    ├── Password hash
    ├── Staff association
    ├── Role
    └── Account status
```

It does not own:

```text
Patient
Appointment
Medical Record
Laboratory Test
Prescription
Drug
Billing
Payment
```

Those belong to their respective bounded contexts.

## Final Consistency Requirements

The following vocabulary must be used consistently throughout the project:

```text
DEPARTMENT
STAFF
ACCOUNT

department_id
staff_id
account_id

department_name
department_type
department_head_id

full_name
job_title
specialization
license_number
phone_number
status

username
password_hash
role
is_active
last_login_at

CLINICAL
PARACLINICAL
ADMINISTRATIVE

DOCTOR
NURSE
TECHNICIAN
PHARMACIST
CASHIER
MANAGER
ADMINISTRATIVE

ACTIVE
INACTIVE

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

There must be no Vietnamese table names, column names, enum values, API field names, event payload fields, or Java domain names in the Organization Service.
