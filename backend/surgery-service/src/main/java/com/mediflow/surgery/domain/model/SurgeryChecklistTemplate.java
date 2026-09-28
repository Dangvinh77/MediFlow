package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable Surgery-owned template revision; changing a template creates another revision. */
public record SurgeryChecklistTemplate(
        UUID templateId,
        String procedureCode,
        long revision,
        List<SurgeryChecklistItemDefinition> items) {

    public SurgeryChecklistTemplate {
        if (templateId == null || procedureCode == null || procedureCode.isBlank() || procedureCode.length() > 64
                || revision < 1 || items == null || items.isEmpty()) {
            throw invalid("SURGERY_CHECKLIST_TEMPLATE_INVALID");
        }
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_CHECKLIST_TEMPLATE_DUPLICATE_ITEM");
        }
        procedureCode = procedureCode.trim();
        items = items.stream().sorted(java.util.Comparator.comparingInt(
                SurgeryChecklistItemDefinition::displayOrder)).toList();
        Set<UUID> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (SurgeryChecklistItemDefinition item : items) {
            if (!ids.add(item.definitionId()) || !codes.add(item.itemCode())
                    || !orders.add(item.displayOrder())) {
                throw invalid("SURGERY_CHECKLIST_TEMPLATE_DUPLICATE_ITEM");
            }
        }
    }

    public SurgeryChecklistSnapshot snapshotForCase(UUID snapshotId, UUID caseId) {
        if (snapshotId == null || caseId == null) {
            throw invalid("SURGERY_CHECKLIST_SNAPSHOT_ID_REQUIRED");
        }
        List<SurgeryChecklistItem> snapshotItems = items.stream()
                .map(item -> SurgeryChecklistItem.pending(UUID.randomUUID(), caseId, item))
                .toList();
        return new SurgeryChecklistSnapshot(snapshotId, caseId, templateId, revision, 0, snapshotItems);
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Mẫu checklist phải có phiên bản và các mục duy nhất");
    }
}
