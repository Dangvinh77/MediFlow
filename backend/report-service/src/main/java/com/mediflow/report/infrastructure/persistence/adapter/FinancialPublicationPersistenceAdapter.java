package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.dto.response.FinancialRebuildProgress;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.out.FinancialPublicationStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.FinancialAmounts;
import com.mediflow.report.domain.model.FinancialSourceProjection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Finite available-history publication. Never certifies upstream completeness or releases events. */
@Component
@Transactional(transactionManager = "reportTransactionManager", propagation=Propagation.MANDATORY)
public class FinancialPublicationPersistenceAdapter implements FinancialPublicationStorePort {
    private final JdbcTemplate jdbc;
    private final FinancialRebuildPersistenceAdapter rebuild;
    private final ObjectMapper mapper;

    public FinancialPublicationPersistenceAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, FinancialRebuildPersistenceAdapter rebuild, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.rebuild = rebuild;
        this.mapper = mapper.copy().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override public FinancialPublicationDTO publish(PublishFinancialSnapshotRequest request, UUID actor) {
        fence();
        String fingerprint = hash(encode(List.of("PUBLISH", request, actor)));
        var retry = retry(request.commandId(), fingerprint);
        if (retry != null) return retry;
        var candidate = candidate(request.generationId());
        require(candidate.status() == FinancialRebuildProgress.Status.VERIFIED && candidate.unresolvedInputs() == 0
                && candidate.sourceInputs() > 0, "REPORT_PUBLICATION_UNVERIFIED");
        require(request.manifestFingerprint().equals(candidate.manifestFingerprint()), "REPORT_PUBLICATION_PROOF_MISMATCH");
        UUID previous = current(candidate.reportZone());
        require(Objects.equals(previous, request.expectedCurrentGenerationId()), "REPORT_PUBLICATION_POINTER_CONFLICT");
        // The exclusive fence is acquired BEFORE this fresh RC source read: no old MVCC snapshot after lock wait.
        require(candidate.manifestFingerprint().equals(rebuild.currentManifestFingerprint(candidate.reportZone())),
                "REPORT_PUBLICATION_CATCHUP_REQUIRED");
        var manifest = rebuild.manifest(candidate);
        manifest.requireKnownHistory(candidate.coveredFrom(), candidate.reportZone());
        var expected = manifest.project(candidate.coveredFrom(), candidate.coveredTo(),
                candidate.reportZone(), candidate.sourceObservedAt());
        var actual = days(candidate.generationId());
        require(expected.equals(actual), "REPORT_PUBLICATION_RECONCILIATION_FAILED");
        var header = jdbc.queryForMap("SELECT aggregate_fingerprint,reconciled_at FROM financial_source_rebuild WHERE generation_id=?", candidate.generationId());
        require(hash(encode(expected)).equals(header.get("aggregate_fingerprint")), "REPORT_PUBLICATION_RECONCILIATION_FAILED");
        copy(candidate);
        // A generation's acceptance never changes when it is later selected again.
        jdbc.update("""
                INSERT INTO financial_generation_acceptance(generation_id,manifest_fingerprint,history_policy,acceptance_reference,actor_account_id)
                VALUES (?,?,?,?,?) ON CONFLICT (generation_id) DO NOTHING
                """, candidate.generationId(), candidate.manifestFingerprint(), request.historyPolicy().name(), request.acceptanceReference(), actor);
        return switchPointer(request.commandId(), candidate.generationId(), previous, candidate.reportZone(),
                request.acceptanceReference(), actor, "PUBLISH", fingerprint);
    }

