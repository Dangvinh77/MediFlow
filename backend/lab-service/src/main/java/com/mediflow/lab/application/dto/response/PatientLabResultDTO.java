package com.mediflow.lab.application.dto.response;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Only completed test results, without financial clearance or cancellation internals. */
public record PatientLabResultDTO(UUID testId, UUID recordId, String labType, LocalDate requestedDate,
        LocalDate performedDate, String conclusion, List<LabResultDTO> results) {
    public PatientLabResultDTO { results = List.copyOf(results); }
    public static PatientLabResultDTO from(LabTestDTO value) {
        return new PatientLabResultDTO(value.testId(), value.recordId(), value.labType(), value.requestedDate(),
                value.performedDate(), value.conclusion(), value.results());
    }
}
