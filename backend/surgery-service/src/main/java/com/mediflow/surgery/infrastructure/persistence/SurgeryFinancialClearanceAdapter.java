package com.mediflow.surgery.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;

@Repository @Profile("!test")
@Transactional(propagation = Propagation.MANDATORY)
public class SurgeryFinancialClearanceAdapter implements SurgeryFinancialClearanceRepositoryPort {
    private final JdbcTemplate jdbc;
    public SurgeryFinancialClearanceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public SaveDecision saveIfAbsentAndMatching(SurgeryFinancialClearance clearance) {
        int inserted = jdbc.update("""
                INSERT INTO surgery_financial_clearance(clearance_id,invoice_id,account_id,patient_id,surgery_case_id,
                    episode_type,episode_id,admission_id,amount,currency,payment_method,granted_at,granted_at_iso,expires_at_iso,fingerprint)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(clearance_id) DO NOTHING
                """, clearance.clearanceId(), clearance.invoiceId(), clearance.accountId(), clearance.patientId(), clearance.surgeryCaseId(),
                clearance.episodeType().name(), clearance.episodeId(), clearance.admissionId(), clearance.amount(), clearance.currency(),
                clearance.paymentMethod(), Timestamp.from(clearance.grantedAt()), clearance.grantedAt().toString(),
                clearance.expiresAt() == null ? null : clearance.expiresAt().toString(), clearance.fingerprint());
        if (inserted == 1) return SaveDecision.CREATED;
        var previous = lockById(clearance.clearanceId()).orElseThrow();
        return previous.fingerprint().equals(clearance.fingerprint()) ? SaveDecision.MATCHING : SaveDecision.CONFLICT;
    }
    @Override public Optional<SurgeryFinancialClearance> lockById(UUID id) {
        return read(id, true);
    }
    @Override @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<SurgeryFinancialClearance> findById(UUID id) { return read(id, false); }

    private Optional<SurgeryFinancialClearance> read(UUID id, boolean locked) {
        return jdbc.query("SELECT * FROM surgery_financial_clearance WHERE clearance_id=?" + (locked ? " FOR UPDATE" : ""), (rs, row) ->
                new SurgeryFinancialClearance(rs.getObject("clearance_id", UUID.class), rs.getObject("invoice_id", UUID.class),
                        rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getObject("surgery_case_id", UUID.class),
                        CareEpisodeType.valueOf(rs.getString("episode_type")), rs.getObject("episode_id", UUID.class),
                        rs.getObject("admission_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("currency").trim(),
                        rs.getString("payment_method"), java.time.Instant.parse(rs.getString("granted_at_iso")),
                        rs.getString("expires_at_iso") == null ? null : java.time.Instant.parse(rs.getString("expires_at_iso")),
                        rs.getString("fingerprint")), id).stream().findFirst();
    }
}
