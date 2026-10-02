package com.mediflow.pharmacy.application.dto.request;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Toàn bộ đơn thuốc bác sĩ kê. V0 giữ bốn UUID tham chiếu legacy; V1 mang thêm episode/admission
 * identity tường minh và không chọn context từ patient hoặc record gần nhất.
 * Danh sách lines phải có ít nhất 1 dòng, và mỗi dòng bên trong được validate nhờ @Valid.
 *
 * @param recordId medical record reference; optional only for an explicit V1 care contract
 * @param patientId patient reference
 * @param doctorId prescribing doctor reference
 * @param departmentId department reference
 * @param prescribedDate prescription date
 * @param lines prescribed drug lines
 * @param careContractVersion optional explicit V1 selector; absent/0 preserves legacy V0
 * @param careContext explicit outpatient/admission context for V1
 * @param careEpisodeType authoritative episode type for V1
 * @param careEpisodeId authoritative episode ID for V1
 * @param admissionId exact admission ID for V1 admission prescriptions
 * @param priceCode stable Billing catalog key for V1
 */
public record CreatePrescriptionRequest(
        UUID recordId,
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        @NotNull UUID departmentId,
        @NotNull @PastOrPresent LocalDate prescribedDate,
        @NotEmpty @Valid List<PrescriptionLineRequest> lines,
        Integer careContractVersion,
        CareContext careContext,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        UUID admissionId,
        @Size(max = 64) String priceCode
) {

    /** Source-compatible constructor for all existing V0 clients and Java call sites. */
    public CreatePrescriptionRequest(
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID departmentId,
            LocalDate prescribedDate,
            List<PrescriptionLineRequest> lines) {
        this(recordId, patientId, doctorId, departmentId, prescribedDate, lines,
                null, null, null, null, null, null);
    }

    /**
     * Requires an explicit, complete context when the request selects V1. Missing V1 fields
     * never fall back to the legacy interpretation.
     */
    @AssertTrue(message = "PHARMACY_CARE_CONTEXT_INVALID")
    @JsonIgnore
    public boolean isCareContractValid() {
        if (careContractVersion == null || careContractVersion == 0) {
            return recordId != null
                    && careContext == null
                    && careEpisodeType == null
                    && careEpisodeId == null
                    && admissionId == null
                    && priceCode == null;
        }
        if (careContractVersion != 1 || careContext == null || careEpisodeType == null
                || careEpisodeId == null || priceCode == null || priceCode.isBlank()
                || priceCode.length() > 64) {
            return false;
        }
        return switch (careContext) {
            case OUTPATIENT -> careEpisodeType == CareEpisodeType.OUTPATIENT_VISIT
                    && admissionId == null;
            case ADMISSION -> careEpisodeType == CareEpisodeType.ADMISSION
                    && admissionId != null
                    && admissionId.equals(careEpisodeId);
        };
    }
}
