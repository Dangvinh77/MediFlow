package com.mediflow.billing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import com.mediflow.billing.application.dto.command.PrescriptionChargeCommand;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.application.service.PrescriptionChargeService;
import com.mediflow.billing.infrastructure.persistence.adapter.LedgerPaymentRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.PrescriptionChargeRepositoryAdapter;
import com.mediflow.billing.domain.model.PaymentMethod;
import org.mapstruct.factory.Mappers;
import java.time.Clock;
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
        var event = completedPayment(request);
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
    private LedgerIntegrationEvent completedPayment(UUID request) {
        var row = jdbc.queryForMap("SELECT r.*, a.patient_id, a.department_id, a.care_episode_id FROM PAYMENT_REQUEST r JOIN BILLING_ACCOUNT a USING(account_id) WHERE payment_request_id=?", request);
        UUID transaction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,
                    classification,status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,?,'PAYMENT','SERVICE_PAYMENT','COMPLETED',?,'VND','CASH',?,now())
                """, transaction, row.get("account_id"), request, row.get("requested_amount"), UUID.randomUUID().toString());
        return new LedgerIntegrationEvent(UUID.randomUUID(), "payment.completed", 1, Instant.now(), "corr", "billing-service",
                new LedgerIntegrationEvent.PaymentCompletedPayload(transaction, (UUID) row.get("invoice_id"), request,
                    (UUID) row.get("account_id"), (UUID) row.get("patient_id"), (UUID) row.get("department_id"),
                    "OUTPATIENT_VISIT", (UUID) row.get("care_episode_id"), "SERVICE_PAYMENT",
                    (BigDecimal) row.get("requested_amount"), "VND", "CASH", Instant.now(), null, List.of()));
    }

    @ParameterizedTest
    @CsvSource({
        "paymentRequestId,foreign", "invoiceId,foreign", "patientId,foreign", "careEpisodeId,foreign",
        "transactionId,foreign", "classification,ADMISSION_DEPOSIT", "careEpisodeType,ADMISSION",
        "totalAmount,1", "currency,USD", "prescriptionId,foreign"
    })
    void mismatchedPayloadIsRejectedByWriterAndDirectSql(String field, String value) throws Exception {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of("EXAM")));
        UUID request = issueExam(cohort, UUID.randomUUID());
        UUID account = cohortAccountOf(request);
        var original = completedPayment(request);
        var payload = MAPPER.valueToTree(original.payload());
        ((ObjectNode) payload).set(field,
                "totalAmount".equals(field) ? MAPPER.valueToTree(new BigDecimal(value))
                : MAPPER.valueToTree("foreign".equals(value) ? UUID.randomUUID().toString() : value));
        var event = new LedgerIntegrationEvent(original.eventId(), original.eventType(), 1, original.occurredAt(),
                original.correlationId(), original.producer(), payload);
        var outbox = new LedgerEventOutboxAdapter(jdbc, MAPPER);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> outbox.appendForOutpatientRequest(account, request, event)))
                .isInstanceOf(BillingRuleException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                VALUES (?,?,?,?,TRUE,1,?)
                """, event.eventId(), event.eventType(), account, MAPPER.writeValueAsString(event), request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX", Integer.class)).isZero();
    }

    private UUID issuePrescription(OutpatientPublicationCohortPort cohort) {
        var adapter = new PrescriptionChargeRepositoryAdapter(jdbc, cohort);
        var service = new PrescriptionChargeService(adapter,
                new LedgerEventOutboxAdapter(jdbc, MAPPER), Clock.systemUTC(), code -> {});
        var command = new PrescriptionChargeCommand(
                UUID.randomUUID(), "a".repeat(64), "b".repeat(64), "corr", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "OUTPATIENT", "OUTPATIENT_VISIT", UUID.randomUUID(), null,
                "RX", List.of(new PrescriptionChargeCommand.Item(
                    UUID.randomUUID(), "drug", 1, new BigDecimal("100000.00"))), new BigDecimal("100000.00"),
                Instant.now(), "c".repeat(64));
        return tx().execute(status -> service.issue(command).paymentRequestId());
    }

    @ParameterizedTest
    @ValueSource(strings={"EXAM", "LAB_TEST", "PRESCRIPTION"})
    void actualIssuanceAndPaymentPublishExactOriginalEvents(String purpose) throws Exception {
        var cohort = new OutpatientPublicationCohortAdapter(jdbc, new OutpatientMvpMode(true), properties(true, List.of(purpose)));
        UUID request = switch (purpose) {
            case "EXAM" -> issueExam(cohort, UUID.randomUUID());
            case "LAB_TEST" -> issueLabTest(cohort, UUID.randomUUID());
            default -> issuePrescription(cohort);
        };
        jdbc.update("UPDATE PAYMENT_REQUEST SET expires_at=now()+interval '1 day' WHERE payment_request_id=?", request);
        var service = new LedgerPaymentService(
                new LedgerPaymentRepositoryAdapter(jdbc),
                new LedgerEventOutboxAdapter(jdbc, MAPPER),
                Mappers.getMapper(LedgerPaymentMapper.class),
                Clock.systemUTC());
        BigDecimal amount = jdbc.queryForObject("SELECT requested_amount FROM PAYMENT_REQUEST WHERE payment_request_id=?", BigDecimal.class, request);
        tx().executeWithoutResult(status -> service.complete(request,
                new CompleteLedgerPaymentRequest(UUID.randomUUID().toString(),
                    amount, "VND", PaymentMethod.CASH, null), UUID.randomUUID(), "corr"));
        var rows = jdbc.queryForList("SELECT * FROM BILLING_EVENT_OUTBOX ORDER BY created_at,event_id");
        assertThat(rows).hasSize("PRESCRIPTION".equals(purpose) ? 3 : 2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("publication_enabled")).isEqualTo(true);
            assertThat(row.get("outpatient_payment_request_id")).isEqualTo(request);
        });
        // Reusing a real event with a forged target, purpose, or emergency flag must fail
        // through both the adapter and SQL, even though its account and routing key are valid.
        for (var row : rows) {
            String type = (String) row.get("routing_key");
            List<String> fields = switch (type) {
                case "invoice.created" -> List.of("purpose", "prescriptionId", "invoiceId");
                case "payment.completed" -> List.of("labTestIds");
                default -> List.of("purpose", "prescriptionId", "emergencyOverride", "invoiceId", "labTestIds", "expiresAt", "missingExpiry");
            };
            for (String field : fields) {
                var envelope = (ObjectNode) MAPPER.readTree((String) row.get("payload"));
                UUID eventId = UUID.randomUUID();
                envelope.put("eventId", eventId.toString());
                var body = (ObjectNode) envelope.get("payload");
                if ("labTestIds".equals(field)) ((com.fasterxml.jackson.databind.node.ArrayNode) body.get(field)).add(UUID.randomUUID().toString());
                else if ("missingExpiry".equals(field)) body.putNull("expiresAt");
                else if ("expiresAt".equals(field)) body.put(field, "2099-01-01T00:00:00Z");
                else if ("emergencyOverride".equals(field)) body.put(field, true);
                else body.put(field, "purpose".equals(field) ? "SURGERY" : UUID.randomUUID().toString());
                var event = MAPPER.treeToValue(envelope, LedgerIntegrationEvent.class);
                UUID account = (UUID) row.get("aggregate_id");
                assertThatThrownBy(() -> tx().executeWithoutResult(status ->
                        new LedgerEventOutboxAdapter(jdbc, MAPPER).appendForOutpatientRequest(account, request, event)))
                        .isInstanceOf(BillingRuleException.class);
                assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                        VALUES (?,?,?,?,TRUE,1,?)
                        """, eventId, type, account, envelope.toString(), request))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

}
