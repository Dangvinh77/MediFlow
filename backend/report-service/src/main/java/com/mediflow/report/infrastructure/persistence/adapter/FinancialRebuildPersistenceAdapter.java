package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress.Status;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.port.out.FinancialRebuildStorePort;
import com.mediflow.report.application.port.out.FinancialSourceArchiveStorePort;
import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.domain.model.CashRefund;
import com.mediflow.report.domain.model.FinancialAmounts;
import com.mediflow.report.domain.model.FinancialSourceManifest;
import com.mediflow.report.domain.model.FinancialSourceProjection;
import com.mediflow.report.domain.model.FinalSettlementRecognition;
import com.mediflow.report.domain.model.SettlementReceivableSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Finite, local-only replay. Four source reads share one RR snapshot, never live totals or foreign I/O. */
@Component
@Transactional(transactionManager = "reportTransactionManager", propagation = Propagation.MANDATORY)
public class FinancialRebuildPersistenceAdapter implements FinancialRebuildStorePort, FinancialSourceArchiveStorePort {
    private static final int MAX_INPUTS = 10_000;
    private static final int MAX_MANIFEST_BYTES = 16 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CashSnapshotCodec originalCodec;

    private record Source(String kind, UUID id, UUID firstEventId, String state, String sourceHash, String envelopeHash,
            String snapshot, String snapshotHash) { }

    public FinancialRebuildPersistenceAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.originalCodec = new CashSnapshotCodec(this.mapper);
    }

    @Override public FinancialEvidenceArchive exportArchive(UUID generationId) {
        transaction(); timeouts();
        require("repeatable read".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class)));
        FinancialRebuildProgress current;
        try { current = progress(generationId); }
        catch (org.springframework.dao.EmptyResultDataAccessException absent) {
            throw new com.mediflow.report.application.exception.ReportProjectionUnavailableException();
        }
        var sources = limited("SELECT * FROM financial_source_rebuild_input WHERE generation_id=? ORDER BY source_kind,source_id",
                (rs, row) -> new Source(rs.getString("source_kind"), uuid(rs,"source_id"), uuid(rs,"first_event_id"),
                        rs.getString("source_state"), rs.getString("source_fingerprint"), rs.getString("envelope_fingerprint"),
                        rs.getString("fact_snapshot"), rs.getString("snapshot_fingerprint")), generationId);
        var entries = sources.stream().map(source -> new FinancialEvidenceArchive.Entry(FinancialEvidenceArchive.SourceKind.valueOf(source.kind()),
                source.id(), source.firstEventId(), FinancialEvidenceArchive.SourceState.valueOf(source.state()),
                source.sourceHash(), source.envelopeHash(), source.snapshot(), source.snapshotHash())).toList();
        require(entries.size() == current.sourceInputs());
        return FinancialEvidenceArchive.create(generationId, current.coveredFrom(), current.coveredTo(), current.reportZone(),
                current.sourceObservedAt(), current.unresolvedInputs(), current.manifestFingerprint(), entries);
    }

    @Override public FinancialArchiveRestoreDTO restoreArchive(FinancialEvidenceArchive archive, UUID actorAccountId) {
        transaction(); timeouts(); require(actorAccountId != null && archive != null);
        require(!archive.sourceObservedAt().isAfter(jdbc.queryForObject("SELECT transaction_timestamp()", Timestamp.class).toInstant()));
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "report-financial-archive:" + archive.archiveFingerprint());
        var restored = jdbc.query("SELECT generation_id,origin_generation_id,archive_fingerprint FROM financial_archive_restore WHERE archive_fingerprint=?",
                (rs,row) -> new FinancialArchiveRestoreDTO(uuid(rs,"generation_id"), uuid(rs,"origin_generation_id"), rs.getString("archive_fingerprint")),
                archive.archiveFingerprint());
        if (!restored.isEmpty()) return restored.getFirst();
        UUID generationId = UUID.randomUUID();
        // Source IDs, delivery IDs, times, state and bytes survive unchanged. Only the isolated generation is new.
        jdbc.update("""
                INSERT INTO financial_source_rebuild(generation_id,covered_from,covered_to,report_zone,source_observed_at,
                    source_inputs,unresolved_inputs,manifest_fingerprint) VALUES (?,?,?,?,?,?,?,?)
                """, generationId, Date.valueOf(archive.coveredFrom()), Date.valueOf(archive.coveredTo()), archive.reportZone(),
                Timestamp.from(archive.sourceObservedAt()), archive.entries().size(), archive.unresolvedInputs(), archive.manifestFingerprint());
        jdbc.batchUpdate("""
                INSERT INTO financial_source_rebuild_input(generation_id,source_kind,source_id,first_event_id,source_state,
                    source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint) VALUES (?,?,?,?,?,?,?,?,?)
                """, archive.entries().stream().map(entry -> new Object[] {generationId, entry.sourceKind().name(), entry.sourceId(), entry.firstEventId(),
                    entry.sourceState().name(), entry.sourceFingerprint(), entry.envelopeFingerprint(), entry.factSnapshot(), entry.snapshotFingerprint()}).toList());
        require(jdbc.update("UPDATE financial_source_rebuild SET manifest_sealed=true WHERE generation_id=?", generationId) == 1);
        // All-APPLIED imports must decode and obey the same projector before they can commit any checkpoint.
        // Unresolved inventory is deliberately retained, not relabeled APPLIED or re-submitted to live consumers.
        if (archive.unresolvedInputs() == 0) {
            manifest(progress(generationId)).project(archive.coveredFrom(), archive.coveredTo(), archive.reportZone(), archive.sourceObservedAt());
        }
        jdbc.update("INSERT INTO financial_archive_restore(archive_fingerprint,origin_generation_id,generation_id,actor_account_id) VALUES (?,?,?,?)",
                archive.archiveFingerprint(), archive.originGenerationId(), generationId, actorAccountId);
        return new FinancialArchiveRestoreDTO(generationId, archive.originGenerationId(), archive.archiveFingerprint());
    }

    @Override public FinancialRebuildProgress freeze(UUID id, LocalDate from, LocalDate to, String zone) {
        transaction();
        require("repeatable read".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class)));
        timeouts();
        var observed = jdbc.queryForObject("SELECT transaction_timestamp()", Timestamp.class).toInstant();
        require(!observed.isBefore(to.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant()));
        var sources = new ArrayList<Source>();
        sources.addAll(receipts(zone));
        bounded(sources);
        sources.addAll(refunds(zone));
        bounded(sources);
        sources.addAll(finals(zone));
        bounded(sources);
        sources.addAll(balances(zone));
        bounded(sources);
        sources.sort(Comparator.comparing(Source::kind).thenComparing(source -> source.id().toString()));
        int unresolved = (int) sources.stream().filter(source -> !"APPLIED".equals(source.state())).count();
        jdbc.update("""
                INSERT INTO financial_source_rebuild(generation_id,covered_from,covered_to,report_zone,
                    source_observed_at,source_inputs,unresolved_inputs,manifest_fingerprint) VALUES (?,?,?,?,?,?,?,?)
                """, id, Date.valueOf(from), Date.valueOf(to), zone, Timestamp.from(observed), sources.size(), unresolved, manifestHash(sources));
        jdbc.batchUpdate("""
                INSERT INTO financial_source_rebuild_input(generation_id,source_kind,source_id,first_event_id,source_state,
                    source_fingerprint,envelope_fingerprint,fact_snapshot,snapshot_fingerprint) VALUES (?,?,?,?,?,?,?,?,?)
                """, sources.stream().map(source -> new Object[] {id, source.kind(), source.id(), source.firstEventId(), source.state(),
                    source.sourceHash(), source.envelopeHash(), source.snapshot(), source.snapshotHash()}).toList());
        require(jdbc.update("UPDATE financial_source_rebuild SET manifest_sealed=true WHERE generation_id=? AND NOT manifest_sealed",id) == 1);
        return progress(id);
    }

    @Override public FinancialRebuildProgress lock(UUID id) {
        transaction();
        timeouts();
        if (jdbc.queryForList("SELECT generation_id FROM financial_source_rebuild WHERE generation_id=? FOR UPDATE", UUID.class, id).isEmpty()) {
            throw new com.mediflow.report.application.exception.ReportProjectionUnavailableException();
        }
        return progress(id);
    }

    @Override public FinancialRebuildProgress progress(UUID id) {
        var rows = jdbc.query("SELECT * FROM financial_source_rebuild WHERE generation_id=?", (rs, row) -> {
            require(rs.getInt("snapshot_version") == 1 && rs.getInt("projector_version") == 1);
            require(rs.getBoolean("manifest_sealed"));
            return new FinancialRebuildProgress(id, Status.valueOf(rs.getString("status")), rs.getDate("covered_from").toLocalDate(),
                    rs.getDate("covered_to").toLocalDate(), rs.getString("report_zone"), rs.getTimestamp("source_observed_at").toInstant(),
                    rs.getInt("source_inputs"), rs.getInt("unresolved_inputs"), rs.getInt("processed_days"),
                    rs.getString("manifest_fingerprint"), rs.getString("reason_code"));
        }, id);
        if (rows.isEmpty()) throw new com.mediflow.report.application.exception.ReportProjectionUnavailableException();
        return rows.getFirst();
    }

    // Pure validation errors must let the caller persist FAILED. Database errors still mark rollback-only.
    @Override
    @Transactional(transactionManager = "reportTransactionManager", propagation = Propagation.MANDATORY, noRollbackFor = {ReportRuleException.class, IllegalArgumentException.class})
    public FinancialSourceManifest manifest(FinancialRebuildProgress progress) {
        var sources = limited("SELECT * FROM financial_source_rebuild_input WHERE generation_id=? ORDER BY source_kind,source_id",
                (rs, row) -> new Source(rs.getString("source_kind"), rs.getObject("source_id", UUID.class), rs.getObject("first_event_id", UUID.class), rs.getString("source_state"),
                    rs.getString("source_fingerprint"), rs.getString("envelope_fingerprint"), rs.getString("fact_snapshot"), rs.getString("snapshot_fingerprint")),
                progress.generationId());
        bounded(sources);
        require(sources.size() == progress.sourceInputs() && manifestHash(sources).equals(progress.manifestFingerprint()));
        var receipts = new ArrayList<CashReceipt>();
        var refunds = new ArrayList<CashRefund>();
        var finals = new ArrayList<FinalSettlementRecognition>();
        var balances = new ArrayList<SettlementReceivableSnapshot>();
        for (var source : sources) {
            require("APPLIED".equals(source.state()) && sha256(source.snapshot()).equals(source.snapshotHash()));
            UUID actual;
            switch (source.kind()) {
                case "RECEIPT" -> { var fact = decode(source.snapshot(), CashReceipt.class); receipts.add(fact); actual = fact.transactionId(); }
                case "REFUND" -> { var fact = decode(source.snapshot(), CashRefund.class); refunds.add(fact); actual = fact.refundTransactionId(); }
                case "FINAL" -> { var fact = decode(source.snapshot(), FinalSettlementRecognition.class); finals.add(fact); actual = fact.recognitionOperationId(); }
                case "BALANCE" -> { var fact = decode(source.snapshot(), SettlementReceivableSnapshot.class); balances.add(fact); actual = fact.settlementId(); }
                default -> throw invalid();
            }
            require(source.id().equals(actual));
        }
        return new FinancialSourceManifest(receipts, refunds, finals, balances);
    }

    @Override public void writeDays(FinancialRebuildProgress progress, LocalDate through, List<FinancialSourceProjection.Day> rows) {
        transaction();
        int processed = Math.toIntExact(ChronoUnit.DAYS.between(progress.coveredFrom(), through) + 1);
        require(processed > progress.processedDays() && processed - progress.processedDays() <= 31);
        jdbc.batchUpdate("""
                INSERT INTO financial_source_rebuild_day(generation_id,business_date,department_id,cash_received,
                    deposit_liability,earned_revenue,refunds,outstanding_receivable) VALUES (?,?,?,?,?,?,?,?)
                """, rows.stream().map(row -> new Object[] {progress.generationId(), Date.valueOf(row.businessDate()), row.departmentId(),
                    row.amounts().cashReceived(), row.amounts().depositLiability(), row.amounts().earnedRevenue(),
                    row.amounts().refunds(), row.amounts().outstandingReceivable()}).toList());
        require(jdbc.update("UPDATE financial_source_rebuild SET processed_days=? WHERE generation_id=? AND status='BUILDING' AND processed_days=?",
                processed, progress.generationId(), progress.processedDays()) == 1);
    }

    @Override public FinancialRebuildProgress reconcile(FinancialRebuildProgress progress, List<FinancialSourceProjection.Day> expected) {
        var actual = jdbc.query(connection -> {
            var statement = connection.prepareStatement("SELECT * FROM financial_source_rebuild_day WHERE generation_id=? ORDER BY business_date,department_id NULLS FIRST");
            statement.setObject(1,progress.generationId());
            statement.setMaxRows(366 * 101 + 1);
            statement.setQueryTimeout(5);
            statement.setFetchSize(128);
            return statement;
        }, (rs, row) -> new FinancialSourceProjection.Day(rs.getDate("business_date").toLocalDate(), rs.getObject("department_id", UUID.class),
                    new FinancialAmounts(rs.getBigDecimal("cash_received"), rs.getBigDecimal("deposit_liability"), rs.getBigDecimal("earned_revenue"),
                        rs.getBigDecimal("refunds"), rs.getBigDecimal("outstanding_receivable"))));
        if (!actual.equals(expected) || progress.unresolvedInputs() != 0
                || progress.processedDays() != ChronoUnit.DAYS.between(progress.coveredFrom(), progress.coveredTo()) + 1) {
            return fail(progress.generationId(), "REPORT_FINANCIAL_RECONCILIATION_FAILED");
        }
        // Re-validate copied provenance too; equality of aggregates alone is insufficient.
        manifest(progress);
        jdbc.update("UPDATE financial_source_rebuild SET status='VERIFIED',aggregate_fingerprint=?,reconciled_at=now() WHERE generation_id=? AND status='BUILDING'",
                sha256(encode(expected)), progress.generationId());
        return progress(progress.generationId());
    }

    @Override public FinancialRebuildProgress fail(UUID id, String reasonCode) {
        jdbc.update("UPDATE financial_source_rebuild SET status='FAILED',reason_code=? WHERE generation_id=? AND status='BUILDING'", reasonCode, id);
        return progress(id);
    }

    private List<Source> receipts(String zone) {
        return limited("""
                SELECT r.*,d.envelope_fingerprint FROM report_cash_receipt r LEFT JOIN report_cash_delivery d
                    ON d.event_id=r.first_event_id AND d.transaction_id=r.transaction_id
                WHERE r.currency='VND' AND r.report_zone=? ORDER BY r.transaction_id
                """, (rs, row) -> {
                    var fact = new CashReceipt(uuid(rs,"transaction_id"),uuid(rs,"invoice_id"),uuid(rs,"payment_request_id"),uuid(rs,"account_id"),
                        uuid(rs,"patient_id"),uuid(rs,"department_id"),rs.getString("care_episode_type"),uuid(rs,"care_episode_id"),
                        CashReceipt.Classification.valueOf(rs.getString("classification")),rs.getBigDecimal("amount"),rs.getString("currency"),
                        rs.getString("payment_method"),Instant.parse(rs.getString("completed_at_iso")),rs.getDate("business_date").toLocalDate(),rs.getString("report_zone"));
                    require(originalCodec.fingerprint(fact).equals(rs.getString("fact_fingerprint")));
                    return source("RECEIPT",fact.transactionId(),uuid(rs,"first_event_id"),"APPLIED",rs.getString("payload_fingerprint"),rs.getString("envelope_fingerprint"),fact);
                }, zone);
    }

    /** Caller holds the exclusive publication fence; READ COMMITTED observes prior writers after waiting. */
    public String currentManifestFingerprint(String zone) {
        transaction(); timeouts();
        require("read committed".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class)));
        var sources = new ArrayList<Source>();
        sources.addAll(receipts(zone)); bounded(sources);
        sources.addAll(refunds(zone)); bounded(sources);
        sources.addAll(finals(zone)); bounded(sources);
        sources.addAll(balances(zone)); bounded(sources);
        sources.sort(Comparator.comparing(Source::kind).thenComparing(source -> source.id().toString()));
        return manifestHash(sources);
    }

    private List<Source> refunds(String zone) {
        return limited("""
                SELECT r.*,d.envelope_fingerprint FROM report_cash_refund r LEFT JOIN report_cash_refund_delivery d
                    ON d.event_id=r.first_event_id AND d.refund_transaction_id=r.refund_transaction_id
                WHERE r.currency='VND' AND r.report_zone=? ORDER BY r.refund_transaction_id
                """, (rs, row) -> source("REFUND",uuid(rs,"refund_transaction_id"),uuid(rs,"first_event_id"),rs.getString("state"),rs.getString("source_fingerprint"),rs.getString("envelope_fingerprint"),
                    new CashRefund(uuid(rs,"refund_transaction_id"),uuid(rs,"original_transaction_id"),uuid(rs,"account_id"),uuid(rs,"patient_id"),
                        uuid(rs,"department_id"),rs.getString("care_episode_type"),uuid(rs,"care_episode_id"),rs.getBigDecimal("amount"),rs.getString("currency"),
                        Instant.parse(rs.getString("completed_at_iso")),rs.getDate("business_date").toLocalDate(),rs.getString("report_zone"))), zone);
    }

    private List<Source> finals(String zone) {
        return limited("""
                SELECT r.*,d.envelope_fingerprint FROM report_final_recognition r LEFT JOIN report_final_recognition_delivery d
                    ON d.event_id=r.first_event_id AND d.recognition_operation_id=r.recognition_operation_id
                WHERE r.report_zone=? ORDER BY r.recognition_operation_id
                """, (rs, row) -> {
                    try {
                        Map<String,Object> payload = mapper.readerFor(new TypeReference<Map<String,Object>>() { })
                                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(rs.getString("payload_snapshot"));
                        var id = uuid(rs,"recognition_operation_id");
                        var event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(uuid(rs,"first_event_id"),"settlement.recognition.completed",1,
                            Instant.parse(rs.getString("completed_at_iso")),"frozen-financial-source","billing-service","recognitionOperationId",id),payload);
                        var fact = new BillingFinalSettlementRecognitionMapper(ZoneId.of(zone)).map(event);
                        require(originalCodec.evidenceFingerprint(payload).equals(rs.getString("payload_fingerprint"))
                            && originalCodec.evidenceFingerprint(Map.of("payload",payload,"reportZone",zone,"businessDate",fact.businessDate().toString())).equals(rs.getString("fact_fingerprint"))
                            && fact.businessDate().equals(rs.getDate("business_date").toLocalDate())
                            && fact.accountId().equals(uuid(rs,"account_id")) && fact.settlementId().equals(uuid(rs,"settlement_id")));
                        return source("FINAL",id,uuid(rs,"first_event_id"),rs.getString("state"),rs.getString("payload_fingerprint"),rs.getString("envelope_fingerprint"),fact);
                    } catch (JsonProcessingException error) { throw invalid(); }
                }, zone);
    }

    private List<Source> balances(String zone) {
        // V19 retains every accepted revision, not only report_receivable_current (which loses history).
        return limited("""
                SELECT s.*,d.first_event_id,d.envelope_fingerprint FROM report_settlement_snapshot s
                LEFT JOIN LATERAL (SELECT event_id AS first_event_id,envelope_fingerprint FROM report_settlement_delivery
                    WHERE settlement_id=s.settlement_id ORDER BY accepted_at,event_id LIMIT 1) d ON true
                WHERE s.payload::jsonb->>'currency'='VND' AND s.payload::jsonb->>'reportZone'=? ORDER BY s.settlement_id
                """, (rs, row) -> {
                    var fact = decode(rs.getString("payload"),SettlementReceivableSnapshot.class);
                    require(rs.getInt("snapshot_version") == 1 && fact.settlementId().equals(uuid(rs,"settlement_id"))
                        && fact.accountId().equals(uuid(rs,"account_id")) && fact.settlementVersion() == rs.getInt("settlement_version")
                        && java.util.Objects.equals(fact.supersedesSettlementId(),uuid(rs,"supersedes_settlement_id"))
                        && originalCodec.evidenceFingerprint(fact).equals(rs.getString("snapshot_fingerprint")));
                    return source("BALANCE",fact.settlementId(),uuid(rs,"first_event_id"),"APPLIED",rs.getString("fact_fingerprint"),rs.getString("envelope_fingerprint"),fact);
                }, zone);
    }

    private List<Source> limited(String sql, RowMapper<Source> rowMapper, Object argument) {
        long[] bytes = {0};
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setObject(1,argument);
            statement.setMaxRows(MAX_INPUTS + 1);
            statement.setQueryTimeout(5);
            statement.setFetchSize(128);
            return statement;
        }, (rs, row) -> {
            require(row < MAX_INPUTS);
            var source = rowMapper.mapRow(rs,row);
            bytes[0] += source.snapshot().getBytes(StandardCharsets.UTF_8).length;
            require(bytes[0] <= MAX_MANIFEST_BYTES);
            return source;
        });
    }

    private Source source(String kind, UUID id, UUID firstEventId, String state, String sourceHash, String envelopeHash, Object fact) {
        require(firstEventId != null && sourceHash != null && sourceHash.matches("[a-f0-9]{64}") && envelopeHash != null && envelopeHash.matches("[a-f0-9]{64}"));
        String snapshot = encode(fact);
        require(snapshot.getBytes(StandardCharsets.UTF_8).length <= 1_048_576);
        return new Source(kind,id,firstEventId,state,sourceHash,envelopeHash,snapshot,sha256(snapshot));
    }

    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); } catch (JsonProcessingException error) { throw invalid(); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return mapper.readValue(value,type); } catch (JsonProcessingException error) { throw invalid(); }
    }
    private static String manifestHash(List<Source> sources) {
        var proof = new StringBuilder();
        for (var source : sources) proof.append(source.kind()).append(':').append(source.id()).append(':').append(source.firstEventId()).append(':').append(source.state()).append(':')
            .append(source.sourceHash()).append(':').append(source.envelopeHash()).append(':').append(source.snapshotHash()).append('\n');
        return sha256(proof.toString());
    }
    private static void bounded(List<Source> sources) {
        require(sources.size() <= MAX_INPUTS && sources.stream().mapToLong(source -> source.snapshot().getBytes(StandardCharsets.UTF_8).length).sum() <= MAX_MANIFEST_BYTES);
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable",error); }
    }
    private void timeouts() { jdbc.execute("SET LOCAL lock_timeout='3s'"); jdbc.execute("SET LOCAL statement_timeout='5s'"); }
    private static UUID uuid(ResultSet rs, String name) throws SQLException { return rs.getObject(name,UUID.class); }
    private static void transaction() { if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Financial rebuild requires a transaction"); }
    private static void require(boolean valid) { if (!valid) throw invalid(); }
    private static ReportRuleException invalid() { return new ReportRuleException("REPORT_FINANCIAL_MANIFEST_INVALID","Invalid or incomplete frozen financial evidence"); }
}
