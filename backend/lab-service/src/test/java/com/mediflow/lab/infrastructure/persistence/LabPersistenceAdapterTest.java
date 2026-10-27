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
@Import({LabTestPersistenceAdapter.class,
        LabClearancePersistenceAdapter.class, LabEmergencyOverridePersistenceAdapter.class,
        LabOutboxPersistenceAdapter.class, LabPersistenceAdapterTest.JsonConfiguration.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LabPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LabTestPersistenceAdapter tests;
    @Autowired LabClearancePersistenceAdapter clearances;
    @Autowired LabEmergencyOverridePersistenceAdapter overrides;
    @Autowired LabOutboxPersistenceAdapter outbox;
    @Autowired LabFinancialClearanceJpaRepository clearanceRows;
    @Autowired LabOutboxEventJpaRepository outboxRows;
    @Autowired ProcessedEventJpaRepository processedEventRows;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper objectMapper;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void lifecycleLookupUsesExactSourceAndReflectsPersistedCancellation() {
        UUID source = UUID.randomUUID();
        LabTest test = v2Test(source);
        tests.save(test);
        entityManager.flush();
        entityManager.clear();
        var lifecycle = new com.mediflow.lab.application.service.LabOrderLifecycleService(tests,
                java.time.Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), java.time.ZoneOffset.UTC));
        var pending = lifecycle.lookup(source);
        assertThat(pending.exists()).isTrue();
        assertThat(pending.sourceOrderId()).isEqualTo(source);
        assertThat(pending.testId()).isEqualTo(test.getTestId());
        assertThat(pending.recordId()).isEqualTo(test.getRecordId());
        assertThat(pending.patientId()).isEqualTo(test.getPatientId());
        assertThat(pending.departmentId()).isEqualTo(test.getRequestingDepartmentId());
        assertThat(pending.careEpisodeId()).isEqualTo(test.getCareEpisodeId());
        assertThat(pending.status()).isEqualTo("AWAITING_PAYMENT");
        test.cancel("Patient declined", UUID.randomUUID(), Instant.parse("2026-10-26T00:01:00Z"));
        tests.save(test);
        entityManager.flush();
        entityManager.clear();
        assertThat(lifecycle.lookup(source).status()).isEqualTo("CANCELLED");
        assertThat(lifecycle.lookup(UUID.randomUUID()).exists()).isFalse();
    }

    @Test
    void cancellationRoundTripRetainsAuditAndCancellationOutbox() {
        LabTest test = v2Test(UUID.randomUUID());
        UUID actor = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-21T10:00:00Z");
        test.cancel("Patient declined", actor, at);
        tests.save(test);
        var payload = new com.mediflow.lab.application.event.LabRequestCancelledPayload(
                test.getTestId(), test.getPatientId(), test.getRecordId(), test.getRequestingDepartmentId(),
                test.getCareEpisodeType(), test.getCareEpisodeId(), test.getSourceOrderId(),
                "LAB_TEST", test.getTestId(), test.getPriceCode(), test.getLabType(), actor,
                "Patient declined", at);
        UUID eventId = UUID.randomUUID();
        outbox.append(new DomainEventEnvelope<>(eventId, "lab.request.cancelled", 1, at,
                "cancel-audit", "lab-service", payload));

        LabTest restored = tests.findById(test.getTestId()).orElseThrow();
        assertThat(restored.getCancelledBy()).isEqualTo(actor);
        assertThat(restored.getCancellationReason()).isEqualTo("Patient declined");
        assertThat(restored.getCancelledAt()).isEqualTo(at);
        assertThat(outboxRows.findById(eventId)).get().satisfies(row -> {
            assertThat(row.getAggregateId()).isEqualTo(test.getTestId());
            assertThat(row.getPayload().path("payload").path("cancelledBy").asText())
                    .isEqualTo(actor.toString());
        });
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void cancellationAndOutboxRollBackTogetherOnFailure() {
        LabTest saved = tests.save(v2Test(UUID.randomUUID()));
        UUID eventId = UUID.randomUUID();
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
                LabTest test = tests.findByIdForUpdate(saved.getTestId()).orElseThrow();
                Instant at = Instant.parse("2026-10-21T10:00:00Z");
                UUID actor = UUID.randomUUID();
                test.cancel("Patient declined", actor, at);
                tests.save(test);
                var payload = new com.mediflow.lab.application.event.LabRequestCancelledPayload(
                        test.getTestId(), test.getPatientId(), test.getRecordId(), test.getRequestingDepartmentId(),
                        test.getCareEpisodeType(), test.getCareEpisodeId(), test.getSourceOrderId(),
                        "LAB_TEST", test.getTestId(), test.getPriceCode(), test.getLabType(), actor,
                        "Patient declined", at);
                outbox.append(new DomainEventEnvelope<>(eventId, "lab.request.cancelled", 1, at,
                        "cancel-rollback", "lab-service", payload));
                throw new IllegalStateException("Simulated failure after outbox append");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(tests.findById(saved.getTestId()).orElseThrow().getStatus()).isEqualTo(saved.getStatus());
            assertThat(tests.findById(saved.getTestId()).orElseThrow().getCancelledBy()).isNull();
            assertThat(outboxRows.findById(eventId)).isEmpty();
        } finally {
            jdbcTemplate.update("DELETE FROM lab_test WHERE test_id = ?", saved.getTestId());
        }
    }

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
    void portalPagesOnlyCompletedTestsOwnedByPatient() {
        UUID patient = UUID.randomUUID();
        LabTest first = LabTest.create(UUID.randomUUID(), patient, UUID.randomUUID(), "CBC", LocalDate.now().minusDays(1));
        first.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4-10")), "Normal", LocalDate.now().minusDays(1));
        tests.save(first);
        LabTest second = LabTest.create(UUID.randomUUID(), patient, UUID.randomUUID(), "CBC", LocalDate.now());
        second.recordResults(List.of(LabResult.create("WBC", "8", "10^9/L", "4-10")), "Normal", LocalDate.now());
        tests.save(second);
        tests.save(LabTest.create(UUID.randomUUID(), patient, UUID.randomUUID(), "Draft", LocalDate.now()));
        LabTest foreign = LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Private", LocalDate.now());
        foreign.recordResults(List.of(LabResult.create("WBC", "9", null, null)), "Private", LocalDate.now());
        tests.save(foreign);
        entityManager.flush();
        entityManager.clear();
        var page = tests.findCompletedByPatient(patient, PageQuery.of(0, 1));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(LabTest::getTestId).containsExactly(second.getTestId());
        assertThat(page.content().get(0).getResults()).hasSize(1);
        assertThat(tests.findCompletedByPatient(patient, PageQuery.of(1, 1)).content())
                .extracting(LabTest::getTestId).containsExactly(first.getTestId());
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
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void v1Constraints_rejectMissingEpisodeAndUnclearedExecution() {
        LabTest test = tests.save(v2Test(UUID.randomUUID()));

        try {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE lab_test SET care_episode_id = NULL WHERE test_id = ?", test.getTestId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE lab_test SET status = 'IN_PROGRESS' WHERE test_id = ?", test.getTestId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM lab_test WHERE test_id = ?", test.getTestId());
        }
    }

    @Test
    void clearanceReloadRetainsOriginalNanosecondTimesFromItsInboxEvidence() throws Exception {
        LabTest test = tests.save(v2Test(UUID.randomUUID()));
        Instant granted = Instant.parse("2026-10-24T16:23:49.467347200Z");
        Instant expiry = granted.plusSeconds(3600);
        LabFinancialClearance g = clearanceFor(test, UUID.randomUUID(), UUID.randomUUID());
        LabFinancialClearance source = LabFinancialClearance.grant(g.clearanceTargetId(), g.clearanceId(),
                g.eventId(), g.invoiceId(), g.accountId(), g.testId(), g.patientId(), g.careEpisodeType(),
                g.careEpisodeId(), g.purpose(), g.amount(), g.currency(), expiry, false, granted);
        byte[] raw = objectMapper.writeValueAsBytes(java.util.Map.of("eventId", g.eventId(),
                "eventType", "financial.clearance.granted", "occurredAt", granted.toString(),
                "payload", java.util.Map.of("clearanceId", g.clearanceId(), "expiresAt", expiry.toString())));
        var claim = new com.mediflow.lab.messaging.consumer.inbox.InboxEvidenceFactory(objectMapper)
                .create("lab-test-clearance", "financial.clearance.granted", g.eventId(),
                        "lab:financial-clearance:v1:" + g.clearanceId(), raw);
        new com.mediflow.lab.infrastructure.persistence.adapter.ConsumerInboxPersistenceAdapter(jdbcTemplate)
                .claim(claim);
        clearances.claimAndSave(g.eventId(), List.of(source));
        // Force the database round trip, including PostgreSQL's microsecond precision loss.
        entityManager.clear();
        jdbcTemplate.execute("SET LOCAL TIME ZONE 'UTC'");
        assertThat(jdbcTemplate.queryForObject("SELECT granted_at FROM lab_financial_clearance WHERE test_id=?",
                java.sql.Timestamp.class, test.getTestId()).toInstant()).isNotEqualTo(granted);
        assertThat(clearances.findLatestByTestId(test.getTestId())).get().satisfies(restored -> {
            assertThat(restored.grantedAt()).isEqualTo(granted);
            assertThat(restored.expiresAt()).isEqualTo(expiry);
        });
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
        assertThat(processedEventRows.insertIfAbsent(eventId, "financial.clearance.granted")).isZero();
        assertThat(clearances.findLatestByTestId(first.getTestId()))
                .get().extracting(LabFinancialClearance::currency).isEqualTo("VND");
        assertThat(clearances.findByTestIdAndClearanceId(first.getTestId(), clearanceId))
                .get().extracting(LabFinancialClearance::testId).isEqualTo(first.getTestId());
        assertThat(clearances.findByTestIdAndClearanceId(first.getTestId(), UUID.randomUUID())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT data_type || ':' || character_maximum_length
                FROM information_schema.columns
                WHERE table_name = 'lab_financial_clearance' AND column_name = 'currency'
                """, String.class)).isEqualTo("character:3");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void clearanceFailedTarget_rollsBackInboxClaimAndAllProjectionRows() {
        LabTest test = tests.save(v2Test(UUID.randomUUID()));
        try {
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
        } finally {
            jdbcTemplate.update("DELETE FROM lab_test WHERE test_id = ?", test.getTestId());
        }
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
        try {
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
        } finally {
            jdbcTemplate.update("DELETE FROM lab_emergency_override WHERE test_id = ?", test.getTestId());
            jdbcTemplate.update("DELETE FROM lab_result WHERE test_id = ?", test.getTestId());
            jdbcTemplate.update("DELETE FROM lab_test WHERE test_id = ?", test.getTestId());
        }
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
                        "Normal", LocalDate.now(), null, Instant.now());
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
