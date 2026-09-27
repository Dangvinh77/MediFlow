package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.infrastructure.persistence.adapter.LabTestPersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.LabClearancePersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.LabEmergencyOverridePersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.LabOutboxPersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.ProcessedEventPersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabFinancialClearanceJpaEntity;
import com.mediflow.lab.infrastructure.persistence.jpaEntity.LabOutboxEventJpaEntity;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.event.DomainEventEnvelope;
import com.mediflow.lab.application.event.LabRequestV2Payload;
import com.mediflow.lab.domain.model.CareEpisodeType;
import com.mediflow.lab.domain.model.ClearancePurpose;
import com.mediflow.lab.domain.model.LabFinancialClearance;
import com.mediflow.lab.domain.model.LabEmergencyOverride;
import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.infrastructure.persistence.repository.LabFinancialClearanceJpaRepository;
import com.mediflow.lab.infrastructure.persistence.repository.LabOutboxEventJpaRepository;
import com.mediflow.lab.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@DataJpaTest
@Import({LabTestPersistenceAdapter.class, ProcessedEventPersistenceAdapter.class,
        LabClearancePersistenceAdapter.class, LabEmergencyOverridePersistenceAdapter.class,
        LabOutboxPersistenceAdapter.class, LabPersistenceAdapterTest.JsonConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LabPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LabTestPersistenceAdapter tests;
    @Autowired ProcessedEventPersistenceAdapter processedEvents;
    @Autowired LabClearancePersistenceAdapter clearances;
    @Autowired LabEmergencyOverridePersistenceAdapter overrides;
    @Autowired LabOutboxPersistenceAdapter outbox;
    @Autowired LabFinancialClearanceJpaRepository clearanceRows;
    @Autowired LabOutboxEventJpaRepository outboxRows;
    @Autowired ProcessedEventJpaRepository processedEventRows;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;

    @Test
    void completedTestRoundTrip_preservesResultsAndPaidFlag() {
        LabTest test = LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());
        test.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());
        test.markPaid();

        LabTest found = tests.findById(tests.save(test).getTestId()).orElseThrow();

        assertThat(found.isPaid()).isTrue();
        assertThat(found.getResults()).singleElement()
                .satisfies(result -> assertThat(result.getValue()).isEqualTo("7.5"));
        assertThat(tests.findByIdForUpdate(found.getTestId())).isPresent();
    }

    @Test
    void processedEvent_firstClaim_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isTrue();
    }

    @Test
    void processedEvent_duplicateClaim_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isTrue();

        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isFalse();
    }

    @Test
    void queriesFilterByPatientRecordDepartmentAndStatus() {
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        LabTest matching = tests.save(LabTest.create(record, patient, department, "CBC", LocalDate.now()));
        tests.save(LabTest.create(UUID.randomUUID(), patient, UUID.randomUUID(), "Glucose", LocalDate.now()));

        assertThat(tests.findByPatient(patient)).hasSize(2);
        assertThat(tests.findByRecord(record)).extracting(LabTest::getTestId)
                .containsExactly(matching.getTestId());
        assertThat(tests.search(department, LabTestStatus.PENDING, new PageQuery(0, 10)).content())
                .extracting(LabTest::getTestId).containsExactly(matching.getTestId());
        assertThat(tests.search(department, null, new PageQuery(0, 10)).totalElements()).isEqualTo(1);
        assertThat(tests.search(null, LabTestStatus.PENDING, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
        assertThat(tests.search(null, null, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
    }

    @Test
    void v2Test_roundTripPreservesEpisodeClearanceAndResultVersion() {
        LabTest test = LabTest.createV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CareEpisodeType.ADMISSION, UUID.randomUUID(), "CBC", "LAB-CBC", LocalDate.now());
        test.grantClearance(clearanceFor(test, UUID.randomUUID(), UUID.randomUUID()));

        LabTest found = tests.findById(tests.save(test).getTestId()).orElseThrow();

        assertThat(found.getCareContractVersion()).isEqualTo(1);
        assertThat(found.getStatus()).isEqualTo(LabTestStatus.READY);
        assertThat(found.getCareEpisodeType()).isEqualTo(CareEpisodeType.ADMISSION);
        assertThat(found.getCareEpisodeId()).isEqualTo(test.getCareEpisodeId());
        assertThat(found.getSourceOrderId()).isEqualTo(test.getSourceOrderId());
        assertThat(found.getPriceCode()).isEqualTo("LAB-CBC");
        assertThat(found.getClearanceId()).isEqualTo(test.getClearanceId());
    }

    @Test
    void sourceOrder_duplicateIdIsRejectedByUniqueIndex() {
        UUID sourceOrderId = UUID.randomUUID();
        tests.save(v2Test(sourceOrderId));

        assertThatThrownBy(() -> tests.save(v2Test(sourceOrderId)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(DuplicateResourceException.class::cast)
                .extracting(DuplicateResourceException::getCode)
                .isEqualTo("LAB_DUPLICATE_SOURCE_ORDER");
    }

    @Test
    void v1Constraints_rejectMissingEpisodeAndUnclearedExecution() {
        LabTest test = tests.save(v2Test(UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE lab_test SET care_episode_id = NULL WHERE test_id = ?", test.getTestId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE lab_test SET status = 'IN_PROGRESS' WHERE test_id = ?", test.getTestId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearance_duplicateEventClaimsOneProjectionPerExplicitTarget() {
        UUID patientId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        LabTest first = tests.save(v2Test(UUID.randomUUID(), patientId, episodeId));
        LabTest second = tests.save(v2Test(UUID.randomUUID(), patientId, episodeId));
        UUID eventId = UUID.randomUUID();
        UUID clearanceId = UUID.randomUUID();
        List<LabFinancialClearance> targets = List.of(
                clearanceFor(first, eventId, clearanceId), clearanceFor(second, eventId, clearanceId));

        assertThat(clearances.claimAndSave(eventId, targets)).isTrue();
        assertThat(clearances.claimAndSave(eventId, targets)).isFalse();
        assertThat(clearanceRows.count()).isEqualTo(2);
        assertThat(processedEvents.tryClaim(eventId, "financial.clearance.granted")).isFalse();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void clearanceFailedTarget_rollsBackInboxClaimAndAllProjectionRows() {
        LabTest test = tests.save(v2Test(UUID.randomUUID()));
        UUID eventId = UUID.randomUUID();
        UUID clearanceId = UUID.randomUUID();
        LabFinancialClearance valid = clearanceFor(test, eventId, clearanceId);
        LabFinancialClearance invalid = LabFinancialClearance.grant(UUID.randomUUID(), clearanceId, eventId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", null, false, Instant.now());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        assertThatThrownBy(() -> transaction.execute(status -> {
            clearances.claimAndSave(eventId, List.of(valid, invalid));
            return null;
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(processedEventRows.findById(eventId)).isEmpty();
        assertThat(clearanceRows.findAll()).isEmpty();
    }

    @Test
    void outbox_appendStoresVersionedJsonEnvelopeForRelay() throws Exception {
        UUID testId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DomainEventEnvelope<LabRequestV2Payload> event = new DomainEventEnvelope<>(eventId,
                "lab.request.created", 1, Instant.parse("2026-09-20T10:00:00Z"), "corr-lab-01",
                "lab-service", new LabRequestV2Payload(testId, patientId, recordId, departmentId,
                CareEpisodeType.OUTPATIENT_VISIT, episodeId, UUID.randomUUID(), "LAB_TEST", testId,
                "LAB-CBC", "CBC", Instant.parse("2026-09-20T10:00:00Z"), null));

        outbox.append(event);

        LabOutboxEventJpaEntity row = outboxRows.findById(eventId).orElseThrow();
        JsonNode json = row.getPayload();
        assertThat(row.getEventType()).isEqualTo("lab.request.created");
        assertThat(row.getAggregateId()).isEqualTo(testId);
        assertThat(json.get("version").asInt()).isEqualTo(1);
        assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.path("payload").path("sourceId").asText()).isEqualTo(testId.toString());
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void addResults_concurrentSingleCompletionCreatesOneResultVersion() throws Exception {
        LabTest test = v2Test(UUID.randomUUID());
        LabEmergencyOverride override = LabEmergencyOverride.approve(UUID.randomUUID(), test.getTestId(),
                test.getPatientId(), test.getCareEpisodeType(), test.getCareEpisodeId(), UUID.randomUUID(),
                "DOCTOR", "Urgent diagnostic care", Instant.now());
        test.start(null, override, Instant.now());
        tests.save(test);
        overrides.save(override);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = workers.submit(() -> completeOnce(test.getTestId(), ready, start));
            Future<Boolean> second = workers.submit(() -> completeOnce(test.getTestId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            workers.shutdownNow();
        }

        LabTest completed = tests.findById(test.getTestId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(completed.getResultVersion()).isEqualTo(1);
        assertThat(completed.getResults()).hasSize(1);
    }

    private boolean completeOnce(UUID testId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent result test did not start in time");
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.execute(status -> {
                LabTest locked = tests.findByIdForUpdate(testId).orElseThrow();
                locked.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0")),
                        "Normal", LocalDate.now());
                tests.save(locked);
                return null;
            });
            return true;
        } catch (com.mediflow.lab.domain.exception.LabResultFinalizedException finalized) {
            return false;
        }
    }

    private static LabTest v2Test(UUID sourceOrderId) {
        return v2Test(sourceOrderId, UUID.randomUUID(), UUID.randomUUID());
    }

    private static LabTest v2Test(UUID sourceOrderId, UUID patientId, UUID episodeId) {
        return LabTest.createV2(UUID.randomUUID(), patientId, UUID.randomUUID(), sourceOrderId,
                CareEpisodeType.OUTPATIENT_VISIT, episodeId, "CBC", "LAB-CBC", LocalDate.now());
    }

    private static LabFinancialClearance clearanceFor(LabTest test, UUID eventId, UUID clearanceId) {
        return LabFinancialClearance.grant(UUID.randomUUID(), clearanceId, eventId,
                UUID.randomUUID(), UUID.randomUUID(), test.getTestId(), test.getPatientId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), ClearancePurpose.LAB_TEST,
                new BigDecimal("250000.00"), "VND", null, false, Instant.now());
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean ObjectMapper objectMapper() {
            return JsonMapper.builder().addModule(new JavaTimeModule()).build();
        }
    }
}
