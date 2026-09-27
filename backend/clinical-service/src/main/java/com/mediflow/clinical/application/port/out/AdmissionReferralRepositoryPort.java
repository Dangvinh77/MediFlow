package com.mediflow.clinical.application.port.out;

import java.util.UUID;

import com.mediflow.clinical.domain.model.AdmissionReferral;

public interface AdmissionReferralRepositoryPort {
    boolean existsByRecordId(UUID recordId);
    AdmissionReferral save(AdmissionReferral referral);
}
