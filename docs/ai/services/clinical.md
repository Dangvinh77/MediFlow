# Service: clinical (Khoa Khám bệnh)

**Source of truth:** `docs/eproject_general_plan/clinical-service.html` plus the approved
[`care-finance redesign`](../../architecture/mediflow-care-finance-redesign.html) for payment gates,
admission referral and cross-service contracts.
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
`appointment_id` UUID PK · `patient_id` UUID (ref → patient) · `doctor_id` UUID (ref → organization `STAFF`) · `department_id` UUID (ref → organization `DEPARTMENT`) · `appointment_date` DATE · `appointment_time` TIME · `status` current `PENDING|ARRIVED|CANCELLED`, target adds `AWAITING_PAYMENT|READY_FOR_EXAM|IN_EXAM|COMPLETED` · `reason` TEXT · `created_at` · `updated_at`.

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
  - `appointment.status.changed` `{appointmentId, recordId, status, patientId, departmentId}` — `recordId` nullable until the record exists; billing must defer record-based fees when absent.
  - `medicalrecord.created` `{recordId, patientId, doctorId, departmentId, diagnosis, examinationDate}`
  - `medicalrecord.completed` `{recordId, patientId, departmentId, disposition, admissionRequired, completedAt}` *(target)*
  - `admission.requested` `{admissionRequestId, recordId, patientId, departmentId, requestedBy, diagnosisSummary, priority, emergency, requestedAt}` *(target)*
  - `diagnosis.added` `{recordId, diagnosisCode, diagnosisName}`
- **Subscribe:**
  - `financial.clearance.granted` with `purpose=EXAM` and matching appointment/record target *(target)*
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
9. `ARRIVED` means the patient checked in; it does not authorize examination. Starting examination
   requires matching EXAM clearance or an audited emergency override.
10. Completing a record requires an explicit disposition: outpatient follow-up, prescription,
    admission referral, transfer, or other approved outcome.
11. An admission referral uses a producer-generated `admissionRequestId`; Clinical never creates an
    Inpatient row or queries the Inpatient database.

## Cross-service

Both are synchronous reads, so both must be resilient (timeout + circuit breaker + fallback, `01`):

- `patient-service` — does this patient exist?
- `organization-service` — does this doctor exist, and are they in this department?

## Care-finance integration gate

- Read [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md) before
  changing appointment/exam payment state or clearance consumption.
- Read [`CONTRACT-INPATIENT-SURGERY-01`](../../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md)
  before changing admission/surgery referral events.
- Read [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md)
  before changing Patient/Organization lookups or service JWTs.
- Read [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md)
  before changing `medicalrecord.completed` or any event consumed by Report/Notification.
- Current `appointment.status.changed`/`medicalrecord.created` Billing behavior is a compatibility
  path. Do not remove it until Billing and Clinical share clearance v1 fixtures and migration tests.
- Contract status is not `IMPLEMENTED` until the producer fixture and Clinical consumer fixture/test
  pass together. Missing target IDs must block the flow; never select a record by patient.
