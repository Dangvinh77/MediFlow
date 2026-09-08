package com.mediflow.clinical.application.dto.request;

import java.util.UUID;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record CreateRecordRequest(
        @NotNull UUID patientId, @NotNull UUID doctorId, @NotNull UUID departmentId,
        @NotNull @PastOrPresent LocalDate examinationDate,
        @Size(max = 4000) String symptoms, UUID appointmentId,
        @NotEmpty @Valid List<@NotNull AddDiagnosisRequest> diagnoses
) {
    public CreateRecordRequest {
        // Keep invalid nulls for Bean Validation to report as field errors.
        if (diagnoses != null) diagnoses = Collections.unmodifiableList(new ArrayList<>(diagnoses));
    }
}
