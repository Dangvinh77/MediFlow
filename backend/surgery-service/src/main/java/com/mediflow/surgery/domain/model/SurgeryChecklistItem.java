package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** A checklist line pinned to one case and one immutable template definition. */
public record SurgeryChecklistItem(
        UUID checklistItemId,
        UUID surgeryCaseId,
        UUID templateDefinitionId,
        String itemCode,
        boolean mandatory,
        int displayOrder,
        SurgeryChecklistStatus status,
        UUID evidenceReferenceId,
        Long evidenceRevision,
        long revision) {

    public SurgeryChecklistItem {
        if (checklistItemId == null || surgeryCaseId == null || templateDefinitionId == null
                || itemCode == null || itemCode.isBlank() || itemCode.length() > 64
                || displayOrder < 1 || status == null
                || revision < 0
                || (evidenceRevision != null && evidenceRevision < 0)
                || (evidenceRevision != null && evidenceReferenceId == null)) {
            throw invalid("SURGERY_CHECKLIST_ITEM_INVALID");
        }
        itemCode = itemCode.trim();
    }

    public SurgeryChecklistItem(UUID checklistItemId, UUID surgeryCaseId, UUID templateDefinitionId,
                                String itemCode, boolean mandatory, int displayOrder,
                                SurgeryChecklistStatus status, UUID evidenceReferenceId,
                                Long evidenceRevision) {
        this(checklistItemId, surgeryCaseId, templateDefinitionId, itemCode, mandatory,
                displayOrder, status, evidenceReferenceId, evidenceRevision, 0);
    }

    public static SurgeryChecklistItem pending(UUID itemId, UUID caseId,
                                               SurgeryChecklistItemDefinition definition) {
        if (definition == null) {
            throw invalid("SURGERY_CHECKLIST_DEFINITION_REQUIRED");
        }
        return new SurgeryChecklistItem(itemId, caseId, definition.definitionId(), definition.itemCode(),
                definition.mandatory(), definition.displayOrder(), SurgeryChecklistStatus.PENDING, null, null, 0);
    }

    public SurgeryChecklistItem revise(SurgeryChecklistStatus nextStatus,
                                       UUID nextEvidenceReferenceId,
                                       Long nextEvidenceRevision) {
        return new SurgeryChecklistItem(checklistItemId, surgeryCaseId, templateDefinitionId,
                itemCode, mandatory, displayOrder, nextStatus, nextEvidenceReferenceId,
                nextEvidenceRevision, revision + 1);
    }

    public boolean satisfiesMandatoryRequirement() {
        return !mandatory || status == SurgeryChecklistStatus.SATISFIED;
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Dòng checklist không hợp lệ");
    }
}
