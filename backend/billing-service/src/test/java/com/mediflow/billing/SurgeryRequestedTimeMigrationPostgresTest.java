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
class SurgeryRequestedTimeMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v10ToV11_doesNotReconstructLostNanosOrModifyHistoricRequest() {
        migrate("10");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        UUID account=UUID.randomUUID(), request=UUID.randomUUID();
        jdbc.update("INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at) VALUES (?,?,?,'ADMISSION',?,now())",account,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount) VALUES (?,?,?,'SURGERY',100)",request,UUID.randomUUID(),account);
        jdbc.update("INSERT INTO surgery_charge_source(surgery_case_id,surgery_request_id,source_fingerprint,payment_request_id,requested_at) VALUES (?,?,?,?,?::timestamptz)",UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),request,"2026-10-07T01:00:00.123456789Z");
        var prior=jdbc.queryForObject("SELECT to_jsonb(s)::text FROM surgery_charge_source s",String.class);
        var money=jdbc.queryForObject("SELECT to_jsonb(r)::text FROM PAYMENT_REQUEST r",String.class);
        migrate("11");
        assertThat(jdbc.queryForObject("SELECT requested_at_iso FROM surgery_charge_source",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT (to_jsonb(s)-'requested_at_iso')::text FROM surgery_charge_source s",String.class)).isEqualTo(prior);
        assertThat(jdbc.queryForObject("SELECT to_jsonb(r)::text FROM PAYMENT_REQUEST r",String.class)).isEqualTo(money);
    }
    private void migrate(String target) { Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).locations("classpath:db/migration").target(target).load().migrate(); }
}
