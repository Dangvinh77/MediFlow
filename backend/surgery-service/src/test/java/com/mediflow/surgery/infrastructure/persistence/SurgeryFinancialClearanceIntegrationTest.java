package com.mediflow.surgery.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase.Outcome;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.infrastructure.messaging.SurgeryClearanceDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"mediflow.jwt.secret=surgery-financial-integration-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false", "mediflow.features.surgery.enabled=true"})
@Testcontainers
class SurgeryFinancialClearanceIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryResourceReservationPort resources;
    @Autowired SurgeryFinancialClearanceRepositoryPort clearances;
    @Autowired ReactToSurgeryClearanceUseCase receipts;
    @Autowired SurgeryClearanceDecoder decoder;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager manager;
    @MockBean SurgeryClockPort clock;
    private static final Instant NOW = Instant.parse("2026-10-05T08:01:00Z");

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE surgery_case, surgery_inbox, surgery_inbox_semantic_mutex CASCADE");
        when(clock.now()).thenReturn(NOW);
    }

    @Test void duplicateProducerEventCommitsOneProofWithoutAutomaticReadiness() throws Exception {
        createCase();
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        assertThat(receipts.receive(command)).isEqualTo(Outcome.APPLIED);
        assertThat(receipts.receive(command)).isEqualTo(Outcome.REPLAYED);
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
        assertThat(count("surgery_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_case", String.class)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT revision FROM surgery_case", Long.class)).isZero();
    }

    @Test void eventBeforeCasePersistsPendingAndResumesAfterCaseAppears() throws Exception {
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        assertThat(receipts.receive(command)).isEqualTo(Outcome.DEFERRED);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_inbox", String.class)).isEqualTo("PENDING");
        assertThat(count("surgery_financial_clearance")).isZero();
        createCase();
        assertThat(receipts.receive(command)).isEqualTo(Outcome.APPLIED);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_inbox", String.class)).isEqualTo("APPLIED");
    }

    @Test void wrongPatientQuarantinesBeforeFinancialEffect() throws Exception {
        createCase();
        var root = (ObjectNode) mapper.readTree(fixture());
        ((ObjectNode) root.path("payload")).put("patientId", UUID.randomUUID().toString());
        assertThat(receipts.receive(decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), NOW)))
                .isEqualTo(Outcome.QUARANTINED);
        assertThat(count("surgery_financial_clearance")).isZero();
    }

    @Test void sameGrantNewEventIsIdempotentButChangedImmutableGrantConflicts() throws Exception {
        createCase();
        receipts.receive(decoder.decode("financial.clearance.granted", fixture(), NOW));
        var root = (ObjectNode) mapper.readTree(fixture());
        root.put("eventId", UUID.randomUUID().toString());
        assertThat(receipts.receive(decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), NOW))).isEqualTo(Outcome.APPLIED);
        root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.path("payload")).put("amount", 101);
        assertThat(receipts.receive(decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), NOW))).isEqualTo(Outcome.CONFLICT);
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
    }

    @Test void concurrentSameEventAppliesExactlyOnce() throws Exception {
        createCase();
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(); return receipts.receive(command); });
            var b = workers.submit(() -> { start.await(); return receipts.receive(command); });
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Outcome.APPLIED, Outcome.REPLAYED);
        }
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
    }

    @Test void inboxFinalizeFailureRollsBackProofAndClaimThenRetryRecovers() throws Exception {
        createCase();
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        jdbc.execute("CREATE FUNCTION reject_clearance_finalize() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_clearance_finalize BEFORE UPDATE ON surgery_inbox FOR EACH ROW EXECUTE FUNCTION reject_clearance_finalize()");
        try {
            assertThatThrownBy(() -> receipts.receive(command)).isInstanceOf(RuntimeException.class);
            assertThat(count("surgery_financial_clearance")).isZero();
            assertThat(count("surgery_inbox")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_clearance_finalize ON surgery_inbox");
            jdbc.execute("DROP FUNCTION reject_clearance_finalize()");
        }
        assertThat(receipts.receive(command)).isEqualTo(Outcome.APPLIED);
    }

    @Test void persistencePreservesNanosecondValidityNotPostgresRoundedTimestamp() throws Exception {
        createCase();
        var root = (ObjectNode) mapper.readTree(fixture());
        root.put("occurredAt", "2026-10-05T08:00:00.123456789Z");
        ((ObjectNode) root.path("payload")).put("expiresAt", "2026-10-05T08:10:00.987654321Z");
        var command = decoder.decode("financial.clearance.granted", mapper.writeValueAsBytes(root), NOW);
        receipts.receive(command);
        var stored = new TransactionTemplate(manager).execute(status -> clearances.lockById(command.clearance().clearanceId()).orElseThrow());
        assertThat(stored.grantedAt().getNano()).isEqualTo(123456789);
        assertThat(stored.expiresAt().getNano()).isEqualTo(987654321);
    }

    @Test void newFinancialAuthorityInvalidatesScheduledCaseAndReleasesExactResourcesAtomically() throws Exception {
        SurgerySchedule schedule = createScheduledCase();
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        assertThat(receipts.receive(command)).isEqualTo(Outcome.APPLIED);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_case", String.class)).isEqualTo("PREOP_IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT revision FROM surgery_case", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_schedule WHERE schedule_id = ?", String.class,
                schedule.scheduleId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM surgery_resource_reservation WHERE status = 'RELEASED'", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT change_code FROM surgery_revision_history WHERE revision = 4", String.class))
                .isEqualTo("FINANCIAL_CLEARANCE_CHANGED");
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
        assertThat(count("surgery_outbox")).isZero();
        assertThat(receipts.receive(command)).isEqualTo(Outcome.REPLAYED);
        assertThat(jdbc.queryForObject("SELECT revision FROM surgery_case", Long.class)).isEqualTo(4);
    }

    @Test void finalizeFailureRestoresScheduledStateAndReservationsAlongWithProofAndInbox() throws Exception {
        createScheduledCase();
        var command = decoder.decode("financial.clearance.granted", fixture(), NOW);
        jdbc.execute("CREATE FUNCTION reject_scheduled_clearance() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_scheduled_clearance BEFORE UPDATE ON surgery_inbox FOR EACH ROW EXECUTE FUNCTION reject_scheduled_clearance()");
        try {
            assertThatThrownBy(() -> receipts.receive(command)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM surgery_case", String.class)).isEqualTo("SCHEDULED");
            assertThat(jdbc.queryForObject("SELECT revision FROM surgery_case", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT status FROM surgery_schedule", String.class)).isEqualTo("FINALIZED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM surgery_resource_reservation WHERE status = 'RESERVED'", Long.class))
                    .isEqualTo(2);
            assertThat(count("surgery_financial_clearance")).isZero();
            assertThat(count("surgery_inbox")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_scheduled_clearance ON surgery_inbox");
            jdbc.execute("DROP FUNCTION reject_scheduled_clearance()");
        }
        assertThat(receipts.receive(command)).isEqualTo(Outcome.APPLIED);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_case", String.class)).isEqualTo("PREOP_IN_PROGRESS");
    }

    private SurgerySchedule createScheduledCase() {
        SurgeryCase surgeryCase = createCase();
        var actor = SurgeryAuditActor.human(id(9), id(9));
        var transaction = new TransactionTemplate(manager);
        surgeryCase.beginPreop(actor, "test", NOW.minusSeconds(90));
        transaction.executeWithoutResult(status -> cases.save(surgeryCase, 0));
        var schedule = new SurgerySchedule(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(), 1, UUID.randomUUID(),
                NOW.plusSeconds(3600), NOW.plusSeconds(5400),
                List.of(new SurgeryTeamAssignment(id(9), SurgeryTeamRole.PRIMARY_SURGEON)));
        transaction.executeWithoutResult(status -> schedules.saveDraft(schedule, 0, NOW.minusSeconds(80)));
        var readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                true, true, true, true, true, true, true, NOW.minusSeconds(60),
                java.util.Arrays.stream(SurgeryDependencyType.values()).map(type -> type == SurgeryDependencyType.SCHEDULE
                        ? new SurgeryDependencyRevision(type, schedule.scheduleId(), schedule.revision())
                        : new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(), null);
        surgeryCase.markReady(readiness, actor, "test");
        transaction.executeWithoutResult(status -> cases.save(surgeryCase, 1));
        transaction.executeWithoutResult(status -> resources.reserve(schedule, NOW.minusSeconds(45)));
        surgeryCase.finalizeSchedule(actor, "test", NOW.minusSeconds(30));
        transaction.executeWithoutResult(status -> cases.save(surgeryCase, 2));
        return schedule;
    }

    private SurgeryCase createCase() {
        var surgeryCase = SurgeryCase.create(id(4), id(12), new CareEpisode(CareEpisodeType.ADMISSION, id(3), id(3), null),
                id(2), id(8), id(9), "TEST_PROC", "Test-only indication", SurgeryPriority.ROUTINE,
                NOW.minusSeconds(120), SurgeryAuditActor.human(id(9), id(9)), "test");
        new TransactionTemplate(manager).executeWithoutResult(status -> cases.save(surgeryCase, -1));
        return surgeryCase;
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private static UUID id(int suffix) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix)); }
    private static byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-surgery.json"));
    }
}
