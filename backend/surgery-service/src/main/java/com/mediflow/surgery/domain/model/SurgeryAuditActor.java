package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.util.UUID;

/**
 * Trusted actor context supplied by the application boundary.
 * A human account and its separately verified staff identity are not interchangeable.
 */
public record SurgeryAuditActor(
        SurgeryActorType actorType,
        UUID accountId,
        UUID verifiedStaffId,
        String systemProducer) {

    public SurgeryAuditActor {
        if (actorType == null) {
            throw invalid("SURGERY_AUDIT_ACTOR_TYPE_REQUIRED");
        }
        if (actorType == SurgeryActorType.HUMAN) {
            if (accountId == null || systemProducer != null) {
                throw invalid("SURGERY_HUMAN_AUDIT_ACTOR_INVALID");
            }
            systemProducer = null;
        } else {
            if (accountId != null || verifiedStaffId != null
                    || systemProducer == null || systemProducer.isBlank()) {
                throw invalid("SURGERY_SYSTEM_AUDIT_ACTOR_INVALID");
            }
            systemProducer = systemProducer.trim();
        }
    }

    public static SurgeryAuditActor human(UUID accountId, UUID verifiedStaffId) {
        return new SurgeryAuditActor(SurgeryActorType.HUMAN, accountId, verifiedStaffId, null);
    }

    public static SurgeryAuditActor system(String producer) {
        return new SurgeryAuditActor(SurgeryActorType.SYSTEM, null, null, producer);
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Thông tin chủ thể kiểm toán không hợp lệ");
    }
}
