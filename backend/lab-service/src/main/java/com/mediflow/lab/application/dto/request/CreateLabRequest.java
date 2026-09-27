package com.mediflow.lab.application.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.lab.domain.model.CareEpisodeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** HTTP/application request for opening a lab test. */
public record CreateLabRequest(
        @NotNull UUID recordId,
        @NotNull UUID patientId,
        @NotNull UUID requestingDepartmentId,
        @NotBlank @Size(max = 50) String labType,
        @NotNull @PastOrPresent LocalDate requestedDate,
        UUID sourceOrderId,
        CareEpisodeType careEpisodeType,
        UUID careEpisodeId,
        @Size(max = 64) String priceCode
) {
    /** Compatibility constructor for the existing version-0 request contract. */
    public CreateLabRequest(UUID recordId, UUID patientId, UUID requestingDepartmentId,
                            String labType, LocalDate requestedDate) {
        this(recordId, patientId, requestingDepartmentId, labType, requestedDate,
                null, null, null, null);
    }
}
