# Service: lab

**Source of truth:** `docs/eproject_general_plan/lab-service.html`
**Module:** `backend/lab-service/` · **Base path:** `/api/v1/lab` · **DB tables:** `LAB_TEST`, `LAB_RESULT`

## Bounded context
Owns: lab tests (`LAB_TEST`) & results (`LAB_RESULT`). Does NOT own: patients, records, billing.

## Data
`LAB_TEST`: `test_id` UUID PK · `record_id` UUID (ref → clinical) · `patient_id` UUID (ref → patient) · `requesting_department_id` UUID (ref → organization `DEPARTMENT`, which department ordered it) · `test_type` VARCHAR(50) · `requested_date` DATE · `performed_date` DATE · `status` ENUM('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') · `conclusion` TEXT.
`LAB_RESULT`: `result_id` UUID PK · `test_id` UUID (FK, same service) · `indicator` VARCHAR(100) · `value` VARCHAR(50) · `unit` VARCHAR(20) · `reference_range` VARCHAR(50).

## Endpoints
| Method | Path | Roles |
|--------|------|-------|
| GET | `/api/v1/lab/{id}` | ADMIN, DOCTOR, NURSE |
| GET | `/api/v1/lab/patient/{patientId}` | ADMIN, DOCTOR |
| GET | `/api/v1/lab?departmentId&status&page&size` | ADMIN, MANAGER, LAB_TECH |
| POST | `/api/v1/lab` | ADMIN, DOCTOR |
| PUT | `/api/v1/lab/{id}/results` | ADMIN, LAB_TECH |
| PUT | `/api/v1/lab/{id}/status` | ADMIN, LAB_TECH |

## Events
- **Publish:** `lab.request.created` `{labId, patientId, recordId, departmentId, labType, requestedDate}`; `lab.result.created` `{labId, patientId, recordId, departmentId, results, conclusion}`.
- **Subscribe:** `medicalrecord.created` → auto-create sample lab test if indicated; `payment.completed` → update payment status for the test.

## Business rules
1. Cannot add a result if `status` is `COMPLETED` or `CANCELLED`.
2. Adding a result auto-transitions `status` → `COMPLETED`.
3. `performed_date` ≥ `requested_date`.
