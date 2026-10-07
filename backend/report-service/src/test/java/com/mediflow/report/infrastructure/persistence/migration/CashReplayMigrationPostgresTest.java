package com.mediflow.report.infrastructure.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.service.CashReceiptApplicationService;
import com.mediflow.report.application.service.CashReplayApplicationService;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.CashReceiptPersistenceAdapter;
import com.mediflow.report.infrastructure.persistence.adapter.CashReplayPersistenceAdapter;

/** Real additive V12 source upgrade; historical data is not fabricated or backfilled. */
@Testcontainers(disabledWithoutDocker = true)
class CashReplayMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;

    @BeforeEach
    void clean() {
        flyway(null).clean();
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(datasource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    }

    @Test
    void v12ToV13_actualReceiptPreservesLegacyHashExactNanosAndCanRebuildWithoutNewDelivery() throws Exception {
        flyway("12").migrate();
        var event = new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode("payment.completed", Files.readAllBytes(Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json")));
        var payload = new LinkedHashMap<>(event.payload());
        payload.put("completedAt", "2026-10-04T18:30:00.123456789Z");
        var accepted = new DecodedCareFinanceEvent(event.metadata(), payload);
        var live = new CashReceiptApplicationService(new CashReceiptPersistenceAdapter(jdbc, new ObjectMapper()),
                new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")));
        transactions.executeWithoutResult(status -> live.apply(accepted));
        // Literal sorted JSON is the pre-refactor V12 receipt fingerprint contract. This is not
        // calculated via the new codec, so accidental hash-format changes cannot pass both sides.
        String legacyJson = """
                {"accountId":"00000000-0000-0000-0000-000000000001","amount":100,"businessDate":"2026-10-05","careEpisodeId":"00000000-0000-0000-0000-000000000003","careEpisodeType":"OUTPATIENT_VISIT","classification":"SERVICE_PAYMENT","completedAt":"2026-10-04T18:30:00.123456789Z","currency":"VND","departmentId":"00000000-0000-0000-0000-000000000008","invoiceId":"00000000-0000-0000-0000-000000000005","patientId":"00000000-0000-0000-0000-000000000002","paymentMethod":"CASH","paymentRequestId":"00000000-0000-0000-0000-000000000012","reportZone":"Asia/Bangkok","transactionId":"00000000-0000-0000-0000-000000000011"}
                """.strip();
        String oldHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacyJson.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT fact_fingerprint FROM report_cash_receipt", String.class)).isEqualTo(oldHash);
        String oldSourceProof = jdbc.queryForObject("SELECT payload_fingerprint FROM report_cash_receipt", String.class);
        jdbc.update("INSERT INTO daily_visit_report(report_id,report_date,visit_count,revenue) VALUES (?,DATE '2026-10-05',7,50)", UUID.randomUUID());
        flyway(null).migrate();
        assertThat(jdbc.queryForObject("SELECT fact_fingerprint FROM report_cash_receipt", String.class)).isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("SELECT payload_fingerprint FROM report_cash_receipt", String.class)).isEqualTo(oldSourceProof);
        assertThat(jdbc.queryForObject("SELECT completed_at_iso FROM report_cash_receipt", String.class))
                .isEqualTo("2026-10-04T18:30:00.123456789Z");
        assertThat(jdbc.queryForObject("SELECT sum(visit_count) FROM daily_visit_report", Long.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_replay_input", Integer.class)).isZero();
        var replay = new CashReplayApplicationService(new CashReplayPersistenceAdapter(jdbc, new ObjectMapper()));
        var generation = transactions.execute(status -> replay.start());
        assertThat(transactions.execute(status -> replay.advance(generation.generationId(), 1)).status()).isEqualTo(Status.VERIFIED);
        assertThat(jdbc.queryForObject("SELECT fact_fingerprint FROM cash_replay_receipt", String.class)).isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("SELECT fact_snapshot->>'completedAt' FROM cash_replay_receipt", String.class))
                .isEqualTo("2026-10-04T18:30:00.123456789Z");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_delivery", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Integer.class)).isZero();
    }

    @Test
    void freshV13_nullScopeUniqueCurrencySeparatedAndProgressConstraintsHold() {
        flyway(null).migrate();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO cash_replay_generation(generation_id,status) VALUES (?,'BUILDING')", id);
        String insert = """
                INSERT INTO cash_replay_scope(generation_id,business_date,currency,report_zone,department_id,
                    classification,gross_receipts,receipt_count) VALUES (?,DATE '2026-10-05',?,'Asia/Bangkok',NULL,'SERVICE_PAYMENT',100,1)
                """;
        jdbc.update(insert, id, "VND");
        assertThatThrownBy(() -> jdbc.update(insert, id, "VND"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        jdbc.update(insert, id, "USD");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_replay_scope", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE cash_replay_generation SET applied_receipts=1 WHERE generation_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE cash_replay_generation SET status='VERIFIED' WHERE generation_id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Integer.class)).isZero();
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
