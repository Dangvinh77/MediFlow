# Service: clinical (Khoa Khám bệnh)

**Source of truth:** `docs/eproject_general_plan/clinical-service.html`
**Module:** `backend/clinical-service/` · **Base paths:** `/api/v1/appointments`, `/api/v1/records` · **DB tables:** `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS`

> **Why appointments and records share one service.** They are one department's single workflow —
> book, examine, record. Separating them would force `MEDICAL_RECORD.appointment_id` to become a foreign key
> across a network boundary, and would require publishing an event just to set the appointment to
> `ARRIVED` — a message broker doing the work of **one local transaction**. Together, neither problem
> exists.

## Bounded context

Owns: the outpatient examination workflow — appointments, medical records, diagnoses.

Does NOT own: patients (→ `patient`), staff and departments (→ `organization`), lab tests (→ `lab`),
drugs (→ `pharmacy`), money (→ `billing`).

## Data

**`APPOINTMENT`** — appointment
`appointment_id` UUID PK · `patient_id` UUID (ref → patient) · `doctor_id` UUID (ref → organization `STAFF`) · `department_id` UUID (ref → organization `DEPARTMENT`) · `appointment_date` DATE · `appointment_time` TIME · `status` ENUM('PENDING','ARRIVED','CANCELLED') · `reason` TEXT · `created_at` · `updated_at`.

**`MEDICAL_RECORD`** — medical record
`record_id` UUID PK · `patient_id` UUID (ref → patient) · `doctor_id` UUID (ref → organization) · `department_id` UUID (ref → organization) · `examination_date` DATE · `symptoms` TEXT · `appointment_id` UUID (**FK, same service**, nullable) · `created_at` · `updated_at`.

**`DIAGNOSIS`** — diagnosis
`diagnosis_id` UUID PK · `record_id` UUID (FK, same service) · `diagnosis_name` VARCHAR(255) · `description` TEXT · `icd_code` VARCHAR(10).

`department_id` is what makes this a *departmental* system: it records which department the appointment and
examination belong to, so every downstream fee, test and report can be attributed to a department.

## Endpoints

| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments/patient/{patientId}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/appointments?departmentId&appointmentDate&page&size` | ADMIN, MANAGER, DOCTOR, NURSE |
| POST | `/api/v1/appointments` | ADMIN, NURSE |
| PUT | `/api/v1/appointments/{id}` | ADMIN, DOCTOR, NURSE |
| PUT | `/api/v1/appointments/{id}/status` `{status}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/records/patient/{patientId}` | ADMIN, DOCTOR, NURSE |
| POST | `/api/v1/records` | ADMIN, DOCTOR |
| PUT | `/api/v1/records/{id}` | ADMIN, DOCTOR |
| POST | `/api/v1/records/{id}/diagnoses` | ADMIN, DOCTOR |

## Events

- **Publish:**
  - `appointment.created` `{appointmentId, patientId, doctorId, departmentId, appointmentDate, appointmentTime}`
  - `appointment.status.changed` `{appointmentId, status, patientId, departmentId}`
  - `medicalrecord.created` `{recordId, patientId, doctorId, departmentId, diagnosis, examinationDate}`
  - `diagnosis.added` `{recordId, diagnosisCode, diagnosisName}`
- **Subscribe:**
  - `lab.result.created` → attach the result to the record
  - `prescription.filled` → attach prescription info to the record

> Creating a record for an appointment flips that appointment to `ARRIVED` **in the same
> transaction** — never via an event. Publishing an event to update your own database means the
> service boundary is cut in the wrong place.

## Business rules

**Appointments**
1. Cannot create an appointment for a past date.
2. A patient cannot hold more than one `PENDING` appointment on the same day.
3. `appointment_time` must fall within 07:00–17:00.
4. The doctor must exist and belong to the department in `department_id` (REST-check `organization-service`).

**Records**
5. Every record must have at least one diagnosis.
6. A patient has one active record per examination; create one if none exists.
7. Cannot create a record if the patient does not exist (REST-check `patient-service`).
8. Creating a record from an appointment sets that appointment to `ARRIVED` — **same transaction**, not an event.

## Cross-service

Both are synchronous reads, so both must be resilient (timeout + circuit breaker + fallback, `01`):

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and are they in this department?
