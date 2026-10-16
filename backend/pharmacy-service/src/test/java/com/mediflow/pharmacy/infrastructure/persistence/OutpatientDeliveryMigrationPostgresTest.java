package com.mediflow.pharmacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Upgrade a test-owned V19 database with pending evidence; never touch operator databases. */
@Testcontainers
class OutpatientDeliveryMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void upgradePreservesHistoricalPayloadsDeliveryStatesLeasesAndOriginalChecksums() {
        var data=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
        Flyway.configure().dataSource(data).locations("classpath:db/migration").target("19").load().migrate();
        var jdbc=new JdbcTemplate(data);UUID held=UUID.randomUUID(),legacy=UUID.randomUUID(),fence=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pharmacy_event_outbox(event_id,routing_key,aggregate_id,payload,delivery_enabled,
                    care_contract_version,care_lifecycle_order)
                VALUES (?,'prescription.created',?,'{"immutable":"historical-held"}',false,1,0)
                """,held,UUID.randomUUID());
        jdbc.update("""
                INSERT INTO pharmacy_event_outbox(event_id,routing_key,aggregate_id,payload,attempts,last_error,
                    available_at,locked_at,locked_by,quarantined_at)
                VALUES (?,'prescription.created',?,'{"immutable":"legacy-pending"}',2,'isolated failure',
                    now()+interval '1 hour',now(),'isolated-owner',now())
                """,legacy,UUID.randomUUID());
        jdbc.update("""
                INSERT INTO pharmacy_scheduler_lease(job_name,lease_owner,lease_until,updated_at,lease_token)
                VALUES ('ISOLATED_V20_UPGRADE','isolated-owner',now()+interval '1 hour',now(),?)
                """,fence);
        String projection="SELECT event_id,routing_key,aggregate_id,payload,created_at,published_at,attempts,last_error,available_at,locked_at,locked_by,quarantined_at,delivery_enabled,care_contract_version,care_lifecycle_order FROM pharmacy_event_outbox ORDER BY event_id";
        var before=jdbc.queryForList(projection);
        var lease=jdbc.queryForMap("SELECT * FROM pharmacy_scheduler_lease WHERE job_name='ISOLATED_V20_UPGRADE'");
        var checksums=jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
        var latest=Flyway.configure().dataSource(data).locations("classpath:db/migration").load();
        assertThat(latest.migrate().migrationsExecuted).isOne();latest.validate();
        assertThat(jdbc.queryForList(projection)).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM pharmacy_scheduler_lease WHERE job_name='ISOLATED_V20_UPGRADE'")).isEqualTo(lease);
        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success AND version<>'20' ORDER BY installed_rank")).isEqualTo(checksums);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_outpatient_delivery_cohort",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_delivery_cohort_id IS NOT NULL",Long.class)).isZero();
        assertThatThrownBy(()->jdbc.update("UPDATE pharmacy_event_outbox SET delivery_enabled=true WHERE event_id=?",held))
                .hasMessageContaining("historical HELD");
    }
}
