package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/** Exact source object and business revision used for one readiness guard. */
public record SurgeryDependencyRevision(
        SurgeryDependencyType dependencyType,
        UUID sourceId,
        long revision) {

    public SurgeryDependencyRevision {
        if (dependencyType == null || sourceId == null || revision < 0) {
            throw new SurgeryRuleException(
                    "SURGERY_READINESS_DEPENDENCY_INVALID", "Phiên bản nguồn readiness không hợp lệ");
        }
    }
}
