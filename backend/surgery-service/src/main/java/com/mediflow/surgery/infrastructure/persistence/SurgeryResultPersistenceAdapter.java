package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryResult;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryResultPersistenceAdapter implements SurgeryResultRepositoryPort {

    private final SurgeryCaseJpaRepository cases;
    private final JdbcTemplate jdbc;

    public SurgeryResultPersistenceAdapter(SurgeryCaseJpaRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SurgeryResult> findByCaseId(UUID caseId) {
        if (caseId == null) return Optional.empty();
        List<SurgeryResult> found = jdbc.query("""
                SELECT result_id, surgery_case_id, procedure_code, method_code, treatment_outcome_code,
                       complication_group_code, actual_start_at, actual_end_at, recorded_at,
                       actor_type, account_id, staff_id, system_producer, correlation_id
                FROM surgery_result WHERE surgery_case_id = ?
                """, (rs, ignored) -> toDomain(rs), caseId);
        return found.stream().findFirst();
    }

    @Override
    public SurgeryResult create(SurgeryResult result) {
        requireTransaction();
        if (result == null) throw new IllegalArgumentException("Surgery result is required");
        SurgeryCaseJpaEntity surgeryCase = cases.lockById(result.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.status != SurgeryStatus.IN_PROGRESS) {
            throw new SurgeryRevisionConflictException();
        }
        Optional<SurgeryResult> existing = findByCaseId(result.surgeryCaseId());
        if (existing.isPresent()) {
            if (!existing.get().equals(result)) throw new SurgeryRevisionConflictException();
            return existing.get();
        }
        SurgeryAuditActor actor = result.recordedBy();
        jdbc.update("""
                INSERT INTO surgery_result
                (result_id, surgery_case_id, procedure_code, method_code, treatment_outcome_code,
                 complication_group_code, actual_start_at, actual_end_at, recorded_at,
                 actor_type, account_id, staff_id, system_producer, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, result.resultId(), result.surgeryCaseId(), result.procedureCode(), result.methodCode(),
                result.treatmentOutcomeCode(), result.complicationGroupCode(),
                Timestamp.from(result.actualStartAt()), Timestamp.from(result.actualEndAt()),
                Timestamp.from(result.recordedAt()), actor.actorType().name(), actor.accountId(),
                actor.verifiedStaffId(), actor.systemProducer(), result.correlationId());
        for (SurgeryPerformedItem item : result.performedItems()) {
            jdbc.update("""
                    INSERT INTO surgery_performed_item
                    (performed_item_id, result_id, item_code, price_code, quantity)
                    VALUES (?, ?, ?, ?, ?)
                    """, item.performedItemId(), result.resultId(), item.itemCode(),
                    item.priceCode(), item.quantity());
        }
        return result;
    }

    private SurgeryResult toDomain(ResultSet rs) throws SQLException {
        UUID resultId = rs.getObject("result_id", UUID.class);
        List<SurgeryPerformedItem> items = jdbc.query("""
                SELECT performed_item_id, item_code, price_code, quantity
                FROM surgery_performed_item WHERE result_id = ? ORDER BY performed_item_id
                """, (item, ignored) -> new SurgeryPerformedItem(
                item.getObject("performed_item_id", UUID.class), item.getString("item_code"),
                item.getString("price_code"), item.getBigDecimal("quantity")), resultId);
        return new SurgeryResult(resultId, rs.getObject("surgery_case_id", UUID.class),
                rs.getString("procedure_code"), rs.getString("method_code"),
                rs.getString("treatment_outcome_code"), rs.getString("complication_group_code"),
                rs.getTimestamp("actual_start_at").toInstant(), rs.getTimestamp("actual_end_at").toInstant(),
                items, rs.getTimestamp("recorded_at").toInstant(), actor(rs), rs.getString("correlation_id"));
    }

    private static SurgeryAuditActor actor(ResultSet rs) throws SQLException {
        return new SurgeryAuditActor(SurgeryActorType.valueOf(rs.getString("actor_type")),
                rs.getObject("account_id", UUID.class), rs.getObject("staff_id", UUID.class),
                rs.getString("system_producer"));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Result persistence requires an application transaction");
        }
    }
}
