package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.domain.model.RecordAccessDeclaration;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;

@Component
public class RecordAccessPersistenceAdapter implements RecordAccessLogPort, RecordAccessDeclarationPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independentTransaction;

    public RecordAccessPersistenceAdapter(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void append(RecordAccessLogEntry entry) {
        independentTransaction.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO record_access_log (actor_account_id, actor_staff_id, actor_role, patient_id, record_id,
                    action, outcome, basis, purpose, correlation_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, entry.actorAccountId(), entry.actorStaffId(), entry.actorRole(), entry.patientId(),
                entry.recordId(), entry.action(), entry.outcome(), entry.basis(), entry.purpose(),
                entry.correlationId(), utc(entry.occurredAt())));
    }

    @Override
    public Optional<RecordAccessDeclaration> findActive(UUID accountId, UUID patientId, Instant at) {
        return jdbc.query("""
                SELECT declaration_id, actor_account_id, actor_staff_id, actor_role, patient_id, purpose, reason,
                       declared_at, expires_at
                FROM record_access_declaration
                WHERE actor_account_id = ? AND patient_id = ? AND expires_at > ?
                ORDER BY expires_at DESC LIMIT 1
                """, (rs, row) -> new RecordAccessDeclaration(
                rs.getObject("declaration_id", UUID.class),
                rs.getObject("actor_account_id", UUID.class),
                rs.getObject("actor_staff_id", UUID.class),
                rs.getString("actor_role"),
                rs.getObject("patient_id", UUID.class),
                rs.getString("purpose"),
                rs.getString("reason"),
                rs.getObject("declared_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant()),
                accountId, patientId, utc(at)).stream().findFirst();
    }

    @Override
    public void save(RecordAccessDeclaration declaration) {
        jdbc.update("""
                INSERT INTO record_access_declaration (declaration_id, actor_account_id, actor_staff_id, actor_role,
                    patient_id, purpose, reason, declared_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, declaration.declarationId(), declaration.actorAccountId(), declaration.actorStaffId(),
                declaration.actorRole(), declaration.patientId(), declaration.purpose(), declaration.reason(),
                utc(declaration.declaredAt()), utc(declaration.expiresAt()));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
