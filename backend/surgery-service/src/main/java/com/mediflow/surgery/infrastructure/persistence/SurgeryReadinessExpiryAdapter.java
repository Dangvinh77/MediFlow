package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.out.SurgeryReadinessExpiryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryReadinessExpiryAdapter implements SurgeryReadinessExpiryPort {
    private final JdbcTemplate jdbc;
    public SurgeryReadinessExpiryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Candidate> findDue(Instant now, int limit) {
        if (now == null || limit < 1 || limit > 100) throw new IllegalArgumentException("Bounded expiry query required");
        return jdbc.query("""
                SELECT c.surgery_case_id, c.readiness_snapshot_id
                FROM surgery_case c
                JOIN surgery_readiness_snapshot s ON s.readiness_snapshot_id = c.readiness_snapshot_id
                    AND s.surgery_case_id = c.surgery_case_id
                LEFT JOIN surgery_readiness_expiry_retry r ON r.surgery_case_id = c.surgery_case_id
                    AND r.readiness_snapshot_id = c.readiness_snapshot_id
                WHERE c.status IN ('READY', 'SCHEDULED') AND s.valid_until <= ?
                    AND (r.next_attempt_at IS NULL OR r.next_attempt_at <= ?)
                ORDER BY s.valid_until, c.surgery_case_id LIMIT ?
                """, (rs, ignored) -> new Candidate(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                Timestamp.from(now), Timestamp.from(now), limit);
    }

    @Override public void deferIfStillDue(Candidate candidate, Instant now, String failureCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Expiry retry needs a transaction");
        if (candidate == null || now == null || failureCode == null || !failureCode.matches("[A-Za-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Safe expiry retry identity is required");
        }
        // Serialize with case mutation before checking whether the original candidate still applies.
        jdbc.query("SELECT surgery_case_id FROM surgery_case WHERE surgery_case_id = ? FOR UPDATE",
                (rs, ignored) -> rs.getObject(1, UUID.class), candidate.surgeryCaseId());
        jdbc.update("""
                INSERT INTO surgery_readiness_expiry_retry
                    (surgery_case_id, readiness_snapshot_id, attempt_count, next_attempt_at, failure_code, updated_at)
                SELECT c.surgery_case_id, c.readiness_snapshot_id, 1, CAST(? AS timestamptz) + interval '5 seconds', ?, ?
                FROM surgery_case c JOIN surgery_readiness_snapshot s
                    ON s.readiness_snapshot_id = c.readiness_snapshot_id AND s.surgery_case_id = c.surgery_case_id
                WHERE c.surgery_case_id = ? AND c.readiness_snapshot_id = ?
                    AND c.status IN ('READY', 'SCHEDULED') AND s.valid_until <= ?
                ON CONFLICT (surgery_case_id) DO UPDATE SET
                    readiness_snapshot_id = EXCLUDED.readiness_snapshot_id,
                    attempt_count = CASE WHEN surgery_readiness_expiry_retry.readiness_snapshot_id = EXCLUDED.readiness_snapshot_id
                        THEN LEAST(1000000, surgery_readiness_expiry_retry.attempt_count + 1) ELSE 1 END,
                    next_attempt_at = EXCLUDED.updated_at + make_interval(secs => CASE
                        WHEN surgery_readiness_expiry_retry.readiness_snapshot_id = EXCLUDED.readiness_snapshot_id
                        THEN LEAST(300, 5 * power(2, LEAST(6, surgery_readiness_expiry_retry.attempt_count))) ELSE 5 END),
                    failure_code = EXCLUDED.failure_code, updated_at = EXCLUDED.updated_at
                """, Timestamp.from(now), failureCode, Timestamp.from(now), candidate.surgeryCaseId(),
                candidate.readinessSnapshotId(), Timestamp.from(now));
    }
}
