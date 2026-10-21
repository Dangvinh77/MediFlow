package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.OutpatientCancellationCommand;
import com.mediflow.billing.application.dto.response.OutpatientAdjustmentDTO;
import com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Durable terminal evidence, exact-source reconciliation, and refund-due projection. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OutpatientCancellationRepositoryAdapter implements OutpatientCancellationRepositoryPort {
    private final JdbcTemplate jdbc;
    public OutpatientCancellationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void receive(OutpatientCancellationCommand c) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        lockSource(c.sourceType(), c.sourceId());
        jdbc.update("""
                INSERT INTO outpatient_cancellation_source(source_type,source_id,event_type,source_fingerprint,first_event_id,
                    correlation_id,source_order_id,patient_id,department_id,record_id,care_episode_type,care_episode_id,
                    price_code,lab_type,cancelled_by,reason,cancelled_at_iso)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(source_type,source_id) DO NOTHING
                """, c.sourceType(), c.sourceId(), eventType(c.sourceType()), c.sourceFingerprint(), c.eventId(), c.correlationId(),
                c.sourceOrderId(), c.patientId(), c.departmentId(), c.recordId(), c.careEpisodeType(), c.careEpisodeId(),
                c.priceCode(), c.labType(), c.cancelledBy(), c.reason(), c.cancelledAt().toString());
        var source = jdbc.queryForMap("SELECT * FROM outpatient_cancellation_source WHERE source_type=? AND source_id=? FOR UPDATE",
                c.sourceType(), c.sourceId());
        same(eventType(c.sourceType()), source.get("event_type")); same(c.sourceFingerprint(), source.get("source_fingerprint"));
        same(c.sourceOrderId(), source.get("source_order_id")); same(c.patientId(), source.get("patient_id"));
        same(c.departmentId(), source.get("department_id")); same(c.recordId(), source.get("record_id"));
        same(c.careEpisodeType(), source.get("care_episode_type")); same(c.careEpisodeId(), source.get("care_episode_id"));
        same(c.priceCode(), source.get("price_code")); same(c.labType(), source.get("lab_type"));
        same(c.cancelledBy(), source.get("cancelled_by")); same(c.reason(), source.get("reason"));
        same(c.cancelledAt().toString(), source.get("cancelled_at_iso"));
        require(!"REJECTED".equals(source.get("status")), "BILLING_OUTPATIENT_CANCELLATION_REJECTED");
        jdbc.update("""
                INSERT INTO outpatient_cancellation_delivery(event_id,source_type,source_id,delivery_fingerprint)
                VALUES (?,?,?,?) ON CONFLICT(event_id) DO NOTHING
                """, c.eventId(), c.sourceType(), c.sourceId(), c.deliveryFingerprint());
        var delivery = jdbc.queryForMap("SELECT * FROM outpatient_cancellation_delivery WHERE event_id=?", c.eventId());
        same(c.sourceType(), delivery.get("source_type")); same(c.sourceId(), delivery.get("source_id"));
        same(c.deliveryFingerprint(), delivery.get("delivery_fingerprint"));
        if ("PENDING".equals(source.get("status"))) apply(c);
    }

    @Override public void recoverDuringIssuance(String sourceType, UUID sourceId) {
        lockSource(sourceType, sourceId);
        var rows = jdbc.queryForList("SELECT * FROM outpatient_cancellation_source WHERE source_type=? AND source_id=? AND status='PENDING' FOR UPDATE",
                sourceType, sourceId);
        if (rows.isEmpty()) return;
        try { apply(storedCommand(rows.getFirst())); }
        catch (BillingRuleException invalid) {
            // Exact snapshot mismatches are evidence failures, never a reason to mutate the ledger.
            jdbc.update("UPDATE outpatient_cancellation_source SET status='REJECTED',rejection_code=? WHERE source_type=? AND source_id=? AND status='PENDING'",
                    invalid.getCode(), sourceType, sourceId);
        }
    }

    private boolean apply(OutpatientCancellationCommand c) {
        var source = source(c.sourceType(), c.sourceId());
        if (source.isEmpty()) return false; // Cancellation is durable before its create fact arrives.
        var created = source.get();
        if (!hasCompleteSnapshot(c.sourceType(), created)) return false;
        same(c.sourceOrderId(), created.get("source_order_id")); same(c.patientId(), created.get("patient_id"));
        same(c.departmentId(), created.get("department_id")); same(c.recordId(), created.get("record_id"));
        same(c.careEpisodeType(), created.get("care_episode_type")); same(c.careEpisodeId(), created.get("care_episode_id"));
        if ("LAB_TEST".equals(c.sourceType())) {
            same(c.priceCode(), created.get("price_code")); same(c.labType(), created.get("lab_type"));
        }
        var requestedAtIso = (String) created.get("requested_at_iso");
        require(requestedAtIso != null, "BILLING_OUTPATIENT_CANCELLATION_CREATION_PROOF_REQUIRED");
        require(!c.cancelledAt().isBefore(Instant.parse(requestedAtIso)), "BILLING_OUTPATIENT_CANCELLATION_TIME_INVALID");
        UUID requestId = (UUID) created.get("payment_request_id");
        UUID accountId = (UUID) created.get("account_id");
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", accountId);
        same(c.patientId(), account.get("patient_id")); same(c.departmentId(), account.get("department_id"));
        same(c.careEpisodeType(), account.get("care_episode_type")); same(c.careEpisodeId(), account.get("care_episode_id"));
        same(accountId, created.get("account_id"));
        require("OPEN".equals(account.get("status")), "BILLING_OUTPATIENT_CANCELLATION_ACCOUNT_CLOSED");
        var charges = jdbc.queryForList("""
                SELECT * FROM CHARGE WHERE source_type=? AND source_id=? ORDER BY charge_id FOR UPDATE
                """, c.sourceType(), c.sourceId());
        require(charges.size() == 1, "BILLING_OUTPATIENT_CANCELLATION_CHARGE_MISMATCH");
        var charge = charges.getFirst();
        same(accountId, charge.get("account_id")); same(c.patientId(), charge.get("patient_id"));
        same(c.departmentId(), charge.get("department_id"));
        require("POSTED".equals(charge.get("status")), "BILLING_OUTPATIENT_CANCELLATION_CHARGE_NOT_POSTED");
        var requestRows = jdbc.queryForList("""
                SELECT r.*,t.appointment_id,t.record_id,t.lab_test_ids,t.prescription_id,t.admission_id,t.surgery_case_id
                FROM PAYMENT_REQUEST r JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                WHERE r.payment_request_id=? FOR UPDATE OF r
                """, requestId);
        require(requestRows.size() == 1, "BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH");
        var request = requestRows.getFirst();
        same(accountId, request.get("account_id")); same(c.sourceType(), request.get("purpose"));
        same(charge.get("charge_id"), jdbc.queryForObject("SELECT charge_id FROM PAYMENT_REQUEST_CHARGE WHERE payment_request_id=?", UUID.class, requestId));
        same((BigDecimal) charge.get("gross_amount"), request.get("requested_amount"));
        same(account.get("currency"), request.get("currency"));
        if ("EXAM".equals(c.sourceType())) {
            same(c.sourceId(), request.get("appointment_id")); same(null, request.get("record_id"));
            same(null, request.get("prescription_id")); same(null, request.get("admission_id")); same(null, request.get("surgery_case_id"));
            require(labIds(request.get("lab_test_ids")).isEmpty(), "BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH");
        } else {
            same(c.recordId(), request.get("record_id")); same(null, request.get("appointment_id"));
            same(null, request.get("prescription_id")); same(null, request.get("admission_id")); same(null, request.get("surgery_case_id"));
            require(labIds(request.get("lab_test_ids")).equals(List.of(c.sourceId())), "BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH");
        }
        Integer linkCount = jdbc.queryForObject("SELECT COUNT(*) FROM PAYMENT_REQUEST_CHARGE WHERE payment_request_id=?", Integer.class, requestId);
        require(linkCount != null && linkCount == 1, "BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH");

        var allocations = jdbc.queryForList("""
                SELECT p.transaction_id,p.account_id,p.payment_request_id,p.classification,p.currency,
                  a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                    WHERE r.charge_id=a.charge_id AND t.original_transaction_id=p.transaction_id
                    AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0) AS due
                FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION p ON p.transaction_id=a.transaction_id
                WHERE a.charge_id=? AND p.transaction_type='PAYMENT' AND p.status='COMPLETED' ORDER BY p.transaction_id
                """, charge.get("charge_id"));
        for (var allocation : allocations) {
            same(accountId, allocation.get("account_id")); same(requestId, allocation.get("payment_request_id"));
            same(account.get("currency"), allocation.get("currency")); same("SERVICE_PAYMENT", allocation.get("classification"));
            require(((BigDecimal) allocation.get("due")).signum() >= 0, "BILLING_OUTPATIENT_CANCELLATION_ALLOCATION_INVALID");
        }
        for (var allocation : allocations) {
            var due = (BigDecimal) allocation.get("due");
            if (due.signum() > 0) jdbc.update("""
                    INSERT INTO outpatient_cancellation_refund_due(source_type,source_id,original_transaction_id,charge_id,account_id,amount,currency)
                    VALUES (?,?,?,?,?,?,?)
                    """, c.sourceType(), c.sourceId(), allocation.get("transaction_id"), charge.get("charge_id"),
                    accountId, due, allocation.get("currency"));
        }
        // All evidence and allocations are validated before any terminal financial effect.
        var appliedAt = jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class);
        jdbc.update("UPDATE CHARGE SET status='VOIDED',void_reason='OUTPATIENT_CANCELLATION' WHERE charge_id=?", charge.get("charge_id"));
        jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED' WHERE payment_request_id=?", requestId);
        jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=? WHERE payment_request_id=? AND revoked_at IS NULL", appliedAt, requestId);
        jdbc.update("UPDATE outpatient_cancellation_source SET status='APPLIED',applied_at=? WHERE source_type=? AND source_id=? AND status='PENDING'",
                appliedAt, c.sourceType(), c.sourceId());
        return true;
    }

    private static boolean hasCompleteSnapshot(String sourceType, Map<String, Object> source) {
        boolean common = source.get("patient_id") != null && source.get("department_id") != null
                && source.get("care_episode_type") != null && source.get("care_episode_id") != null
                && source.get("price_code") != null && source.get("requested_at_iso") != null;
        return common && (!"LAB_TEST".equals(sourceType)
                || source.get("source_order_id") != null && source.get("lab_type") != null);
    }

    @Override public OutpatientAdjustmentDTO get(String sourceType, UUID sourceId) {
        var rows = jdbc.queryForList("""
                SELECT s.event_type,s.status,s.rejection_code,x.original_transaction_id,x.currency,x.amount
                FROM outpatient_cancellation_source s LEFT JOIN LATERAL (
                  SELECT d.original_transaction_id,d.currency,
                    SUM(GREATEST(0,LEAST(d.amount,a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r
                      JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                      WHERE t.original_transaction_id=d.original_transaction_id AND r.charge_id=d.charge_id
                        AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0)))) AS amount
                  FROM outpatient_cancellation_refund_due d JOIN PAYMENT_ALLOCATION a
                    ON a.transaction_id=d.original_transaction_id AND a.charge_id=d.charge_id
                  WHERE d.source_type=s.source_type AND d.source_id=s.source_id
                  GROUP BY d.original_transaction_id,d.currency
                ) x ON TRUE WHERE s.source_type=? AND s.source_id=? ORDER BY x.original_transaction_id
                """, sourceType, sourceId);
        if (rows.isEmpty()) throw new com.mediflow.common.exception.ResourceNotFoundException(
                "BILLING_OUTPATIENT_ADJUSTMENT_NOT_FOUND", "Outpatient adjustment not found");
        var first = rows.getFirst();
        var due = rows.stream().filter(row -> row.get("original_transaction_id") != null)
                .filter(row -> ((BigDecimal) row.get("amount")).signum() > 0)
                .map(row -> new OutpatientAdjustmentDTO.RefundDue((UUID) row.get("original_transaction_id"),
                        (BigDecimal) row.get("amount"), row.get("currency").toString().trim())).toList();
        return new OutpatientAdjustmentDTO(sourceId, sourceType, (String) first.get("event_type"),
                (String) first.get("status"), (String) first.get("rejection_code"), due);
    }

    private java.util.Optional<Map<String, Object>> source(String type, UUID id) {
        String sql = "EXAM".equals(type) ? """
                SELECT s.*,r.account_id FROM exam_charge_source s JOIN PAYMENT_REQUEST r ON r.payment_request_id=s.payment_request_id
                WHERE s.appointment_id=?
                """ : """
                SELECT s.*,r.account_id FROM lab_test_charge_source s JOIN PAYMENT_REQUEST r ON r.payment_request_id=s.payment_request_id
                WHERE s.lab_id=?
                """;
        return jdbc.queryForList(sql, id).stream().findFirst();
    }
    private OutpatientCancellationCommand storedCommand(Map<String,Object> row) {
        return new OutpatientCancellationCommand((UUID) row.get("first_event_id"), "0".repeat(64),
                (String) row.get("source_fingerprint"), (String) row.get("correlation_id"), (String) row.get("source_type"),
                (UUID) row.get("source_id"), (UUID) row.get("source_order_id"), (UUID) row.get("patient_id"),
                (UUID) row.get("department_id"), (UUID) row.get("record_id"), (String) row.get("care_episode_type"),
                (UUID) row.get("care_episode_id"), (String) row.get("price_code"), (String) row.get("lab_type"),
                (UUID) row.get("cancelled_by"), (String) row.get("reason"), Instant.parse((String) row.get("cancelled_at_iso")));
    }
    private static List<UUID> labIds(Object raw) {
        if (raw == null) return List.of();
        try { return Arrays.asList((UUID[]) ((java.sql.Array) raw).getArray()); }
        catch (Exception malformed) { throw new BillingRuleException("BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH", "BILLING_OUTPATIENT_CANCELLATION_REQUEST_MISMATCH"); }
    }
    private void lockSource(String type, UUID id) {
        String key = ("EXAM".equals(type) ? "exam-charge:" : "lab-test-charge:") + id;
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", key);
    }
    private static String eventType(String type) { return "EXAM".equals(type) ? "appointment.exam.cancelled" : "lab.request.cancelled"; }
    private static void same(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new BillingRuleException("BILLING_OUTPATIENT_CANCELLATION_CONFLICT", "BILLING_OUTPATIENT_CANCELLATION_CONFLICT");
    }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
