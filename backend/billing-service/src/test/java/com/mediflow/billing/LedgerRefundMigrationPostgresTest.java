package com.mediflow.billing;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LedgerRefundMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v7ToV8_preservesCompletedPaymentAndHeldOutboxRejectsOrphanRefundEvidence() {
        migrate("7");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        UUID account = UUID.randomUUID(), original = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'ADMISSION',?,now())
                """, account, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,transaction_type,classification,status,amount,currency,
                    payment_method,idempotency_key,completed_at,actor_account_id)
                VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',100,'VND','CASH','old-payment',now(),?)
                """, original, account, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,contract_version,publication_enabled)
                VALUES (?,'payment.completed',?,'{}',1,false)
                """, UUID.randomUUID(), account);
        String payment = jdbc.queryForObject("SELECT row_to_json(p)::text FROM PAYMENT_TRANSACTION p", String.class);
        String held = jdbc.queryForObject("SELECT row_to_json(p)::text FROM BILLING_EVENT_OUTBOX p", String.class);
        migrate("8");
        assertThat(jdbc.queryForObject("SELECT row_to_json(p)::text FROM PAYMENT_TRANSACTION p", String.class)).isEqualTo(payment);
        assertThat(jdbc.queryForObject("SELECT row_to_json(p)::text FROM BILLING_EVENT_OUTBOX p", String.class)).isEqualTo(held);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ledger_refund_evidence(refund_transaction_id,original_transaction_id,reason,created_at) VALUES (?,?,?,now())",
                UUID.randomUUID(), original, "audit")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=true WHERE contract_version=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private void migrate(String target) { Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).locations("classpath:db/migration").target(target).load().migrate(); }
}
