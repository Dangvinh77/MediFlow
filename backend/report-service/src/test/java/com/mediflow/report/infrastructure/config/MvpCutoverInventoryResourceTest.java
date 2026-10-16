package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MvpCutoverInventoryResourceTest {
    @Test void operatorSnapshotIsBoundedReadOnlyAndDoesNotPublishOrRevealSourcePayloads() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/operations/mvp-cutover-inventory.sql")) {
            assertThat(input).isNotNull(); sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String statements = sql.replaceAll("(?m)--.*$", "").trim();
        assertThat(statements).startsWith("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;")
                .endsWith("ROLLBACK;").contains("statement_timeout", "lock_timeout", "refund_state", "fingerprint_present", "coverage_record_count");
        assertThat(statements).doesNotContainPattern("(?i)\\b(INSERT|UPDATE|DELETE|TRUNCATE|ALTER|DROP|CREATE|COMMIT|COPY|CALL|DO)\\b")
                .doesNotContain("event_snapshot", "source_references", "patient_id", "account_id", "SELECT *", "notification");
        for (String statement : statements.split(";")) {
            if (!statement.isBlank()) assertThat(statement.stripLeading()).matches("(?s)(BEGIN TRANSACTION|SET LOCAL|SELECT|ROLLBACK).*?");
        }
    }
}
