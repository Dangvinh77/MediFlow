package com.mediflow.lab.application.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.domain.model.LabReport;

public record LabReportDTO(UUID reportId, UUID testId, int resultVersion, String status, int draftRevision,
        String templateCode, int templateVersion, List<Value> values, String conclusion, LocalDate performedDate,
        String amendmentReason, UUID enteredBy, UUID submittedBy, Instant submittedAt, UUID returnedBy,
        String returnReason, UUID releasedBy, Instant releasedAt) {

    public record Value(String fieldCode, String operator, String value, String noResultReason, String flag) {
    }

    public static LabReportDTO of(LabReport r) {
        return new LabReportDTO(r.getReportId(), r.getTestId(), r.getResultVersion(), r.getStatus().name(),
                r.getDraftRevision(), r.getTemplateCode(), r.getTemplateVersion(),
                r.getValues().stream().map(v -> {
                    LabFlag flag = r.getFlags().get(v.fieldCode());
                    return new Value(v.fieldCode(), v.operator(), v.value(), v.noResultReason(),
                            flag == null ? null : flag.name());
                }).toList(),
                r.getConclusion(), r.getPerformedDate(), r.getAmendmentReason(), r.getEnteredBy(), r.getSubmittedBy(),
                r.getSubmittedAt(), r.getReturnedBy(), r.getReturnReason(), r.getReleasedBy(), r.getReleasedAt());
    }
}
