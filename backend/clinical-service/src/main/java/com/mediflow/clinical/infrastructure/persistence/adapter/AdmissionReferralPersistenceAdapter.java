package com.mediflow.clinical.infrastructure.persistence.adapter;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.domain.model.AdmissionReferral;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.AdmissionReferralJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.AdmissionReferralJpaRepository;
import com.mediflow.common.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdmissionReferralPersistenceAdapter implements AdmissionReferralRepositoryPort {
    private final AdmissionReferralJpaRepository repository;

    @Override
    public boolean existsByRecordId(java.util.UUID recordId) {
        return repository.existsByRecordId(recordId);
    }

    @Override
    public AdmissionReferral save(AdmissionReferral referral) {
        try {
            repository.saveAndFlush(AdmissionReferralJpaEntity.builder()
                    .admissionRequestId(referral.getAdmissionRequestId())
                    .recordId(referral.getRecordId())
                    .patientId(referral.getPatientId())
                    .departmentId(referral.getDepartmentId())
                    .requestedBy(referral.getRequestedBy())
                    .diagnosisSummary(referral.getDiagnosisSummary())
                    .priority(referral.getPriority())
                    .emergency(referral.isEmergency())
                    .requestedAt(referral.getRequestedAt())
                    .build());
            return referral;
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_admission_referral_record")) {
                throw new DuplicateResourceException("CLINICAL_ADMISSION_REFERRAL_CONFLICT",
                        "An admission referral already exists for this record");
            }
            throw exception;
        }
    }

    private static boolean hasConstraint(Throwable error, String constraint) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
