# Vinh–Lộc Care-Finance Contract Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Commit canonical Clinical EXAM producer fixtures and prove that Inpatient consumes Billing's exact `settlement.completed` producer bytes.

**Architecture:** This is a contract-evidence slice only. Clinical tests serialize the real application envelope and payload records into stable version-1 JSON shapes; Inpatient feeds Billing's unchanged fixture bytes through the real Rabbit consumer and verifies the application command. Production services, bindings, feature flags and foreign-owner modules remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Jackson, JUnit 5, AssertJ, Mockito, Maven.

---

## File map

- Create `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalAppointmentStatusChangedContractFixtureTest.java`: serializer proof for the normal EXAM charge trigger.
- Create `backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json`: canonical check-in event bytes.
- Create `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalMedicalRecordCreatedContractFixtureTest.java`: serializer proof for the post-clearance record-created fact.
- Create `backend/clinical-service/src/test/resources/contracts/medicalrecord.created.v1.json`: canonical record-created event bytes.
- Create `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/messaging/consumer/SettlementCompletedContractFixtureTest.java`: real-consumer mapping proof for every settlement command field.
- Create `backend/inpatient-service/src/test/resources/contracts/settlement.completed.v1.json`: exact copy of Billing's producer fixture.
- Do not modify production Java, service configuration, Rabbit bindings, feature flags, Billing files or Lab files.

### Task 1: Lock the Clinical appointment check-in charge fact

**Files:**
- Create: `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalAppointmentStatusChangedContractFixtureTest.java`
- Create: `backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json`

- [ ] **Step 1: Write the failing producer fixture test**

Create the test class with the real envelope and payload types:

```java
package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.AppointmentStatusChangedV2Payload;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalAppointmentStatusChangedContractFixtureTest {

    private static final String FIXTURE = "/contracts/appointment.status.changed.v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void checkInStatusChangeMatchesCanonicalExamChargeFixture() throws IOException {
        Instant changedAt = Instant.parse("2026-10-10T09:00:00Z");
        var payload = new AppointmentStatusChangedV2Payload(
                id(2), null, id(3), id(4),
                AppointmentStatus.PENDING, AppointmentStatus.AWAITING_PAYMENT,
                CareEpisodeType.OUTPATIENT_VISIT, id(2),
                "EXAM", id(2), "OUTPATIENT_EXAM", changedAt, null);
        var event = new DomainEventEnvelope<>(id(1), "appointment.status.changed", 1,
                changedAt, id(7).toString(), "clinical-service", payload);

        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(actual).isEqualTo(fixture);
        assertThat(fixture.at("/payload/recordId").isNull()).isTrue();
        assertThat(fixture.at("/payload/sourceId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
        assertThat(fixture.at("/payload/careEpisodeId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical appointment.status.changed fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -q -pl backend/clinical-service -Dtest=ClinicalAppointmentStatusChangedContractFixtureTest test
```

Expected: FAIL because `/contracts/appointment.status.changed.v1.json` is absent; the assertion message contains `canonical appointment.status.changed fixture`.

- [ ] **Step 3: Add the minimal canonical fixture**

Create `appointment.status.changed.v1.json` with this exact JSON tree:

