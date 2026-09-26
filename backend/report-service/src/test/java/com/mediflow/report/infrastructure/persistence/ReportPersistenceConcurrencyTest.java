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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.BeforeEach;
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
    private MonthlyRevenueReportPersistenceAdapter monthlyReports;

    @Autowired
    private DrugStatisticPersistenceAdapter drugStatistics;

    @Autowired
    private AggregateUpdaterService updater;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanProjectionTables() {
        jdbcTemplate.execute("TRUNCATE TABLE processed_event, payment_contribution, "
                + "daily_visit_report, monthly_revenue_report, drug_statistic CASCADE");
    }

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
    void findOrCreateConcurrent_hospitalScopeCreatesSingleRow() throws Exception {
        LocalDate reportDate = LocalDate.of(2026, 9, 18);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = submitDailyFindOrCreate(executor, ready, start, reportDate, null);
            Future<?> second = submitDailyFindOrCreate(executor, ready, start, reportDate, null);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                    Integer.class, reportDate)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void findOrCreateConcurrent_monthlyNaturalKeyCreatesSingleRow() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = submitMonthlyFindOrCreate(executor, ready, start, 9, 2026, null);
            Future<?> second = submitMonthlyFindOrCreate(executor, ready, start, 9, 2026, null);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM monthly_revenue_report "
                            + "WHERE year = 2026 AND month = 9 AND department_id IS NULL",
                    Integer.class)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void findOrCreateConcurrent_drugHospitalScopeCreatesSingleRow() throws Exception {
        UUID drugId = UUID.randomUUID();
        LocalDate reportDate = LocalDate.of(2026, 9, 19);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = submitDrugFindOrCreate(executor, ready, start, drugId, reportDate);
            Future<?> second = submitDrugFindOrCreate(executor, ready, start, drugId, reportDate);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM drug_statistic WHERE drug_id = ? AND report_date = ? "
                            + "AND department_id IS NULL",
                    Integer.class, drugId, reportDate)).isOne();
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
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT revenue FROM daily_visit_report WHERE report_date = ? AND department_id = ?",
                    BigDecimal.class, reportDate, departmentId)).isEqualByComparingTo("100.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT invoice_count FROM monthly_revenue_report "
                            + "WHERE year = ? AND month = ? AND department_id = ?",
                    Integer.class, reportDate.getYear(), reportDate.getMonthValue(), departmentId)).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void paymentCompleted_nullDepartmentCreatesHospitalScopeOnly() {
        UUID invoiceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-21T10:00:00Z");
        LocalDate reportDate = occurredAt.atZone(ZoneId.of("Asia/Bangkok")).toLocalDate();

        updater.onPaymentCompleted(eventId, occurredAt, invoiceId, null, new BigDecimal("80.00"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                Integer.class, reportDate)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM daily_visit_report WHERE report_date = ? AND department_id IS NOT NULL",
                Integer.class, reportDate)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT revenue FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                BigDecimal.class, reportDate)).isEqualByComparingTo("80.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_revenue_report WHERE year = ? AND month = ? "
                        + "AND department_id IS NULL",
                Integer.class, reportDate.getYear(), reportDate.getMonthValue())).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM monthly_revenue_report WHERE year = ? AND month = ? "
                        + "AND department_id IS NOT NULL",
                Integer.class, reportDate.getYear(), reportDate.getMonthValue())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_contribution WHERE invoice_id = ? AND department_id IS NULL",
                Integer.class, invoiceId)).isOne();
    }

    @RepeatedTest(10)
    void paymentCompletedFailedRace_alwaysEndsReversedWithoutRevenue() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID completedEventId = UUID.randomUUID();
        UUID failedEventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-20T10:00:00Z");
        LocalDate reportDate = occurredAt.atZone(ZoneId.of("Asia/Bangkok")).toLocalDate();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> completed = submitPayment(executor, ready, start, completedEventId, occurredAt,
                    invoiceId, departmentId);
            Future<?> failed = submitPaymentFailed(executor, ready, start, failedEventId, occurredAt,
                    invoiceId);

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            completed.get(5, TimeUnit.SECONDS);
            failed.get(5, TimeUnit.SECONDS);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_contribution WHERE invoice_id = ?", String.class,
                    invoiceId)).isEqualTo("REVERSED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COALESCE((SELECT revenue FROM daily_visit_report "
                            + "WHERE report_date = ? AND department_id IS NULL), 0)",
                    BigDecimal.class, reportDate)).isEqualByComparingTo("0.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COALESCE((SELECT revenue FROM daily_visit_report "
                            + "WHERE report_date = ? AND department_id = ?), 0)",
                    BigDecimal.class, reportDate, departmentId)).isEqualByComparingTo("0.00");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COALESCE((SELECT invoice_count FROM monthly_revenue_report "
                            + "WHERE year = ? AND month = ? AND department_id = ?), 0)",
                    Integer.class, reportDate.getYear(), reportDate.getMonthValue(), departmentId)).isZero();
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

    private Future<?> submitMonthlyFindOrCreate(ExecutorService executor, CountDownLatch ready,
                                                CountDownLatch start, int month, int year,
                                                UUID departmentId) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            monthlyReports.findOrCreate(year, month, departmentId);
        });
    }

    private Future<?> submitDrugFindOrCreate(ExecutorService executor, CountDownLatch ready,
                                             CountDownLatch start, UUID drugId,
                                             LocalDate reportDate) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            drugStatistics.findOrCreate(drugId, "Concurrent drug", reportDate, null);
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

    private Future<?> submitPaymentFailed(ExecutorService executor, CountDownLatch ready,
                                          CountDownLatch start, UUID eventId, Instant occurredAt,
                                          UUID invoiceId) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            updater.onPaymentFailed(eventId, occurredAt, invoiceId);
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
