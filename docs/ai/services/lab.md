# Service: lab

**Source of truth:** `docs/eproject_general_plan/lab-service.html` plus the approved
[`care-finance redesign`](../../architecture/mediflow-care-finance-redesign.html) for request-time
charging and financial clearance.
**Module:** `backend/lab-service/` · **Base path:** `/api/v1/lab` · **DB tables:** `LAB_TEST`, `LAB_RESULT`

## Bounded context
Owns: lab tests (`LAB_TEST`) & results (`LAB_RESULT`). Does NOT own: patients, records, billing.

## Data
`LAB_TEST`: `test_id` UUID PK · `record_id` UUID (ref → clinical) · `patient_id` UUID (ref → patient) · `requesting_department_id` UUID (ref → organization `DEPARTMENT`, which department ordered it) · `test_type` VARCHAR(50) · `requested_date` DATE · `performed_date` DATE · `status` ENUM('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') · current compatibility `paid` projection · `conclusion` TEXT.
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
- **Publish:** `lab.request.created` `{labId, patientId, recordId, departmentId, labType, requestedDate}`; `lab.result.created` `{labId, patientId, recordId, departmentId, labType, performedDate, results, conclusion}`.
- **Subscribe:** `financial.clearance.granted` with `purpose=LAB_TEST` and explicit `labTestIds`
  *(target)*; `payment.completed.labTestIds` remains the compatibility path during migration.

`medicalrecord.created` is not a lab order because it does not carry ordered tests. Lab must not
auto-create a test from diagnosis text. A future Clinical → Lab order requires its own explicit
contract with test codes and producer-generated order ID.

## Business rules
1. Cannot add a result if `status` is `COMPLETED` or `CANCELLED`.
2. Adding a result auto-transitions `status` → `COMPLETED`.
3. `performed_date` ≥ `requested_date`.
4. A non-emergency test cannot enter `IN_PROGRESS`, accept results, or become `COMPLETED` without a
   matching LAB_TEST clearance for its exact `testId`.
5. An emergency override records approver, role, reason, time and episode; it does not mark the test
   paid. Billing records the receivable separately.
6. `lab.request.created` is the Billing charge trigger. `lab.result.created` must not create the
   first charge in the target flow.
7. Repeated clearance/payment events are idempotent by `eventId`; target IDs are never inferred from
   invoice, record or patient.

## Care-finance integration gate

- Required: [`CONTRACT-CARE-BILLING-01`](../../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md),
  [`CONTRACT-IDENTITY-LOOKUP-01`](../../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md) and
  [`CONTRACT-CARE-PROJECTIONS-01`](../../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md).
- Existing explicit `labTestIds` behavior remains defined by
  [`HANDOFF-LAB-PAYMENT-COMPLETED`](../../../backend/billing-service/HANDOFF-LAB-PAYMENT-COMPLETED.md)
  until clearance v1 is implemented on both sides.
- Any change to `lab.request.created`, `lab.result.created`, or clearance fixtures must update Billing,
  Clinical, Notification and Report consumer fixtures in the same PR or mark those consumers blocked.
