package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CashReplayInput;
import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.port.out.CashReplayStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.CashReceipt;

/** Frozen minimal source proof and isolated aggregates. No live reset, Billing lookup or read switch. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class CashReplayPersistenceAdapter implements CashReplayStorePort {
    private final JdbcTemplate jdbc;
    private final CashSnapshotCodec snapshots;

    public CashReplayPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.snapshots = new CashSnapshotCodec(mapper);
    }

    @Override
    public CashReplayProgress freeze(UUID generationId) {
        jdbc.update("INSERT INTO cash_replay_generation(generation_id,status) VALUES (?,'BUILDING')", generationId);
        // One statement copies precisely its MVCC-visible committed set. MAX(id)/created_at is not
        // a safe boundary for old transactions that commit later. A missing first-delivery proof
        // fails the entire freeze, rather than silently excluding an invalid receipt from coverage.
        int count = jdbc.update("""
                INSERT INTO cash_replay_input(generation_id,transaction_id,first_event_id,fact_snapshot,
                    fact_fingerprint,payload_fingerprint,first_envelope_fingerprint,snapshot_version,projector_version)
                SELECT ?,r.transaction_id,r.first_event_id,jsonb_build_object(
                    'transactionId',r.transaction_id::text,'invoiceId',r.invoice_id::text,
                    'paymentRequestId',r.payment_request_id::text,'accountId',r.account_id::text,
                    'patientId',r.patient_id::text,'departmentId',r.department_id::text,
                    'careEpisodeType',r.care_episode_type,'careEpisodeId',r.care_episode_id::text,
                    'classification',r.classification,'amount',r.amount,'currency',r.currency,
                    'paymentMethod',r.payment_method,'completedAt',r.completed_at_iso,
                    'businessDate',r.business_date::text,'reportZone',r.report_zone),
                    r.fact_fingerprint,r.payload_fingerprint,d.envelope_fingerprint,1,1
                FROM report_cash_receipt r LEFT JOIN report_cash_delivery d
                    ON d.event_id=r.first_event_id AND d.transaction_id=r.transaction_id
                """, generationId);
        jdbc.update("UPDATE cash_replay_generation SET source_receipts=? WHERE generation_id=?", count, generationId);
        return progress(generationId);
    }

    @Override
    public CashReplayProgress lock(UUID generationId) { return readProgress(generationId, " FOR UPDATE"); }

    @Override
    public CashReplayProgress progress(UUID generationId) { return readProgress(generationId, ""); }

    private CashReplayProgress readProgress(UUID id, String lock) {
        var rows = jdbc.query("""
                SELECT generation_id,status,source_receipts,applied_receipts,snapshot_version,projector_version
                FROM cash_replay_generation WHERE generation_id=?
                """ + lock, (rs, row) -> new CashReplayProgress(rs.getObject("generation_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getLong("source_receipts"), rs.getLong("applied_receipts"),
                rs.getInt("snapshot_version"), rs.getInt("projector_version")), id);
        if (rows.isEmpty()) throw new ReportRuleException("CASH_REPLAY_NOT_FOUND", "Cash replay generation not found");
        return rows.getFirst();
    }

    @Override
    public List<CashReplayInput> pending(UUID generationId, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("Cash replay batch size must be 1..500");
        return jdbc.query("""
                SELECT transaction_id,first_event_id,fact_snapshot::text,fact_fingerprint,payload_fingerprint,
                    first_envelope_fingerprint,snapshot_version,projector_version FROM cash_replay_input
                WHERE generation_id=? AND NOT applied ORDER BY transaction_id LIMIT ?
                """, (rs, row) -> new CashReplayInput(snapshots.decode(rs.getObject("transaction_id", UUID.class),
                rs.getString("fact_snapshot"), rs.getString("fact_fingerprint"), rs.getInt("snapshot_version"),
                rs.getInt("projector_version")), rs.getObject("first_event_id", UUID.class),
                rs.getString("fact_fingerprint"), rs.getString("payload_fingerprint"),
                rs.getString("first_envelope_fingerprint")), generationId, limit);
    }

    @Override
    public boolean insertReceipt(UUID generationId, CashReplayInput input) {
        requireSame(snapshots.fingerprint(input.receipt()), input.factFingerprint());
        int inserted = jdbc.update("""
                INSERT INTO cash_replay_receipt(generation_id,transaction_id,first_event_id,fact_snapshot,
                    fact_fingerprint,payload_fingerprint,first_envelope_fingerprint)
                VALUES (?,?,?,CAST(? AS JSONB),?,?,?) ON CONFLICT (generation_id,transaction_id) DO NOTHING
                """, generationId, input.receipt().transactionId(), input.firstEventId(), snapshots.snapshot(input.receipt()),
                input.factFingerprint(), input.payloadFingerprint(), input.firstEnvelopeFingerprint());
        var stored = jdbc.queryForMap("""
                SELECT first_event_id,fact_fingerprint,payload_fingerprint,first_envelope_fingerprint
                FROM cash_replay_receipt WHERE generation_id=? AND transaction_id=?
                """, generationId, input.receipt().transactionId());
        requireSame(input.firstEventId(), stored.get("first_event_id"));
        requireSame(input.factFingerprint(), stored.get("fact_fingerprint"));
        requireSame(input.payloadFingerprint(), stored.get("payload_fingerprint"));
        requireSame(input.firstEnvelopeFingerprint(), stored.get("first_envelope_fingerprint"));
        return inserted == 1;
    }

    @Override
    public void incrementScope(UUID generationId, CashReceipt receipt, UUID departmentId) {
        if (departmentId != null && !departmentId.equals(receipt.departmentId())) {
            throw new ReportRuleException("CASH_REPLAY_SCOPE_INVALID", "Cash replay cannot reallocate a receipt");
        }
        jdbc.update("""
                INSERT INTO cash_replay_scope(generation_id,business_date,currency,report_zone,
                    department_id,classification,gross_receipts,receipt_count) VALUES (?,?,?,?,?,?,?,1)
                ON CONFLICT (generation_id,business_date,currency,report_zone,department_id,classification)
                DO UPDATE SET gross_receipts=cash_replay_scope.gross_receipts+EXCLUDED.gross_receipts,
                    receipt_count=cash_replay_scope.receipt_count+1
                """, generationId, Date.valueOf(receipt.businessDate()), receipt.currency(), receipt.reportZone(),
                departmentId, receipt.classification().name(), receipt.amount());
    }

    @Override
    public void markApplied(UUID generationId, UUID transactionId) {
        if (jdbc.update("UPDATE cash_replay_input SET applied=true WHERE generation_id=? AND transaction_id=? AND NOT applied",
                generationId, transactionId) == 1) {
            jdbc.update("UPDATE cash_replay_generation SET applied_receipts=applied_receipts+1 WHERE generation_id=?", generationId);
        }
    }

    @Override
    public CashReplayProgress reconcile(UUID generationId) {
        var current = lock(generationId);
        if (current.status() != Status.BUILDING) return current;
        // Bidirectional comparisons include source proof, complete accepted facts and all currency/
        // classification/zone/department scopes. The live tables may advance and are never compared.
        boolean matched = Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH expected_facts AS (
                    SELECT transaction_id,first_event_id,fact_snapshot,fact_fingerprint,payload_fingerprint,
                        first_envelope_fingerprint FROM cash_replay_input WHERE generation_id=?
                ), actual_facts AS (
                    SELECT transaction_id,first_event_id,fact_snapshot,fact_fingerprint,payload_fingerprint,
                        first_envelope_fingerprint FROM cash_replay_receipt WHERE generation_id=?
                ), fact_diff AS (
                    (SELECT * FROM expected_facts EXCEPT SELECT * FROM actual_facts)
                    UNION ALL (SELECT * FROM actual_facts EXCEPT SELECT * FROM expected_facts)
                ), expected_scopes AS (
                    SELECT (fact_snapshot->>'businessDate')::date business_date,
                        fact_snapshot->>'currency' currency,fact_snapshot->>'reportZone' report_zone,
                        scope.department_id,fact_snapshot->>'classification' classification,
                        sum((fact_snapshot->>'amount')::numeric) gross_receipts,count(*) receipt_count
                    FROM expected_facts CROSS JOIN LATERAL
                        (VALUES (NULL::uuid),((fact_snapshot->>'departmentId')::uuid)) scope(department_id)
                    GROUP BY 1,2,3,4,5
                ), actual_scopes AS (
                    SELECT business_date,currency,report_zone,department_id,classification,gross_receipts,
                        receipt_count FROM cash_replay_scope WHERE generation_id=?
                ), scope_diff AS (
                    (SELECT * FROM expected_scopes EXCEPT SELECT * FROM actual_scopes)
                    UNION ALL (SELECT * FROM actual_scopes EXCEPT SELECT * FROM expected_scopes)
                ) SELECT NOT EXISTS (SELECT 1 FROM fact_diff) AND NOT EXISTS (SELECT 1 FROM scope_diff)
                    AND NOT EXISTS (SELECT 1 FROM cash_replay_input WHERE generation_id=? AND NOT applied)
                    AND NOT EXISTS (SELECT 1 FROM cash_replay_input WHERE generation_id=?
                        AND (snapshot_version<>? OR projector_version<>?))
                    AND (SELECT count(*) FROM cash_replay_input WHERE generation_id=?)=?
                    AND (SELECT count(*) FROM cash_replay_input WHERE generation_id=? AND applied)=?
                """, Boolean.class, generationId, generationId, generationId, generationId, generationId,
                current.snapshotVersion(), current.projectorVersion(), generationId, current.sourceReceipts(),
                generationId, current.appliedReceipts()));
        Status status = matched && current.appliedReceipts() == current.sourceReceipts() ? Status.VERIFIED : Status.FAILED;
        jdbc.update("UPDATE cash_replay_generation SET status=?,completed_at=now() WHERE generation_id=?", status.name(), generationId);
        return progress(generationId);
    }

    private static void requireSame(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new ReportRuleException("CASH_REPLAY_SOURCE_CONFLICT", "Cash replay source conflict");
    }
}
