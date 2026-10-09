package com.mediflow.billing;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SurgeryCancellationMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v9ToV10_preservesCompletedMoneyReconciledHistoryAndHeldFence_withoutInventedCancellations() {
        migrate("9");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        UUID account=UUID.randomUUID(), patient=UUID.randomUUID(), department=UUID.randomUUID();
        jdbc.update("INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at) VALUES (?,?,?,'ADMISSION',?,now())",
                account,patient,department,UUID.randomUUID());
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,reconciled_result_id,incurred_at)
                VALUES (?,?,?,?,'SURGERY',?,'OLD_PRICE','Historic care',1.0001,100,100.01,?,now())
                """,UUID.randomUUID(),account,patient,department,UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,transaction_type,classification,status,
                    amount,currency,payment_method,idempotency_key,completed_at,actor_account_id)
                VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',100,'VND','CASH','historic-payment',now(),?)
                """,UUID.randomUUID(),account,UUID.randomUUID());
        jdbc.update("INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,contract_version,publication_enabled) VALUES (?,'payment.completed',?,'{}',1,false)",UUID.randomUUID(),account);
        var charge=jdbc.queryForObject("SELECT row_to_json(c)::text FROM CHARGE c",String.class);
        var payment=jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t",String.class);
        var held=jdbc.queryForObject("SELECT row_to_json(o)::text FROM BILLING_EVENT_OUTBOX o",String.class);
        migrate("10");
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM CHARGE c",String.class)).isEqualTo(charge);
        assertThat(jdbc.queryForObject("SELECT row_to_json(t)::text FROM PAYMENT_TRANSACTION t",String.class)).isEqualTo(payment);
        assertThat(jdbc.queryForObject("SELECT row_to_json(o)::text FROM BILLING_EVENT_OUTBOX o",String.class)).isEqualTo(held);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_cancellation_source",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_cancellation_refund_due",Long.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=true WHERE contract_version=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO surgery_cancellation_refund_due(cancellation_id,original_transaction_id,charge_id,account_id,amount,currency) VALUES (?,?,?,?,1,'VND')",
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),account)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private void migrate(String target) { Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).locations("classpath:db/migration").target(target).load().migrate(); }
}