```json
{
  "eventId": "00000000-0000-4000-8000-000000000001",
  "eventType": "appointment.status.changed",
  "version": 1,
  "occurredAt": "2026-10-10T09:00:00Z",
  "correlationId": "00000000-0000-4000-8000-000000000007",
  "producer": "clinical-service",
  "payload": {
    "appointmentId": "00000000-0000-4000-8000-000000000002",
    "recordId": null,
    "patientId": "00000000-0000-4000-8000-000000000003",
    "departmentId": "00000000-0000-4000-8000-000000000004",
    "oldStatus": "PENDING",
    "newStatus": "AWAITING_PAYMENT",
    "careEpisodeType": "OUTPATIENT_VISIT",
    "careEpisodeId": "00000000-0000-4000-8000-000000000002",
    "sourceType": "EXAM",
    "sourceId": "00000000-0000-4000-8000-000000000002",
    "priceCode": "OUTPATIENT_EXAM",
    "changedAt": "2026-10-10T09:00:00Z",
    "emergencyOverrideId": null
  }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Maven command. Expected: PASS with zero failures.

- [ ] **Step 5: Commit the first contract fixture**

```powershell
git add -- backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalAppointmentStatusChangedContractFixtureTest.java backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json
git commit -m "test(clinical): lock exam charge trigger fixture"
```

### Task 2: Lock the Clinical medical-record-created wire shape

**Files:**
- Create: `backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalMedicalRecordCreatedContractFixtureTest.java`
- Create: `backend/clinical-service/src/test/resources/contracts/medicalrecord.created.v1.json`

- [ ] **Step 1: Write the failing producer fixture test**

```java
package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.DomainEventEnvelope;
import com.mediflow.clinical.application.event.MedicalRecordCreatedV2Payload;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalMedicalRecordCreatedContractFixtureTest {

    private static final String FIXTURE = "/contracts/medicalrecord.created.v1.json";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void recordCreatedMatchesCanonicalExamSourceFixture() throws IOException {
        Instant occurredAt = Instant.parse("2026-10-10T09:30:00Z");
        var payload = new MedicalRecordCreatedV2Payload(
                id(8), id(2), id(3), id(5), id(4),
                CareEpisodeType.OUTPATIENT_VISIT, id(2),
                "EXAM", id(2), "OUTPATIENT_EXAM", LocalDate.parse("2026-10-10"));
        var event = new DomainEventEnvelope<>(id(9), "medicalrecord.created", 1,
                occurredAt, id(7).toString(), "clinical-service", payload);

        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsBytes(event));
        JsonNode fixture = objectMapper.readTree(readFixture());

        assertThat(actual).isEqualTo(fixture);
        assertThat(fixture.at("/payload/sourceId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
        assertThat(fixture.at("/payload/careEpisodeId").asText())
                .isEqualTo(fixture.at("/payload/appointmentId").asText());
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical medicalrecord.created fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
mvn -q -pl backend/clinical-service -Dtest=ClinicalMedicalRecordCreatedContractFixtureTest test
```

Expected: FAIL because `/contracts/medicalrecord.created.v1.json` is absent; the assertion message contains `canonical medicalrecord.created fixture`.

- [ ] **Step 3: Add the minimal canonical fixture**

```json
{
  "eventId": "00000000-0000-4000-8000-000000000009",
  "eventType": "medicalrecord.created",
  "version": 1,
  "occurredAt": "2026-10-10T09:30:00Z",
  "correlationId": "00000000-0000-4000-8000-000000000007",
  "producer": "clinical-service",
  "payload": {
    "recordId": "00000000-0000-4000-8000-000000000008",
    "appointmentId": "00000000-0000-4000-8000-000000000002",
    "patientId": "00000000-0000-4000-8000-000000000003",
    "doctorId": "00000000-0000-4000-8000-000000000005",
    "departmentId": "00000000-0000-4000-8000-000000000004",
    "careEpisodeType": "OUTPATIENT_VISIT",
    "careEpisodeId": "00000000-0000-4000-8000-000000000002",
    "sourceType": "EXAM",
    "sourceId": "00000000-0000-4000-8000-000000000002",
    "priceCode": "OUTPATIENT_EXAM",
    "examinationDate": "2026-10-10"
  }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Maven command. Expected: PASS with zero failures.

- [ ] **Step 5: Commit the second Clinical fixture**

```powershell
git add -- backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalMedicalRecordCreatedContractFixtureTest.java backend/clinical-service/src/test/resources/contracts/medicalrecord.created.v1.json
git commit -m "test(clinical): lock medical record charge source fixture"
```

### Task 3: Prove Inpatient consumes Billing's settlement fixture

**Files:**
- Create: `backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/messaging/consumer/SettlementCompletedContractFixtureTest.java`
- Create: `backend/inpatient-service/src/test/resources/contracts/settlement.completed.v1.json`
- Read-only source: `backend/billing-service/src/test/resources/contracts/ledger-v1/settlement-completed.json`

- [ ] **Step 1: Write the failing consumer fixture test**

```java
package com.mediflow.inpatient.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SettlementCompletedContractFixtureTest {

    private static final String FIXTURE = "/contracts/settlement.completed.v1.json";

    private final ReactToAdmissionReferralUseCase referrals = mock(ReactToAdmissionReferralUseCase.class);
    private final ReactToFinancialClearanceUseCase clearances = mock(ReactToFinancialClearanceUseCase.class);
    private final ReactToSettlementUseCase settlements = mock(ReactToSettlementUseCase.class);
    private final ReactToDepositTopupUseCase topups = mock(ReactToDepositTopupUseCase.class);
    private final ReactToExternalOrderUseCase externalOrders = mock(ReactToExternalOrderUseCase.class);
    private final InpatientEventConsumer consumer = new InpatientEventConsumer(
            new ObjectMapper(), referrals, clearances, settlements, topups, externalOrders);

    @Test
    void billingFixtureMapsEverySettlementFieldToApplicationCommand() throws IOException {
        consumer.receive(new Message(readFixture(), new MessageProperties()));

        ArgumentCaptor<SettlementCompletedCommand> command =
                ArgumentCaptor.forClass(SettlementCompletedCommand.class);
        verify(settlements).onSettlementCompleted(command.capture());

        SettlementCompletedCommand actual = command.getValue();
        assertThat(actual.maSuKien()).isEqualTo(id(99));
        assertThat(actual.phienBan()).isEqualTo(1);
        assertThat(actual.xayRaLuc()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(actual.maTuongQuan()).isEqualTo(id(9).toString());
        assertThat(actual.maQuyetToan()).isEqualTo(id(98));
        assertThat(actual.maDotNoiTru()).isEqualTo(id(3));
        assertThat(actual.maTaiKhoan()).isEqualTo(id(1));
        assertThat(actual.tongTien()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.baoHiemThanhToan()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.benhNhanPhaiTra()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.daThanhToan()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(actual.daHoanTien()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.soDu()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(actual.ketQua()).isEqualTo(SettlementOutcome.PAID_IN_FULL);
        assertThat(actual.hoanTatLuc()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        verifyNoInteractions(referrals, clearances, topups, externalOrders);
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical Billing settlement.completed fixture").isNotNull();
            return input.readAllBytes();
        }
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
mvn -q -pl backend/inpatient-service -Dtest=SettlementCompletedContractFixtureTest test
```

Expected: FAIL because `/contracts/settlement.completed.v1.json` is absent; the assertion message contains `canonical Billing settlement.completed fixture`.

- [ ] **Step 3: Add Billing's exact producer bytes to Inpatient**

Create `settlement.completed.v1.json` as the exact one-line content below, copied from Billing without changing names, values or numeric scales:

```json
{"eventId":"00000000-0000-0000-0000-000000000099","eventType":"settlement.completed","version":1,"occurredAt":"2026-10-10T08:00:00Z","correlationId":"00000000-0000-0000-0000-000000000009","producer":"billing-service","payload":{"settlementId":"00000000-0000-0000-0000-000000000098","admissionId":"00000000-0000-0000-0000-000000000003","accountId":"00000000-0000-0000-0000-000000000001","patientId":"00000000-0000-0000-0000-000000000002","departmentId":"00000000-0000-0000-0000-000000000008","grossAmount":500000.00,"insuranceAmount":0.00,"patientLiability":500000.00,"completedPayments":500000.00,"completedRefunds":0.00,"balance":0.00,"outcome":"PAID_IN_FULL","completedAt":"2026-10-10T08:00:00Z"}}
```

- [ ] **Step 4: Verify same-byte provenance**

```powershell
$billingHash = (Get-FileHash 'backend/billing-service/src/test/resources/contracts/ledger-v1/settlement-completed.json' -Algorithm SHA256).Hash
$inpatientHash = (Get-FileHash 'backend/inpatient-service/src/test/resources/contracts/settlement.completed.v1.json' -Algorithm SHA256).Hash
if ($billingHash -ne $inpatientHash) { throw 'Settlement fixture bytes differ from Billing producer' }
```

Expected: command exits successfully and both hashes are identical.

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the same Maven command. Expected: PASS with zero failures.

- [ ] **Step 6: Commit the Inpatient consumer proof**

```powershell
git add -- backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/messaging/consumer/SettlementCompletedContractFixtureTest.java backend/inpatient-service/src/test/resources/contracts/settlement.completed.v1.json
git commit -m "test(inpatient): accept billing settlement fixture"
```

### Task 4: Verify the complete contract-evidence slice

**Files:**
- Verify only; no production file changes.

- [ ] **Step 1: Run all focused contract tests together**

```powershell
mvn -q -pl backend/clinical-service -Dtest=ClinicalAppointmentStatusChangedContractFixtureTest,ClinicalMedicalRecordCreatedContractFixtureTest test
mvn -q -pl backend/inpatient-service -Dtest=SettlementCompletedContractFixtureTest test
```

Expected: all three tests pass with zero failures.

- [ ] **Step 2: Run both owned module test suites**

```powershell
mvn -q -pl "backend/clinical-service,backend/inpatient-service" -am test
```

Expected: Maven exits `0`; no test failure or compilation failure.

- [ ] **Step 3: Attempt the existing broker-backed Inpatient acceptance test**

```powershell
mvn -q -pl backend/inpatient-service -Dtest=InpatientRabbitIntegrationTest test
```

Expected when Docker/Testcontainers is reachable: PASS. If Testcontainers cannot obtain Docker `/info`, preserve the complete error as environmental evidence and do not describe this acceptance test as passing.

- [ ] **Step 4: Verify scope and formatting**

```powershell
git diff --check
git status --short
git diff --name-only HEAD~3..HEAD
```

Expected task-file list: only the six Clinical/Inpatient test/fixture files above, plus project-managed `.changelog/entries.jsonl` if the commit hook updated it. Pre-existing untracked `docs/documentation_docx/` and the Word lock file remain untouched.

- [ ] **Step 5: Refresh codebase-memory coverage for the new files**

Invoke the graph tools with these exact arguments:

```json
{"tool":"index_repository","arguments":{"repo_path":"E:/DEV/Coding_Resource/Project/e_PROJECT/Semester4/Source","mode":"moderate","persistence":false}}
```

```json
{"tool":"check_index_coverage","arguments":{"project":"E-DEV-Coding_Resource-Project-e_PROJECT-Semester4-Source","paths":["backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalAppointmentStatusChangedContractFixtureTest.java","backend/clinical-service/src/test/resources/contracts/appointment.status.changed.v1.json","backend/clinical-service/src/test/java/com/mediflow/clinical/infrastructure/messaging/ClinicalMedicalRecordCreatedContractFixtureTest.java","backend/clinical-service/src/test/resources/contracts/medicalrecord.created.v1.json","backend/inpatient-service/src/test/java/com/mediflow/inpatient/infrastructure/messaging/consumer/SettlementCompletedContractFixtureTest.java","backend/inpatient-service/src/test/resources/contracts/settlement.completed.v1.json"],"diagnostics":"full","format":"tree"}}
```

Expected: the Java test classes and JSON resources are visible or explicitly reported as deliberately non-semantic resources; no production dependency boundary changes appear.
