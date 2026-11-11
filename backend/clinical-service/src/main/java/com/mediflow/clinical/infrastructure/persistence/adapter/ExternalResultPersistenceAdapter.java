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
        return jdbc.update("""
                INSERT INTO attached_result(record_id, type, reference_id, summary, source_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (record_id, type, reference_id) DO NOTHING
                """, recordId, type.name(), referenceId, summary,
                sourceAt == null ? null : java.sql.Timestamp.from(sourceAt)) == 1;
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
