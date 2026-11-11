package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.repository.AttachedResultJpaRepository;

@Component
public class ExternalResultPersistenceAdapter implements ExternalResultRepositoryPort {

    private final AttachedResultJpaRepository repository;
    private final JdbcTemplate jdbc;

    public ExternalResultPersistenceAdapter(AttachedResultJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public boolean attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary,
            java.time.Instant sourceAt) {
        java.sql.Timestamp at = sourceAt == null ? null : java.sql.Timestamp.from(sourceAt);
        // An amendment delivered before its original carried a later time; the original restores the
        // earliest source time without replacing the newer summary.
        boolean attached = jdbc.update("""
                INSERT INTO attached_result(record_id, type, reference_id, summary, source_at, result_version)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (record_id, type, reference_id) DO UPDATE SET source_at = EXCLUDED.source_at
                WHERE EXCLUDED.type = 'LAB' AND EXCLUDED.source_at IS NOT NULL
                  AND (attached_result.source_at IS NULL OR EXCLUDED.source_at < attached_result.source_at)
                """, recordId, type.name(), referenceId, summary, at,
                type == ExternalResultType.LAB ? 1 : null) == 1;
        if (type != ExternalResultType.LAB) {
            return attached;
        }
        // An amendment may have arrived first; the original still joins the history without replacing it.
        boolean recorded = insertRevision(recordId, referenceId, 1, summary, null, at);
        return attached || recorded;
    }

    @Override
    public boolean attachLabRevision(UUID recordId, UUID labId, int resultVersion, String summary,
            String amendmentReason, java.time.Instant sourceAt) {
        if (resultVersion < 2) {
            throw new IllegalArgumentException("An amended Lab revision starts at 2");
        }
        java.sql.Timestamp at = sourceAt == null ? null : java.sql.Timestamp.from(sourceAt);
        boolean recorded = insertRevision(recordId, labId, resultVersion, summary, amendmentReason, at);
        // source_at stays the original report time so documents keep placing the result inside the visit.
        jdbc.update("""
                INSERT INTO attached_result(record_id, type, reference_id, summary, source_at, result_version)
                VALUES (?, 'LAB', ?, ?, ?, ?)
                ON CONFLICT (record_id, type, reference_id) DO UPDATE
                SET summary = EXCLUDED.summary, result_version = EXCLUDED.result_version
                WHERE COALESCE(attached_result.result_version, 1) < EXCLUDED.result_version
                """, recordId, labId, summary, at, resultVersion);
        return recorded;
    }

    @Override
    public List<LabResultRevision> labResultRevisions(UUID recordId, UUID labId) {
        return jdbc.query("""
                SELECT result_version, summary, amendment_reason, source_at FROM attached_result_revision
                WHERE record_id = ? AND reference_id = ? ORDER BY result_version
                """, (rs, n) -> new LabResultRevision(rs.getInt("result_version"), rs.getString("summary"),
                rs.getString("amendment_reason"),
                rs.getTimestamp("source_at") == null ? null : rs.getTimestamp("source_at").toInstant()),
                recordId, labId);
    }

    private boolean insertRevision(UUID recordId, UUID labId, int resultVersion, String summary,
            String amendmentReason, java.sql.Timestamp at) {
        return jdbc.update("""
                INSERT INTO attached_result_revision(record_id, reference_id, result_version, summary,
                                                     amendment_reason, source_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (record_id, reference_id, result_version) DO NOTHING
                """, recordId, labId, resultVersion, summary, amendmentReason, at) == 1;
    }

    @Override
    public List<AttachedDocument> findByRecord(UUID recordId) {
        return jdbc.query("""
                SELECT type, reference_id, summary, COALESCE(source_at, created_at) AS created_at FROM attached_result
                WHERE record_id = ? ORDER BY 4, type, reference_id
                """, (rs, n) -> new AttachedDocument(rs.getString("type"), rs.getObject("reference_id", UUID.class),
                rs.getString("summary"), rs.getTimestamp("created_at").toInstant()), recordId);
    }
}
