package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable V1 operation result. Corrections and partial-abort results are not supported. */
public record SurgeryResult(
        UUID resultId,
        UUID surgeryCaseId,
        String procedureCode,
        String methodCode,
        String treatmentOutcomeCode,
        String complicationGroupCode,
        Instant actualStartAt,
        Instant actualEndAt,
        List<SurgeryPerformedItem> performedItems,
        Instant recordedAt,
        SurgeryAuditActor recordedBy,
        String correlationId) {

    public SurgeryResult {
        if (resultId == null || surgeryCaseId == null || !validCode(procedureCode)
                || !validCode(methodCode) || !validCode(treatmentOutcomeCode)
                || (complicationGroupCode != null && !complicationGroupCode.matches("[A-Za-z0-9._-]{1,64}"))
                || actualStartAt == null || actualEndAt == null
                || !actualEndAt.isAfter(actualStartAt)
                || performedItems == null || recordedAt == null
                || recordedAt.isBefore(actualEndAt)
                || recordedBy == null || correlationId == null
                || correlationId.isBlank() || correlationId.length() > 128) {
            throw invalid("SURGERY_RESULT_INVALID");
        }
        if (performedItems.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_RESULT_DUPLICATE_ITEM");
        }
        procedureCode = procedureCode.trim();
        methodCode = methodCode.trim();
        treatmentOutcomeCode = treatmentOutcomeCode.trim();
        complicationGroupCode = complicationGroupCode == null ? null : complicationGroupCode.trim();
        correlationId = correlationId.trim();
        performedItems = performedItems.stream()
                .sorted(Comparator.comparing(SurgeryPerformedItem::performedItemId))
                .toList();
        Set<UUID> lineIds = new HashSet<>();
        for (SurgeryPerformedItem item : performedItems) {
            if (!lineIds.add(item.performedItemId())) {
                throw invalid("SURGERY_RESULT_DUPLICATE_ITEM");
            }
        }
    }

    private static boolean validCode(String value) {
        return value != null && !value.isBlank() && value.length() <= 64;
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code,
                "Kết quả phẫu thuật hoặc các mục thực hiện không hợp lệ");
    }
}
