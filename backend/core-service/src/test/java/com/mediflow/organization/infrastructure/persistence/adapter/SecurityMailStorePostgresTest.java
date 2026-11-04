package com.mediflow.organization.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageQuery;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class SecurityMailStorePostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private SecurityMailStoreAdapter store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS account(account_id UUID PRIMARY KEY,patient_id UUID,staff_id UUID,role VARCHAR(20),username VARCHAR(254),email_migrated BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS staff(staff_id UUID PRIMARY KEY,full_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS local_demo_seed_receipt(seed_key VARCHAR(100) PRIMARY KEY,entity_id UUID NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS local_demo_mailbox_account(account_id UUID PRIMARY KEY,seed_key VARCHAR(100) UNIQUE,synthetic_email VARCHAR(254) UNIQUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS security_mail_rate_limit(email_hash CHAR(64) NOT NULL,window_start TIMESTAMPTZ NOT NULL,request_count INTEGER NOT NULL,PRIMARY KEY(email_hash,window_start))");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS security_mail_message(
                    message_id UUID PRIMARY KEY,operation_key VARCHAR(220) UNIQUE,account_id UUID,
                    sender_email VARCHAR(254),recipient_email VARCHAR(254),message_kind VARCHAR(32),subject VARCHAR(180),
                    encrypted_content BYTEA,content_expires_at TIMESTAMPTZ,demo_mailbox BOOLEAN,status VARCHAR(16),
                    attempts INTEGER,lease_until TIMESTAMPTZ,next_attempt_at TIMESTAMPTZ,created_at TIMESTAMPTZ,
                    sent_at TIMESTAMPTZ,last_failure_code VARCHAR(80))
                """);
        jdbc.update("DELETE FROM security_mail_message");
        jdbc.update("DELETE FROM local_demo_mailbox_account");
        jdbc.update("DELETE FROM local_demo_seed_receipt");
        jdbc.update("DELETE FROM security_mail_rate_limit");
        jdbc.update("DELETE FROM account");
        jdbc.update("DELETE FROM staff");
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        store = new SecurityMailStoreAdapter(dataSource, new ObjectMapper(), key, 30);
    }

    @Test
    void demoMailboxRequiresSeedReceiptAndSyntheticAddressAndScrubsExpiredBody() {
        UUID accountId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        jdbc.update("INSERT INTO account VALUES(?,?,NULL,'PATIENT','demo.patient1@example.test',true)", accountId, patientId);
        jdbc.update("INSERT INTO local_demo_seed_receipt VALUES('account-demo.patient',?)", accountId);
        jdbc.update("INSERT INTO local_demo_mailbox_account VALUES(?,?,?)", accountId,
                "account-demo.patient", "demo.patient1@example.test");
        var delivery = store.enqueue(new SecurityMailStore.NewMessage(accountId, "MediFlow <notice@example.test>",
                "demo.patient1@example.test", "PASSWORD_RECOVERY", "recover-1", "Reset password",
                "Synthetic body", "<p>Synthetic body</p>"));
        assertThat(delivery.status()).isEqualTo(com.mediflow.organization.application.dto.response.MailDeliveryDTO.Status.SENT);
        assertThat(store.demoAccounts()).hasSize(1);
        assertThat(store.demoMessages(accountId, PageQuery.of(0, 10)).totalElements()).isEqualTo(1);

        jdbc.update("UPDATE security_mail_message SET content_expires_at=now()-interval '1 day' WHERE message_id=?", delivery.messageId());
        store.claimDue(10);
        assertThat(jdbc.queryForObject("SELECT encrypted_content IS NULL FROM security_mail_message WHERE message_id=?", Boolean.class,
                delivery.messageId())).isTrue();
        var expired = store.demoMessage(accountId, delivery.messageId()).orElseThrow();
        assertThat(expired.subject()).isEqualTo("Reset password");
        assertThat(expired.from()).isEqualTo("MediFlow <notice@example.test>");
        assertThat(expired.preview()).contains("hết thời hạn");

        jdbc.update("UPDATE account SET username='real.person@gmail.com' WHERE account_id=?", accountId);
        assertThat(store.demoAccounts()).isEmpty();
        assertThat(store.demoMessages(accountId, PageQuery.of(0, 10)).totalElements()).isZero();
        assertThat(store.demoMessage(accountId, delivery.messageId())).isEmpty();
    }

    @Test
    void syntheticLookingAddressWithoutSeedReceiptNeverEntersDemoMailbox() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO account VALUES(?,NULL,NULL,'PATIENT','demo.patient2@example.test',true)", accountId);
        var delivery = store.enqueue(new SecurityMailStore.NewMessage(accountId, "MediFlow", "demo.patient2@example.test",
                "ADMIN_RESET", "reset-no-seed", "Credentials", "Synthetic password", "<p>Synthetic</p>"));
        assertThat(delivery.status()).isEqualTo(com.mediflow.organization.application.dto.response.MailDeliveryDTO.Status.PENDING);
        assertThat(store.demoAccounts()).isEmpty();
        assertThat(store.demoMessages(accountId, PageQuery.of(0, 10)).totalElements()).isZero();
    }

    @Test
    void expiredSmtpLeaseIsReclaimedWithTheSameEncryptedPayload() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO account VALUES(?,NULL,NULL,'PATIENT','real.person@example.test',true)", accountId);
        var delivery = store.enqueue(new SecurityMailStore.NewMessage(accountId, "MediFlow", "real.person@example.test",
                "ADMIN_RESET", "reset-lease", "Credentials", "Same synthetic secret", "<p>Same synthetic secret</p>"));

        var firstClaim = store.claimDue(10);
        assertThat(firstClaim).hasSize(1);
        assertThat(firstClaim.getFirst().content().textBody()).isEqualTo("Same synthetic secret");
        jdbc.update("UPDATE security_mail_message SET lease_until=now()-interval '1 second' WHERE message_id=?", delivery.messageId());

        var afterRestart = store.claimDue(10);
        assertThat(afterRestart).hasSize(1);
        assertThat(afterRestart.getFirst().messageId()).isEqualTo(delivery.messageId());
        assertThat(afterRestart.getFirst().content().textBody()).isEqualTo("Same synthetic secret");
    }

    @Test
    void recoveryRateLimitPersistsAnInstantWindowStartInPostgres() {
        var windowStart = java.time.Instant.parse("2026-11-04T02:30:00Z");

        assertThat(store.incrementRecoveryRequest("a".repeat(64), windowStart)).isEqualTo(1);
        assertThat(store.incrementRecoveryRequest("a".repeat(64), windowStart)).isEqualTo(2);
    }

    @Test
    void patientScopedRetryRequiresTheCurrentRecipientAddress() {
        UUID accountId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO security_mail_message
                    (message_id,operation_key,account_id,sender_email,recipient_email,message_kind,subject,
                     encrypted_content,content_expires_at,demo_mailbox,status,attempts,next_attempt_at,created_at)
                VALUES (?,?,?,?,?,?,?, ?,now()+interval '1 day',false,'FAILED',5,now(),now())
                """, messageId, "failed-patient-credential-" + messageId, accountId,
                "notice@example.test", "current@example.test", "ADMIN_RESET", "Credential update", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> store.retry(accountId, messageId, "old@example.test"))
                .isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM security_mail_message WHERE message_id=?", String.class,
                messageId)).isEqualTo("FAILED");

        var retried = store.retry(accountId, messageId, "current@example.test");

        assertThat(retried.messageId()).isEqualTo(messageId);
        assertThat(retried.status()).isEqualTo(com.mediflow.organization.application.dto.response.MailDeliveryDTO.Status.PENDING);
    }
}
