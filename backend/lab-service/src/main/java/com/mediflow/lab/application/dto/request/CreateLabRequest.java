package com.mediflow.lab.application.dto.request;

import java.time.LocalDate;
import java.util.UUID;

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
        @NotNull @PastOrPresent LocalDate requestedDate
) {}
