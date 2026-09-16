package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.report.application.service.AggregateUpdaterService;
import com.mediflow.report.infrastructure.config.ReportConfiguration;
import com.mediflow.report.infrastructure.persistence.adapter.DailyVisitReportPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.DrugStatisticPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.MonthlyRevenueReportPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.PaymentContributionPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.ProcessedEventPersistenceAdapter;

/** PostgreSQL race tests for idempotency claims, natural-key upserts and payment effects. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReportConfiguration.class, AggregateUpdaterService.class,
        DailyVisitReportPersistenceAdapter.class, DrugStatisticPersistenceAdapter.class,
        MonthlyRevenueReportPersistenceAdapter.class, PaymentContributionPersistenceAdapter.class,
        ProcessedEventPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReportPersistenceConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProcessedEventPersistenceAdapter processedEvents;

    @Autowired
    private DailyVisitReportPersistenceAdapter dailyReports;

    @Autowired
    private AggregateUpdaterService updater;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimConcurrent_sameEventHasSingleWinner() throws Exception {
        UUID eventId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = submitClaim(executor, ready, start, eventId);
            Future<Boolean> second = submitClaim(executor, ready, start, eventId);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isNotEqualTo(second.get(5, TimeUnit.SECONDS));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId))
                    .isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void findOrCreateConcurrent_sameNaturalKeyCreatesSingleRow() throws Exception {
        LocalDate reportDate = LocalDate.of(2026, 9, 17);
        UUID departmentId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = submitDailyFindOrCreate(executor, ready, start, reportDate, departmentId);
            Future<?> second = submitDailyFindOrCreate(executor, ready, start, reportDate, departmentId);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM daily_visit_report WHERE report_date = ? AND department_id = ?",
                    Integer.class, reportDate, departmentId)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void paymentCompletedConcurrent_sameInvoiceAppliesRevenueOnce() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-17T10:00:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = submitPayment(executor, ready, start, firstEventId, occurredAt, invoiceId,
                    departmentId);
            Future<?> second = submitPayment(executor, ready, start, secondEventId, occurredAt, invoiceId,
                    departmentId);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            LocalDate reportDate = occurredAt.atZone(ZoneId.of("Asia/Bangkok")).toLocalDate();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT revenue FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                    BigDecimal.class, reportDate)).isEqualByComparingTo("100.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT invoice_count FROM monthly_revenue_report "
                            + "WHERE year = ? AND month = ? AND department_id IS NULL",
                    Integer.class, reportDate.getYear(), reportDate.getMonthValue())).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Future<Boolean> submitClaim(ExecutorService executor, CountDownLatch ready,
                                        CountDownLatch start, UUID eventId) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            return processedEvents.claimIfAbsent(eventId, "medicalrecord.created");
        });
    }

    private Future<?> submitDailyFindOrCreate(ExecutorService executor, CountDownLatch ready,
                                              CountDownLatch start, LocalDate reportDate,
                                              UUID departmentId) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            dailyReports.findOrCreate(reportDate, departmentId);
        });
    }

    private Future<?> submitPayment(ExecutorService executor, CountDownLatch ready,
                                    CountDownLatch start, UUID eventId, Instant occurredAt,
                                    UUID invoiceId, UUID departmentId) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            updater.onPaymentCompleted(eventId, occurredAt, invoiceId, departmentId,
                    new BigDecimal("100.00"));
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Test thread did not reach the start barrier");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test thread was interrupted", ex);
        }
    }
}
