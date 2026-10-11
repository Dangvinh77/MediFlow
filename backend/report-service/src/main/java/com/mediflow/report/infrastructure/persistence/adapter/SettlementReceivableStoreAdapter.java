package com.mediflow.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.SettlementReceivableStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.SettlementReceivableSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Database fences serialize replicas. Delivery, immutable revision and both stock scopes are atomic. */
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementReceivableStoreAdapter implements SettlementReceivableStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CashSnapshotCodec evidence;
    public SettlementReceivableStoreAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.evidence = new CashSnapshotCodec(this.mapper);
    }
    @Override public void apply(DecodedCareFinanceEvent event, SettlementReceivableSnapshot snapshot) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Settlement stock requires an active transaction");
        }
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        FinancialPublicationFence.writer(jdbc);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "report-final-account:" + snapshot.accountId());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "report-settlement:" + snapshot.accountId());
        var factHash = evidence.evidenceFingerprint(event.payload());
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", event.metadata().eventId().toString());
        envelope.put("occurredAt", event.metadata().occurredAt().toString());
        envelope.put("correlationId", event.metadata().correlationId());
        envelope.put("eventType", event.metadata().eventType()); envelope.put("producer", event.metadata().producer());
        envelope.put("version", event.metadata().version()); envelope.put("payload", event.payload());
        String envelopeHash = evidence.evidenceFingerprint(envelope);
        var previous = current(snapshot.accountId());
        if (previous != null) {
            snapshot.requireSameAccountContext(previous);
            if ((snapshot.settlementVersion() < previous.settlementVersion() && snapshot.reconciledAt().isAfter(previous.reconciledAt()))
                    || (snapshot.settlementVersion() > previous.settlementVersion() && snapshot.reconciledAt().isBefore(previous.reconciledAt()))) throw conflict();
        }
        checkAdjacent(snapshot);
        persistSource(snapshot, factHash);
        int claimed = jdbc.update("""
                INSERT INTO report_settlement_delivery(event_id,settlement_id,envelope_fingerprint)
                VALUES (?,?,?) ON CONFLICT DO NOTHING
                """, event.metadata().eventId(), snapshot.settlementId(), envelopeHash);
        if (claimed == 0) {
            String accepted = jdbc.queryForObject("SELECT envelope_fingerprint FROM report_settlement_delivery WHERE event_id=?",
                    String.class, event.metadata().eventId());
            if (!envelopeHash.equals(accepted)) throw conflict();
        }
        if (previous != null && previous.settlementVersion() >= snapshot.settlementVersion()) return;
        BigDecimal delta = snapshot.receivable().subtract(previous == null ? BigDecimal.ZERO : previous.receivable());
        applyScope(snapshot, null, delta);
        applyScope(snapshot, snapshot.departmentId(), delta);
        jdbc.update("""
                INSERT INTO report_receivable_current(account_id,settlement_id,settlement_version) VALUES (?,?,?)
                ON CONFLICT (account_id) DO UPDATE SET settlement_id=EXCLUDED.settlement_id,
                    settlement_version=EXCLUDED.settlement_version,updated_at=now()
                """, snapshot.accountId(), snapshot.settlementId(), snapshot.settlementVersion());
    }
    private void persistSource(SettlementReceivableSnapshot snapshot, String factHash) {
        try {
            int inserted = jdbc.update("""
                    INSERT INTO report_settlement_snapshot(settlement_id,account_id,settlement_version,supersedes_settlement_id,
                        snapshot_version,payload,fact_fingerprint,snapshot_fingerprint) VALUES (?,?,?,?,1,?,?,?) ON CONFLICT DO NOTHING
                    """, snapshot.settlementId(), snapshot.accountId(), snapshot.settlementVersion(), snapshot.supersedesSettlementId(),
                        mapper.writeValueAsString(snapshot), factHash, evidence.evidenceFingerprint(snapshot));
            if (inserted == 0) {
                var rows = jdbc.query("""
                        SELECT * FROM report_settlement_snapshot WHERE settlement_id=? OR (account_id=? AND settlement_version=?)
                        """, (rs, row) -> rs.getObject("settlement_id", UUID.class).equals(snapshot.settlementId())
                            && rs.getObject("account_id", UUID.class).equals(snapshot.accountId())
                            && rs.getInt("settlement_version") == snapshot.settlementVersion()
                            && factHash.equals(rs.getString("fact_fingerprint"))
                            && evidence.evidenceFingerprint(snapshot).equals(rs.getString("snapshot_fingerprint")),
                        snapshot.settlementId(), snapshot.accountId(), snapshot.settlementVersion());
                if (rows.size() != 1 || !rows.getFirst()) throw conflict();
            }
        } catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode accepted settlement input", error); }
    }
    private SettlementReceivableSnapshot current(UUID account) {
        var rows = jdbc.query("""
                SELECT s.payload,s.snapshot_fingerprint FROM report_receivable_current c JOIN report_settlement_snapshot s ON s.settlement_id=c.settlement_id
                WHERE c.account_id=?
                """, (rs, row) -> decode(rs.getString("payload"), rs.getString("snapshot_fingerprint")), account);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private void checkAdjacent(SettlementReceivableSnapshot snapshot) {
        var rows = jdbc.query("""
                SELECT payload,snapshot_fingerprint FROM report_settlement_snapshot WHERE (account_id=? AND settlement_version IN (?,?))
                    OR settlement_id=? OR supersedes_settlement_id=? OR supersedes_settlement_id=?
                """, (rs, row) -> decode(rs.getString("payload"),rs.getString("snapshot_fingerprint")), snapshot.accountId(), snapshot.settlementVersion() - 1,
                    (long) snapshot.settlementVersion() + 1, snapshot.supersedesSettlementId(), snapshot.settlementId(), snapshot.supersedesSettlementId());
        for (var adjacent : rows) {
            if (adjacent.settlementId().equals(snapshot.settlementId())) continue;
            if (snapshot.supersedesSettlementId() != null && snapshot.supersedesSettlementId().equals(adjacent.supersedesSettlementId())) throw conflict();
            if (adjacent.settlementId().equals(snapshot.supersedesSettlementId())
                    || (adjacent.accountId().equals(snapshot.accountId()) && adjacent.settlementVersion() == snapshot.settlementVersion()-1)) {
                adjacent.requireSuccessor(snapshot);
            }
            if (snapshot.settlementId().equals(adjacent.supersedesSettlementId())
                    || (adjacent.accountId().equals(snapshot.accountId()) && adjacent.settlementVersion() == (long)snapshot.settlementVersion()+1)) {
                snapshot.requireSuccessor(adjacent);
            }
        }
    }
    private void applyScope(SettlementReceivableSnapshot snapshot, UUID department, BigDecimal delta) {
        // Insert zero first: a negative delta cannot be evaluated against a non-negative INSERT check.
        jdbc.update("""
                INSERT INTO report_receivable_stock(currency,report_zone,department_id,outstanding_receivable)
                VALUES (?,?,?,0) ON CONFLICT DO NOTHING
                """, snapshot.currency(), snapshot.reportZone(), department);
        jdbc.update("""
                UPDATE report_receivable_stock SET outstanding_receivable=outstanding_receivable+?,updated_at=now()
                WHERE currency=? AND report_zone=? AND department_id IS NOT DISTINCT FROM ?
                """, delta, snapshot.currency(), snapshot.reportZone(), department);
    }
    private SettlementReceivableSnapshot decode(String input, String fingerprint) {
        try {
            var snapshot = mapper.readValue(input, SettlementReceivableSnapshot.class);
            if (!evidence.evidenceFingerprint(snapshot).equals(fingerprint)) throw conflict();
            return snapshot;
        }
        catch (JsonProcessingException error) { throw new IllegalStateException("Invalid retained settlement input", error); }
    }
    private static ReportRuleException conflict() {
        return new ReportRuleException("REPORT_SETTLEMENT_CONFLICT", "Conflicting settlement source or delivery");
    }
}
