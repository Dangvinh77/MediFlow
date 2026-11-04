package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.AccountEmailChangeStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

@Repository
public class AccountEmailChangeRepositoryAdapter implements AccountEmailChangeStore {
    private final JdbcTemplate jdbc;
    public AccountEmailChangeRepositoryAdapter(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override public void record(UUID accountId, UUID changedBy, String oldEmail, String newEmail, String reason) {
        jdbc.update("""
                INSERT INTO account_email_audit(audit_id,account_id,changed_by_account_id,old_email,new_email,reason,changed_at)
                VALUES (?,?,?,?,?,?,now())
                """, UUID.randomUUID(), accountId, changedBy, oldEmail, newEmail, reason);
    }

    @Override public void queuePatientProfileSync(UUID accountId, UUID patientId, String email) {
        jdbc.update("""
                INSERT INTO account_patient_email_sync(sync_id,account_id,patient_id,target_email,status,attempts,next_attempt_at,created_at)
                VALUES (?,?,?,?,'PENDING',0,now(),now())
                ON CONFLICT(account_id,target_email) DO UPDATE SET status='PENDING',attempts=0,
                    next_attempt_at=now(),lease_until=NULL,completed_at=NULL,last_failure_code=NULL
                """, UUID.randomUUID(), accountId, patientId, email);
    }

    @Override public List<PatientEmailSync> claimPatientProfileSyncs(int limit) {
        return jdbc.query("""
                WITH batch AS (
                    SELECT sync_id FROM account_patient_email_sync
                    WHERE ((status='PENDING' AND next_attempt_at<=now())
                        OR (status='SENDING' AND lease_until<now()))
                      AND (lease_until IS NULL OR lease_until<now())
                    ORDER BY created_at,sync_id FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE account_patient_email_sync s SET status='SENDING',lease_until=now()+interval '90 seconds'
                FROM batch WHERE s.sync_id=batch.sync_id
                RETURNING s.sync_id,s.account_id,s.patient_id,s.target_email
                """, (rs, index) -> new PatientEmailSync(rs.getObject("sync_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("target_email")), limit);
    }

    @Override public void completePatientProfileSync(UUID syncId) {
        jdbc.update("""
                UPDATE account_patient_email_sync SET status='SENT',completed_at=now(),lease_until=NULL
                WHERE sync_id=? AND status='SENDING'""", syncId);
    }

    @Override public void retryPatientProfileSync(UUID syncId) {
        jdbc.update("""
                UPDATE account_patient_email_sync SET status='PENDING',attempts=attempts+1,
                    next_attempt_at=now()+(least(3600,greatest(30,attempts*30))*interval '1 second'),
                    lease_until=NULL,last_failure_code='PATIENT_PROFILE_SYNC_FAILED'
                WHERE sync_id=? AND status='SENDING'
                """, syncId);
    }
}
