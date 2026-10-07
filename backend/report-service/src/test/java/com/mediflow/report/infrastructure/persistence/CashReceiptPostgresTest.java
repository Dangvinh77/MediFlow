package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.service.CashReceiptApplicationService;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.CashReceiptPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CashReceiptApplicationService.class, CashReceiptPersistenceAdapter.class, CashReceiptPostgresTest.Config.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashReceiptPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired CashReceiptApplicationService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE report_cash_delivery,report_cash_receipt,report_gross_cash_daily CASCADE");
    }

    @Test
    void actualBillingReceipts_createGrossScopesButNoEarnedLiabilityOrLegacyEffects() throws Exception {
        service.apply(fixture("service"));
        service.apply(fixture("deposit"));
        assertThat(count("report_cash_receipt")).isEqualTo(2);
        assertThat(count("report_gross_cash_daily")).isEqualTo(4);
        assertThat(total(null, "VND")).isEqualByComparingTo("200.00");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT classification) FROM report_gross_cash_daily", Integer.class)).isEqualTo(2);
        assertThat(count("financial_contribution")).isZero();
        assertThat(count("daily_financial_report")).isZero();
        assertThat(count("payment_contribution")).isZero();
        assertThat(count("monthly_revenue_report")).isZero();
    }

    @Test
    void exactRedeliveryAndSameTransactionNewEvent_onlyOneReceiptAndTwoScopes() throws Exception {
        var event = fixture("service");
        service.apply(event);
        service.apply(event);
        service.apply(delivery(event, UUID.randomUUID(), event.metadata().sourceId(), "100.0000", "VND"));
        assertThat(count("report_cash_delivery")).isEqualTo(2);
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(count("report_gross_cash_daily")).isEqualTo(2);
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
    }

    @Test
    void twoPartialTransactionsOnOneInvoice_areTwoIndependentReceipts() throws Exception {
        // Consumer mutation scenario, NOT an additional producer-owned fixture or approval.
        var event = fixture("service");
        service.apply(delivery(event, UUID.randomUUID(), UUID.randomUUID(), "60.00", "VND"));
        service.apply(delivery(event, UUID.randomUUID(), UUID.randomUUID(), "40.00", "VND"));
        assertThat(count("report_cash_receipt")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT invoice_id) FROM report_cash_receipt", Integer.class)).isOne();
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForList("SELECT gross_receipts,receipt_count FROM report_gross_cash_daily"))
                .hasSize(2).allSatisfy(row -> {
                    assertThat((BigDecimal) row.get("gross_receipts")).isEqualByComparingTo("100.00");
                    assertThat(row.get("receipt_count")).isEqualTo(2L);
                });
    }

    @Test
    void differentCurrencies_areNeverAddedTogether() throws Exception {
        var event = fixture("service");
        service.apply(event);
        service.apply(delivery(event, UUID.randomUUID(), UUID.randomUUID(), "25.00", "USD"));
        assertThat(count("report_gross_cash_daily")).isEqualTo(4);
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
        assertThat(total(null, "USD")).isEqualByComparingTo("25.00");
    }

    @Test
    void completedTimeRetainsNanosecondsAndRepublishTimeDoesNotChangeBusinessDate() throws Exception {
        var event = fixture("service");
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("completedAt", "2026-10-04T18:30:00.123456789Z");
        event = new DecodedCareFinanceEvent(event.metadata(), payload);
        service.apply(event);
        var metadata = event.metadata();
        var republished = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(UUID.randomUUID(),
                metadata.eventType(), 1, metadata.occurredAt().plusSeconds(172800), "another-delivery",
                metadata.producer(), metadata.sourceField(), metadata.sourceId()), payload);
        service.apply(republished);
        assertThat(jdbc.queryForObject("SELECT completed_at_iso FROM report_cash_receipt", String.class))
                .isEqualTo("2026-10-04T18:30:00.123456789Z");
        assertThat(jdbc.queryForObject("SELECT business_date::text FROM report_cash_receipt", String.class))
                .isEqualTo("2026-10-05");
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(count("report_cash_delivery")).isEqualTo(2);
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
    }

    @Test
    void hugeUnknownNumber_rejectsBeforeAnyEvidenceOrEffects() throws Exception {
        var event = fixture("service");
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("futureAmount", new BigDecimal("1E+1000000"));
        var invalid = new DecodedCareFinanceEvent(event.metadata(), payload);
        assertThatThrownBy(() -> service.apply(invalid)).hasMessageContaining("fingerprint bounds");
        assertThat(count("report_cash_receipt")).isZero();
        assertThat(count("report_cash_delivery")).isZero();
        assertThat(count("report_gross_cash_daily")).isZero();
    }

    @Test
    void sameTransactionChangedNonCashEvidence_conflictsAndRollsBackNewDelivery() throws Exception {
        var event = fixture("service");
        service.apply(event);
        var next = delivery(event, UUID.randomUUID(), event.metadata().sourceId(), "100.00", "VND");
        var payload = new LinkedHashMap<>(next.payload());
        payload.put("prescriptionId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> service.apply(new DecodedCareFinanceEvent(next.metadata(), payload)))
                .hasMessageContaining("conflict");
        assertThat(count("report_cash_delivery")).isOne();
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
    }

    @Test
    void sameEventChangedTransaction_conflictsAndRollsBackNewSource() throws Exception {
        var event = fixture("service");
        service.apply(event);
        assertThatThrownBy(() -> service.apply(delivery(event, event.metadata().eventId(), UUID.randomUUID(), "100.00", "VND")))
                .hasMessageContaining("conflict");
        assertThat(count("report_cash_delivery")).isOne();
        assertThat(count("report_cash_receipt")).isOne();
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
    }

    @Test
    void secondScopeFailure_rollsBackJournalDeliveryAndHospitalScope_thenRetrySucceeds() throws Exception {
        var event = fixture("deposit");
        jdbc.execute("ALTER TABLE report_gross_cash_daily ADD CONSTRAINT test_deny_department CHECK (department_id IS NULL)");
        try {
            assertThatThrownBy(() -> service.apply(event)).isInstanceOf(RuntimeException.class);
            assertThat(count("report_cash_delivery")).isZero();
            assertThat(count("report_cash_receipt")).isZero();
            assertThat(count("report_gross_cash_daily")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE report_gross_cash_daily DROP CONSTRAINT test_deny_department");
        }
        service.apply(event);
        assertThat(total(null, "VND")).isEqualByComparingTo("100.00");
        assertThat(count("report_gross_cash_daily")).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateAndNewTransactions_preserveOneFactPerOperationAndBothScopes() throws Exception {
        var event = fixture("service");
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            // Four deliveries of one operation plus four distinct operations = five receipts.
            for (int index = 0; index < 8; index++) {
                UUID transaction = index < 4 ? event.metadata().sourceId() : UUID.randomUUID();
                var next = delivery(event, UUID.randomUUID(), transaction, "100.00", "VND");
                tasks.add(pool.submit(() -> {
                    try {
                        if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout");
                        service.apply(next);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(30, TimeUnit.SECONDS);
        }
        assertThat(count("report_cash_delivery")).isEqualTo(8);
        assertThat(count("report_cash_receipt")).isEqualTo(5);
        assertThat(count("report_gross_cash_daily")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT gross_receipts,receipt_count FROM report_gross_cash_daily"))
                .allSatisfy(row -> {
                    assertThat((BigDecimal) row.get("gross_receipts")).isEqualByComparingTo("500.00");
                    assertThat(row.get("receipt_count")).isEqualTo(5L);
                });
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private BigDecimal total(UUID department, String currency) {
        return jdbc.queryForObject("""
                SELECT sum(gross_receipts) FROM report_gross_cash_daily
                WHERE department_id IS NOT DISTINCT FROM CAST(? AS UUID) AND currency=?
                """, BigDecimal.class, department, currency);
    }

    private static DecodedCareFinanceEvent fixture(String type) throws Exception {
        return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode("payment.completed", Files.readAllBytes(
                Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-" + type + ".json")));
    }

    private static DecodedCareFinanceEvent delivery(DecodedCareFinanceEvent event, UUID eventId,
            UUID transactionId, String amount, String currency) {
        var metadata = event.metadata();
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("transactionId", transactionId.toString());
        payload.put("totalAmount", new BigDecimal(amount));
        payload.put("currency", currency);
        return new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId, metadata.eventType(), 1,
                metadata.occurredAt(), metadata.correlationId(), metadata.producer(), metadata.sourceField(), transactionId), payload);
    }

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean BillingCashReceiptMapper billingCashReceiptMapper() {
            return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok"));
        }
    }
}
