package com.mediflow.organization.domain.model;

import java.time.LocalDate;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;

public record StaffProfile(LocalDate dateOfBirth, Gender gender, String address, LocalDate hireDate, String qualification) {
    public enum Gender { MALE, FEMALE, OTHER }
    public static final StaffProfile EMPTY = new StaffProfile(null, null, null, null, null);
    public StaffProfile {
        address = clean(address); qualification = clean(qualification);
        if (dateOfBirth != null && !dateOfBirth.isBefore(LocalDate.now())) throw new InvalidStaffDataException("Date of birth must be in the past");
        if (hireDate != null && dateOfBirth != null && !hireDate.isAfter(dateOfBirth)) throw new InvalidStaffDataException("Hire date must be after date of birth");
        if (address != null && address.length() > 255) throw new InvalidStaffDataException("Address must not exceed 255 characters");
        if (qualification != null && qualification.length() > 100) throw new InvalidStaffDataException("Qualification must not exceed 100 characters");
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
