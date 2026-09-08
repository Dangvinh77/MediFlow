# Clinical / Lab foundation — contract handoff (2026-09-08)

Scope approved by Vinh: domain models, exceptions, use-case/repository/lookup ports, validated DTOs, MapStruct and published event records. This is the domain + application-contract milestone, comparable to billing/notification's first two parts. It is not complete HTTP, database or RabbitMQ integration.

## Sources checked

- Service HTML: [clinical](../clinical-service.html), [lab](../lab-service.html), organization, patient, pharmacy, billing, notification and report in the same folder. These govern business meaning.
- Implementation specs: [shared](00-overview.md), [organization](01-organization.md), [patient](02-patient.md), [clinical](03-clinical.md), [lab](04-lab.md), [pharmacy](05-pharmacy.md), [billing](06-billing.md), [notification](07-notification.md), [report](08-report.md).
- Current billing `application/event` and pharmacy `application/event` records, notification's trigger contract, and organization demo HTTP collection.

Per-service HTML and current event records use English fields. Generic Vietnamese naming examples in older `docs/ai`/shared specs, and the organization `maKhoa` example, do not describe these English wire contracts.

## Contracts provided by this slice

Every published event carries `UUID eventId`, `Instant occurredAt`, `String correlationId`. Correlation ID may be absent; event ID is distinct from every business identifier. Publisher adapters must deliver after commit and consumers must deduplicate by event ID. The records alone do not implement either guarantee.

| Producer / event | Contract and consumer requirement |
|---|---|
| Clinical / `appointment.created` | `appointmentId`, `patientId`, `doctorId`, `departmentId`, `appointmentDate`, `appointmentTime`. Time serializes as `HH:mm` for notification. |
| Clinical / `appointment.status.changed` | Canonical fields plus nullable `recordId`, matching billing's existing receive record. When record creation marks arrival, use the new record ID. Standalone arrival has no record ID: billing must defer the EXAM fee until `medicalrecord.created`, not substitute appointment ID. Deduplicate the fee by the same record source reference even if both events arrive. |
| Clinical / `medicalrecord.created` | Canonical fields including `doctorId`, `departmentId`, `examinationDate` and textual `diagnosis` (diagnosis names joined with `; `). Billing consumes its subset. Report maps `examinationDate` to its local report date. No lab orders are present: lab must not auto-create tests from a diagnosis string. |
| Clinical / `diagnosis.added` | `recordId`, `diagnosisCode` (from domain `icdCode`, nullable), `diagnosisName`. |
| Lab / `lab.request.created` | Canonical `labId` maps from API/domain `testId`; `departmentId` maps from `requestingDepartmentId`; includes patient, record, type and requested date. |
| Lab / `lab.result.created` | Canonical fields plus `labType` for billing's price lookup/notification template and `performedDate` for report. Keep result values as strings, including values such as `<0.01` or `negative`. |

These additive fields must be tolerated by consumer JSON readers. Tests use Boot-compatible unknown-field handling and local consumer projections so clinical/lab never depend on another service's Java classes. They test JSON shape/mapping, not a running broker or a future consumer implementation.

Event records live in `application/event`, matching current billing/pharmacy. The older blueprint location `infrastructure/messaging/payload` must not cause application publisher ports to import infrastructure. Future inbound wire records may live under `messaging/consumer/payload` and map into application commands.

## REST lookup boundary

`PatientLookupPort.exists(patientId)` returns false only for confirmed absence. `StaffLookupPort.departmentOf(staffId)` returns an empty result only for confirmed missing/ineligible staff. Transport timeout, circuit-open, 5xx and invalid envelopes throw `UpstreamUnavailableException` (`UPSTREAM_UNAVAILABLE`), which the future web handler must map to HTTP 503, not 422/404.

Organization documents `GET /api/v1/org/staff/{id}/exists` for `SYSTEM`, with `{exists, departmentId}` inside `ApiResponse.data`; its controller is not implemented yet and the demo's `maKhoa` is stale. Patient documents `GET /api/v1/patients/{id}/exists` inside the same envelope. Before wiring either adapter, validate the live provider contract and service authentication. Existence plus department does not itself prove doctor eligibility: BR-A4 requires the organization provider/adapter to supply or verify that fact. No Feign adapter or network call is added here.

## Producer prerequisites still unresolved

| Owner | Gap | Required follow-up before enabling the consumer |
|---|---|---|
| Billing → Lab | Current `PaymentCompletedEvent` has `prescriptionId` but no lab IDs. | Producer must identify all lab tests covered by the invoice, e.g. an agreed `labTestIds` list. Never pass invoice ID, record ID or prescription ID as test ID. |
| Pharmacy → Clinical | Current `PrescriptionFilledEvent` has no `recordId`, although its prescription owns that ID. | Add producer-sourced record correlation and corresponding consumer contract tests before enabling attachment. Do not infer from patient/department. |
| Billing arrival consumer | `recordId` is absent for standalone arrival. | Defer record-based fee creation and enforce source-reference uniqueness across arrival/record events. |
| Billing → Report | Current `PaymentFailedEvent` lacks `departmentId`/`totalAmount` required for reversal in report spec. | Agree a reversal payload with billing/report owners. Outside the clinical/lab implementation scope. |

## Remaining service work

- Clinical: application transactions, patient/doctor checks, pending-per-day uniqueness (including updates/concurrency), one record per appointment, database/Flyway, endpoint roles and 503 mapping. `findByAppointmentId` and `existsPendingSameDayExcludingId` ports prepare these checks; the ports do not enforce them.
- Clinical attachments: separate storage (`ATTACHED_RESULT`), never append external results into symptoms. Event deduplication and attachment must share a transaction and survive concurrent redelivery.
- Lab: application orchestration, adapters/migrations/endpoints, after-commit publishing and transactional consumer deduplication. Completing through `recordResults` owns result/date validation; status updates cannot bypass those invariants.
- Both: real provider/consumer JSON tests, broker/database integration tests, authentication and resilience tests after adapters exist. Foundation test success is not proof that distributed communication is already operational.

## Validation

Clinical tests cover BR-A1/A3/A5 and BR-R1, diagnosis validation, immutable collections, DTO constraints, mapper identity and JSON envelopes/correlation. Lab tests cover BR-L1/L2/L3/L5/L7, textual values and DTO/event mapping. Orchestration, concurrency, publishing and consumer rules are deferred with their corresponding implementations.

Run from repository root:

```sh
mvn -pl backend/clinical-service,backend/lab-service -am verify
```

The repeatable [wire check](../../../scripts/contract-checks/check-clinical-lab.ps1) also serializes
clinical arrival, clinical record and lab result events into the **actual compiled billing receive
records**, without adding cross-service production dependencies:

```powershell
./scripts/contract-checks/check-clinical-lab.ps1
```

Broader verification passed for billing, notification, pharmacy, organization and report; seven
pharmacy container tests were skipped because Docker was unavailable. Including patient in
`verify` fails at Boot repackage: the unchanged baseline patient module has no main class.
