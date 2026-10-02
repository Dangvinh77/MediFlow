package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.PrescriptionClearancePort;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.CareEpisode;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

/** Immutable semantic grant plus event ledger; claims and pending/verified state commit together. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionClearancePersistenceAdapter implements PrescriptionClearancePort {
    private final JdbcTemplate jdbc;

    public PrescriptionClearancePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(UUID eventId, String eventFingerprint, PrescriptionClearance grant) {
        String snapshotFingerprint = snapshotHash(grant);
        int inserted = jdbc.update("""
                INSERT INTO prescription_clearance_event(event_id, event_fingerprint, snapshot_fingerprint)
                VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventFingerprint, snapshotFingerprint);
        var row = jdbc.queryForMap("""
                SELECT event_fingerprint, snapshot_fingerprint FROM prescription_clearance_event WHERE event_id = ?
                """, eventId);
        if (!eventFingerprint.equals(row.get("event_fingerprint"))
                || !snapshotFingerprint.equals(row.get("snapshot_fingerprint"))) {
            throw conflict("Event identity reused with a different clearance snapshot");
        }
        return inserted == 1;
    }

    @Override
    public void lockTarget(UUID prescriptionId) {
        jdbc.update("""
                INSERT INTO prescription_clearance_target(prescription_id) VALUES (?)
                ON CONFLICT (prescription_id) DO NOTHING
                """, prescriptionId);
        jdbc.queryForObject("""
                SELECT prescription_id FROM prescription_clearance_target WHERE prescription_id = ? FOR UPDATE
                """, UUID.class, prescriptionId);
    }

    @Override
    public void store(PrescriptionClearance grant, boolean targetVerified) {
        // Cast source strings instead of Timestamp to keep PostgreSQL rounding identical to its
        // constraint casts. Exact nanoseconds are retained independently for replay/comparison.
        jdbc.update("""
                INSERT INTO prescription_clearance(clearance_id, invoice_id, account_id, prescription_id,
                    patient_id, purpose, care_episode_type, care_episode_id, amount, currency, payment_method,
                    granted_at, source_granted_at, expires_at, source_expires_at, payload_fingerprint, target_status)
                VALUES (?, ?, ?, ?, ?, 'PRESCRIPTION', 'OUTPATIENT_VISIT', ?, ?, ?, ?,
                    CAST(? AS timestamptz), ?, CAST(? AS timestamptz), ?, ?, ?)
                ON CONFLICT (clearance_id) DO NOTHING
                """, grant.clearanceId(), grant.invoiceId(), grant.accountId(), grant.prescriptionId(),
                grant.patientId(), grant.episode().id(), grant.amount(), grant.currency(), grant.paymentMethod(),
                grant.grantedAt().toString(), grant.grantedAt().toString(),
                grant.expiresAt() == null ? null : grant.expiresAt().toString(),
                grant.expiresAt() == null ? null : grant.expiresAt().toString(), grant.payloadFingerprint(),
                targetVerified ? "VERIFIED" : "PENDING");
        var existing = jdbc.query("SELECT * FROM prescription_clearance WHERE clearance_id = ? FOR UPDATE",
                this::read, grant.clearanceId()).get(0);
        if (!grant.equals(existing)) {
            throw conflict("Clearance identity reused with a different target or payload");
        }
        if (targetVerified) {
            markVerified(grant.clearanceId());
        }
    }

    @Override
    public List<PrescriptionClearance> findByTargetForUpdate(UUID prescriptionId) {
        return jdbc.query("""
                SELECT * FROM prescription_clearance WHERE prescription_id = ? ORDER BY clearance_id FOR UPDATE
                """, this::read, prescriptionId);
    }

    @Override
    public void markVerified(UUID clearanceId) {
        int updated = jdbc.update("UPDATE prescription_clearance SET target_status = 'VERIFIED' WHERE clearance_id = ?",
                clearanceId);
        if (updated != 1) {
            throw conflict("Clearance disappeared during authorization");
        }
    }

    private PrescriptionClearance read(ResultSet row, int index) throws SQLException {
        String expiresAt = row.getString("source_expires_at");
        return new PrescriptionClearance(row.getObject("clearance_id", UUID.class),
                row.getObject("invoice_id", UUID.class), row.getObject("account_id", UUID.class),
                row.getObject("prescription_id", UUID.class), row.getObject("patient_id", UUID.class),
                new CareEpisode(CareEpisodeType.valueOf(row.getString("care_episode_type")),
                        row.getObject("care_episode_id", UUID.class)),
                row.getBigDecimal("amount"), row.getString("currency"), row.getString("payment_method"),
                Instant.parse(row.getString("source_granted_at")),
                expiresAt == null ? null : Instant.parse(expiresAt), row.getString("payload_fingerprint"));
    }

    private static String snapshotHash(PrescriptionClearance grant) {
        try {
            // Values have constrained alphabets: UUID/enum/decimal/ISO time/hex, no delimiter ambiguity.
            String canonical = String.join("|", grant.clearanceId().toString(), grant.invoiceId().toString(),
                    grant.accountId().toString(), grant.prescriptionId().toString(), grant.patientId().toString(),
                    grant.episode().type().name(), grant.episode().id().toString(), grant.amount().toPlainString(),
                    grant.currency(), grant.paymentMethod(), grant.grantedAt().toString(),
                    String.valueOf(grant.expiresAt()), grant.payloadFingerprint());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static DispenseAuthorizationException conflict(String message) {
        return new DispenseAuthorizationException("PHARMACY_CLEARANCE_CONFLICT", message);
    }
}
