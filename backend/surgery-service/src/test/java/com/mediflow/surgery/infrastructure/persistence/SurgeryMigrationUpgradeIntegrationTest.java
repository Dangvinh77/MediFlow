package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.support.SurgeryScheduledFixture;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SurgeryMigrationUpgradeIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @ParameterizedTest(name = "upgrade existing V{0} to V6 preserves all prior rows and retry bytes")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void migrate_existingData_preservesRowsAndDoesNotCreateAuthorityOrJobs(int baseline) {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var initial = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).defaultSchema(schema).target(Integer.toString(baseline)).load();
        initial.migrate();
        var dataSource = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        dataSource.setConnectionProperties(properties(schema));
        var db = new JdbcTemplate(dataSource);
        Instant now = Instant.now();
        var fixture = SurgeryScheduledFixture.seed(db, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                now.plusSeconds(600), now.plusSeconds(1200));
        byte[] retained = "{\"testOnly\":\"retained bytes\"}".getBytes(StandardCharsets.UTF_8);
        db.update("""
                INSERT INTO surgery_command_receipt(receipt_id,surgery_case_id,actor_scope,command_code,idempotency_key,
                    fingerprint,status,response_payload,applied_at) VALUES (?,?,'test','TEST_ONLY','upgrade',?,'APPLIED',?,?)
                """, UUID.randomUUID(), fixture.caseId(), "a".repeat(64), retained, Timestamp.from(now));
        db.update("""
                INSERT INTO surgery_inbox(event_id,event_type,event_version,system_producer,fingerprint,semantic_key,
                    payload,status,attempt_count,next_attempt_at) VALUES (?,'test.only',1,'test',?,'upgrade',?,'PENDING',2,?)
                """, UUID.randomUUID(), "b".repeat(64), retained, Timestamp.from(now.plusSeconds(60)));
        db.update("""
                INSERT INTO surgery_outbox(event_id,surgery_case_id,aggregate_revision,sequence_no,event_type,event_version,
                    correlation_id,payload,status,attempt_count,next_attempt_at) VALUES (?,?,3,0,'test.only',1,'upgrade',?,'PENDING',3,?)
                """, UUID.randomUUID(), fixture.caseId(), retained, Timestamp.from(now.plusSeconds(120)));
        if (baseline >= 2) {
            db.update("""
                    INSERT INTO surgery_financial_clearance(clearance_id,invoice_id,account_id,patient_id,surgery_case_id,
                        episode_type,episode_id,amount,currency,payment_method,granted_at,granted_at_iso,expires_at_iso,fingerprint)
                    SELECT ?,?,?,patient_id,surgery_case_id,episode_type,episode_id,100,'VND','CASH',?,?,?,?
                    FROM surgery_case WHERE surgery_case_id=?
                    """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(now),
                    now.toString(), now.plusSeconds(3600).toString(), "c".repeat(64), fixture.caseId());
        }
        if (baseline >= 4) {
            db.update("""
                    INSERT INTO surgery_readiness_expiry_retry(surgery_case_id,readiness_snapshot_id,attempt_count,
                        next_attempt_at,failure_code,updated_at) VALUES (?,?,3,?,'TestFailure',?)
                    """,
                    fixture.caseId(), fixture.snapshotId(), Timestamp.from(now.plusSeconds(120)), Timestamp.from(now));
        }
        Map<String, List<String>> before = snapshot(db);
        var current = Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .schemas(schema).defaultSchema(schema).load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(6 - baseline);
        for (var table : before.entrySet()) {
            assertThat(rows(db, table.getKey())).as("unchanged existing %s", table.getKey()).isEqualTo(table.getValue());
        }
        assertThat(db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?", String.class, fixture.caseId()))
                .isEqualTo("SCHEDULED");
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation", String.class)).containsExactly("RESERVED", "RESERVED");
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_authority_change", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_authority_invalidation", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_lifecycle_intent", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_readiness_precision", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT payload FROM surgery_inbox", byte[].class)).containsExactly(retained);
        assertThat(current.validateWithResult().validationSuccessful).isTrue();
        assertThat(current.migrate().migrationsExecuted).isZero();
        for (var table : before.entrySet()) assertThat(rows(db, table.getKey())).isEqualTo(table.getValue());
    }

    private static java.util.Properties properties(String schema) {
        var properties = new java.util.Properties(); properties.setProperty("currentSchema", schema); return properties;
    }
    private static Map<String, List<String>> snapshot(JdbcTemplate db) {
        var result = new LinkedHashMap<String, List<String>>();
        for (String table : db.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=current_schema() AND tablename <> 'flyway_schema_history' ORDER BY tablename", String.class)) {
            result.put(table, rows(db, table));
        }
        return result;
    }
    private static List<String> rows(JdbcTemplate db, String table) {
        // Names originate only from this isolated schema's catalog, never a request.
        if (!table.matches("[a-z_]+")) throw new IllegalArgumentException("Unexpected test table");
        return db.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY to_jsonb(t)::text", String.class);
    }
}
