package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.PatientAccountSecurityAuditStore;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class PatientAccountSecurityAuditStoreAdapter implements PatientAccountSecurityAuditStore {
    private final JdbcTemplate jdbc;

    public PatientAccountSecurityAuditStoreAdapter(
            @Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public void record(Action action, UUID actorAccountId, UUID patientId, UUID targetAccountId,
            UUID correlationId, String operationKey) {
        jdbc.update("""
                INSERT INTO patient_account_security_audit
                    (audit_id,actor_account_id,patient_id,target_account_id,operation,correlation_id,operation_key,occurred_at)
                VALUES (?,?,?,?,?,?,?,now())
                """, UUID.randomUUID(), actorAccountId, patientId, targetAccountId,
                action.name(), correlationId, operationKey);
    }
}
