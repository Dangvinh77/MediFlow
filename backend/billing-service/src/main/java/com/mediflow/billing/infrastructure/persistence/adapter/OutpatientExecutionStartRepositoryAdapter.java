package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.dto.response.OutpatientExecutionStartDTO;
import com.mediflow.billing.application.port.out.OutpatientExecutionStartRepositoryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OutpatientExecutionStartRepositoryAdapter implements OutpatientExecutionStartRepositoryPort {
    private final JdbcTemplate jdbc;
    public OutpatientExecutionStartRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<UUID> lockAccount(UUID clearanceId) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        var accounts = jdbc.queryForList("SELECT account_id FROM FINANCIAL_CLEARANCE WHERE clearance_id=?", UUID.class, clearanceId);
        if (accounts.isEmpty()) return Optional.empty();
        UUID accountId = accounts.getFirst();
        jdbc.queryForMap("SELECT account_id FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", accountId);
        return Optional.of(accountId);
    }
    @Override public Optional<OutpatientExecutionStartDTO> find(UUID operationId) {
        return jdbc.query("SELECT * FROM outpatient_execution_start WHERE operation_id=?", (rs,row) -> new OutpatientExecutionStartDTO(
                rs.getObject("operation_id", UUID.class), rs.getObject("clearance_id", UUID.class), rs.getString("source_type"),
                rs.getObject("source_id", UUID.class), rs.getString("state"), rs.getTimestamp("started_at").toInstant()), operationId)
                .stream().findFirst();
    }
    @Override public OutpatientExecutionStartDTO save(UUID clearanceId, UUID accountId,
            StartOutpatientExecutionCommand c, Instant startedAt) {
        jdbc.update("""
                INSERT INTO outpatient_execution_start(operation_id,clearance_id,account_id,source_type,source_id,patient_id,
                    care_episode_type,care_episode_id,state,started_at)
                VALUES (?,?,?,?,?,?,?,?, 'STARTED',?)
                """, c.operationId(), clearanceId, accountId, c.sourceType(), c.sourceId(), c.patientId(),
                c.careEpisodeType(), c.careEpisodeId(), Timestamp.from(startedAt));
        return new OutpatientExecutionStartDTO(c.operationId(), clearanceId, c.sourceType(), c.sourceId(), "STARTED", startedAt);
    }
}
