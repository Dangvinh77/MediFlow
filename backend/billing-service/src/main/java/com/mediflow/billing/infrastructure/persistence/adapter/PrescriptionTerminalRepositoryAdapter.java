package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand;
import com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionTerminalRepositoryAdapter implements PrescriptionTerminalRepositoryPort {
    private final JdbcTemplate jdbc;
    public PrescriptionTerminalRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void receive(PrescriptionTerminalCommand terminal) {
        var c = terminal.snapshot();
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-delivery:" + c.eventId());
        require(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prescription_charge_delivery WHERE event_id=?)", Boolean.class, c.eventId())),
                "BILLING_PRESCRIPTION_DELIVERY_CONFLICT");
        jdbc.update("INSERT INTO prescription_terminal_delivery(event_id,prescription_id,delivery_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                c.eventId(), c.prescriptionId(), c.deliveryFingerprint());
        var delivery = jdbc.queryForMap("SELECT * FROM prescription_terminal_delivery WHERE event_id=?", c.eventId());
        same(c.prescriptionId(), delivery.get("prescription_id")); same(c.deliveryFingerprint(), delivery.get("delivery_fingerprint"));
        lock(c.prescriptionId());
        jdbc.update("""
                INSERT INTO prescription_terminal_source(prescription_id,event_type,source_fingerprint,snapshot_fingerprint,terminal_at_iso)
                VALUES (?,?,?,?,?) ON CONFLICT(prescription_id) DO NOTHING
                """, c.prescriptionId(), terminal.eventType(), c.sourceFingerprint(), c.snapshotFingerprint(), c.createdAt().toString());
        var source = jdbc.queryForMap("SELECT * FROM prescription_terminal_source WHERE prescription_id=? FOR UPDATE", c.prescriptionId());
        same(terminal.eventType(), source.get("event_type")); same(c.sourceFingerprint(), source.get("source_fingerprint"));
        require(!"REJECTED".equals(source.get("status")), "BILLING_PRESCRIPTION_TERMINAL_REJECTED");
        if ("PENDING".equals(source.get("status"))) apply(source);
    }

    @Override public void recoverDuringIssuance(UUID prescriptionId) {
        lock(prescriptionId);
        var rows = jdbc.queryForList("SELECT * FROM prescription_terminal_source WHERE prescription_id=? AND status='PENDING' FOR UPDATE", prescriptionId);
        if (rows.isEmpty()) return;
        try { apply(rows.getFirst()); }
        catch (BillingRuleException invalid) {
            // apply validates before effects; known-invalid early evidence cannot poison creation.
            // Storage errors are NOT caught: they roll back creation and adjustment together.
            jdbc.update("UPDATE prescription_terminal_source SET status='REJECTED',rejection_code=? WHERE prescription_id=? AND status='PENDING'",
                    invalid.getCode(), prescriptionId);
        }
    }

    private void apply(Map<String, Object> terminal) {
        UUID rx = (UUID) terminal.get("prescription_id");
        var sources = jdbc.queryForList("SELECT * FROM prescription_charge_source WHERE prescription_id=?", rx);
        if (sources.isEmpty()) return; // Durable PENDING before the exact creation arrives.
        var source = sources.getFirst();
        require(source.get("snapshot_fingerprint") != null, "BILLING_PRESCRIPTION_CREATION_PROOF_REQUIRED");
        same(terminal.get("snapshot_fingerprint"), source.get("snapshot_fingerprint"));
        require(!Instant.parse((String)terminal.get("terminal_at_iso")).isBefore(Instant.parse((String)source.get("created_at_iso"))),
                "BILLING_PRESCRIPTION_TERMINAL_CHRONOLOGY");
        UUID accountId = (UUID) source.get("account_id"), chargeId = (UUID) source.get("charge_id");
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", accountId);
        var charge = jdbc.queryForMap("SELECT * FROM CHARGE WHERE charge_id=? FOR UPDATE", chargeId);
        same(accountId, charge.get("account_id")); same("PRESCRIPTION", charge.get("source_type")); same(rx, charge.get("source_id"));
        require("POSTED".equals(charge.get("status")), "BILLING_PRESCRIPTION_CHARGE_NOT_POSTED");
        BigDecimal storedTotal = jdbc.queryForObject("SELECT SUM(quantity*unit_price) FROM prescription_charge_item WHERE prescription_id=?", BigDecimal.class, rx);
        require(storedTotal != null && storedTotal.compareTo((BigDecimal)charge.get("gross_amount")) == 0
                && storedTotal.compareTo((BigDecimal)charge.get("unit_amount")) == 0
                && BigDecimal.ONE.compareTo((BigDecimal)charge.get("quantity")) == 0,
                "BILLING_PRESCRIPTION_CHARGE_SNAPSHOT_INVALID");
        // Filled is absorbing evidence, not revenue recognition and not another payment authority.
        if ("prescription.filled".equals(terminal.get("event_type"))) { applied(rx); return; }
        require("OPEN".equals(account.get("status")), "BILLING_PRESCRIPTION_ADJUSTMENT_ACCOUNT_CLOSED");
        UUID requestId = (UUID) source.get("payment_request_id");
        var links = jdbc.queryForList("SELECT payment_request_id FROM PAYMENT_REQUEST_CHARGE WHERE charge_id=? ORDER BY payment_request_id", chargeId);
        require(requestId == null ? links.isEmpty() : links.size() == 1 && requestId.equals(links.getFirst().get("payment_request_id")),
                "BILLING_PRESCRIPTION_ADJUSTMENT_REQUEST_MISMATCH");
        if (requestId != null) {
            var request = jdbc.queryForMap("""
                    SELECT r.*,t.prescription_id FROM PAYMENT_REQUEST r JOIN PAYMENT_REQUEST_TARGET t
                    ON t.payment_request_id=r.payment_request_id WHERE r.payment_request_id=? FOR UPDATE OF r
                    """, requestId);
            same(accountId, request.get("account_id")); same("PRESCRIPTION", request.get("purpose")); same(rx, request.get("prescription_id"));
            require(storedTotal.compareTo((BigDecimal)request.get("requested_amount")) == 0, "BILLING_PRESCRIPTION_ADJUSTMENT_REQUEST_MISMATCH");
            same(account.get("currency"), request.get("currency"));
            require(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_REQUEST_CHARGE WHERE payment_request_id=?", Long.class, requestId) == 1,
                    "BILLING_PRESCRIPTION_ADJUSTMENT_REQUEST_MISMATCH");
        }
        var allocations = jdbc.queryForList("""
                SELECT p.transaction_id,p.account_id,p.payment_request_id,p.classification,
                  a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                    WHERE r.charge_id=a.charge_id AND t.original_transaction_id=p.transaction_id
                    AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0) AS due
                FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION p ON p.transaction_id=a.transaction_id
                WHERE a.charge_id=? AND p.transaction_type='PAYMENT' AND p.status='COMPLETED' ORDER BY p.transaction_id
                """, chargeId);
        for (var allocation : allocations) {
            same(accountId, allocation.get("account_id")); same(requestId, allocation.get("payment_request_id"));
            same("SERVICE_PAYMENT", allocation.get("classification"));
            require(((BigDecimal)allocation.get("due")).signum() >= 0, "BILLING_PRESCRIPTION_REFUND_ALLOCATION_INVALID");
        }
        // Every validation precedes all effects. Account locking serializes real payments/refunds.
        for (var allocation : allocations) {
            var due = (BigDecimal) allocation.get("due");
            if (due.signum() > 0) jdbc.update("INSERT INTO prescription_terminal_refund_due(prescription_id,original_transaction_id,charge_id,amount) VALUES (?,?,?,?)",
                    rx, allocation.get("transaction_id"), chargeId, due);
        }
        jdbc.update("UPDATE CHARGE SET status='VOIDED',void_reason=? WHERE charge_id=?", terminal.get("event_type"), chargeId);
        Timestamp now = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (requestId != null) {
            jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED' WHERE payment_request_id=?", requestId);
            jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=? WHERE payment_request_id=? AND revoked_at IS NULL", now, requestId);
        }
        applied(rx);
    }
    private void applied(UUID rx) {
        jdbc.update("UPDATE prescription_terminal_source SET status='APPLIED',applied_at=clock_timestamp() WHERE prescription_id=? AND status='PENDING'", rx);
    }
    @Override public com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO get(UUID rx) {
        // One owned MVCC statement: immutable obligation capped by current original-only net allocations.
        var rows = jdbc.queryForList("""
                SELECT s.event_type,s.status,s.rejection_code,x.original_transaction_id,x.amount,x.currency
                FROM prescription_terminal_source s LEFT JOIN LATERAL (
                  SELECT d.original_transaction_id,p.currency,
                    SUM(GREATEST(0,LEAST(d.amount,a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r
                      JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                      WHERE t.original_transaction_id=d.original_transaction_id AND r.charge_id=d.charge_id
                        AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0)))) AS amount
                  FROM prescription_terminal_refund_due d JOIN PAYMENT_ALLOCATION a
                    ON a.transaction_id=d.original_transaction_id AND a.charge_id=d.charge_id
                  JOIN PAYMENT_TRANSACTION p ON p.transaction_id=d.original_transaction_id
                  WHERE d.prescription_id=s.prescription_id GROUP BY d.original_transaction_id,p.currency
                ) x ON TRUE WHERE s.prescription_id=? ORDER BY x.original_transaction_id
                """, rx);
        if (rows.isEmpty()) throw new com.mediflow.common.exception.ResourceNotFoundException("BILLING_PRESCRIPTION_ADJUSTMENT_NOT_FOUND", "Prescription adjustment not found");
        var first = rows.getFirst();
        var due = rows.stream().filter(row -> row.get("original_transaction_id") != null)
                .filter(row -> ((BigDecimal)row.get("amount")).signum() > 0)
                .map(row -> new com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO.RefundDue(
                        (UUID)row.get("original_transaction_id"), (BigDecimal)row.get("amount"), row.get("currency").toString().trim())).toList();
        return new com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO(rx, (String)first.get("event_type"),
                (String)first.get("status"), (String)first.get("rejection_code"), due);
    }
    private void lock(UUID rx) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-charge:" + rx);
    }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_PRESCRIPTION_TERMINAL_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
