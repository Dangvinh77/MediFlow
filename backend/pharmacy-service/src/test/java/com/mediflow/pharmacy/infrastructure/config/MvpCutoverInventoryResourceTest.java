package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MvpCutoverInventoryResourceTest {
    @Test void operatorSnapshotIsBoundedReadOnlyAndDoesNotSelectSensitivePayloadsOrIdentifiers() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/operations/mvp-cutover-inventory.sql")) {
            assertThat(input).isNotNull(); sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String statements = sql.replaceAll("(?m)--.*$", "").trim();
        assertThat(statements).startsWith("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;")
                .endsWith("ROLLBACK;").contains("statement_timeout", "lock_timeout", "delivery_enabled", "'HELD'", "'UNKNOWN'");
        assertThat(statements).doesNotContainPattern("(?i)\\b(INSERT|UPDATE|DELETE|TRUNCATE|ALTER|DROP|CREATE|COMMIT|COPY|CALL|DO)\\b")
                .doesNotContain("payload", "patient_id", "invoice_id", "SELECT *", "lease_owner AS", "lease_token AS");
        for (String statement : statements.split(";")) {
            if (!statement.isBlank()) assertThat(statement.stripLeading()).matches("(?s)(BEGIN TRANSACTION|SET LOCAL|SELECT|ROLLBACK).*?");
        }
    }
}
