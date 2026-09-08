# Clinical and Lab Application/Persistence Design

## Goal

Bring `clinical-service` and `lab-service` to the same implementation milestone as Billing and Notification: complete application orchestration plus PostgreSQL persistence, while preserving the event contracts already integrated on `master`.

## Scope

Clinical gains application services for appointment and medical-record commands/queries, resilient lookup decisions through the existing patient/staff ports, and atomic record creation with appointment arrival. Lab gains application services for test creation/query, result recording, status changes, explicit record-driven creation, and payment marking. Both services gain Flyway V1 schemas, JPA entities/repositories, persistence adapters, and focused unit and PostgreSQL integration tests.

HTTP controllers, JWT/security, Feign clients, RabbitMQ configuration/consumers/publishers, and Clinical external-result attachments remain for the final adapter milestone. Event publisher ports are invoked only after successful repository operations; the future messaging adapters remain responsible for dispatching after transaction commit.

## Architecture

Application services implement the existing input ports and coordinate domain models through output ports. They may use Spring `@Service` and `@Transactional`, but do not import JPA, Spring Data, AMQP, HTTP, or concrete infrastructure classes. Persistence adapters translate between immutable domain aggregates and JPA entities.

Clinical stores `Appointment` and `MedicalRecord` aggregates. `Diagnosis` is owned by `MedicalRecord` and persists through cascade/orphan removal. Lab stores `LabTest`; `LabResult` is its cascaded child. Cross-service identifiers remain plain UUID values.

## Clinical behavior

- Appointment creation confirms the patient, confirms the doctor and department, rejects another pending appointment for the patient/day, saves, then publishes `appointment.created`.
- Appointment updates exclude the current appointment from the pending duplicate check; immutable patient/doctor/department references are not revalidated because the request cannot change them.
- Status changes use the domain transition rules and publish `appointment.status.changed` with a nullable `recordId`.
- Record creation validates patient and doctor/department. When `appointmentId` exists, it rejects a record already assigned to that appointment, verifies the appointment belongs to the patient, marks it `ARRIVED`, and saves both changes in one transaction. It publishes `medicalrecord.created` and the correlated appointment-status event.
- Record updates and diagnosis additions operate on the aggregate; diagnosis addition publishes `diagnosis.added`.
- PostgreSQL adds a partial unique index for one `PENDING` appointment per patient/day and a partial unique index for one record per non-null appointment. These constraints close the race left by application prechecks.

## Lab behavior

- Test creation saves `LabTest.create(...)` and publishes `lab.request.created`.
- Adding results maps request items to domain results, calls `recordResults`, saves, and publishes `lab.result.created` with `labId`, `labType`, `performedDate`, department, results, and conclusion.
- Status changes and payment marking use domain methods and persist the aggregate.
- Record-driven creation occurs only when a nonblank explicit `labType` is supplied. The service never creates a default test for every medical record.
- `PROCESSED_EVENT` uses insert-only persistence so duplicate event IDs cannot be overwritten. Consumers and their transaction boundary remain in the adapter milestone.

## Error and concurrency handling

Existing domain exceptions provide 404/409/422 codes. Confirmed missing patient/doctor data maps to the documented remote-validation errors; infrastructure outages continue through `UpstreamUnavailableException`. Mutation loads use pessimistic locking where concurrent updates could otherwise lose status/results/payment changes. Database uniqueness violations are translated to the matching Clinical conflict rather than leaking Spring exceptions.

## Testing

Work follows red-green-refactor. Application tests cover every algorithm branch that is not already a domain invariant. Testcontainers/PostgreSQL tests cover aggregate round trips, child cascade, searches, uniqueness constraints, mutation locks, and insert-only processed-event behavior. The final check runs both service test suites together and rejects skipped Docker tests when Docker is available.

## Quota boundary

This change deliberately stops at application and persistence. It avoids duplicative mapper tests, broad framework smoke tests, and adapter code whose producer contract is still incomplete. The result is a reviewable Part 3/4 change that leaves Part 5 for a separate PR.
