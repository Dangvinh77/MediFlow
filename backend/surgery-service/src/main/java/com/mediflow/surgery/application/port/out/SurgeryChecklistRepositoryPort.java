package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistTemplate;
import com.mediflow.surgery.domain.model.SurgeryChecklistItemChange;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Immutable checklist catalogue and case snapshot storage. Item mutations use a separate audited command. */
public interface SurgeryChecklistRepositoryPort {

    void createTemplate(SurgeryChecklistTemplate template, Instant createdAt);

    Optional<SurgeryChecklistTemplate> findTemplate(String procedureCode, long revision);

    void createSnapshot(SurgeryChecklistSnapshot snapshot);

    Optional<SurgeryChecklistSnapshot> findSnapshotByCaseId(UUID caseId);

    SurgeryChecklistSnapshot saveItemChange(SurgeryChecklistSnapshot snapshot,
                                            long expectedSnapshotRevision,
                                            SurgeryChecklistItemChange change);
}
