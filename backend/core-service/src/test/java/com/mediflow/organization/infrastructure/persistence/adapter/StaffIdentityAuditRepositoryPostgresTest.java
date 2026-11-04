package com.mediflow.organization.infrastructure.persistence.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class StaffIdentityAuditRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;
    private StaffIdentityAuditRepositoryAdapter audit;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS staff_identity_audit(
                    audit_id UUID PRIMARY KEY,staff_id UUID NOT NULL,changed_by_account_id UUID NOT NULL,
                    reason VARCHAR(500) NOT NULL,old_identity_number VARCHAR(12) NOT NULL,
                    new_identity_number VARCHAR(12) NOT NULL,changed_at TIMESTAMPTZ NOT NULL)
                """);
        jdbc.update("DELETE FROM staff_identity_audit");
        audit = new StaffIdentityAuditRepositoryAdapter(dataSource);
    }

    @Test
    void recordsIdentityCorrectionTimestampInPostgres() {
        UUID staffId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant changedAt = Instant.parse("2026-11-04T02:30:00Z");

        audit.record(staffId, adminId, "Corrected from verified document", "012345678901", "012345678902", changedAt);

        assertThat(jdbc.queryForObject("SELECT changed_at FROM staff_identity_audit WHERE staff_id=?", Timestamp.class, staffId)
                .toInstant()).isEqualTo(changedAt);
    }
}
