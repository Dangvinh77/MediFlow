package com.mediflow.pharmacy.application.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Only the expected revision and requested medication lines are client editable. */
public record UpdatePrescriptionRequest(
        @NotNull @Min(0) Integer expectedRevision,
        @NotEmpty @Valid List<PrescriptionLineRequest> lines) {
}
