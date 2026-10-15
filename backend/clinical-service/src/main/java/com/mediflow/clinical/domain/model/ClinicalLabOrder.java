package com.mediflow.clinical.domain.model;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable clinical authorization for one outpatient Lab request. */
public record ClinicalLabOrder(
        UUID sourceOrderId,
        UUID recordId,
        UUID patientId,
        UUID requestingDoctorId,
        UUID requestingDepartmentId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        String labType,
        LocalDate requestedDate,
        String priceCode,
        Instant orderedAt) {

    public static ClinicalLabOrder create(UUID recordId, UUID patientId, UUID doctorId, UUID departmentId,
                                          UUID episodeId, String labType, LocalDate requestedDate,
                                          String priceCode, Instant orderedAt) {
        if (recordId == null || patientId == null || doctorId == null || departmentId == null || episodeId == null
                || labType == null || labType.isBlank() || labType.length() > 50 || requestedDate == null
                || orderedAt == null || priceCode == null || priceCode.isBlank() || priceCode.length() > 64) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID", "Lab order fields are invalid");
        }
        return new ClinicalLabOrder(UUID.randomUUID(), recordId, patientId, doctorId, departmentId,
                CareEpisodeType.OUTPATIENT_VISIT, episodeId, labType, requestedDate, priceCode, orderedAt);
    }

    public ClinicalLabOrder {
        Objects.requireNonNull(sourceOrderId);
        Objects.requireNonNull(recordId);
        Objects.requireNonNull(patientId);
        Objects.requireNonNull(requestingDoctorId);
        Objects.requireNonNull(requestingDepartmentId);
        Objects.requireNonNull(careEpisodeType);
        Objects.requireNonNull(careEpisodeId);
        if (careEpisodeType != CareEpisodeType.OUTPATIENT_VISIT) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID",
                    "Only outpatient Lab orders are available in the MVP");
        }
        Objects.requireNonNull(labType);
        Objects.requireNonNull(requestedDate);
        if (priceCode == null || priceCode.isBlank() || priceCode.length() > 64) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID", "A valid Lab price code is required");
        }
        Objects.requireNonNull(orderedAt);
    }
}
