package com.mediflow.pharmacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Test-owned malformed/unknown aggregate evidence must stay HELD, never be inferred as outpatient. */
@Testcontainers(disabledWithoutDocker = true)
class MvpCutoverInventoryPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void inventoryCompilesOnMigratedStoreAndPreservesHeldBytesAndLeaseFence() throws Exception {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        UUID event = UUID.randomUUID(), token = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pharmacy_event_outbox(event_id,routing_key,aggregate_id,payload,delivery_enabled,
                    care_contract_version,care_lifecycle_order)
                VALUES (?,'prescription.created',?,'{"fixture":"ISOLATED_TEST_ONLY"}',false,1,0)
                """, event, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO pharmacy_scheduler_lease(job_name,lease_owner,lease_until,updated_at,lease_token)
                VALUES ('ISOLATED_TEST_ONLY','TEST_OWNER',now()+interval '5 minutes',now(),?)
                """, token);
        var held = jdbc.queryForMap("SELECT * FROM pharmacy_event_outbox WHERE event_id=?", event);
        var fence = jdbc.queryForMap("SELECT * FROM pharmacy_scheduler_lease WHERE job_name='ISOLATED_TEST_ONLY'");
        String script;
        try (var input = getClass().getResourceAsStream("/operations/mvp-cutover-inventory.sql")) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
        }
        int results = 0;
        boolean heldUnknown = false;
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (sql.isBlank() || !statement.execute(sql)) continue;
                results++;
                try (var rows = statement.getResultSet()) {
                    if (results == 1) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getString("read_only")).isEqualTo("on");
                        assertThat(rows.getString("isolation_level")).isEqualTo("repeatable read");
                    } else if (results == 2) {
                        while (rows.next()) {
                            heldUnknown |= "HELD".equals(rows.getString("delivery_state"))
                                    && "UNKNOWN".equals(rows.getString("care_context")) && rows.getLong("event_count") == 1;
                        }
                    }
                }
            }
        }
        assertThat(results).isEqualTo(5);
        assertThat(heldUnknown).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM pharmacy_event_outbox WHERE event_id=?", event)).isEqualTo(held);
        assertThat(jdbc.queryForMap("SELECT * FROM pharmacy_scheduler_lease WHERE job_name='ISOLATED_TEST_ONLY'")).isEqualTo(fence);
    }
}
