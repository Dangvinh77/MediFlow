package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.StaffIdentityAuditPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class StaffIdentityAuditRepositoryAdapter implements StaffIdentityAuditPort {
    private final JdbcTemplate jdbc;

    public StaffIdentityAuditRepositoryAdapter(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public void record(UUID staffId, UUID changedByAccountId, String reason, String oldIdentityNumber,
            String newIdentityNumber, Instant changedAt) {
        jdbc.update("""
                INSERT INTO staff_identity_audit
                    (audit_id, staff_id, changed_by_account_id, reason, old_identity_number, new_identity_number, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), staffId, changedByAccountId, reason, oldIdentityNumber, newIdentityNumber,
                Timestamp.from(changedAt));
    }
}
