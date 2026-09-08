# Clinical and Lab Application/Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement the Clinical and Lab application services and PostgreSQL persistence milestone without adding HTTP, messaging, security, or Feign adapters.

**Architecture:** Spring application services implement the existing input ports and coordinate immutable domain aggregates through output ports. JPA adapters own all Spring Data details, translate aggregates manually, and enforce concurrent mutation and uniqueness at PostgreSQL boundaries.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, MapStruct, Flyway, PostgreSQL, JUnit 5, Mockito, Testcontainers.

---

### Task 1: Clinical application orchestration

**Files:**
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/AppointmentApplicationService.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/service/MedicalRecordApplicationService.java`
- Modify: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/port/out/AppointmentRepositoryPort.java`
- Modify: `backend/clinical-service/src/main/java/com/mediflow/clinical/application/port/out/MedicalRecordRepositoryPort.java`
- Create: `backend/clinical-service/src/test/java/com/mediflow/clinical/application/service/ClinicalApplicationServiceTest.java`

- [x] **Step 1: Write failing tests**

Cover the required orchestration with Mockito-backed ports:

```java
@Test void createAppointment_secondPendingSameDay_throwsDuplicate() { /* exists=true; assert code */ }
@Test void createAppointment_doctorFromOtherDepartment_throwsBusinessRule() { /* assert DOCTOR_WRONG_DEPARTMENT */ }
@Test void createRecord_withAppointment_marksArrivedAtomicallyAndCorrelatesEvents() { /* verify both saves and event correlation */ }
@Test void createRecord_sameAppointmentTwice_rejectsDuplicateRecord() { /* findByAppointmentId present */ }
@Test void addDiagnosis_persistsAggregateAndPublishesEvent() { /* capture saved record/event */ }
```

- [x] **Step 2: Verify RED**

Run `mvn -pl backend/clinical-service -Dtest=ClinicalApplicationServiceTest test`. Expected: compilation failure because the two application services and mutation-load port methods do not exist.

- [x] **Step 3: Implement the service and mutation ports**

Add `findByIdForUpdate(UUID)` to both repositories. Implement `ManageAppointmentUseCase` and `ManageRecordUseCase` in separate `@Service` classes because their same-signature query methods return different DTO types. Mutation methods are `@Transactional`, queries are `@Transactional(readOnly = true)`. Use one correlation UUID per command and reuse it for the two record-creation events.

```java
private void requirePatient(UUID patientId) {
    if (!patientLookup.exists(patientId)) {
        throw new InvalidClinicalDataException("PATIENT_NOT_FOUND_REMOTE", "Patient does not exist");
    }
}

private void requireDoctorDepartment(UUID doctorId, UUID departmentId) {
    UUID actual = staffLookup.departmentOf(doctorId)
            .orElseThrow(() -> new InvalidClinicalDataException("DOCTOR_NOT_FOUND_REMOTE", "Doctor does not exist"));
    if (!actual.equals(departmentId)) {
        throw new InvalidClinicalDataException("DOCTOR_WRONG_DEPARTMENT", "Doctor does not belong to department");
    }
}
```

- [x] **Step 4: Verify GREEN and commit**

Run `mvn -pl backend/clinical-service -am test`. Expected: all Clinical tests pass. Commit `feat(clinical): implement application orchestration`.

### Task 2: Lab application orchestration

