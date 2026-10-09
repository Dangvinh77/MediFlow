package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.SurgeryCancellationCommand;
import com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class SurgeryCancellationRepositoryAdapter implements SurgeryCancellationRepositoryPort {
    private final JdbcTemplate jdbc;
    public SurgeryCancellationRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void claimAndLock(SurgeryCancellationCommand c) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.update("""
                INSERT INTO surgery_cancellation_delivery(event_id,surgery_case_id,delivery_fingerprint)
                VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING
                """, c.eventId(), c.surgeryCaseId(), c.deliveryFingerprint());
        var delivery = jdbc.queryForMap("SELECT * FROM surgery_cancellation_delivery WHERE event_id=?", c.eventId());
        same(c.surgeryCaseId(), delivery.get("surgery_case_id"));
        same(c.deliveryFingerprint(), delivery.get("delivery_fingerprint"));
        lockCase(c.surgeryCaseId());
        jdbc.update("""
                INSERT INTO surgery_cancellation_source(surgery_case_id,cancellation_id,surgery_request_id,first_event_id,
                    source_fingerprint,delivery_fingerprint,correlation_id,patient_id,department_id,care_episode_type,
                    care_episode_id,admission_id,record_id,case_revision,cancellation_stage,cancelled_by,
                    cancelled_by_staff_id,cancelled_at_iso)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(surgery_case_id) DO NOTHING
                """, c.surgeryCaseId(), c.cancellationId(), c.surgeryRequestId(), c.eventId(), c.sourceFingerprint(),
                c.deliveryFingerprint(), c.correlationId(), c.patientId(), c.departmentId(), c.careEpisodeType(),
                c.careEpisodeId(), c.admissionId(), c.recordId(), c.caseRevision(), c.cancellationStage(),
                c.cancelledBy(), c.cancelledByStaffId(), c.cancelledAt().toString());
        var source = jdbc.queryForMap("SELECT * FROM surgery_cancellation_source WHERE surgery_case_id=? FOR UPDATE", c.surgeryCaseId());
        same(c.cancellationId(), source.get("cancellation_id"));
        same(c.sourceFingerprint(), source.get("source_fingerprint"));
        require(!"REJECTED".equals(source.get("status")), "BILLING_SURGERY_CANCELLATION_REJECTED");
    }

    @Override public Optional<SurgeryCancellationCommand> lockPending(UUID caseId) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        lockCase(caseId);
        return jdbc.query("SELECT * FROM surgery_cancellation_source WHERE surgery_case_id=? AND status='PENDING' FOR UPDATE",
                (rs, row) -> command(rs), caseId).stream().findFirst();
    }

    @Override public boolean apply(SurgeryCancellationCommand c) {
        var sources = jdbc.queryForList("""
                SELECT s.*,r.account_id FROM surgery_charge_source s
                JOIN PAYMENT_REQUEST r ON r.payment_request_id=s.payment_request_id WHERE s.surgery_case_id=?
                """, c.surgeryCaseId());
        if (sources.isEmpty()) return false;
        var source = sources.getFirst();
        same(c.surgeryRequestId(), source.get("surgery_request_id"));
        same(c.recordId(), source.get("record_id"));
        var exactRequested = (String) source.get("requested_at_iso");
        if (exactRequested != null) {
            require(!c.cancelledAt().isBefore(Instant.parse(exactRequested)), "BILLING_SURGERY_CANCELLATION_TIME_INVALID");
        } else {
            // Old timestamp-only history has up to half a microsecond rounding uncertainty.
            // Require provable chronological separation, not a fabricated exact original Instant.
            var rounded = ((Timestamp) source.get("requested_at")).toInstant();
            require(!c.cancelledAt().isBefore(rounded.plusNanos(500)), "BILLING_SURGERY_CANCELLATION_TIME_UNVERIFIABLE");
        }
        UUID accountId = (UUID) source.get("account_id");
        var account = jdbc.queryForMap("SELECT * FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", accountId);
        same(c.patientId(), account.get("patient_id"));
        same(c.careEpisodeType(), account.get("care_episode_type"));
        same(c.careEpisodeId(), account.get("care_episode_id"));
        require("OPEN".equals(account.get("status")), "BILLING_SURGERY_CANCELLATION_ACCOUNT_CLOSED");
        var charges = jdbc.queryForList("""
                SELECT * FROM CHARGE WHERE source_type='SURGERY' AND source_id=? ORDER BY charge_id FOR UPDATE
                """, c.surgeryCaseId());
        require(!charges.isEmpty(), "BILLING_SURGERY_CHARGE_SOURCE_MISSING");
        for (var charge : charges) {
            same(accountId, charge.get("account_id"));
            same(c.patientId(), charge.get("patient_id"));
            same(c.departmentId(), charge.get("department_id"));
            require("POSTED".equals(charge.get("status")) && charge.get("reconciled_result_id") == null,
                    "BILLING_SURGERY_ALREADY_PERFORMED_OR_VOIDED");
        }
        // The account lock is shared by payment/refund writers. Never cancel a mixed/foreign request.
        var requests = jdbc.queryForList("""
                SELECT DISTINCT r.payment_request_id FROM PAYMENT_REQUEST r
                LEFT JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                WHERE t.surgery_case_id=? OR EXISTS (SELECT 1 FROM PAYMENT_REQUEST_CHARGE l JOIN CHARGE c ON c.charge_id=l.charge_id
                    WHERE l.payment_request_id=r.payment_request_id AND c.source_type='SURGERY' AND c.source_id=?)
                ORDER BY r.payment_request_id
                """, c.surgeryCaseId(), c.surgeryCaseId());
        require(!requests.isEmpty(), "BILLING_SURGERY_REQUEST_MISSING");
        for (var request : requests) {
            UUID requestId = (UUID) request.get("payment_request_id");
            var context = jdbc.queryForMap("""
                    SELECT r.*,t.surgery_case_id,t.admission_id FROM PAYMENT_REQUEST r
                    JOIN PAYMENT_REQUEST_TARGET t ON t.payment_request_id=r.payment_request_id
                    WHERE r.payment_request_id=? FOR UPDATE OF r
                    """, requestId);
            same(accountId, context.get("account_id"));
            same("SURGERY", context.get("purpose"));
            same(c.surgeryCaseId(), context.get("surgery_case_id"));
            same(c.admissionId(), context.get("admission_id"));
            Integer foreign = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM PAYMENT_REQUEST_CHARGE l JOIN CHARGE c ON c.charge_id=l.charge_id
                    WHERE l.payment_request_id=? AND (c.source_type<>'SURGERY' OR c.source_id<>? OR c.account_id<>?)
                    """, Integer.class, requestId, c.surgeryCaseId(), accountId);
            require(foreign != null && foreign == 0, "BILLING_SURGERY_REQUEST_MISMATCH");
        }
        // Snapshot remaining allocated cash per original + charge, not one refund per line or a bank action.
        var allocations = jdbc.queryForList("""
                SELECT p.transaction_id,c.charge_id,p.account_id,p.currency,p.payment_request_id,p.classification,
                  a.amount-COALESCE((SELECT SUM(x.amount) FROM PAYMENT_ALLOCATION x JOIN PAYMENT_TRANSACTION rt ON rt.transaction_id=x.transaction_id
                    WHERE x.charge_id=c.charge_id AND rt.original_transaction_id=p.transaction_id
                      AND rt.transaction_type IN ('REFUND','REVERSAL') AND rt.status='COMPLETED'),0) AS refundable
                FROM CHARGE c JOIN PAYMENT_ALLOCATION a ON a.charge_id=c.charge_id
                JOIN PAYMENT_TRANSACTION p ON p.transaction_id=a.transaction_id
                WHERE c.source_type='SURGERY' AND c.source_id=? AND p.transaction_type='PAYMENT' AND p.status='COMPLETED'
                ORDER BY p.transaction_id,c.charge_id
                """, c.surgeryCaseId());
        for (var allocation : allocations) {
            same(accountId, allocation.get("account_id"));
            same(account.get("currency"), allocation.get("currency"));
            same("SERVICE_PAYMENT", allocation.get("classification"));
            require(requests.stream().anyMatch(r -> r.get("payment_request_id").equals(allocation.get("payment_request_id"))),
                    "BILLING_SURGERY_REFUND_REQUEST_MISMATCH");
            var due = (BigDecimal) allocation.get("refundable");
            require(due.signum() >= 0, "BILLING_SURGERY_REFUND_ALLOCATION_MISMATCH");
        }
        // All business validation precedes every effect, including a prefix of due lines.
        for (var allocation : allocations) {
            var due = (BigDecimal) allocation.get("refundable");
            if (due.signum() > 0) jdbc.update("""
                    INSERT INTO surgery_cancellation_refund_due(cancellation_id,original_transaction_id,charge_id,account_id,amount,currency)
                    VALUES (?,?,?,?,?,?)
                    """, c.cancellationId(), allocation.get("transaction_id"), allocation.get("charge_id"), accountId, due, allocation.get("currency"));
        }
        // Processing audit time is captured AFTER every financial lock wait. A winning payment
        // may have granted clearance while we waited; revocation must not predate that grant.
        Timestamp appliedAt = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        jdbc.update("UPDATE CHARGE SET status='VOIDED',void_reason='PRE_START_CANCELLATION' WHERE source_type='SURGERY' AND source_id=?", c.surgeryCaseId());
        for (var request : requests) {
            jdbc.update("UPDATE PAYMENT_REQUEST SET status='CANCELLED' WHERE payment_request_id=?", request.get("payment_request_id"));
            jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=? WHERE payment_request_id=? AND revoked_at IS NULL",
                    appliedAt, request.get("payment_request_id"));
        }
        jdbc.update("UPDATE surgery_cancellation_source SET status='APPLIED',applied_at=? WHERE surgery_case_id=? AND status='PENDING'",
                appliedAt, c.surgeryCaseId());
        return true;
    }

    @Override public boolean recoverDuringIssuance(UUID caseId) {
        var pending = lockPending(caseId);
        if (pending.isEmpty()) return false;
        try {
            return apply(pending.get());
        } catch (BillingRuleException invalid) {
            // Validation above writes nothing. Catch inside the mandatory adapter (not across its
            // transaction interceptor) so an invalid early fact cannot mark valid issuance rollback-only.
            // SQL/storage failures still escape and roll back the ENTIRE creation/adjustment.
            reject(caseId, invalid.getCode());
            return false;
        }
    }

    @Override public List<UUID> dueCases(int limit) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("Invalid cancellation recovery batch");
        return jdbc.queryForList("SELECT surgery_case_id FROM surgery_cancellation_source WHERE status='PENDING' AND retry_at<=now() ORDER BY retry_at,surgery_case_id LIMIT ?", UUID.class, limit);
    }
    @Override public void defer(UUID caseId, Instant retryAt) {
        jdbc.update("UPDATE surgery_cancellation_source SET retry_at=? WHERE surgery_case_id=? AND status='PENDING'", Timestamp.from(retryAt), caseId);
    }
    @Override public void reject(UUID caseId, String code) {
        jdbc.update("UPDATE surgery_cancellation_source SET status='REJECTED',rejection_code=? WHERE surgery_case_id=? AND status='PENDING'", code, caseId);
    }
    @Override public com.mediflow.billing.application.dto.response.SurgeryCancellationDTO get(UUID caseId) {
        // One MVCC statement includes source state and current original-only reversal budgets.
        var rows = jdbc.queryForList("""
                SELECT s.cancellation_id,s.status,s.rejection_code,x.original_transaction_id,x.currency,x.amount
                FROM surgery_cancellation_source s LEFT JOIN LATERAL (
                    SELECT d.original_transaction_id,d.currency,
                      SUM(GREATEST(0,LEAST(d.amount,a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r
                        JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                        WHERE t.original_transaction_id=d.original_transaction_id AND r.charge_id=d.charge_id
                          AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0)))) AS amount
                    FROM surgery_cancellation_refund_due d JOIN PAYMENT_ALLOCATION a
                      ON a.transaction_id=d.original_transaction_id AND a.charge_id=d.charge_id
                    WHERE d.cancellation_id=s.cancellation_id GROUP BY d.original_transaction_id,d.currency
                ) x ON TRUE WHERE s.surgery_case_id=? ORDER BY x.original_transaction_id
                """, caseId);
        if (rows.isEmpty()) throw new com.mediflow.common.exception.ResourceNotFoundException(
                "BILLING_SURGERY_CANCELLATION_NOT_FOUND", "Surgery cancellation not found");
        var first = rows.getFirst();
        var due = rows.stream().filter(row -> row.get("original_transaction_id") != null)
                .filter(row -> ((BigDecimal) row.get("amount")).signum() > 0)
                .map(row -> new com.mediflow.billing.application.dto.response.SurgeryCancellationDTO.RefundDue(
                        (UUID) row.get("original_transaction_id"), (BigDecimal) row.get("amount"), row.get("currency").toString().trim())).toList();
        return new com.mediflow.billing.application.dto.response.SurgeryCancellationDTO(caseId,
                (UUID) first.get("cancellation_id"), first.get("status").toString(), (String) first.get("rejection_code"), due);
    }
    private void lockCase(UUID caseId) {
        // Identical namespace/order to planned issuance, before account/charge locks.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "surgery-charge:" + caseId);
    }
    private static SurgeryCancellationCommand command(ResultSet rs) throws SQLException {
        return new SurgeryCancellationCommand(id(rs,"first_event_id"),rs.getString("delivery_fingerprint"),rs.getString("source_fingerprint"),
                rs.getString("correlation_id"),id(rs,"surgery_case_id"),id(rs,"surgery_request_id"),id(rs,"cancellation_id"),
                id(rs,"patient_id"),id(rs,"department_id"),rs.getString("care_episode_type"),id(rs,"care_episode_id"),
                id(rs,"admission_id"),id(rs,"record_id"),rs.getLong("case_revision"),rs.getString("cancellation_stage"),
                id(rs,"cancelled_by"),id(rs,"cancelled_by_staff_id"),Instant.parse(rs.getString("cancelled_at_iso")));
    }
    private static UUID id(ResultSet rs, String key) throws SQLException { return rs.getObject(key, UUID.class); }
    private static void same(Object expected, Object actual) { require(Objects.equals(expected, actual), "BILLING_SURGERY_CANCELLATION_CONFLICT"); }
    private static void require(boolean valid, String code) { if (!valid) throw new BillingRuleException(code, code); }
}
