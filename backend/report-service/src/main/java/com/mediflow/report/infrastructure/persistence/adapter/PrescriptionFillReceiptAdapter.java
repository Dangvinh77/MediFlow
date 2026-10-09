package com.mediflow.report.infrastructure.persistence.adapter;

import com.mediflow.report.application.dto.command.DispensedItem;
import com.mediflow.report.application.port.out.PrescriptionFillReceiptPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Unique INSERT serializes concurrent source claims across replicas; rollback releases the claim. */
@Repository
public class PrescriptionFillReceiptAdapter implements PrescriptionFillReceiptPort {
    private final JdbcTemplate jdbc;

    public PrescriptionFillReceiptAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(UUID prescriptionId, Instant filledAt, UUID departmentId, ZoneId reportZone,
                         List<DispensedItem> groupedItems) {
        String fingerprint = fingerprint(prescriptionId, filledAt, departmentId, reportZone, groupedItems);
        int inserted = jdbc.update("""
                INSERT INTO prescription_fill_receipt(prescription_id, fact_fingerprint)
                VALUES (?,?) ON CONFLICT (prescription_id) DO NOTHING
                """, prescriptionId, fingerprint);
        if (inserted == 1) return true;
        String original = jdbc.queryForObject("SELECT fact_fingerprint FROM prescription_fill_receipt WHERE prescription_id=?",
                String.class, prescriptionId);
        if (!fingerprint.equals(original)) {
            throw new ReportRuleException("REPORT_PRESCRIPTION_FILL_CONFLICT", "Prescription fill source conflicts with accepted evidence");
        }
        return false;
    }

    private static String fingerprint(UUID prescriptionId, Instant filledAt, UUID departmentId, ZoneId reportZone,
                                      List<DispensedItem> groupedItems) {
        try {
            var buffer = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(buffer)) {
                output.writeInt(1); // Local fingerprint version; not the producer wire version.
                output.writeUTF(prescriptionId.toString());
                output.writeUTF(filledAt.toString()); // Preserve nanoseconds; never hash rounded SQL time.
                output.writeUTF(departmentId.toString());
                output.writeUTF(reportZone.getId());
                output.writeInt(groupedItems.size());
                for (var item : groupedItems.stream().sorted(java.util.Comparator.comparing(DispensedItem::drugId)).toList()) {
                    output.writeUTF(item.drugId().toString());
                    output.writeUTF(item.drugName());
                    output.writeInt(item.quantity());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot fingerprint accepted prescription fill", failure);
        }
    }
}