**Files:**
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/application/service/LabApplicationService.java`
- Modify: `backend/lab-service/src/main/java/com/mediflow/lab/application/port/out/LabTestRepositoryPort.java`
- Create: `backend/lab-service/src/test/java/com/mediflow/lab/application/service/LabApplicationServiceTest.java`

- [x] **Step 1: Write failing tests**

```java
@Test void create_valid_persistsAndPublishesRequest() { /* capture saved aggregate and event */ }
@Test void addResults_valid_marksCompletedAndPublishesWireCompleteEvent() { /* assert labType/date/results */ }
@Test void changeStatus_missingTest_throwsNotFound() { /* locked load empty */ }
@Test void autoCreateFromRecord_blankLabType_doesNothing() { /* verifyNoInteractions */ }
@Test void markPaid_explicitTestId_marksAggregatePaid() { /* capture saved aggregate */ }
```

- [x] **Step 2: Verify RED**

Run `mvn -pl backend/lab-service -Dtest=LabApplicationServiceTest test`. Expected: compilation failure because the service and mutation-load method do not exist.

- [x] **Step 3: Implement the service**

Add `findByIdForUpdate(UUID)` to the repository port. Implement `ManageLabTestUseCase` and `ReactToClinicalUseCase`; map result items with `LabResult.create`, use `LocalDate.now()` only for explicit record-driven requests, and never infer a test ID from invoice or record IDs.

```java
if (labType == null || labType.isBlank()) return;
LabTest test = repository.save(LabTest.create(recordId, patientId, departmentId, labType, LocalDate.now()));
publisher.publishRequestCreated(LabRequestCreatedEvent.from(test, UUID.randomUUID().toString()));
```

- [x] **Step 4: Verify GREEN and commit**

Run `mvn -pl backend/lab-service -am test`. Expected: all Lab tests pass. Commit `feat(lab): implement application orchestration`.

### Task 3: Clinical PostgreSQL persistence

**Files:**
- Create: `backend/clinical-service/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/jpaEntity/AppointmentJpaEntity.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/jpaEntity/MedicalRecordJpaEntity.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/jpaEntity/DiagnosisJpaEntity.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/repository/AppointmentJpaRepository.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/repository/MedicalRecordJpaRepository.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/adapter/AppointmentPersistenceAdapter.java`
- Create: `backend/clinical-service/src/main/java/com/mediflow/clinical/infrastructure/persistence/adapter/MedicalRecordPersistenceAdapter.java`
- Create: `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/persistence/ClinicalPersistenceAdapterTest.java`

- [x] **Step 1: Write failing PostgreSQL tests**

Test aggregate round trips, diagnosis cascade, patient/date search, partial uniqueness for pending appointments, partial uniqueness for record appointment IDs, and locked mutation loads. Use `@DataJpaTest`, `@Import` adapters, `@Testcontainers(disabledWithoutDocker = true)`, and PostgreSQL 16.

- [x] **Step 2: Verify RED**

Run `mvn -pl backend/clinical-service -Dtest=ClinicalPersistenceAdapterTest test`. Expected: compilation failure because persistence types do not exist.

- [x] **Step 3: Add the exact schema and adapters**

Use the documented columns plus these concurrency constraints:

```sql
CREATE UNIQUE INDEX uq_appointment_pending_patient_date
    ON appointment(patient_id, appointment_date) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_medical_record_appointment
    ON medical_record(appointment_id) WHERE appointment_id IS NOT NULL;
```

Map domain values manually through `restore(...)`; cascade diagnoses through `MedicalRecordJpaEntity`. Repository mutation queries use `@Lock(PESSIMISTIC_WRITE)`.

- [x] **Step 4: Verify GREEN and commit**

Run `mvn -pl backend/clinical-service -am test`. Expected: all unit and available PostgreSQL tests pass. Commit `feat(clinical): add PostgreSQL persistence`.

### Task 4: Lab PostgreSQL persistence

**Files:**
- Create: `backend/lab-service/src/main/resources/db/migration/V1__init.sql`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/jpaEntity/LabTestJpaEntity.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/jpaEntity/LabResultJpaEntity.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/jpaEntity/ProcessedEventJpaEntity.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/repository/LabTestJpaRepository.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/repository/ProcessedEventJpaRepository.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/adapter/LabTestPersistenceAdapter.java`
- Create: `backend/lab-service/src/main/java/com/mediflow/lab/infrastructure/persistence/adapter/ProcessedEventPersistenceAdapter.java`
- Create: `backend/lab-service/src/test/java/com/mediflow/lab/infrastructure/persistence/LabPersistenceAdapterTest.java`

- [x] **Step 1: Write failing PostgreSQL tests**

Test aggregate/result round trips, patient/record/search queries, locked mutation loads, and duplicate processed-event insertion without overwrite.

- [x] **Step 2: Verify RED**

Run `mvn -pl backend/lab-service -Dtest=LabPersistenceAdapterTest test`. Expected: compilation failure because persistence types do not exist.

- [x] **Step 3: Add schema and adapters**

Use the spec's `lab_test`, `lab_result`, and `processed_event` tables. Cascade results through the aggregate entity and use insert-only SQL for dedupe:

```java
@Modifying
@Query(value = "INSERT INTO processed_event(event_id, routing_key) VALUES (:eventId, :routingKey) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
int insertIfAbsent(UUID eventId, String routingKey);
```

- [x] **Step 4: Verify GREEN and commit**

Run `mvn -pl backend/lab-service -am test`. Expected: all unit and available PostgreSQL tests pass. Commit `feat(lab): add PostgreSQL persistence`.

### Task 5: Documentation and final verification

**Files:**
- Modify: `backend/clinical-service/README.md`
- Modify: `backend/lab-service/README.md`

- [x] **Step 1: Update status accurately**

State that application and persistence are implemented and that HTTP, security, Feign, RabbitMQ, and Clinical external attachments remain Part 5.

- [x] **Step 2: Run full focused verification**

Run `mvn -pl backend/clinical-service,backend/lab-service -am verify`. Expected: reactor success with zero failures; report Docker skips explicitly if Docker is unavailable.

- [x] **Step 3: Check scope and commit**

Run `git diff --check`, `git status --short`, and inspect `git diff --stat master...HEAD`. Commit `docs(clinical-lab): update implementation status`, then push `codex/clinical-lab-app-persistence` and open one focused PR against `master`.

