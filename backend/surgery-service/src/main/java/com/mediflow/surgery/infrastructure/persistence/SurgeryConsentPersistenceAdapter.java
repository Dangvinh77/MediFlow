package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryConsentAction;
import com.mediflow.surgery.domain.model.SurgeryConsentAuditEntry;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryConsentPersistenceAdapter implements SurgeryConsentRepositoryPort {

    private final SurgeryCaseJpaRepository cases;
    private final JdbcTemplate jdbc;

    public SurgeryConsentPersistenceAdapter(SurgeryCaseJpaRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    @Override
    public List<SurgeryConsentRecord> findByCaseId(UUID caseId) {
        if (caseId == null) return List.of();
        List<UUID> consentIds = jdbc.query("""
                SELECT consent_id FROM surgery_consent
                WHERE surgery_case_id = ? ORDER BY consent_type, consent_id
                """, (rs, ignored) -> rs.getObject("consent_id", UUID.class), caseId);
        return consentIds.stream().map(this::load).flatMap(Optional::stream).toList();
    }

    @Override
    public Optional<SurgeryConsentRecord> findById(UUID consentId) {
        if (consentId == null) return Optional.empty();
        List<SurgeryConsentRecord> found = jdbc.query("""
                SELECT consent_id, surgery_case_id, consent_type, signer_id, signer_type,
                       evidence_document_id, status, signed_at, revoked_at, revocation_reason
                FROM surgery_consent WHERE consent_id = ?
                """, (rs, ignored) -> toDomain(rs), consentId);
        return found.stream().findFirst();
    }

    @Override
    public SurgeryConsentRecord save(SurgeryConsentRecord consent) {
        requireTransaction();
        if (consent == null) throw new IllegalArgumentException("Consent is required");
        cases.lockById(consent.surgeryCaseId()).orElseThrow(SurgeryRevisionConflictException::new);
        if (consent.auditHistory().size() == 1) return saveSigned(consent);
        if (consent.auditHistory().size() == 2) return saveRevocation(consent);
        throw new IllegalArgumentException("V1 consent must contain one signature and at most one revocation");
    }

    private SurgeryConsentRecord saveSigned(SurgeryConsentRecord consent) {
        Optional<SurgeryConsentRecord> existing = findById(consent.consentId());
        if (existing.isPresent()) {
            if (!existing.get().equals(consent)) throw new SurgeryRevisionConflictException();
            return existing.get();
        }
        SurgeryConsentAuditEntry signed = consent.auditHistory().getFirst();
        jdbc.update("""
                INSERT INTO surgery_consent
                (consent_id, surgery_case_id, consent_type, signer_id, signer_type,
                 evidence_document_id, status, signed_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, consent.consentId(), consent.surgeryCaseId(), consent.consentType().name(),
                consent.signerId(), consent.signerType().name(), consent.evidenceDocumentId(),
                Timestamp.from(signed.occurredAt()));
        appendAudit(consent.consentId(), 0, signed);
        return consent;
    }

    private SurgeryConsentRecord saveRevocation(SurgeryConsentRecord consent) {
        Optional<SurgeryConsentRecord> current = findById(consent.consentId());
        if (current.isEmpty()) throw new SurgeryRevisionConflictException();
        if (current.get().equals(consent)) return current.get();
        SurgeryConsentRecord signed = current.get();
        if (!signed.isActive() || !sameSignature(signed, consent)
                || !signed.auditHistory().getFirst().equals(consent.auditHistory().getFirst())) {
            throw new SurgeryRevisionConflictException();
        }
        SurgeryConsentAuditEntry revoked = consent.auditHistory().getLast();
        int updated = jdbc.update("""
                UPDATE surgery_consent SET status = 'REVOKED', revoked_at = ?, revocation_reason = ?
                WHERE consent_id = ? AND surgery_case_id = ? AND status = 'ACTIVE'
                """, Timestamp.from(revoked.occurredAt()), revoked.reason(),
                consent.consentId(), consent.surgeryCaseId());
        if (updated != 1) throw new SurgeryRevisionConflictException();
        appendAudit(consent.consentId(), 1, revoked);
        return consent;
    }

    private boolean sameSignature(SurgeryConsentRecord left, SurgeryConsentRecord right) {
        return left.consentId().equals(right.consentId())
                && left.surgeryCaseId().equals(right.surgeryCaseId())
                && left.consentType() == right.consentType()
                && left.signerId().equals(right.signerId())
                && left.signerType() == right.signerType()
                && java.util.Objects.equals(left.evidenceDocumentId(), right.evidenceDocumentId());
    }

    private void appendAudit(UUID consentId, int sequence, SurgeryConsentAuditEntry entry) {
        SurgeryAuditActor actor = entry.recordedBy();
        jdbc.update("""
                INSERT INTO surgery_consent_history
                (consent_id, sequence_no, action, actor_type, account_id, staff_id,
                 system_producer, occurred_at, correlation_id, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, consentId, sequence, entry.action().name(), actor.actorType().name(),
                actor.accountId(), actor.verifiedStaffId(), actor.systemProducer(),
                Timestamp.from(entry.occurredAt()), entry.correlationId(), entry.reason());
    }

    private Optional<SurgeryConsentRecord> load(UUID consentId) {
        return findById(consentId);
    }

    private SurgeryConsentRecord toDomain(ResultSet rs) throws SQLException {
        UUID consentId = rs.getObject("consent_id", UUID.class);
        List<SurgeryConsentAuditEntry> history = jdbc.query("""
                SELECT action, actor_type, account_id, staff_id, system_producer,
                       occurred_at, correlation_id, reason
                FROM surgery_consent_history WHERE consent_id = ? ORDER BY sequence_no
                """, (audit, ignored) -> new SurgeryConsentAuditEntry(
                SurgeryConsentAction.valueOf(audit.getString("action")),
                actor(audit), audit.getTimestamp("occurred_at").toInstant(),
                audit.getString("correlation_id"), audit.getString("reason")), consentId);
        SurgeryConsentRecord consent = new SurgeryConsentRecord(consentId,
                rs.getObject("surgery_case_id", UUID.class),
                SurgeryConsentType.valueOf(rs.getString("consent_type")),
                rs.getObject("signer_id", UUID.class),
                SurgeryConsentSignerType.valueOf(rs.getString("signer_type")),
                rs.getObject("evidence_document_id", UUID.class), history);
        boolean active = "ACTIVE".equals(rs.getString("status"));
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        String revocationReason = rs.getString("revocation_reason");
        if (consent.isActive() != active
                || !consent.signedAt().equals(rs.getTimestamp("signed_at").toInstant())
                || (active && (revokedAt != null || revocationReason != null))
                || (!active && (revokedAt == null
                    || !consent.auditHistory().getLast().occurredAt().equals(revokedAt.toInstant())
                    || !java.util.Objects.equals(consent.auditHistory().getLast().reason(), revocationReason)))) {
            throw new IllegalStateException("Stored consent row disagrees with its append-only audit history");
        }
        return consent;
    }

    private static SurgeryAuditActor actor(ResultSet rs) throws SQLException {
        return new SurgeryAuditActor(SurgeryActorType.valueOf(rs.getString("actor_type")),
                rs.getObject("account_id", UUID.class), rs.getObject("staff_id", UUID.class),
                rs.getString("system_producer"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Consent mutation requires an application transaction");
        }
    }
}
