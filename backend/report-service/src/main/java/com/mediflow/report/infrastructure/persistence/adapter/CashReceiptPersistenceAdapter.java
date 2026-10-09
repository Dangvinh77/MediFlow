package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.CashReceiptStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.CashReceipt;

/** Minimal receipt evidence and hashes, never a raw patient/clinical payload archive. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CashReceiptPersistenceAdapter implements CashReceiptStorePort {
    private final JdbcTemplate jdbc;
    private final CashSnapshotCodec snapshots;

    public CashReceiptPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.snapshots = new CashSnapshotCodec(mapper);
    }

    @Override
    public boolean record(DecodedCareFinanceEvent event, CashReceipt receipt) {
        jdbc.execute("SET LOCAL lock_timeout = '3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "report-cash:" + receipt.transactionId());
        var metadata = event.metadata();
        String payloadHash = snapshots.evidenceFingerprint(event.payload());
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", metadata.eventId().toString());
        envelope.put("eventType", metadata.eventType());
        envelope.put("version", metadata.version());
        envelope.put("occurredAt", metadata.occurredAt().toString());
        envelope.put("correlationId", metadata.correlationId());
        envelope.put("producer", metadata.producer());
        envelope.put("payload", event.payload());
        String envelopeHash = snapshots.evidenceFingerprint(envelope);
        // Same source may be republished with another delivery ID/time, but its business evidence
        // and date/zone cannot change. Null invoice is legitimate; it is not the semantic key.
        String factHash = snapshots.fingerprint(receipt);

        int inserted = jdbc.update("""
                INSERT INTO report_cash_receipt(transaction_id, first_event_id, invoice_id,
                    payment_request_id, account_id, patient_id, department_id, care_episode_type,
                    care_episode_id, classification, amount, currency, payment_method, completed_at,
                    completed_at_iso, business_date, report_zone, payload_fingerprint, fact_fingerprint)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (transaction_id) DO NOTHING
                """, receipt.transactionId(), metadata.eventId(), receipt.invoiceId(), receipt.paymentRequestId(),
                receipt.accountId(), receipt.patientId(), receipt.departmentId(), receipt.careEpisodeType(),
                receipt.careEpisodeId(), receipt.classification().name(), receipt.amount(), receipt.currency(),
                receipt.paymentMethod(), Timestamp.from(receipt.completedAt()), receipt.completedAt().toString(),
                Date.valueOf(receipt.businessDate()), receipt.reportZone(), payloadHash, factHash);
        var stored = jdbc.queryForMap("""
                SELECT payload_fingerprint, fact_fingerprint FROM report_cash_receipt WHERE transaction_id=?
                """, receipt.transactionId());
        requireSame(payloadHash, stored.get("payload_fingerprint"), "CASH_SOURCE_CONFLICT");
        requireSame(factHash, stored.get("fact_fingerprint"), "CASH_PROJECTION_CONFLICT");

        jdbc.update("""
                INSERT INTO report_cash_delivery(event_id,transaction_id,envelope_fingerprint)
                VALUES (?,?,?) ON CONFLICT (event_id) DO NOTHING
                """, metadata.eventId(), receipt.transactionId(), envelopeHash);
        var delivery = jdbc.queryForMap("""
                SELECT transaction_id,envelope_fingerprint FROM report_cash_delivery WHERE event_id=?
                """, metadata.eventId());
        requireSame(receipt.transactionId(), delivery.get("transaction_id"), "CASH_DELIVERY_CONFLICT");
        requireSame(envelopeHash, delivery.get("envelope_fingerprint"), "CASH_DELIVERY_CONFLICT");
        return inserted == 1;
    }

    @Override
    public void incrementGrossReceipts(CashReceipt receipt, UUID departmentId) {
        if (departmentId != null && !departmentId.equals(receipt.departmentId())) {
            throw new ReportRuleException("CASH_SCOPE_INVALID", "Receipt department cannot be reallocated");
        }
        jdbc.update("""
                INSERT INTO report_gross_cash_daily(scope_id,business_date,currency,report_zone,
                    department_id,classification,gross_receipts,receipt_count) VALUES (?,?,?,?,?,?,?,1)
                ON CONFLICT (business_date,currency,report_zone,department_id,classification)
                DO UPDATE SET gross_receipts=report_gross_cash_daily.gross_receipts+EXCLUDED.gross_receipts,
                    receipt_count=report_gross_cash_daily.receipt_count+1, updated_at=now()
                """, UUID.randomUUID(), Date.valueOf(receipt.businessDate()), receipt.currency(), receipt.reportZone(),
                departmentId, receipt.classification().name(), receipt.amount());
    }

    private static void requireSame(Object expected, Object actual, String code) {
        if (!expected.equals(actual)) {
            throw new ReportRuleException(code, "Cash receipt source/delivery conflict");
        }
    }
}
