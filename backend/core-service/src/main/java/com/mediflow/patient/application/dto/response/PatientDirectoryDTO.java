package com.mediflow.patient.application.dto.response;

import java.time.LocalDate;
import java.util.UUID;

/** Minimal human identity for prescribing, dispensing, cashier and laboratory workflows. */
public record PatientDirectoryDTO(UUID maBenhNhan, String hoTen, LocalDate ngaySinh) {
    public static PatientDirectoryDTO from(PatientDTO patient) {
        return new PatientDirectoryDTO(patient.maBenhNhan(), patient.hoTen(), patient.ngaySinh());
    }
}