    @Override public FinancialPublicationDTO rollback(RollbackFinancialSnapshotRequest request, UUID actor) {
        fence();
        String fingerprint = hash(encode(List.of("ROLLBACK", request, actor)));
        var retry = retry(request.commandId(), fingerprint);
        if (retry != null) return retry;
        var targets = jdbc.queryForList("""
                SELECT g.report_zone FROM financial_generation_acceptance a JOIN financial_report_generation g USING(generation_id)
                WHERE a.generation_id=? AND g.status='VERIFIED' AND g.currency='VND'
                  AND EXISTS(SELECT 1 FROM financial_publication_audit h WHERE h.generation_id=g.generation_id AND h.action='PUBLISH')
                """, String.class, request.generationId());
        require(targets.size() == 1, "REPORT_ROLLBACK_TARGET_UNAVAILABLE");
        UUID previous = current(targets.getFirst());
        require(Objects.equals(previous, request.expectedCurrentGenerationId()) && !Objects.equals(previous, request.generationId()),
                "REPORT_PUBLICATION_POINTER_CONFLICT");
        // Intentional rollback has older finite as-of evidence; never relabel it caught-up/current.
        return switchPointer(request.commandId(), request.generationId(), previous, targets.getFirst(),
                request.acceptanceReference(), actor, "ROLLBACK", fingerprint);
    }

    private void copy(FinancialRebuildProgress candidate) {
        int inserted = jdbc.update("""
                INSERT INTO financial_report_generation(generation_id,status,snapshot_version,projector_version,covered_from,covered_to,
                    currency,report_zone,source_observed_at,source_inputs,applied_inputs,unresolved_inputs,manifest_fingerprint)
                SELECT generation_id,'BUILDING',snapshot_version,projector_version,covered_from,covered_to,'VND',report_zone,
                    source_observed_at,source_inputs,0,0,manifest_fingerprint FROM financial_source_rebuild WHERE generation_id=?
                ON CONFLICT (generation_id) DO NOTHING
                """, candidate.generationId());
        if (inserted == 0) {
            var existing = jdbc.queryForMap("SELECT * FROM financial_report_generation WHERE generation_id=?", candidate.generationId());
            require("VERIFIED".equals(existing.get("status")) && candidate.manifestFingerprint().equals(existing.get("manifest_fingerprint"))
                    && "VND".equals(existing.get("currency")) && candidate.reportZone().equals(existing.get("report_zone"))
                    && java.sql.Date.valueOf(candidate.coveredFrom()).equals(existing.get("covered_from"))
                    && java.sql.Date.valueOf(candidate.coveredTo()).equals(existing.get("covered_to"))
                    && Timestamp.from(candidate.sourceObservedAt()).equals(existing.get("source_observed_at"))
                    && ((Number) existing.get("snapshot_version")).intValue() == 1 && ((Number) existing.get("projector_version")).intValue() == 1
                    && ((Number) existing.get("source_inputs")).longValue() == candidate.sourceInputs()
                    && ((Number) existing.get("applied_inputs")).longValue() == candidate.sourceInputs()
                    && ((Number) existing.get("unresolved_inputs")).longValue() == 0
                    && Objects.equals(existing.get("aggregate_fingerprint"), jdbc.queryForObject("SELECT aggregate_fingerprint FROM financial_source_rebuild WHERE generation_id=?", String.class, candidate.generationId()))
                    && days(candidate.generationId()).equals(days(candidate.generationId(), "financial_report_day")),
                    "REPORT_PUBLICATION_RECONCILIATION_FAILED");
            return;
        }
        jdbc.update("""
                INSERT INTO financial_report_day SELECT generation_id,business_date,department_id,cash_received,deposit_liability,
                    earned_revenue,refunds,outstanding_receivable FROM financial_source_rebuild_day WHERE generation_id=?
                """, candidate.generationId());
        require(days(candidate.generationId(), "financial_source_rebuild_day").equals(days(candidate.generationId(), "financial_report_day")),
                "REPORT_PUBLICATION_RECONCILIATION_FAILED");
        require(jdbc.update("""
                UPDATE financial_report_generation g SET status='VERIFIED',applied_inputs=g.source_inputs,
                    aggregate_fingerprint=s.aggregate_fingerprint,reconciled_at=s.reconciled_at
                FROM financial_source_rebuild s WHERE g.generation_id=s.generation_id AND g.generation_id=? AND g.status='BUILDING'
                """, candidate.generationId()) == 1, "REPORT_PUBLICATION_RECONCILIATION_FAILED");
    }

