package com.mediflow.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.domain.model.ScheduledPaymentOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ScheduledPaymentRecoveryFairnessPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private ScheduledPaymentTransactionsPort operations;
    private UUID accountId;
    private UUID patientId;
    private UUID departmentId;
    private UUID actorId;

    @BeforeEach
    void prepareDatabaseAndOperations() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false)
                .load().clean();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        operations = new ScheduledPaymentTransactionsAdapter(jdbc, null, (LedgerPaymentService) null);
        accountId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,?)
                """, accountId, patientId, departmentId, UUID.randomUUID(), java.sql.Timestamp.from(Instant.now()));
        seedPreparingOperations(3);
    }

    @Test
    void claimedUnavailableBatchMovesBehindOtherDueOperations() {
        List<ScheduledPaymentOperation> firstBatch = operations.recoverable(2);
        assertThat(firstBatch).extracting(ScheduledPaymentOperation::paymentOperationId)
                .containsExactly(operationIds.get(0), operationIds.get(1));

        for (int index = 0; index < 2; index++) {
            UUID operationId = operationIds.get(index);
            assertThat(jdbc.queryForObject("SELECT recovery_attempt_count FROM scheduled_payment_operation "
                    + "WHERE payment_operation_id=?", Integer.class, operationId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT next_recovery_attempt_at > CURRENT_TIMESTAMP "
                    + "FROM scheduled_payment_operation WHERE payment_operation_id=?", Boolean.class, operationId)).isTrue();
        }

        List<ScheduledPaymentOperation> secondBatch = operations.recoverable(2);
        assertThat(secondBatch).extracting(ScheduledPaymentOperation::paymentOperationId)
                .containsExactly(operationIds.get(2));
        assertThat(jdbc.queryForObject("SELECT recovery_attempt_count FROM scheduled_payment_operation "
                + "WHERE payment_operation_id=?", Integer.class, operationIds.get(2))).isEqualTo(1);
    }

    @Test
    void cashierQueuePagesInArrivalOrderFiltersPatientAndHidesTerminalOrExpiredRequests() {
        var queue = new ScheduledPaymentQueueAdapter(jdbc);
        var first = queue.find(patientId, com.mediflow.common.api.PageQuery.of(0, 2));
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(2);
        var second = queue.find(patientId, com.mediflow.common.api.PageQuery.of(1, 2));
        assertThat(second.content()).hasSize(1);
        assertThat(first.content().get(0).createdAt()).isBefore(second.content().get(0).createdAt());
        assertThat(queue.find(UUID.randomUUID(), com.mediflow.common.api.PageQuery.of(0, 10)).content()).isEmpty();
        jdbc.update("UPDATE PAYMENT_REQUEST SET status='PAID' WHERE payment_request_id=(SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?)", first.content().get(0).registrationId());
        jdbc.update("UPDATE PAYMENT_REQUEST SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE payment_request_id=(SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?)", first.content().get(1).registrationId());
        jdbc.update("UPDATE outpatient_registration_source SET registration_status='CANCELLED' WHERE registration_id=?", second.content().get(0).registrationId());
        assertThat(queue.find(null, com.mediflow.common.api.PageQuery.of(0, 10)).content()).isEmpty();
    }

    private final List<UUID> operationIds = new ArrayList<>();

    private void seedPreparingOperations(int count) {
        Instant oldestCreatedAt = Instant.now().minusSeconds(60);
        for (int index = 0; index < count; index++) {
            UUID registrationId = UUID.randomUUID();
            UUID paymentRequestId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();
            UUID invoiceId = UUID.randomUUID();
            UUID operationId = UUID.randomUUID();
            Instant createdAt = oldestCreatedAt.plusSeconds(index);
            operationIds.add(operationId);

            jdbc.update("""
                    INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,
                        expires_at,created_by,created_at)
                    VALUES (?,?,?,'EXAM',?,'VND',?,?,?)
                    """, paymentRequestId, invoiceId, accountId, new BigDecimal("100"),
                    java.sql.Timestamp.from(createdAt.plusSeconds(3600)), actorId,
                    java.sql.Timestamp.from(createdAt));
            jdbc.update("""
                    INSERT INTO outpatient_registration_source(registration_id,registration_revision,patient_id,doctor_id,
                        department_id,session_id,room_id,session_date,start_time,end_time,visit_type,reservation_id,
                        reservation_status,registration_status,reservation_expires_at,price_code,source_type,source_id,
                        care_episode_type,care_episode_id,changed_at,payload_fingerprint,payment_request_id,invoice_id,
                        account_id,request_issue_status)
                    VALUES (?,1,?,?,?,?,?,CURRENT_DATE,'09:00','09:30','NEW_VISIT',?,'HELD','AWAITING_PAYMENT',?,
                        'OUTPATIENT_EXAM','EXAM',?,'OUTPATIENT_VISIT',?,?,?, ?,?,?, 'READY')
                    """, registrationId, patientId, UUID.randomUUID(), departmentId, UUID.randomUUID(), UUID.randomUUID(),
                    reservationId, java.sql.Timestamp.from(createdAt.plusSeconds(3600)), registrationId,
                    registrationId, java.sql.Timestamp.from(createdAt), "a".repeat(64), paymentRequestId, invoiceId, accountId);
            jdbc.update("""
                    INSERT INTO scheduled_payment_operation(payment_operation_id,payment_request_id,account_id,registration_id,
                        reservation_id,patient_id,source_type,source_id,care_episode_id,actor_account_id,idempotency_key,
                        command_fingerprint,correlation_id,expected_reservation_revision,amount,currency,payment_method,
                        status,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,'EXAM',?,?,?,?,?,?,1,?,'VND','CASH','PREPARING',?,?)
                    """, operationId, paymentRequestId, accountId, registrationId, reservationId, patientId,
                    registrationId, registrationId, actorId, "recovery-test-" + index, Integer.toHexString(index).repeat(64 / Integer.toHexString(index).length()).substring(0, 64),
                    "recovery-test-trace-" + index, new BigDecimal("100"), java.sql.Timestamp.from(createdAt),
                    java.sql.Timestamp.from(createdAt));
        }
    }
}
