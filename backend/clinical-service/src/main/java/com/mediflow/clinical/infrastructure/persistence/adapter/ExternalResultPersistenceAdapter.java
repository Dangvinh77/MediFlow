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
    public boolean attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary) {
        return repository.insertIfAbsent(recordId, type.name(), referenceId, summary) == 1;
    }

    @Override
    public List<AttachedDocument> findByRecord(UUID recordId) {
        return jdbc.query("""
                SELECT type, reference_id, summary, created_at FROM attached_result
                WHERE record_id = ? ORDER BY created_at, type, reference_id
                """, (rs, n) -> new AttachedDocument(rs.getString("type"), rs.getObject("reference_id", UUID.class),
                rs.getString("summary"), rs.getTimestamp("created_at").toInstant()), recordId);
    }
}
