package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCreationReceiptPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("!test")
public class SurgeryCreationReceiptAdapter implements SurgeryCreationReceiptPort {
    private final JdbcTemplate jdbc;
    public SurgeryCreationReceiptAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<SurgeryCreationOutcome> find(UUID requestId, String fingerprint) {
        return load(requestId, fingerprint, false).map(Claim::outcome);
    }

    @Override public Claim claim(UUID requestId, String fingerprint) {
        requireWrite();
        jdbc.update("""
                INSERT INTO surgery_creation_receipt(surgery_request_id,receipt_id,intent_fingerprint,state)
                VALUES (?,?,?,'PENDING') ON CONFLICT (surgery_request_id) DO NOTHING
                """, requestId, UUID.randomUUID(), fingerprint);
        return load(requestId, fingerprint, true).orElseThrow(SurgeryRevisionConflictException::new);
    }

    private Optional<Claim> load(UUID requestId, String fingerprint, boolean lock) {
        var rows = jdbc.query("SELECT * FROM surgery_creation_receipt WHERE surgery_request_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, ignored) -> {
                    if (!fingerprint.equals(rs.getString("intent_fingerprint"))) throw new SurgeryRevisionConflictException();
                    var outcome = "COMPLETED".equals(rs.getString("state")) ? new SurgeryCreationOutcome(
                            requestId, rs.getObject("surgery_case_id", UUID.class), rs.getObject("checklist_snapshot_id", UUID.class),
                            Instant.parse(rs.getString("completed_at_iso")), false) : null;
                    // A committed PENDING row is not a successful replay or permission to create another case.
                    if (!lock && outcome == null) throw new SurgeryRevisionConflictException();
                    return new Claim(rs.getObject("receipt_id", UUID.class), outcome);
                }, requestId);
        return rows.stream().findFirst();
    }

    @Override public void complete(UUID receiptId, SurgeryCreationOutcome outcome) {
        requireWrite();
        int changed = jdbc.update("""
                UPDATE surgery_creation_receipt SET state='COMPLETED',surgery_case_id=?,checklist_snapshot_id=?,completed_at_iso=?
                WHERE receipt_id=? AND surgery_request_id=? AND state='PENDING'
                    AND EXISTS (SELECT 1 FROM surgery_case c JOIN preop_checklist_snapshot s
                        ON s.surgery_case_id=c.surgery_case_id WHERE c.surgery_case_id=?
                        AND c.surgery_request_id=surgery_creation_receipt.surgery_request_id AND s.checklist_snapshot_id=?)
                """, outcome.surgeryCaseId(), outcome.checklistSnapshotId(), outcome.createdAt().toString(), receiptId, outcome.requestId(),
                outcome.surgeryCaseId(), outcome.checklistSnapshotId());
        if (changed != 1) throw new SurgeryRevisionConflictException();
    }

    private static void requireWrite() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Creation receipt requires the caller's write transaction");
        }
    }
}
