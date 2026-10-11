package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.CloseAdmissionChargesCommand;
import com.mediflow.billing.application.port.out.AdmissionChargeCloseStorePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionChargeCloseStoreAdapter implements AdmissionChargeCloseStorePort {
    private final JdbcTemplate jdbc;

    public AdmissionChargeCloseStoreAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void acceptAndClose(CloseAdmissionChargesCommand command) {
        AdmissionChargeIntakeFence.lock(jdbc, command.admissionId());
        var accounts = jdbc.queryForList("""
                SELECT account_id,patient_id,status FROM billing_account
                 WHERE care_episode_type='ADMISSION' AND care_episode_id=? FOR UPDATE
                """, command.admissionId());
        if (!accounts.isEmpty()) {
            same(command.patientId(), accounts.getFirst().get("patient_id"));
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "billing-medical-delivery:" + command.eventId());
        require(!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM processed_event WHERE event_id=?
                    UNION ALL SELECT 1 FROM prescription_charge_delivery WHERE event_id=?
                    UNION ALL SELECT 1 FROM prescription_terminal_delivery WHERE event_id=?
                    UNION ALL SELECT 1 FROM surgery_charge_delivery WHERE event_id=?)
                """, Boolean.class, command.eventId(), command.eventId(), command.eventId(), command.eventId())));
        jdbc.update("""
                INSERT INTO admission_charge_close(admission_id,patient_id,summary_id,approved_by,approved_at_iso,
                    source_fingerprint,first_event_id,status) VALUES (?,?,?,?,?,?,?,'PENDING_ACCOUNT')
                ON CONFLICT(admission_id) DO NOTHING
                """, command.admissionId(), command.patientId(), command.summaryId(), command.approvedBy(),
                command.approvedAtIso(), command.sourceFingerprint(), command.eventId());
        var source = jdbc.queryForMap("SELECT * FROM admission_charge_close WHERE admission_id=?", command.admissionId());
        same(command.sourceFingerprint(), source.get("source_fingerprint"));
        same(command.patientId(), source.get("patient_id"));
        same(command.summaryId(), source.get("summary_id"));
        same(command.approvedBy(), source.get("approved_by"));
        same(command.approvedAtIso(), source.get("approved_at_iso"));
        jdbc.update("""
                INSERT INTO admission_charge_close_delivery(event_id,admission_id,delivery_fingerprint)
                VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING
                """, command.eventId(), command.admissionId(), command.deliveryFingerprint());
        var delivery = jdbc.queryForMap("SELECT * FROM admission_charge_close_delivery WHERE event_id=?", command.eventId());
        same(command.admissionId(), delivery.get("admission_id"));
        same(command.deliveryFingerprint(), delivery.get("delivery_fingerprint"));
        if (!"PENDING_ACCOUNT".equals(source.get("status")) || accounts.isEmpty()) return;
        var account = accounts.getFirst();
        require("OPEN".equals(account.get("status")));
        // Capture after financial locks; the clinical proof timestamp is retained independently.
        var appliedAt = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        require(appliedAt != null && !appliedAt.toInstant().isBefore(command.approvedAt()));
        jdbc.update("""
                UPDATE billing_account SET status='CHARGE_CLOSED',charge_closed_at=?,version=version+1,updated_at=?
                 WHERE account_id=? AND status='OPEN'
                """, appliedAt, appliedAt, account.get("account_id"));
        jdbc.update("UPDATE admission_charge_close SET status='APPLIED',account_id=?,applied_at=? WHERE admission_id=?",
                account.get("account_id"), appliedAt, command.admissionId());
    }

    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual)); }
    private static void require(boolean valid) {
        if (!valid) throw new BillingRuleException("BILLING_MEDICAL_DISCHARGE_CONFLICT", "Conflicting admission charge-close proof");
    }
}
