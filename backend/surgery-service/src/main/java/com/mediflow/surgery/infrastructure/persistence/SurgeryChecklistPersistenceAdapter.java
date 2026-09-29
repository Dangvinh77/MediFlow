package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemDefinition;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class SurgeryChecklistPersistenceAdapter implements SurgeryChecklistRepositoryPort {

    private final SurgeryCaseJpaRepository cases;
    private final JdbcTemplate jdbc;

    public SurgeryChecklistPersistenceAdapter(SurgeryCaseJpaRepository cases, JdbcTemplate jdbc) {
        this.cases = cases;
        this.jdbc = jdbc;
    }

    @Override
    public void createTemplate(SurgeryChecklistTemplate template, Instant createdAt) {
        requireTransaction();
        if (template == null || createdAt == null) {
            throw new IllegalArgumentException("Checklist template and creation time are required");
        }
        Optional<SurgeryChecklistTemplate> existing = findTemplate(template.procedureCode(), template.revision());
        if (existing.isPresent()) {
            if (!existing.get().equals(template)) throw new SurgeryRevisionConflictException();
            return;
        }
        jdbc.update("""
                INSERT INTO preop_checklist_template (template_id, procedure_code, revision, created_at)
                VALUES (?, ?, ?, ?)
                """, template.templateId(), template.procedureCode(), template.revision(), Timestamp.from(createdAt));
        for (SurgeryChecklistItemDefinition item : template.items()) {
            jdbc.update("""
                    INSERT INTO preop_checklist_definition
                    (definition_id, template_id, item_code, mandatory, display_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, item.definitionId(), template.templateId(), item.itemCode(),
                    item.mandatory(), item.displayOrder());
        }
    }

    @Override
    public Optional<SurgeryChecklistTemplate> findTemplate(String procedureCode, long revision) {
        if (procedureCode == null || procedureCode.isBlank() || revision < 1) return Optional.empty();
        List<SurgeryChecklistTemplate> found = jdbc.query("""
                SELECT template_id FROM preop_checklist_template
                WHERE procedure_code = ? AND revision = ?
                """, (rs, ignored) -> {
            UUID templateId = rs.getObject("template_id", UUID.class);
            List<SurgeryChecklistItemDefinition> definitions = jdbc.query("""
                    SELECT definition_id, item_code, mandatory, display_order
                    FROM preop_checklist_definition WHERE template_id = ? ORDER BY display_order
                    """, (item, unused) -> new SurgeryChecklistItemDefinition(
                    item.getObject("definition_id", UUID.class), item.getString("item_code"),
                    item.getBoolean("mandatory"), item.getInt("display_order")), templateId);
            return new SurgeryChecklistTemplate(templateId, procedureCode, revision, definitions);
        }, procedureCode.trim(), revision);
        return found.stream().findFirst();
    }

    @Override
    public void createSnapshot(SurgeryChecklistSnapshot snapshot) {
        requireTransaction();
        if (snapshot == null) throw new IllegalArgumentException("Checklist snapshot is required");
        if (snapshot.revision() != 0 || snapshot.items().stream().anyMatch(item ->
                item.status() != SurgeryChecklistStatus.PENDING || item.evidenceReferenceId() != null
                        || item.evidenceRevision() != null)) {
            throw new IllegalArgumentException("A new checklist snapshot must start pending at revision zero");
        }
        SurgeryCaseJpaEntity surgeryCase = cases.lockById(snapshot.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.status != SurgeryStatus.REQUESTED
                && surgeryCase.status != SurgeryStatus.PREOP_IN_PROGRESS) {
            throw new SurgeryRevisionConflictException();
        }
        Optional<SurgeryChecklistSnapshot> existing = findSnapshotByCaseId(snapshot.surgeryCaseId());
        if (existing.isPresent()) {
            if (!existing.get().equals(snapshot)) throw new SurgeryRevisionConflictException();
            return;
        }
        validateTemplateSnapshot(snapshot);
        jdbc.update("""
                INSERT INTO preop_checklist_snapshot
                (checklist_snapshot_id, surgery_case_id, template_id, template_revision, revision)
                VALUES (?, ?, ?, ?, ?)
                """, snapshot.checklistSnapshotId(), snapshot.surgeryCaseId(), snapshot.templateId(),
                snapshot.templateRevision(), snapshot.revision());
        for (SurgeryChecklistItem item : snapshot.items()) {
            jdbc.update("""
                    INSERT INTO preop_checklist_item
                    (checklist_item_id, checklist_snapshot_id, surgery_case_id, template_definition_id,
                     item_code, mandatory, display_order, status, evidence_reference_id, evidence_revision, revision)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, item.checklistItemId(), snapshot.checklistSnapshotId(), item.surgeryCaseId(),
                    item.templateDefinitionId(), item.itemCode(), item.mandatory(), item.displayOrder(),
                    item.status().name(), item.evidenceReferenceId(), item.evidenceRevision());
        }
    }

    private void validateTemplateSnapshot(SurgeryChecklistSnapshot snapshot) {
        Integer templateCount = jdbc.queryForObject("""
                SELECT count(*) FROM preop_checklist_template
                WHERE template_id = ? AND revision = ?
                """, Integer.class, snapshot.templateId(), snapshot.templateRevision());
        if (templateCount == null || templateCount != 1) throw new SurgeryRevisionConflictException();
        List<SurgeryChecklistItemDefinition> definitions = jdbc.query("""
                SELECT definition_id, item_code, mandatory, display_order
                FROM preop_checklist_definition WHERE template_id = ? ORDER BY display_order
                """, (rs, ignored) -> new SurgeryChecklistItemDefinition(
                rs.getObject("definition_id", UUID.class), rs.getString("item_code"),
                rs.getBoolean("mandatory"), rs.getInt("display_order")), snapshot.templateId());
        if (definitions.size() != snapshot.items().size()) throw new SurgeryRevisionConflictException();
        for (SurgeryChecklistItem item : snapshot.items()) {
            SurgeryChecklistItemDefinition definition = definitions.stream()
                    .filter(candidate -> candidate.definitionId().equals(item.templateDefinitionId()))
                    .findFirst().orElseThrow(SurgeryRevisionConflictException::new);
            if (!definition.definitionId().equals(item.templateDefinitionId())
                    || !definition.itemCode().equals(item.itemCode())
                    || definition.mandatory() != item.mandatory()
                    || definition.displayOrder() != item.displayOrder()) {
                throw new SurgeryRevisionConflictException();
            }
        }
    }

    @Override
    public Optional<SurgeryChecklistSnapshot> findSnapshotByCaseId(UUID caseId) {
        if (caseId == null) return Optional.empty();
        List<SurgeryChecklistSnapshot> found = jdbc.query("""
                SELECT checklist_snapshot_id, surgery_case_id, template_id, template_revision, revision
                FROM preop_checklist_snapshot WHERE surgery_case_id = ?
                """, (rs, ignored) -> {
            UUID snapshotId = rs.getObject("checklist_snapshot_id", UUID.class);
            List<SurgeryChecklistItem> items = jdbc.query("""
                    SELECT checklist_item_id, surgery_case_id, template_definition_id, item_code,
                           mandatory, display_order, status, evidence_reference_id, evidence_revision, revision
                    FROM preop_checklist_item WHERE checklist_snapshot_id = ? ORDER BY display_order
                    """, (item, unused) -> new SurgeryChecklistItem(
                    item.getObject("checklist_item_id", UUID.class),
                    item.getObject("surgery_case_id", UUID.class),
                    item.getObject("template_definition_id", UUID.class), item.getString("item_code"),
                    item.getBoolean("mandatory"), item.getInt("display_order"),
                    SurgeryChecklistStatus.valueOf(item.getString("status")),
                    item.getObject("evidence_reference_id", UUID.class),
                    (Long) item.getObject("evidence_revision"), item.getLong("revision")), snapshotId);
            return new SurgeryChecklistSnapshot(snapshotId, rs.getObject("surgery_case_id", UUID.class),
                    rs.getObject("template_id", UUID.class), rs.getLong("template_revision"),
                    rs.getLong("revision"), items);
        }, caseId);
        return found.stream().findFirst();
    }

    @Override
    public SurgeryChecklistSnapshot saveItemChange(SurgeryChecklistSnapshot snapshot,
                                                   long expectedSnapshotRevision,
                                                   SurgeryChecklistItemChange change) {
        requireTransaction();
        if (snapshot == null || change == null || snapshot.revision() != expectedSnapshotRevision + 1) {
            throw new IllegalArgumentException("Checklist update revision is invalid");
        }
        SurgeryCaseJpaEntity surgeryCase = cases.lockById(snapshot.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (surgeryCase.status != SurgeryStatus.REQUESTED
                && surgeryCase.status != SurgeryStatus.PREOP_IN_PROGRESS) {
            throw new SurgeryRevisionConflictException();
        }
        SurgeryChecklistSnapshot current = findSnapshotByCaseId(snapshot.surgeryCaseId())
                .orElseThrow(SurgeryRevisionConflictException::new);
        if (current.revision() != expectedSnapshotRevision
                || !current.checklistSnapshotId().equals(snapshot.checklistSnapshotId())
                || !current.templateId().equals(snapshot.templateId())
                || current.templateRevision() != snapshot.templateRevision()) {
            throw new SurgeryRevisionConflictException();
        }
        SurgeryChecklistItem before = current.items().stream()
                .filter(item -> item.checklistItemId().equals(change.checklistItemId()))
                .findFirst().orElseThrow(SurgeryRevisionConflictException::new);
        SurgeryChecklistItem after = snapshot.items().stream()
                .filter(item -> item.checklistItemId().equals(change.checklistItemId()))
                .findFirst().orElseThrow(SurgeryRevisionConflictException::new);
        if (before.revision() + 1 != after.revision() || change.revision() != after.revision()
                || before.status() != change.previousStatus() || after.status() != change.newStatus()
                || !java.util.Objects.equals(after.evidenceReferenceId(), change.evidenceReferenceId())
                || !java.util.Objects.equals(after.evidenceRevision(), change.evidenceRevision())
                || current.items().size() != snapshot.items().size()) {
            throw new SurgeryRevisionConflictException();
        }
        for (SurgeryChecklistItem currentItem : current.items()) {
            if (currentItem.checklistItemId().equals(change.checklistItemId())) continue;
            SurgeryChecklistItem updatedItem = snapshot.items().stream()
                    .filter(item -> item.checklistItemId().equals(currentItem.checklistItemId()))
                    .findFirst().orElseThrow(SurgeryRevisionConflictException::new);
            if (!currentItem.equals(updatedItem)) throw new SurgeryRevisionConflictException();
        }
        int snapshotUpdated = jdbc.update("""
                UPDATE preop_checklist_snapshot SET revision = ?
                WHERE checklist_snapshot_id = ? AND surgery_case_id = ? AND revision = ?
                """, snapshot.revision(), snapshot.checklistSnapshotId(), snapshot.surgeryCaseId(),
                expectedSnapshotRevision);
        if (snapshotUpdated != 1) throw new SurgeryRevisionConflictException();
        int itemUpdated = jdbc.update("""
                UPDATE preop_checklist_item
                SET status = ?, evidence_reference_id = ?, evidence_revision = ?, revision = ?
                WHERE checklist_item_id = ? AND surgery_case_id = ? AND checklist_snapshot_id = ?
                  AND revision = ? AND status = ?
                """, after.status().name(), after.evidenceReferenceId(), after.evidenceRevision(),
                after.revision(), after.checklistItemId(), after.surgeryCaseId(),
                snapshot.checklistSnapshotId(), before.revision(), before.status().name());
        if (itemUpdated != 1) throw new SurgeryRevisionConflictException();
        jdbc.update("""
                INSERT INTO preop_checklist_item_history
                (change_code, checklist_item_id, revision, previous_status, new_status,
                 evidence_reference_id, evidence_revision, actor_type, account_id, staff_id,
                 system_producer, occurred_at, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, change.changeId(), change.checklistItemId(), change.revision(),
                change.previousStatus().name(), change.newStatus().name(), change.evidenceReferenceId(),
                change.evidenceRevision(), change.actor().actorType().name(), change.actor().accountId(),
                change.actor().verifiedStaffId(), change.actor().systemProducer(),
                Timestamp.from(change.occurredAt()), change.correlationId());
        return snapshot;
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Checklist persistence requires an application transaction");
        }
    }
}
