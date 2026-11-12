package com.mediflow.patient.application.dto.response;

import com.mediflow.patient.domain.model.Gender;

import java.time.LocalDate;
import java.util.UUID;

/** Service-only projection for Lab reference ranges: sex and date of birth, nothing that identifies. */
public record PatientDemographicsDTO(UUID maBenhNhan, Gender gioiTinh, LocalDate ngaySinh) {
}
