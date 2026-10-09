package com.mediflow.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.CashRefundStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.*;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CashRefundPersistenceAdapter implements CashRefundStorePort {
    private final JdbcTemplate jdbc;
    private final CashSnapshotCodec codec;
    public CashRefundPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.codec = new CashSnapshotCodec(mapper); }
    @Override public void lockOriginal(UUID original) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "report-cash:" + original);
    }
    @Override public boolean record(DecodedCareFinanceEvent event, CashRefund refund) {
        var source = new LinkedHashMap<String, Object>(); source.put("payload", event.payload()); source.put("reportZone", refund.reportZone());
        source.put("businessDate", refund.businessDate().toString());
        String sourceHash = codec.evidenceFingerprint(source);
        var m = event.metadata(); var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", m.eventId().toString()); envelope.put("eventType", m.eventType()); envelope.put("version", m.version());
        envelope.put("occurredAt", m.occurredAt().toString()); envelope.put("correlationId", m.correlationId());
        envelope.put("producer", m.producer()); envelope.put("payload", event.payload());
        String deliveryHash = codec.evidenceFingerprint(envelope);
        int inserted = jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,account_id,
                    patient_id,department_id,care_episode_type,care_episode_id,amount,currency,completed_at_iso,
                    business_date,report_zone,source_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (refund_transaction_id) DO NOTHING
                """, refund.refundTransactionId(), refund.originalTransactionId(), m.eventId(), refund.accountId(), refund.patientId(),
                refund.departmentId(), refund.careEpisodeType(), refund.careEpisodeId(), refund.amount(), refund.currency(),
                refund.completedAt().toString(), Date.valueOf(refund.businessDate()), refund.reportZone(), sourceHash);
        var stored = jdbc.queryForObject("SELECT source_fingerprint FROM report_cash_refund WHERE refund_transaction_id=?", String.class, refund.refundTransactionId());
        require(sourceHash.equals(stored), "CASH_REFUND_SOURCE_CONFLICT");
        jdbc.update("""
                INSERT INTO report_cash_refund_delivery(event_id,refund_transaction_id,envelope_fingerprint)
                VALUES (?,?,?) ON CONFLICT (event_id) DO NOTHING
                """, m.eventId(), refund.refundTransactionId(), deliveryHash);
        var delivery = jdbc.queryForMap("SELECT refund_transaction_id,envelope_fingerprint FROM report_cash_refund_delivery WHERE event_id=?", m.eventId());
        require(refund.refundTransactionId().equals(delivery.get("refund_transaction_id"))
                && deliveryHash.equals(delivery.get("envelope_fingerprint")), "CASH_REFUND_DELIVERY_CONFLICT");
        return inserted == 1;
    }
    @Override public Optional<CashReceipt> original(UUID id) {
        return jdbc.query("SELECT * FROM report_cash_receipt WHERE transaction_id=?", (rs, row) -> new CashReceipt(
                rs.getObject("transaction_id", UUID.class), rs.getObject("invoice_id", UUID.class), rs.getObject("payment_request_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getObject("department_id", UUID.class),
                rs.getString("care_episode_type"), rs.getObject("care_episode_id", UUID.class), CashReceipt.Classification.valueOf(rs.getString("classification")),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("payment_method"), Instant.parse(rs.getString("completed_at_iso")),
                rs.getDate("business_date").toLocalDate(), rs.getString("report_zone")), id).stream().findFirst();
    }
    @Override public BigDecimal appliedTotal(UUID id) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM report_cash_refund WHERE original_transaction_id=? AND state='APPLIED'", BigDecimal.class, id);
    }
    @Override public List<CashRefund> pending(UUID original, int limit) {
        require(limit >= 1 && limit <= 20, "CASH_REFUND_BATCH_INVALID");
        return jdbc.query("""
                SELECT * FROM report_cash_refund WHERE original_transaction_id=? AND state='PENDING'
                ORDER BY created_at,refund_transaction_id LIMIT ?
                """, (rs, row) -> new CashRefund(rs.getObject("refund_transaction_id", UUID.class), rs.getObject("original_transaction_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getObject("department_id", UUID.class),
                rs.getString("care_episode_type"), rs.getObject("care_episode_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("currency"),
                Instant.parse(rs.getString("completed_at_iso")), rs.getDate("business_date").toLocalDate(), rs.getString("report_zone")), original, limit);
    }
    @Override public void apply(CashRefund refund, CashReceipt original) {
        int changed = jdbc.update("""
                UPDATE report_cash_refund SET state='APPLIED',original_business_date=?,classification=?,applied_at=now()
                WHERE refund_transaction_id=? AND state='PENDING'
                """, Date.valueOf(original.businessDate()), original.classification().name(), refund.refundTransactionId());
        require(changed == 1, "CASH_REFUND_STATE_CONFLICT");
        for (var scope : com.mediflow.report.application.mapper.CashRefundProjectionPlanner.scopes(refund)) {
            increment(refund, original, scope.departmentId());
        }
    }
    private void increment(CashRefund refund, CashReceipt original, UUID department) {
        jdbc.update("""
                INSERT INTO report_refund_cash_daily(scope_id,business_date,currency,report_zone,department_id,
                    classification,completed_refunds,refund_count) VALUES (?,?,?,?,?,?,?,1)
                ON CONFLICT (business_date,currency,report_zone,department_id,classification)
                DO UPDATE SET completed_refunds=report_refund_cash_daily.completed_refunds+EXCLUDED.completed_refunds,
                    refund_count=report_refund_cash_daily.refund_count+1,updated_at=now()
                """, UUID.randomUUID(), Date.valueOf(refund.businessDate()), refund.currency(), refund.reportZone(), department,
                original.classification().name(), refund.amount());
    }
    @Override public void reject(UUID refund, String reason) {
        jdbc.update("UPDATE report_cash_refund SET state='REJECTED',reason_code=? WHERE refund_transaction_id=? AND state='PENDING'", reason, refund);
    }
    @Override @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public List<UUID> readyOriginals(int limit) {
        require(limit >= 1 && limit <= 20, "CASH_REFUND_BATCH_INVALID");
        return jdbc.queryForList("""
                SELECT r.original_transaction_id FROM report_cash_refund r
                JOIN report_cash_receipt p ON p.transaction_id=r.original_transaction_id
                WHERE r.state='PENDING' AND r.next_attempt_at<=now() GROUP BY r.original_transaction_id
                ORDER BY MIN(r.created_at),r.original_transaction_id LIMIT ?
                """, UUID.class, limit);
    }
    @Override @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void defer(UUID original) {
        jdbc.update("""
                UPDATE report_cash_refund SET next_attempt_at=now()+interval '60 seconds', recovery_attempts=recovery_attempts+1
                WHERE original_transaction_id=? AND state='PENDING'
                """, original);
    }
    private static void require(boolean valid, String code) { if (!valid) throw new ReportRuleException(code, "Cash refund evidence conflict"); }
}
