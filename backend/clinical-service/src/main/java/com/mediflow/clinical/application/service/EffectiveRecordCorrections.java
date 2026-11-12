package com.mediflow.clinical.application.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import com.mediflow.clinical.application.dto.response.EffectiveRecordCorrectionDTO;
import com.mediflow.clinical.domain.model.RecordAmendment;
import com.mediflow.clinical.domain.model.RecordAmendmentStatus;

final class EffectiveRecordCorrections {
    private EffectiveRecordCorrections() {}

    static List<EffectiveRecordCorrectionDTO> latestPerSection(List<RecordAmendment> amendments) {
        var latest = new LinkedHashMap<String, RecordAmendment>();
        amendments.stream()
                .filter(amendment -> amendment.getStatus() == RecordAmendmentStatus.APPROVED
                        || amendment.getStatus() == RecordAmendmentStatus.APPLIED)
                .sorted(Comparator.comparing(RecordAmendment::getEffectiveRevision,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(RecordAmendment::getDecidedAt)
                        .thenComparing(RecordAmendment::getRequestedAt)
                        .thenComparing(amendment -> amendment.getAmendmentId().toString()))
                .forEach(amendment -> latest.put(amendment.getSection().name(), amendment));
        return latest.values().stream().map(amendment -> new EffectiveRecordCorrectionDTO(
                amendment.getSection().name(), amendment.getOriginalText(), amendment.getAmendedText(),
                amendment.getReason(), amendment.getDecidedAt())).toList();
    }
}
