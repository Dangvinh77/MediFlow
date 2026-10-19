package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.service.CarePaymentReceiptService;
import com.mediflow.notification.infrastructure.persistence.adapter.CareNotificationRepositoryAdapter;
import com.mediflow.notification.infrastructure.recovery.CareReceiptByteIdentityBackfill;
import com.mediflow.notification.messaging.consumer.CarePaymentReceiptWireHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real V4 store/effects upgraded by append-only V5; archive import never runs business commands. */
@Testcontainers
class CareReceiptByteIdentityUpgradeTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private CarePaymentReceiptWireHandler legacy;

    @BeforeEach void v4Store() {
        var flyway = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .cleanDisabled(false).target("4").load();
        flyway.clean(); flyway.migrate();
        var source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        // Exact pre-upgrade claim: normalized hash only, same transaction/history/outbox implementation.
        legacy = handler(new CareNotificationRepositoryAdapter(jdbc, mapper) {
            @Override public boolean claimPaymentReceipt(UUID id, String raw, String normalized) {
                return claim(id, "payment.completed", normalized);
            }
        });
    }

    @Test void upgradeBackfillReplayAndOldReaderPreserveHistoryAndNewWrites() throws Exception {
        byte[] original = fixture();
        legacy.receive("payment.completed", original);
        String oldHash = jdbc.queryForObject("SELECT payload_hash FROM PROCESSED_EVENT", String.class);
        var history = jdbc.queryForList("SELECT * FROM NOTIFICATION");
        var outbox = jdbc.queryForList("SELECT * FROM NOTIFICATION_EVENT_OUTBOX");
        upgrade();
        assertThatThrownBy(() -> current().receive("payment.completed", original))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(backfill(List.of(original), false)).isOne();
        assertThat(rawCount()).isZero();
        assertThat(backfill(List.of(original), true)).isOne();
        assertThat(rawCount()).isOne();
        assertThat(backfill(List.of(original), true)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_receipt_byte_backfill", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT payload_hash FROM PROCESSED_EVENT", String.class)).isEqualTo(oldHash);
        current().receive("payment.completed", original);
        // Reconstruct reader against persisted state instead of retaining any in-memory receipt cache.
        current().receive("payment.completed", original);
        assertThatThrownBy(() -> current().receive("payment.completed", variant(original)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(jdbc.queryForList("SELECT * FROM NOTIFICATION")).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT * FROM NOTIFICATION_EVENT_OUTBOX")).isEqualTo(outbox);
        byte[] next = another(original);
        current().receive("payment.completed", next);
        legacy.receive("payment.completed", original);
        legacy.receive("payment.completed", next);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM NOTIFICATION", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM NOTIFICATION_EVENT_OUTBOX", Integer.class)).isEqualTo(2);
        assertThat(rawCount()).isEqualTo(2);
    }

    @Test void incompleteArchiveCannotPartiallyConvertHistory() throws Exception {
        byte[] first = fixture();
        legacy.receive("payment.completed", first);
        legacy.receive("payment.completed", another(first));
        upgrade();
        assertThatThrownBy(() -> backfill(List.of(first), true)).hasMessageContaining("cover all");
        unchanged();
    }

    @Test void changedArchivedBusinessPayloadCannotReplaceOriginalReceipt() throws Exception {
        byte[] original = seedAndUpgrade();
        var changed = (ObjectNode) mapper.readTree(original);
        ((ObjectNode) changed.path("payload")).put("totalAmount", 50);
        assertThatThrownBy(() -> backfill(List.of(mapper.writeValueAsBytes(changed)), true))
                .hasMessageContaining("conflicts");
        unchanged();
    }

    @Test void archiveWithUnknownIdentityCannotPartiallyImportKnownReceipt() throws Exception {
        byte[] original = seedAndUpgrade();
        assertThatThrownBy(() -> backfill(List.of(original, another(original)), true))
                .hasMessageContaining("unknown");
        unchanged();
    }

    @Test void duplicateArchiveIdsAndMalformedBodiesCannotImport() throws Exception {
        byte[] original = seedAndUpgrade();
        assertThatThrownBy(() -> backfill(List.of(original, original), true)).hasMessageContaining("duplicate");
        assertThatThrownBy(() -> backfill(List.of("{}".getBytes(StandardCharsets.UTF_8)), true))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        unchanged();
    }

    @Test void alreadyPinnedBytesCannotBeReboundToWhitespaceEquivalentArchive() throws Exception {
        byte[] original = seedAndUpgrade();
        backfill(List.of(original), true);
        String hash = jdbc.queryForObject("SELECT receipt_raw_hash FROM PROCESSED_EVENT", String.class);
        assertThatThrownBy(() -> backfill(List.of(variant(original)), true)).hasMessageContaining("conflicts");
        assertThat(jdbc.queryForObject("SELECT receipt_raw_hash FROM PROCESSED_EVENT", String.class)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_receipt_byte_backfill", Integer.class)).isOne();
    }

    @Test void auditFailureRollsBackRawHashAndProvenanceTogether() throws Exception {
        byte[] original = seedAndUpgrade();
        jdbc.execute("ALTER TABLE care_receipt_byte_backfill ADD CONSTRAINT isolated_import_fault CHECK (false)");
        assertThatThrownBy(() -> backfill(List.of(original), true)).isInstanceOf(java.sql.SQLException.class);
        unchanged();
    }

    @Test void v0AndOtherContextHashesRemainUnmodifiedWithoutReceiptArchive() throws Exception {
        jdbc.update("INSERT INTO PROCESSED_EVENT(event_id,routing_key) VALUES (?,'payment.completed')", UUID.randomUUID());
        jdbc.update("INSERT INTO PROCESSED_EVENT(event_id,routing_key,payload_hash) VALUES (?,'prescription.filled',?)",
                UUID.randomUUID(), "a".repeat(64));
        upgrade();
        assertThat(backfill(List.of(), true)).isZero();
        unchanged();
        assertThat(jdbc.queryForObject("SELECT payload_hash FROM PROCESSED_EVENT WHERE routing_key='prescription.filled'",
                String.class)).isEqualTo("a".repeat(64));
    }

    private byte[] seedAndUpgrade() throws Exception {
        byte[] original = fixture(); legacy.receive("payment.completed", original); upgrade(); return original;
    }
    private void upgrade() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).load().migrate();
    }
    private CarePaymentReceiptWireHandler current() { return handler(new CareNotificationRepositoryAdapter(jdbc, mapper)); }
    private CarePaymentReceiptWireHandler handler(CareNotificationRepositoryAdapter repository) {
        var service = new CarePaymentReceiptService(repository);
        return new CarePaymentReceiptWireHandler(mapper,
                command -> transactions.executeWithoutResult(status -> service.receive(command)), true);
    }
    private int backfill(List<byte[]> bodies, boolean apply) throws Exception {
        try (Connection connection = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())) {
            return CareReceiptByteIdentityBackfill.backfill(connection, bodies, apply);
        }
    }
    private int rawCount() { return jdbc.queryForObject("SELECT count(*) FROM PROCESSED_EVENT WHERE receipt_raw_hash IS NOT NULL", Integer.class); }
    private void unchanged() {
        assertThat(rawCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_receipt_byte_backfill", Integer.class)).isZero();
    }
    private byte[] another(byte[] original) throws Exception {
        var next = (ObjectNode) mapper.readTree(original); next.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) next.path("payload")).put("transactionId", UUID.randomUUID().toString());
        return mapper.writeValueAsBytes(next);
    }
    private byte[] variant(byte[] original) {
        return (" \n" + new String(original, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }
    private byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json"));
    }
}
