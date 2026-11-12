package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.PrescriptionRevisionBillingPayload;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.model.PrescriptionRevisionOperation;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionOperationPort;
import com.mediflow.common.exception.DuplicateResourceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL JSONB persistence for immutable request/snapshot fields and retry state. */
@Component
public class PrescriptionRevisionOperationPersistenceAdapter implements PrescriptionRevisionOperationPort {
    private static final String COLUMNS = "operation_id,prescription_id,expected_revision,actor_account_id,actor_staff_id,"
            + "actor_role,correlation_id,command_fingerprint,requested_lines::text,billing_payload::text,"
            + "operation_status,failure_code,failure_message,created_at,updated_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PrescriptionRevisionOperationPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Optional<PrescriptionRevisionOperation> findByIdForUpdate(UUID operationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM pharmacy_prescription_revision_operation WHERE operation_id=? FOR UPDATE",
                this::single, operationId);
    }

    @Override
    public Optional<PrescriptionRevisionOperation> findById(UUID operationId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM pharmacy_prescription_revision_operation WHERE operation_id=?",
                this::single, operationId);
    }

    @Override
    public Optional<PrescriptionRevisionOperation> findActiveByPrescriptionForUpdate(UUID prescriptionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM pharmacy_prescription_revision_operation "
                        + "WHERE prescription_id=? AND operation_status IN ('PENDING','PREPARED','APPLIED') FOR UPDATE",
                this::single, prescriptionId);
    }

    @Override
    public Optional<PrescriptionRevisionOperation> findActiveByPrescription(UUID prescriptionId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM pharmacy_prescription_revision_operation "
                        + "WHERE prescription_id=? AND operation_status IN ('PENDING','PREPARED','APPLIED','ABORTING')",
                this::single, prescriptionId);
    }

    @Override
    public PrescriptionRevisionOperation create(PrescriptionRevisionOperation operation) {
        try {
            int inserted = jdbc.update("""
                    INSERT INTO pharmacy_prescription_revision_operation(
                        operation_id,prescription_id,expected_revision,actor_account_id,actor_staff_id,actor_role,
                        correlation_id,command_fingerprint,requested_lines,billing_payload,operation_status,
                        failure_code,failure_message,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?, ?,?::jsonb,?::jsonb,?,?, ?,?,?)
                    ON CONFLICT (operation_id) DO NOTHING
                    """, operation.operationId(), operation.prescriptionId(), operation.expectedRevision(),
                    operation.actor().accountId(), operation.actor().staffId(), operation.actor().role(),
                    operation.correlationId(), operation.commandFingerprint(), encode(operation.requestedLines()),
                    encode(operation.billingPayload()), operation.status().name(), operation.failureCode(),
                    operation.failureMessage(), timestamp(operation.createdAt()), timestamp(operation.updatedAt()));
            if (inserted != 1) {
                throw new DuplicateResourceException("PRESCRIPTION_IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key already identifies another prescription edit");
            }
            return operation;
        } catch (DataIntegrityViolationException failure) {
            throw failure;
        }
    }

    @Override
    public PrescriptionRevisionOperation update(PrescriptionRevisionOperation operation,
            PrescriptionRevisionOperation.Status expectedStatus) {
        int changed = jdbc.update("""
                UPDATE pharmacy_prescription_revision_operation
                   SET operation_status=?,failure_code=?,failure_message=?,updated_at=?
                 WHERE operation_id=? AND operation_status=?
                """, operation.status().name(), operation.failureCode(), operation.failureMessage(),
                timestamp(operation.updatedAt()), operation.operationId(), expectedStatus.name());
        if (changed != 1) {
            throw new IllegalStateException("Prescription revision operation state changed concurrently");
        }
        return operation;
    }

    @Override
    public List<UUID> findActiveIds(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Revision recovery batch must be bounded");
        return jdbc.queryForList("SELECT operation_id FROM pharmacy_prescription_revision_operation "
                + "WHERE operation_status IN ('PENDING','PREPARED','APPLIED','ABORTING') ORDER BY created_at,operation_id LIMIT ?",
                UUID.class, limit);
    }

    private static java.sql.Timestamp timestamp(Instant value) {
        return value == null ? null : java.sql.Timestamp.from(value);
    }

    private Optional<PrescriptionRevisionOperation> single(ResultSet rs) throws SQLException {
        return rs.next() ? Optional.of(read(rs)) : Optional.empty();
    }

    private PrescriptionRevisionOperation read(ResultSet row) throws SQLException {
        try {
            var actor = new ActorIdentity(row.getObject("actor_account_id", UUID.class),
                    row.getObject("actor_staff_id", UUID.class), row.getString("actor_role"));
            var lines = mapper.readValue(row.getString("requested_lines"),
                    new TypeReference<List<PrescriptionLineRequest>>() {});
            var payload = mapper.readValue(row.getString("billing_payload"), PrescriptionRevisionBillingPayload.class);
            return new PrescriptionRevisionOperation(row.getObject("operation_id", UUID.class),
                    row.getObject("prescription_id", UUID.class), row.getInt("expected_revision"), actor,
                    row.getString("correlation_id"), row.getString("command_fingerprint"), lines, payload,
                    PrescriptionRevisionOperation.Status.valueOf(row.getString("operation_status")),
                    row.getString("failure_code"), row.getString("failure_message"),
                    row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
        } catch (Exception invalid) {
            throw new SQLException("Stored prescription revision operation is invalid", invalid);
        }
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Cannot serialize prescription revision intent", failure);
        }
    }
}
