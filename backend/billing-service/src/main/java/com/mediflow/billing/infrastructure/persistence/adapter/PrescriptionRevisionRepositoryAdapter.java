package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import com.mediflow.billing.application.port.out.PrescriptionRevisionRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
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

/** Serializes Rx revisions against source updates and the same account/request locks used by payment. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class PrescriptionRevisionRepositoryAdapter implements PrescriptionRevisionRepositoryPort {
    private final JdbcTemplate jdbc;

    public PrescriptionRevisionRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public OperationReceipt prepare(PrescriptionRevisionCommand command, String payloadFingerprint, Instant preparedAt) {
        var scope = lockScope(command);
        var existing = findOperation(command.operationId());
        if (existing != null) {
            requireExactOperation(existing, command, payloadFingerprint);
            return receipt(existing);
        }
        requireCurrentRevision(scope.source(), command);
        verifySource(scope, command);
        requireUnpaid(scope.request());
        boolean pending = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM prescription_charge_revision_operation
                               WHERE prescription_id=? AND status='PREPARED')
                """, Boolean.class, command.prescriptionId()));
        if (pending) throw rule("BILLING_PRESCRIPTION_REVISION_PENDING");
        jdbc.update("""
                INSERT INTO prescription_charge_revision_operation(operation_id,prescription_id,expected_revision,
                    revision,payload_fingerprint,status,created_at)
                VALUES (?,?,?,?,?,'PREPARED',?)
                """, command.operationId(), command.prescriptionId(), command.expectedRevision(), command.revision(),
                payloadFingerprint, Timestamp.from(preparedAt));
        return new OperationReceipt(command.prescriptionId(), command.operationId(), command.revision(), "PREPARED", payloadFingerprint);
    }

    @Override public OperationReceipt commit(PrescriptionRevisionCommand command, String payloadFingerprint,
            String snapshotFingerprint, UUID eventId, Instant finalizedAt) {
        var scope = lockScope(command);
        var existing = findOperation(command.operationId());
        if (existing == null) throw rule("BILLING_PRESCRIPTION_REVISION_FENCE_REQUIRED");
        requireExactOperation(existing, command, payloadFingerprint);
        String status = (String) existing.get("status");
        if ("FINALIZED".equals(status)) return attachEventIfNeeded(existing, eventId);
        if ("ABORTED".equals(status)) throw rule("BILLING_PRESCRIPTION_REVISION_ABORTED");

        requireCurrentRevision(scope.source(), command);
        verifySource(scope, command);
        requireUnpaid(scope.request());
        Instant createdAt = Instant.parse((String) scope.source().get("created_at_iso"));
        if (finalizedAt == null || finalizedAt.isBefore(createdAt)) throw rule("BILLING_PRESCRIPTION_REVISION_CHRONOLOGY");
        UUID chargeId = (UUID) scope.source().get("charge_id");
        UUID requestId = (UUID) scope.source().get("payment_request_id");
        int updatedCharge = jdbc.update("""
                UPDATE CHARGE SET unit_amount=?, gross_amount=?
                 WHERE charge_id=? AND account_id=? AND source_type='PRESCRIPTION' AND source_id=? AND status='POSTED'
                """, command.totalAmount(), command.totalAmount(), chargeId,
                scope.source().get("account_id"), command.prescriptionId());
        if (updatedCharge != 1) throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
        int updatedRequest = jdbc.update("""
                UPDATE PAYMENT_REQUEST SET requested_amount=?
                 WHERE payment_request_id=? AND account_id=? AND purpose='PRESCRIPTION' AND status='PENDING'
                """, command.totalAmount(), requestId, scope.source().get("account_id"));
        if (updatedRequest != 1) throw rule("BILLING_PRESCRIPTION_REVISION_REQUEST_NOT_PAYABLE");
        int updatedLink = jdbc.update("UPDATE PAYMENT_REQUEST_CHARGE SET requested_amount=? WHERE payment_request_id=? AND charge_id=?",
                command.totalAmount(), requestId, chargeId);
        if (updatedLink != 1) throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
        jdbc.update("DELETE FROM prescription_charge_item WHERE prescription_id=?", command.prescriptionId());
        for (var item : command.items().stream().sorted(java.util.Comparator.comparing(value -> value.drugId().toString())).toList()) {
            jdbc.update("""
                    INSERT INTO prescription_charge_item(prescription_id,drug_id,drug_name,quantity,unit_price)
                    VALUES (?,?,?,?,?)
                    """, command.prescriptionId(), item.drugId(), item.drugName(), item.quantity(), item.unitPrice());
        }
        int updatedSource = jdbc.update("""
                UPDATE prescription_charge_source SET revision=?, snapshot_fingerprint=?
                 WHERE prescription_id=? AND revision=?
                """, command.revision(), snapshotFingerprint, command.prescriptionId(), command.expectedRevision());
        if (updatedSource != 1) throw rule("BILLING_PRESCRIPTION_REVISION_STALE");
        int finalized = jdbc.update("""
                UPDATE prescription_charge_revision_operation SET status='FINALIZED',event_id=?,finalized_at=?
                 WHERE operation_id=? AND status='PREPARED'
                """, eventId, Timestamp.from(finalizedAt), command.operationId());
        if (finalized != 1) throw rule("BILLING_PRESCRIPTION_REVISION_FENCE_REQUIRED");
        return new OperationReceipt(command.prescriptionId(), command.operationId(), command.revision(), "FINALIZED", payloadFingerprint);
    }

    @Override public OperationReceipt abort(PrescriptionRevisionCommand command, String payloadFingerprint, Instant abortedAt) {
        var scope = lockScope(command);
        var existing = findOperation(command.operationId());
        if (existing == null) {
            verifySource(scope, command, false);
            jdbc.update("""
                    INSERT INTO prescription_charge_revision_operation(operation_id,prescription_id,expected_revision,
                        revision,payload_fingerprint,status,created_at,aborted_at)
                    VALUES (?,?,?,?,?,'ABORTED',?,?)
                    """, command.operationId(), command.prescriptionId(), command.expectedRevision(), command.revision(),
                    payloadFingerprint, Timestamp.from(abortedAt), Timestamp.from(abortedAt));
            return new OperationReceipt(command.prescriptionId(), command.operationId(), command.revision(), "ABORTED", payloadFingerprint);
        }
        requireExactOperation(existing, command, payloadFingerprint);
        String status = (String) existing.get("status");
        if ("ABORTED".equals(status)) return receipt(existing);
        if ("FINALIZED".equals(status)) throw rule("BILLING_PRESCRIPTION_REVISION_ALREADY_FINALIZED");
        int updated = jdbc.update("""
                UPDATE prescription_charge_revision_operation SET status='ABORTED',aborted_at=?
                 WHERE operation_id=? AND status='PREPARED'
                """, Timestamp.from(abortedAt), command.operationId());
        if (updated != 1) throw rule("BILLING_PRESCRIPTION_REVISION_FENCE_REQUIRED");
        return new OperationReceipt(command.prescriptionId(), command.operationId(), command.revision(), "ABORTED", payloadFingerprint);
    }

    private OperationReceipt attachEventIfNeeded(Map<String, Object> existing, UUID eventId) {
        if (eventId == null) return receipt(existing);
        UUID currentEvent = (UUID) existing.get("event_id");
        if (currentEvent == null) {
            jdbc.update("UPDATE prescription_charge_revision_operation SET event_id=? WHERE operation_id=? AND event_id IS NULL",
                    eventId, existing.get("operation_id"));
        } else if (!currentEvent.equals(eventId)) {
            throw rule("BILLING_PRESCRIPTION_REVISION_EVENT_CONFLICT");
        }
        return receipt(existing);
    }

    private Scope lockScope(PrescriptionRevisionCommand command) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-revision-operation:" + command.operationId());
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "prescription-charge:" + command.prescriptionId());
        var candidates = jdbc.queryForList("SELECT account_id,payment_request_id,charge_id FROM prescription_charge_source WHERE prescription_id=?",
                command.prescriptionId());
        if (candidates.isEmpty()) throw new ResourceNotFoundException("BILLING_PRESCRIPTION_SOURCE_NOT_FOUND", "Prescription charge source not found");
        var candidate = candidates.getFirst();
        UUID accountId = (UUID) candidate.get("account_id");
        UUID requestId = (UUID) candidate.get("payment_request_id");
        if (accountId == null || requestId == null || candidate.get("charge_id") == null) {
            throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
        }
        jdbc.queryForMap("SELECT account_id FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", accountId);
        var request = jdbc.queryForMap("SELECT * FROM PAYMENT_REQUEST WHERE payment_request_id=? FOR UPDATE", requestId);
        var source = jdbc.queryForMap("SELECT * FROM prescription_charge_source WHERE prescription_id=? FOR UPDATE", command.prescriptionId());
        return new Scope(source, request);
    }

    private Map<String, Object> findOperation(UUID operationId) {
        var rows = jdbc.queryForList("SELECT * FROM prescription_charge_revision_operation WHERE operation_id=? FOR UPDATE", operationId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void requireExactOperation(Map<String, Object> operation, PrescriptionRevisionCommand command, String fingerprint) {
        if (!command.prescriptionId().equals(operation.get("prescription_id"))
                || number(operation.get("expected_revision")) != command.expectedRevision()
                || number(operation.get("revision")) != command.revision()
                || !fingerprint.equals(operation.get("payload_fingerprint"))) {
            throw new DuplicateResourceException("BILLING_PRESCRIPTION_REVISION_IDEMPOTENCY_CONFLICT",
                    "Revision operation ID is already bound to another prescription snapshot");
        }
    }

    private void requireCurrentRevision(Map<String, Object> source, PrescriptionRevisionCommand command) {
        if (number(source.get("revision")) != command.expectedRevision()) throw rule("BILLING_PRESCRIPTION_REVISION_STALE");
    }

    private void verifySource(Scope scope, PrescriptionRevisionCommand command) {
        verifySource(scope, command, true);
    }

    private void verifySource(Scope scope, PrescriptionRevisionCommand command, boolean rejectTerminal) {
        var source = scope.source();
        var request = scope.request();
        UUID prescriptionId = command.prescriptionId();
        UUID accountId = (UUID) source.get("account_id");
        UUID chargeId = (UUID) source.get("charge_id");
        UUID requestId = (UUID) source.get("payment_request_id");
        same("OUTPATIENT", source.get("care_context"));
        same(command.recordId(), source.get("record_id"));
        same("PRESCRIPTION", request.get("purpose"));
        same(accountId, request.get("account_id"));
        same(source.get("invoice_id"), request.get("invoice_id"));
        var account = jdbc.queryForMap("SELECT patient_id,care_episode_type,care_episode_id,currency,status FROM BILLING_ACCOUNT WHERE account_id=?", accountId);
        same(command.patientId(), account.get("patient_id"));
        same("OUTPATIENT_VISIT", account.get("care_episode_type"));
        same(command.careEpisodeType(), account.get("care_episode_type"));
        same(command.careEpisodeId(), account.get("care_episode_id"));
        same("VND", account.get("currency").toString().trim());
        if ("CLOSED".equals(account.get("status")) || "SETTLED".equals(account.get("status")))
            throw rule("BILLING_PRESCRIPTION_REVISION_ACCOUNT_CLOSED");
        var charge = jdbc.queryForMap("SELECT account_id,patient_id,department_id,source_type,source_id,price_code,status,quantity,unit_amount,gross_amount FROM CHARGE WHERE charge_id=? FOR UPDATE", chargeId);
        same(accountId, charge.get("account_id"));
        same(command.patientId(), charge.get("patient_id"));
        same(command.departmentId(), charge.get("department_id"));
        same("PRESCRIPTION", charge.get("source_type"));
        same(prescriptionId, charge.get("source_id"));
        same(command.priceCode(), source.get("price_code"));
        same(source.get("price_code"), charge.get("price_code"));
        same("POSTED", charge.get("status"));
        same(BigDecimal.ONE, ((BigDecimal) charge.get("quantity")).stripTrailingZeros());
        same(requestId, request.get("payment_request_id"));
        var target = jdbc.queryForMap("SELECT prescription_id FROM PAYMENT_REQUEST_TARGET WHERE payment_request_id=?", requestId);
        same(prescriptionId, target.get("prescription_id"));
        var links = jdbc.queryForList("SELECT charge_id,requested_amount FROM PAYMENT_REQUEST_CHARGE WHERE payment_request_id=?", requestId);
        if (links.size() != 1 || !chargeId.equals(links.getFirst().get("charge_id"))) throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
        same(request.get("requested_amount"), links.getFirst().get("requested_amount"));
        same(request.get("requested_amount"), charge.get("gross_amount"));
        same(request.get("requested_amount"), charge.get("unit_amount"));
        BigDecimal itemTotal = jdbc.queryForObject("SELECT COALESCE(SUM(quantity*unit_price),0) FROM prescription_charge_item WHERE prescription_id=?",
                BigDecimal.class, prescriptionId);
        same(request.get("requested_amount"), itemTotal);
        if (rejectTerminal) {
            boolean terminal = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM prescription_terminal_source WHERE prescription_id=?)",
                    Boolean.class, prescriptionId));
            if (terminal) throw rule("BILLING_PRESCRIPTION_REVISION_TERMINAL");
        }
    }

    private void requireUnpaid(Map<String, Object> request) {
        requireNoCollectionInProgress(request);
        boolean paid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM PAYMENT_TRANSACTION
                               WHERE payment_request_id=? AND transaction_type='PAYMENT' AND status='COMPLETED' AND amount>0)
                """, Boolean.class, request.get("payment_request_id")));
        if (paid) throw rule("BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS");
        if (!"PENDING".equals(request.get("status"))) throw rule("BILLING_PRESCRIPTION_REVISION_REQUEST_NOT_PAYABLE");
    }

    private void requireNoCollectionInProgress(Map<String, Object> request) {
        UUID requestId = (UUID) request.get("payment_request_id");
        boolean pendingPayment = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM PAYMENT_TRANSACTION WHERE payment_request_id=? AND status='PENDING')
                    OR EXISTS(SELECT 1 FROM scheduled_payment_operation WHERE payment_request_id=? AND status='PREPARING')
                """, Boolean.class, requestId, requestId));
        if (pendingPayment) throw rule("BILLING_PRESCRIPTION_REVISION_PAYMENT_IN_PROGRESS");
    }

    private static OperationReceipt receipt(Map<String, Object> operation) {
        return new OperationReceipt((UUID) operation.get("prescription_id"), (UUID) operation.get("operation_id"),
                number(operation.get("revision")), (String) operation.get("status"), (String) operation.get("payload_fingerprint"));
    }

    private static long number(Object value) { return ((Number) value).longValue(); }
    private static void same(Object expected, Object actual) {
        if (expected instanceof BigDecimal expectedAmount && actual instanceof BigDecimal actualAmount) {
            if (expectedAmount.compareTo(actualAmount) != 0) throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
            return;
        }
        if (!Objects.equals(expected, actual)) throw rule("BILLING_PRESCRIPTION_REVISION_SOURCE_CONFLICT");
    }
    private static BillingRuleException rule(String code) { return new BillingRuleException(code, code); }
    private record Scope(Map<String, Object> source, Map<String, Object> request) { }
}