    private FinancialPublicationDTO switchPointer(UUID commandId, UUID generationId, UUID previous, String zone,
            String reference, UUID actor, String action, String fingerprint) {
        var publishedAt = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        jdbc.update("""
                INSERT INTO financial_report_publication(currency,report_zone,generation_id,acceptance_reference,published_at)
                VALUES ('VND',?,?,?,?) ON CONFLICT(currency,report_zone) DO UPDATE SET generation_id=EXCLUDED.generation_id,
                    acceptance_reference=EXCLUDED.acceptance_reference,published_at=EXCLUDED.published_at
                """, zone, generationId, reference, Timestamp.from(publishedAt));
        jdbc.update("""
                INSERT INTO financial_publication_audit(command_id,generation_id,previous_generation_id,action,request_fingerprint,
                    acceptance_reference,actor_account_id,published_at) VALUES (?,?,?,?,?,?,?,?)
                """, commandId, generationId, previous, action, fingerprint, reference, actor, Timestamp.from(publishedAt));
        return new FinancialPublicationDTO(commandId, generationId, previous, publishedAt, action);
    }

    private FinancialPublicationDTO retry(UUID commandId, String fingerprint) {
        var rows = jdbc.queryForList("SELECT * FROM financial_publication_audit WHERE command_id=?", commandId);
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        require(fingerprint.equals(row.get("request_fingerprint")), "REPORT_PUBLICATION_COMMAND_CONFLICT");
        return new FinancialPublicationDTO(commandId, (UUID) row.get("generation_id"), (UUID) row.get("previous_generation_id"),
                ((Timestamp) row.get("published_at")).toInstant(), (String) row.get("action"));
    }

    private UUID current(String zone) {
        return jdbc.queryForList("SELECT generation_id FROM financial_report_publication WHERE currency='VND' AND report_zone=?", UUID.class, zone)
                .stream().findFirst().orElse(null);
    }

    private FinancialRebuildProgress candidate(UUID id) {
        if (jdbc.queryForList("SELECT generation_id FROM financial_source_rebuild WHERE generation_id=?", UUID.class, id).isEmpty()) {
            throw new ReportProjectionUnavailableException();
        }
        return rebuild.progress(id);
    }

    private List<FinancialSourceProjection.Day> days(UUID id) {
        return days(id, "financial_source_rebuild_day");
    }
    private List<FinancialSourceProjection.Day> days(UUID id, String table) {
        // Table names are private fixed constants, never user-controlled SQL.
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE generation_id=? ORDER BY business_date,department_id NULLS FIRST");
            statement.setObject(1, id); statement.setMaxRows(366 * 101 + 1); statement.setFetchSize(128); statement.setQueryTimeout(5);
            return statement;
        }, (rs, index) -> new FinancialSourceProjection.Day(rs.getDate("business_date").toLocalDate(), rs.getObject("department_id", UUID.class),
                new FinancialAmounts(rs.getBigDecimal("cash_received"), rs.getBigDecimal("deposit_liability"), rs.getBigDecimal("earned_revenue"),
                        rs.getBigDecimal("refunds"), rs.getBigDecimal("outstanding_receivable"))));
    }

    private void fence() {
        FinancialPublicationFence.publisher(jdbc);
        require("read committed".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class)), "REPORT_PUBLICATION_ISOLATION_INVALID");
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new ReportRuleException("REPORT_PUBLICATION_INVALID", "Invalid publication evidence"); }
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new ReportRuleException(code, "Publication evidence, catch-up or expected pointer is not valid");
    }
}
