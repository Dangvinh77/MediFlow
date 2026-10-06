package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryCaseAuditEntry;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryStateChange;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA case row plus append-only JDBC histories in the caller's single transaction. */
@Repository
@Profile("!test")
public class SurgeryCasePersistenceAdapter implements SurgeryCaseRepositoryPort,
        com.mediflow.surgery.application.port.out.SurgeryReadinessSnapshotPort {

    private final SurgeryCaseJpaRepository cases;
    private final SurgeryCasePersistenceMapper mapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public SurgeryCasePersistenceAdapter(SurgeryCaseJpaRepository cases,
                                         SurgeryCasePersistenceMapper mapper,
                                         JdbcTemplate jdbc) {
        this(cases, mapper, jdbc, Clock.systemUTC());
    }

    SurgeryCasePersistenceAdapter(SurgeryCaseJpaRepository cases,
                                  SurgeryCasePersistenceMapper mapper,
                                  JdbcTemplate jdbc, Clock clock) {
        this.cases = cases;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<SurgeryCase> findById(UUID caseId) {
        return cases.findById(caseId).map(this::toDomain);
    }

    @Override
    public Optional<SurgeryCase> findByRequestId(UUID requestId) {
        return cases.findBySurgeryRequestId(requestId).map(this::toDomain);
    }

    @Override
    public Optional<SurgeryCase> lockById(UUID caseId) {
        requireTransaction();
        try {
            return cases.lockById(caseId).map(this::toDomain);
        } catch (org.springframework.dao.OptimisticLockingFailureException stale) {
            // A prior read in this persistence context can become stale while the row lock waits.
            // Do not hide a programming/DB failure as a revision conflict; only optimistic conflicts map here.
            throw new SurgeryRevisionConflictException();
        }
    }

    @Override
    public SurgeryCase save(SurgeryCase aggregate, long expectedRevision) {
        requireTransaction();
        if (aggregate == null || expectedRevision < -1) {
            throw new IllegalArgumentException("Aggregate và expected revision không hợp lệ");
        }
        SurgeryCaseJpaEntity row;
        if (expectedRevision == -1) {
            if (aggregate.getRevision() != 0) throw new SurgeryRevisionConflictException();
            row = mapper.newRow();
        } else {
            row = cases.lockById(aggregate.getSurgeryCaseId())
                    .orElseThrow(SurgeryRevisionConflictException::new);
            if (row.revision != expectedRevision
                    || aggregate.getRevision() <= expectedRevision) {
                throw new SurgeryRevisionConflictException();
            }
        }
        if (expectedRevision >= 0 && aggregate.getReadinessSnapshot() != null) {
            saveSnapshot(aggregate.getReadinessSnapshot());
        }
        cases.saveAndFlush(mapper.toRow(aggregate, row, clock.instant()));
        int storedStates = expectedRevision == -1 ? 0 : countRows(
                "surgery_status_history", aggregate.getSurgeryCaseId());
        for (int index = storedStates; index < aggregate.getStatusHistory().size(); index++) {
            appendState(aggregate.getSurgeryCaseId(), index, aggregate.getStatusHistory().get(index));
        }
        int firstAudit = expectedRevision == -1 ? 0 : Math.toIntExact(expectedRevision + 1);
        for (int index = firstAudit; index < aggregate.getRevisionHistory().size(); index++) {
            appendAudit(aggregate.getSurgeryCaseId(), aggregate.getRevisionHistory().get(index));
        }
        return aggregate;
    }

    private SurgeryCase toDomain(SurgeryCaseJpaEntity row) {
        UUID id = row.surgeryCaseId;
        List<SurgeryStateChange> states = jdbc.query("""
                SELECT * FROM surgery_status_history
                WHERE surgery_case_id = ? ORDER BY sequence_no
                """, (rs, ignored) -> new SurgeryStateChange(
                status(rs, "previous_status"), status(rs, "new_status"), actor(rs),
                rs.getString("reason"), time(rs, "occurred_at"), rs.getString("correlation_id")), id);
        List<SurgeryCaseAuditEntry> audit = jdbc.query("""
                SELECT * FROM surgery_revision_history
                WHERE surgery_case_id = ? ORDER BY revision
                """, (rs, ignored) -> new SurgeryCaseAuditEntry(
                rs.getLong("revision"), rs.getString("change_code"),
                status(rs, "previous_status"), status(rs, "new_status"),
                actor(rs), time(rs, "occurred_at"), rs.getString("correlation_id")), id);
        ReadinessSnapshot readiness = row.readinessSnapshotId == null
                ? null : loadSnapshot(row.readinessSnapshotId);
        return mapper.toDomain(row, readiness, states, audit);
    }

    @Override
    public void store(ReadinessSnapshot snapshot) {
        requireTransaction();
        saveSnapshot(snapshot);
    }

    @Override
    public Optional<ReadinessSnapshot> findSnapshot(UUID snapshotId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM surgery_readiness_snapshot WHERE readiness_snapshot_id=?",
                Integer.class, snapshotId);
        return count != null && count == 1 ? Optional.of(loadSnapshot(snapshotId)) : Optional.empty();
    }

    private void saveSnapshot(ReadinessSnapshot snapshot) {
        Integer existing = jdbc.queryForObject("""
                SELECT count(*) FROM surgery_readiness_snapshot WHERE readiness_snapshot_id = ?
                """, Integer.class, snapshot.readinessSnapshotId());
        if (existing != null && existing > 0) {
            if (!snapshot.equals(loadSnapshot(snapshot.readinessSnapshotId()))) {
                throw new SurgeryRevisionConflictException();
            }
            return;
        }
        jdbc.update("""
                INSERT INTO surgery_readiness_snapshot
                (readiness_snapshot_id, surgery_case_id, indication_valid,
                 mandatory_checklist_complete, surgery_consent_active, anesthesia_consent_active,
                 team_eligible, schedule_confirmed,
                 financial_clearance_valid, evaluated_at, valid_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshot.readinessSnapshotId(), snapshot.surgeryCaseId(),
                snapshot.indicationValid(), snapshot.mandatoryChecklistComplete(),
                snapshot.surgeryConsentActive(), snapshot.anesthesiaConsentActive(),
                snapshot.teamEligible(), snapshot.scheduleConfirmed(),
                snapshot.financialClearanceValid(), Timestamp.from(snapshot.evaluatedAt()),
                timestamp(snapshot.validUntil()));
        jdbc.update("""
                INSERT INTO surgery_readiness_precision(readiness_snapshot_id,evaluated_at_iso,valid_until_iso)
                VALUES (?,?,?)
                """,snapshot.snapshotId(),snapshot.evaluatedAt().toString(),snapshot.validUntil() == null ? null : snapshot.validUntil().toString());
        for (int index = 0; index < snapshot.dependencyRevisions().size(); index++) {
            SurgeryDependencyRevision dependency = snapshot.dependencyRevisions().get(index);
            jdbc.update("""
                    INSERT INTO surgery_readiness_dependency
                    (readiness_snapshot_id, sequence_no, dependency_type, source_id, revision)
                    VALUES (?, ?, ?, ?, ?)
                    """, snapshot.readinessSnapshotId(), index, dependency.dependencyType().name(),
                    dependency.sourceId(), dependency.revision());
        }
        for (int index = 0; index < snapshot.blockingReasons().size(); index++) {
            jdbc.update("""
                    INSERT INTO surgery_readiness_blocking_reason (readiness_snapshot_id, sequence_no, reason_code)
                    VALUES (?, ?, ?)
                    """, snapshot.readinessSnapshotId(), index, snapshot.blockingReasons().get(index));
        }
    }

    private ReadinessSnapshot loadSnapshot(UUID snapshotId) {
        List<SurgeryDependencyRevision> dependencies = jdbc.query("""
                SELECT * FROM surgery_readiness_dependency
                WHERE readiness_snapshot_id = ? ORDER BY sequence_no
                """, (rs, ignored) -> new SurgeryDependencyRevision(
                SurgeryDependencyType.valueOf(rs.getString("dependency_type")),
                rs.getObject("source_id", UUID.class), rs.getLong("revision")), snapshotId);
        List<String> reasons = jdbc.query("""
                SELECT reason_code FROM surgery_readiness_blocking_reason
                WHERE readiness_snapshot_id = ? ORDER BY sequence_no
                """, (rs, ignored) -> rs.getString("reason_code"), snapshotId);
        return jdbc.queryForObject("""
                SELECT s.*,p.evaluated_at_iso,p.valid_until_iso FROM surgery_readiness_snapshot s
                LEFT JOIN surgery_readiness_precision p USING(readiness_snapshot_id) WHERE s.readiness_snapshot_id = ?
                """, (rs, ignored) -> new ReadinessSnapshot(
                rs.getObject("readiness_snapshot_id", UUID.class),
                rs.getObject("surgery_case_id", UUID.class),
                rs.getBoolean("indication_valid"), rs.getBoolean("mandatory_checklist_complete"),
                rs.getBoolean("surgery_consent_active"), rs.getBoolean("anesthesia_consent_active"),
                rs.getBoolean("team_eligible"),
                rs.getBoolean("schedule_confirmed"),
                rs.getBoolean("financial_clearance_valid"),
                exactSnapshotTime(rs,"evaluated_at"), dependencies, exactSnapshotTime(rs,"valid_until"), reasons), snapshotId);
    }

    private static Instant exactSnapshotTime(ResultSet rs,String column) throws SQLException {
        String iso = rs.getString(column+"_iso");
        return iso == null ? time(rs,column) : Instant.parse(iso);
    }

    private void appendState(UUID caseId, int index, SurgeryStateChange change) {
        jdbc.update("""
                INSERT INTO surgery_status_history
                (surgery_case_id, sequence_no, previous_status, new_status,
                 actor_type, account_id, staff_id, system_producer,
                 reason, occurred_at, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, caseId, index, name(change.previousStatus()), change.newStatus().name(),
                change.actor().actorType().name(), change.actor().accountId(),
                change.actor().verifiedStaffId(), change.actor().systemProducer(),
                change.reason(), Timestamp.from(change.occurredAt()), change.correlationId());
    }

    private void appendAudit(UUID caseId, SurgeryCaseAuditEntry audit) {
        jdbc.update("""
                INSERT INTO surgery_revision_history
                (surgery_case_id, revision, change_code, previous_status, new_status,
                 actor_type, account_id, staff_id, system_producer,
                 occurred_at, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, caseId, audit.revision(), audit.changeCode(), name(audit.previousStatus()),
                audit.newStatus().name(), audit.actor().actorType().name(),
                audit.actor().accountId(), audit.actor().verifiedStaffId(),
                audit.actor().systemProducer(), Timestamp.from(audit.occurredAt()),
                audit.correlationId());
    }

    private int countRows(String table, UUID caseId) {
        // Table names are fixed by this adapter, never supplied by callers.
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE surgery_case_id = ?",
                Integer.class, caseId);
        return count == null ? 0 : count;
    }

    private static SurgeryAuditActor actor(ResultSet rs) throws SQLException {
        return new SurgeryAuditActor(SurgeryActorType.valueOf(rs.getString("actor_type")),
                rs.getObject("account_id", UUID.class),
                rs.getObject("staff_id", UUID.class), rs.getString("system_producer"));
    }

    private static SurgeryStatus status(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : SurgeryStatus.valueOf(value);
    }

    private static String name(SurgeryStatus status) {
        return status == null ? null : status.name();
    }

    private static Instant time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Surgery case lock/save yêu cầu application transaction");
        }
    }
}
