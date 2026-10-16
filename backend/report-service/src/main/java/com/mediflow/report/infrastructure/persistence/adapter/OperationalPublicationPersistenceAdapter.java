package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.port.out.OperationalPublicationStorePort;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** SURGERY finite snapshots only. No census, capacity, upstream completeness or held release. */
@Component
@Transactional(transactionManager = "reportTransactionManager", propagation=Propagation.MANDATORY)
public class OperationalPublicationPersistenceAdapter implements OperationalPublicationStorePort {
    private final JdbcTemplate jdbc;
    private final OperationalReplayPersistenceAdapter replay;
    private final ObjectMapper mapper;
    private final OperationalSnapshotCodec snapshots;
    private final ZoneId zone;

    public OperationalPublicationPersistenceAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, OperationalReplayPersistenceAdapter replay,
            ObjectMapper mapper, @org.springframework.beans.factory.annotation.Qualifier("reportZoneId") ZoneId zone) {
        this.jdbc = jdbc; this.replay = replay; this.mapper = mapper.copy().findAndRegisterModules();
        this.snapshots = new OperationalSnapshotCodec(mapper); this.zone = zone;
    }

    @Override public OperationalPublicationCandidateDTO candidate(UUID id) {
        if (id == null || jdbc.queryForList("SELECT generation_id FROM operational_replay_generation WHERE generation_id=?", UUID.class, id).isEmpty()) {
            throw new ReportProjectionUnavailableException();
        }
        return new OperationalPublicationCandidateDTO(replay.progress(id), fingerprint(id));
    }

    @Override public OperationalPublicationDTO publish(PublishOperationalSnapshotRequest request, UUID actor) {
        OperationalPublicationFence.publisher(jdbc);
        String intent = hash(List.of("PUBLISH", request, actor));
        var retry = retry(request.commandId(), intent);
        if (retry != null) return retry;
        var candidate = candidate(request.generationId());
        require(candidate.progress().sourceEvents() > 0 && replay.verifiedIntegrity(request.generationId()), "OPERATIONAL_PUBLICATION_UNVERIFIED");
        require(candidate.manifestFingerprint().equals(request.manifestFingerprint()), "OPERATIONAL_PUBLICATION_PROOF_MISMATCH");
        UUID previous = current();
        require(Objects.equals(previous, request.expectedCurrentGenerationId()), "OPERATIONAL_PUBLICATION_POINTER_CONFLICT");
        // Exclusive lock precedes a READ COMMITTED read of all source bytes/provenance, after writer waits.
        require(candidate.manifestFingerprint().equals(fingerprint(null)), "OPERATIONAL_PUBLICATION_CATCHUP_REQUIRED");
        var first = jdbc.queryForObject("""
                SELECT min((f->>'metricDate')::date) FROM operational_replay_input i,
                LATERAL jsonb_array_elements(i.contribution_snapshot) f WHERE i.generation_id=?
                AND f->>'metric' IN ('SURGERIES_COMPLETED','SURGERIES_CANCELLED','SURGERY_DURATION_MINUTES')
                """, Date.class, request.generationId());
        require(first != null && !request.coveredFrom().isBefore(first.toLocalDate()), "OPERATIONAL_PUBLICATION_HISTORY_UNAVAILABLE");
        var accepted = jdbc.queryForList("SELECT * FROM operational_generation_acceptance WHERE generation_id=?", request.generationId());
        if (accepted.isEmpty()) {
            jdbc.update("""
                    INSERT INTO operational_generation_acceptance(generation_id,report_kind,manifest_fingerprint,
                        covered_from,covered_to,zone_id,history_policy,acceptance_reference,actor_account_id)
                    VALUES (?,'SURGERY',?,?,?,?,'AVAILABLE_EVIDENCE_ONLY',?,?)
                    """, request.generationId(), request.manifestFingerprint(), Date.valueOf(request.coveredFrom()),
                    Date.valueOf(request.coveredTo()), zone.getId(), request.acceptanceReference(), actor);
        } else {
            var old = accepted.getFirst();
            require(request.manifestFingerprint().equals(old.get("manifest_fingerprint"))
                    && Date.valueOf(request.coveredFrom()).equals(old.get("covered_from"))
                    && Date.valueOf(request.coveredTo()).equals(old.get("covered_to")) && zone.getId().equals(old.get("zone_id")),
                    "OPERATIONAL_PUBLICATION_ACCEPTANCE_CONFLICT");
        }
        return switchPointer(request.commandId(), request.generationId(), previous, request.acceptanceReference(), actor, "PUBLISH", intent);
    }

    @Override public OperationalPublicationDTO rollback(RollbackOperationalSnapshotRequest request, UUID actor) {
        OperationalPublicationFence.publisher(jdbc);
        String intent = hash(List.of("ROLLBACK", request, actor));
        var retry = retry(request.commandId(), intent);
        if (retry != null) return retry;
        var accepted = jdbc.queryForList("""
                SELECT generation_id FROM operational_generation_acceptance a WHERE generation_id=? AND zone_id=?
                AND EXISTS(SELECT 1 FROM operational_publication_audit h WHERE h.generation_id=a.generation_id AND action='PUBLISH')
                """, UUID.class, request.generationId(), zone.getId());
        require(accepted.size() == 1 && replay.verifiedIntegrity(request.generationId()), "OPERATIONAL_ROLLBACK_TARGET_UNAVAILABLE");
        UUID previous = current();
        require(Objects.equals(previous, request.expectedCurrentGenerationId()) && !Objects.equals(previous, request.generationId()),
                "OPERATIONAL_PUBLICATION_POINTER_CONFLICT");
        return switchPointer(request.commandId(), request.generationId(), previous, request.acceptanceReference(), actor, "ROLLBACK", intent);
    }

    private OperationalPublicationDTO switchPointer(UUID command, UUID id, UUID previous, String reference, UUID actor,
            String action, String intent) {
        var time = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        jdbc.update("""
                INSERT INTO operational_report_publication(report_kind,generation_id,covered_from,covered_to,zone_id,metrics,acceptance_reference)
                SELECT 'SURGERY',generation_id,covered_from,covered_to,zone_id,
                    ARRAY['SURGERIES_COMPLETED','SURGERIES_CANCELLED','SURGERY_DURATION_MINUTES']::varchar[],acceptance_reference
                FROM operational_generation_acceptance WHERE generation_id=?
                ON CONFLICT(report_kind) DO UPDATE SET generation_id=EXCLUDED.generation_id,covered_from=EXCLUDED.covered_from,
                    covered_to=EXCLUDED.covered_to,zone_id=EXCLUDED.zone_id,metrics=EXCLUDED.metrics,
                    acceptance_reference=EXCLUDED.acceptance_reference,created_at=EXCLUDED.created_at
                """, id);
        jdbc.update("""
                INSERT INTO operational_publication_audit(command_id,generation_id,previous_generation_id,action,
                    request_fingerprint,acceptance_reference,actor_account_id,published_at) VALUES (?,?,?,?,?,?,?,?)
                """, command, id, previous, action, intent, reference, actor, Timestamp.from(time));
        return new OperationalPublicationDTO(command, id, previous, time, action);
    }

    private UUID current() {
        var rows = jdbc.queryForList("SELECT generation_id FROM operational_report_publication WHERE report_kind='SURGERY'", UUID.class);
        if (rows.isEmpty()) return null;
        UUID id = rows.getFirst();
        // Old manual publications have no operator acceptance; never silently adopt their history.
        require(!jdbc.queryForList("SELECT generation_id FROM operational_generation_acceptance WHERE generation_id=?", UUID.class, id).isEmpty(),
                "OPERATIONAL_PUBLICATION_UNACCEPTED_POINTER");
        return id;
    }

    private OperationalPublicationDTO retry(UUID command, String intent) {
        var rows = jdbc.queryForList("SELECT * FROM operational_publication_audit WHERE command_id=?", command);
        if (rows.isEmpty()) return null;
        var old = rows.getFirst();
        require(intent.equals(old.get("request_fingerprint")), "OPERATIONAL_PUBLICATION_COMMAND_CONFLICT");
        return new OperationalPublicationDTO(command, (UUID) old.get("generation_id"), (UUID) old.get("previous_generation_id"),
                ((Timestamp) old.get("published_at")).toInstant(), (String) old.get("action"));
    }

    private String fingerprint(UUID id) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
        String table = id == null ? "operational_event_journal" : "operational_replay_input";
        String sql = "SELECT event_id,event_snapshot::text,contribution_snapshot::text,projection_fingerprint,projector_version,envelope_fingerprint,"
                + "jsonb_build_array(event_id,event_snapshot,contribution_snapshot,envelope_fingerprint,projection_fingerprint,projector_version)::text proof FROM "
                + table + (id == null ? "" : " WHERE generation_id=?") + " ORDER BY event_id";
        jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            if (id != null) statement.setObject(1, id);
            statement.setFetchSize(128); statement.setQueryTimeout(5);
            return statement;
        }, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            require(rs.getString("envelope_fingerprint").matches("[a-f0-9]{64}"), "OPERATIONAL_PUBLICATION_PROOF_MISMATCH");
            var decoded = snapshots.decode(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5));
            for (var fact : decoded.contributions()) {
                if (fact.metric() == Metric.SURGERIES_COMPLETED || fact.metric() == Metric.SURGERIES_CANCELLED || fact.metric() == Metric.SURGERY_DURATION_MINUTES) {
                    require(fact.metricDate().equals(fact.occurredAt().atZone(zone).toLocalDate()), "OPERATIONAL_PUBLICATION_ZONE_MISMATCH");
                }
            }
            digest.update(rs.getString("proof").getBytes(StandardCharsets.UTF_8)); digest.update((byte) '\n');
        });
        return HexFormat.of().formatHex(digest.digest());
    }

    private String hash(Object value) {
        try { return OperationalSnapshotCodec.hash(mapper.writeValueAsString(value)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalArgumentException("Invalid publication intent", exception); }
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new ReportRuleException(code, "Publication evidence, coverage or pointer is not valid");
    }
}
