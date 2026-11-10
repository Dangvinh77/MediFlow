package com.mediflow.clinical.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.CloseRecordRequest;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ForbiddenOperationException;

/** Makes ending a visit without a conclusion safe to retry; the lifecycle change itself lives with record completion. */
@Service
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ExamClosureApplicationService {
    private final ClinicalCareFinanceApplicationService careFinance;
    private final CurrentClinicalActorPort actors;
    private final IdempotentCommandPort idempotency;

    public ExamClosureApplicationService(ClinicalCareFinanceApplicationService careFinance, CurrentClinicalActorPort actors,
            IdempotentCommandPort idempotency) {
        this.careFinance = careFinance;
        this.actors = actors;
        this.idempotency = idempotency;
    }

    public IdempotentCommandPort.Result<MedicalRecordDTO> closeWithoutConclusion(UUID recordId, CloseRecordRequest request, String key) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"DOCTOR".equals(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED",
                    "Only the examining doctor may end a visit without a conclusion");
        }
        requireKey(key);
        String path = "/api/v2/records/" + recordId + "/close-without-conclusion";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, key, fingerprint(recordId + ":" + request),
                MedicalRecordDTO.class, 200, ignored -> null, () -> careFinance.closeWithoutConclusion(recordId, request));
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 120 || !key.chars().allMatch(value -> value >= 33 && value <= 126)) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-120 printable ASCII characters without spaces");
        }
    }

    private static String fingerprint(String command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(command.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
