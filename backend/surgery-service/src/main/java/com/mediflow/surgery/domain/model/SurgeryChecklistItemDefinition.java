package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** Procedure-specific item definition. It deliberately carries no unapproved clinical catalogue. */
public record SurgeryChecklistItemDefinition(
        UUID definitionId,
        String itemCode,
        boolean mandatory,
        int displayOrder) {

    public SurgeryChecklistItemDefinition {
        if (definitionId == null || itemCode == null || itemCode.isBlank() || itemCode.length() > 64 || displayOrder < 1) {
            throw invalid("SURGERY_CHECKLIST_DEFINITION_INVALID");
        }
        itemCode = itemCode.trim();
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Định nghĩa mục checklist không hợp lệ");
    }
}
