package com.mediflow.report.infrastructure.persistence;

import com.mediflow.report.application.dto.command.DispensedItem;
import com.mediflow.report.application.port.in.UpdateAggregateUseCase;
import com.mediflow.report.application.port.out.PrescriptionFillReceiptPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false",
        "mediflow.jwt.secret=report-source-dedupe-test-only-at-least-32-bytes"
})
@Testcontainers
class PrescriptionFillReceiptPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired UpdateAggregateUseCase updater;
    @Autowired PrescriptionFillReceiptPort receipts;
    @Autowired JdbcTemplate jdbc;
    private UUID prescriptionId;
    private UUID departmentId;
    private UUID drugId;
    private final Instant filledAt = Instant.parse("2026-10-08T02:00:00.123456789Z");

    @BeforeEach
    void cleanOwnedProjection() {
        jdbc.execute("TRUNCATE prescription_fill_receipt,processed_event,drug_statistic,daily_visit_report,monthly_revenue_report,payment_contribution");
        prescriptionId = UUID.randomUUID(); departmentId = UUID.randomUUID(); drugId = UUID.randomUUID();
    }

    @Test
    void sameSource_newDelivery_updatesBothScopesOnce() {
        apply(UUID.randomUUID(), filledAt, departmentId, "Test drug", 3);
        apply(UUID.randomUUID(), filledAt, departmentId, "Test drug", 3);
        assertSingleEffect(2);
    }

    @Test
    void concurrentNewDeliveries_uniqueSourceSerializesAcrossTransactions() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); apply(UUID.randomUUID(), filledAt, departmentId, "Test drug", 3); return true; });
            var second = pool.submit(() -> { start.await(); apply(UUID.randomUUID(), filledAt, departmentId, "Test drug", 3); return true; });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertSingleEffect(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"department", "nanosecond", "name", "quantity"})
    void conflictingSource_rollsBackNewDeliveryAndPreservesOriginal(String change) {
        apply(UUID.randomUUID(), filledAt, departmentId, "Test drug", 3);
        UUID changedEvent = UUID.randomUUID();
        assertThatThrownBy(() -> apply(changedEvent,
                change.equals("nanosecond") ? filledAt.plusNanos(1) : filledAt,
                change.equals("department") ? UUID.randomUUID() : departmentId,
                change.equals("name") ? "Changed drug" : "Test drug", change.equals("quantity") ? 4 : 3))
                .isInstanceOfSatisfying(ReportRuleException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("REPORT_PRESCRIPTION_FILL_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id=?", Integer.class, changedEvent)).isZero();
        assertSingleEffect(1);
    }

    @Test
    void projectionFailure_rollsBackSourceDeliveryAndBothScopes_thenRetrySucceeds() {
        UUID eventId = UUID.randomUUID();
        jdbc.execute("ALTER TABLE drug_statistic ADD CONSTRAINT test_reject_department CHECK (department_id IS NULL)");
        try {
            assertThatThrownBy(() -> apply(eventId, filledAt, departmentId, "Test drug", 3)).isInstanceOf(RuntimeException.class);
            for (String table : List.of("prescription_fill_receipt", "processed_event", "daily_visit_report", "drug_statistic")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
            }
        } finally { jdbc.execute("ALTER TABLE drug_statistic DROP CONSTRAINT test_reject_department"); }
        apply(eventId, filledAt, departmentId, "Test drug", 3);
        assertSingleEffect(1);
    }

    @Test
    void sourceClaim_withoutProjectionTransaction_rejects() {
        assertThatThrownBy(() -> receipts.claim(prescriptionId, filledAt, departmentId, ZoneId.of("Asia/Bangkok"),
                List.of(new DispensedItem(drugId, "Test drug", 3))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private void apply(UUID eventId, Instant at, UUID department, String name, int quantity) {
        updater.onPrescriptionFilled(eventId, at, department, prescriptionId,
                List.of(new DispensedItem(drugId, name, quantity)));
    }

    private void assertSingleEffect(int deliveries) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prescription_fill_receipt", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Integer.class)).isEqualTo(deliveries);
        assertThat(jdbc.queryForList("SELECT prescription_count FROM daily_visit_report", Integer.class)).containsExactlyInAnyOrder(1, 1);
        assertThat(jdbc.queryForList("SELECT dispensed_quantity FROM drug_statistic", Integer.class)).containsExactlyInAnyOrder(3, 3);
    }
}
