package com.mediflow.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.dto.command.ExamChargeCommand;
import com.mediflow.billing.application.dto.command.LabTestChargeCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.OutpatientMvpMode;
import com.mediflow.billing.application.port.out.OutpatientPublicationCohortPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.Charge;
import com.mediflow.billing.infrastructure.config.OutpatientPublicationProperties;
import com.mediflow.billing.infrastructure.messaging.LedgerEventOutboxAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.ExamChargeRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.LabTestChargeRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.OutpatientPublicationCohortAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * L2 enrollment + eligible-publish behavior against real adapters and a real PostgreSQL snapshot
 * (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §4). Issuance goes through the real
 * {@link ExamChargeRepositoryAdapter}/{@link LabTestChargeRepositoryAdapter} so enrollment is
 * exercised exactly where production wires it (inside the issuer's own save transaction).
 */
@Testcontainers
class OutpatientPublicationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DataSource source;
    private static JdbcTemplate jdbc;
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeAll static void migrate() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @BeforeEach void setup() {
        // Private ephemeral Testcontainers DB only, never the development database.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
    }

    private TransactionTemplate tx() {
        var template = new TransactionTemplate(new DataSourceTransactionManager(source));
        template.setTimeout(5);
        return template;
    }

    private static OutpatientPublicationProperties properties(boolean enabled, List<String> purposes) {
        return new OutpatientPublicationProperties(enabled, purposes, enabled ? "ACCEPT-REF-1" : null);
    }

    private UUID issueExam(OutpatientPublicationCohortPort cohort, UUID appointmentId) {
        var adapter = new ExamChargeRepositoryAdapter(jdbc, cohort);
        var command = new ExamChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "corr",
                appointmentId, UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointmentId, "EXAM", Instant.now());
        return tx().execute(status -> {
            UUID account = adapter.openAndLockExactAccount(command);
            var charge = Charge.post(account, command.patientId(), command.departmentId(), "EXAM", appointmentId,
                    "EXAM", "exam", BigDecimal.ONE, new BigDecimal("100000.00"), Instant.now());
            return adapter.saveChargeAndRequest(command, account, charge).paymentRequestId();
        });
    }

    private UUID issueLabTest(OutpatientPublicationCohortPort cohort, UUID labId) {
        var adapter = new LabTestChargeRepositoryAdapter(jdbc, cohort);
        UUID episode = UUID.randomUUID();
        var command = new LabTestChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "corr",
                labId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", episode,
                null, "LAB_TEST", "CBC", Instant.now(), null);
        return tx().execute(status -> {
            UUID account = adapter.openAndLockExactAccount(command);
            var charge = Charge.post(account, command.patientId(), command.departmentId(), "LAB_TEST", labId,
                    "LAB_TEST", "lab", BigDecimal.ONE, new BigDecimal("80000.00"), Instant.now());
            return adapter.saveChargeAndRequest(command, account, charge).paymentRequestId();
        });
    }

    private UUID cohortAccountOf(UUID request) {
        var rows = jdbc.queryForList("SELECT account_id FROM billing_outpatient_publication_cohort WHERE payment_request_id=?", UUID.class, request);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Test void disabledGlobalFlag_neverEnrollsEvenWithModeOn() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(false, List.of()));
        UUID request = issueExam(cohort, UUID.randomUUID());
        assertThat(cohortAccountOf(request)).isNull();
    }

    @Test void disabledMode_neverEnrollsEvenWithGlobalFlagOn() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(false), properties(true, List.of("EXAM")));
        UUID request = issueExam(cohort, UUID.randomUUID());
        assertThat(cohortAccountOf(request)).isNull();
    }

    @Test void purposeNotInAllowlist_isNotEnrolled() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("LAB_TEST")));
        UUID request = issueExam(cohort, UUID.randomUUID());
        assertThat(cohortAccountOf(request)).isNull();
    }

    @Test void examPurposeEnabledAndAllowlisted_enrollsWithExactSourceId() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(cohort, appointment);
        var row = jdbc.queryForMap("SELECT account_id,purpose,source_id,acceptance_reference FROM billing_outpatient_publication_cohort WHERE payment_request_id=?", request);
        assertThat(row.get("purpose")).isEqualTo("EXAM");
        assertThat(row.get("source_id")).isEqualTo(appointment);
        assertThat(row.get("acceptance_reference")).isEqualTo("ACCEPT-REF-1");
    }

    @Test void labTestPurposeEnabledAndAllowlisted_enrollsWithSingletonLabId() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("LAB_TEST")));
        UUID labId = UUID.randomUUID();
        UUID request = issueLabTest(cohort, labId);
        var row = jdbc.queryForMap("SELECT purpose,source_id FROM billing_outpatient_publication_cohort WHERE payment_request_id=?", request);
        assertThat(row.get("purpose")).isEqualTo("LAB_TEST");
        assertThat(row.get("source_id")).isEqualTo(labId);
    }

    @Test void duplicateEnrollmentAttemptOnSameRequest_doesNotFailOrCreateSecondRow() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(cohort, appointment);
        assertThat(cohortAccountOf(request)).isNotNull();
        tx().executeWithoutResult(status -> cohort.enrollIfEligible(request));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_outpatient_publication_cohort WHERE payment_request_id=?", Integer.class, request))
                .isOne();
    }

    @Test void enrollmentRollsBackWithTheRestOfItsTransactionOnLaterFailure() {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        var adapter = new ExamChargeRepositoryAdapter(jdbc, cohort);
        var command = new ExamChargeCommand(UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "corr",
                appointment, UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointment, "EXAM", Instant.now());
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            UUID account = adapter.openAndLockExactAccount(command);
            var charge = Charge.post(account, command.patientId(), command.departmentId(), "EXAM", appointment,
                    "EXAM", "exam", BigDecimal.ONE, new BigDecimal("100000.00"), Instant.now());
            adapter.saveChargeAndRequest(command, account, charge);
            throw new IllegalStateException("force rollback after enrollment");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_outpatient_publication_cohort", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_REQUEST", Integer.class)).isZero();
    }

    @Test void eligibleOutboxWrite_publishesOnlyAllowedShapeForEnrolledRequest() throws Exception {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(cohort, appointment);
        UUID account = jdbc.queryForObject("SELECT account_id FROM PAYMENT_REQUEST WHERE payment_request_id=?", UUID.class, request);
        var outbox = new LedgerEventOutboxAdapter(jdbc, MAPPER);
        var event = new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed", 1, Instant.now(), "corr", "billing-service",
                new LedgerIntegrationEvent.PaymentCompletedPayload(UUID.randomUUID(), UUID.randomUUID(), request, account,
                        UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointment, "SERVICE_PAYMENT",
                        new BigDecimal("100000.00"), "VND", "CASH", Instant.now(), null, List.of()));
        tx().executeWithoutResult(status -> outbox.appendForOutpatientRequest(account, request, event));
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, event.eventId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT outpatient_payment_request_id FROM BILLING_EVENT_OUTBOX WHERE event_id=?", UUID.class, event.eventId()))
                .isEqualTo(request);
    }

    @Test void outboxWriteForNonEnrolledRequest_staysHeldNotAnError() throws Exception {
        var neverEnrolled = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(false, List.of()));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(neverEnrolled, appointment);
        UUID account = jdbc.queryForObject("SELECT account_id FROM PAYMENT_REQUEST WHERE payment_request_id=?", UUID.class, request);
        var outbox = new LedgerEventOutboxAdapter(jdbc, MAPPER);
        var event = new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed", 1, Instant.now(), "corr", "billing-service",
                new LedgerIntegrationEvent.PaymentCompletedPayload(UUID.randomUUID(), UUID.randomUUID(), request, account,
                        UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointment, "SERVICE_PAYMENT",
                        new BigDecimal("100000.00"), "VND", "CASH", Instant.now(), null, List.of()));
        tx().executeWithoutResult(status -> outbox.appendForOutpatientRequest(account, request, event));
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, event.eventId())).isFalse();
    }

    @Test void mismatchedAccountOnAnEnrolledRequest_isConflictNotSilentHeld() throws Exception {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(cohort, appointment);
        var outbox = new LedgerEventOutboxAdapter(jdbc, MAPPER);
        UUID wrongAccount = UUID.randomUUID();
        var event = new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed", 1, Instant.now(), "corr", "billing-service",
                new LedgerIntegrationEvent.PaymentCompletedPayload(UUID.randomUUID(), UUID.randomUUID(), request, wrongAccount,
                        UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT_VISIT", appointment, "SERVICE_PAYMENT",
                        new BigDecimal("100000.00"), "VND", "CASH", Instant.now(), null, List.of()));
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> outbox.appendForOutpatientRequest(wrongAccount, request, event)))
                .isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Integer.class, event.eventId())).isZero();
    }

    @Test void disallowedEventTypeOnAnEnrolledRequest_isConflictNotSilentHeld() throws Exception {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(cohort, appointment);
        UUID account = jdbc.queryForObject("SELECT account_id FROM PAYMENT_REQUEST WHERE payment_request_id=?", UUID.class, request);
        var outbox = new LedgerEventOutboxAdapter(jdbc, MAPPER);
        var event = new LedgerIntegrationEvent(UUID.randomUUID(), "payment.refunded", 1, Instant.now(), "corr", "billing-service",
                new LedgerIntegrationEvent.PaymentRefundedPayload(UUID.randomUUID(), UUID.randomUUID(), account, UUID.randomUUID(),
                        UUID.randomUUID(), "OUTPATIENT_VISIT", appointment, new BigDecimal("10"), "VND", "reason", Instant.now()));
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> outbox.appendForOutpatientRequest(account, request, event)))
                .isInstanceOf(BillingRuleException.class);
    }
}
