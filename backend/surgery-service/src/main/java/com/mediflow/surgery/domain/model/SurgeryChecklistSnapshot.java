package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Case-owned copy; later template revisions cannot change these item definitions. */
public record SurgeryChecklistSnapshot(
        UUID checklistSnapshotId,
        UUID surgeryCaseId,
        UUID templateId,
        long templateRevision,
        long revision,
        List<SurgeryChecklistItem> items) {

    public SurgeryChecklistSnapshot {
        if (checklistSnapshotId == null || surgeryCaseId == null || templateId == null
                || templateRevision < 1 || revision < 0 || items == null || items.isEmpty()) {
            throw invalid("SURGERY_CHECKLIST_SNAPSHOT_INVALID");
        }
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_CHECKLIST_SNAPSHOT_MISMATCH");
        }
        items = items.stream().sorted(java.util.Comparator.comparingInt(
                SurgeryChecklistItem::displayOrder)).toList();
        Set<UUID> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (SurgeryChecklistItem item : items) {
            if (!surgeryCaseId.equals(item.surgeryCaseId())
                    || !ids.add(item.checklistItemId()) || !codes.add(item.itemCode())
                    || !orders.add(item.displayOrder())) {
                throw invalid("SURGERY_CHECKLIST_SNAPSHOT_MISMATCH");
            }
        }
    }

    public boolean mandatoryChecklistComplete() {
        return items.stream().anyMatch(SurgeryChecklistItem::mandatory)
                && items.stream().allMatch(SurgeryChecklistItem::satisfiesMandatoryRequirement);
    }

    public SurgeryChecklistSnapshot reviseItem(UUID itemId, long expectedSnapshotRevision,
                                               SurgeryChecklistItem revisedItem) {
        if (itemId == null || revisedItem == null || revision != expectedSnapshotRevision
                || revisedItem.revision() < 1 || !surgeryCaseId.equals(revisedItem.surgeryCaseId())) {
            throw invalid("SURGERY_CHECKLIST_REVISION_CONFLICT");
        }
        List<SurgeryChecklistItem> revisedItems = new java.util.ArrayList<>(items);
        int index = -1;
        for (int current = 0; current < items.size(); current++) {
            if (items.get(current).checklistItemId().equals(itemId)) index = current;
        }
        if (index < 0 || !items.get(index).itemCode().equals(revisedItem.itemCode())
                || !items.get(index).templateDefinitionId().equals(revisedItem.templateDefinitionId())
                || items.get(index).revision() + 1 != revisedItem.revision()
                || items.get(index).status() == revisedItem.status()
                && java.util.Objects.equals(items.get(index).evidenceReferenceId(), revisedItem.evidenceReferenceId())
                && java.util.Objects.equals(items.get(index).evidenceRevision(), revisedItem.evidenceRevision())) {
            throw invalid("SURGERY_CHECKLIST_REVISION_CONFLICT");
        }
        revisedItems.set(index, revisedItem);
        return new SurgeryChecklistSnapshot(checklistSnapshotId, surgeryCaseId, templateId,
                templateRevision, revision + 1, revisedItems);
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Ảnh chụp checklist của ca phẫu thuật không hợp lệ");
    }
}
