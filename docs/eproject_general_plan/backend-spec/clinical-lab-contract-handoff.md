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

Organization now implements `GET /api/v1/org/staff/{id}/exists` for `SYSTEM`, with
`{exists, eligibleDoctor, departmentId}` inside `ApiResponse.data`. Clinical projects those three
states, sends a short-lived `SYSTEM` JWT signed with the shared `MEDIFLOW_JWT_SECRET`, and preserves
transport/contract failures as `UpstreamUnavailableException`. Patient lookup remains blocked until
Patient provides an authoritative read/exists contract and service authentication.

## Cross-service contract status

| Owner | Gap | Required follow-up before enabling the consumer |
|---|---|---|
| Billing → Lab | **Resolved (2026-09-18).** Billing publishes deduplicated `labTestIds`; Lab consumes the explicit list and marks each aggregate paid in the same transaction as its `eventId` claim. | Keep `labTestIds` additive and explicit; never substitute invoice/record/prescription ID for a test ID. |
| Pharmacy → Clinical | **Resolved (2026-09-18).** Pharmacy publishes the persisted prescription's `recordId`; Clinical consumes it and attaches the prescription idempotently. | Preserve `recordId` in publisher/outbox fixtures and never infer it from patient or department. |
| Patient → Clinical | Patient still has no Java read/existence endpoint. | Implement the contract in `backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md`; Clinical must keep treating transport failure as 503, not absence. |
| Billing arrival consumer | `recordId` is absent for standalone arrival. | Defer record-based fee creation and enforce source-reference uniqueness across arrival/record events. |
| Billing → Report | Current `PaymentFailedEvent` lacks `departmentId`/`totalAmount` required for reversal in report spec. | Agree a reversal payload with billing/report owners. Outside the clinical/lab implementation scope. |

The owner-facing handoffs are now colocated with the producer modules and made mandatory from each
service's `AGENTS.md`:

- `backend/organization-service/HANDOFF-CLINICAL-STAFF-LOOKUP.md`
- `backend/patient-service/HANDOFF-CLINICAL-PATIENT-LOOKUP.md`
- `backend/pharmacy-service/HANDOFF-CLINICAL-PRESCRIPTION-FILLED.md`
- `backend/billing-service/HANDOFF-LAB-PAYMENT-COMPLETED.md`

## Remaining service work

- Clinical: wait for Patient's authoritative lookup endpoint before claiming live appointment/record E2E. The Organization, Lab and Pharmacy integrations are implemented.
- Lab: payment and medical-record consumers, application orchestration, persistence, endpoints and publisher paths are implemented. A clinical lab order must still be explicit; `medicalrecord.created` alone remains a deliberate no-op.
- Both: run broker-level E2E with all producer services when Patient is implemented and the full compose stack is available. Module tests already exercise PostgreSQL migrations/persistence through Testcontainers.

## Validation

Clinical tests cover application/domain rules, persistence constraints, resilient remote lookups,
publishing, queue routing and idempotent Lab/Pharmacy consumers. Lab tests cover application/domain
rules, persistence, publishing, queue routing and idempotent Billing/Clinical consumers. On
2026-09-18 the combined Clinical/Lab suite passed 278 tests, including PostgreSQL Testcontainers.

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

### Docker verification update (2026-09-17)

Docker Engine 29 compatibility is pinned for Clinical and Lab tests through
`src/test/resources/docker-java.properties`. The combined suite completed **262 tests with zero
failures, errors, or skips**, including PostgreSQL Testcontainers. A Compose runtime smoke test also
confirmed Clinical and Lab health, Eureka registration, Flyway versions Clinical v3 / Lab v1,
RabbitMQ consumers, and the two active bindings `lab.result.created -> clinical.q` and
`medicalrecord.created -> lab.q`.
