package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffTest {

    @Test
    void create_validStaff_succeeds() {
        assertDoesNotThrow(() ->
                Staff.create(
                        "Nguyen Van A",
                        UUID.randomUUID(),
                        JobTitle.NURSE,
                        null,
                        null,
                        "0912345678",
                        "nurse@example.com"
                )
        );
    }

    @Test
    void create_doctorWithoutLicense_throwsException() {
        assertThrows(
                DoctorLicenseRequiredException.class,
                () -> Staff.create(
                        "Nguyen Van B",
                        UUID.randomUUID(),
                        JobTitle.DOCTOR,
                        "Internal Medicine",
                        null,
                        "0912345678",
                        "doctor@example.com"
                )
        );
    }

    @Test
    void create_nullDepartment_throwsException() {
        assertThrows(
                InvalidStaffDataException.class,
                () -> Staff.create(
                        "Nguyen Van C",
                        null,
                        JobTitle.NURSE,
                        null,
                        null,
                        "0912345678",
                        "nurse@example.com"
                )
        );
    }

    @Test
    void create_invalidPhone_throwsException() {
        assertThrows(
                InvalidStaffDataException.class,
                () -> Staff.create(
                        "Nguyen Van D",
                        UUID.randomUUID(),
                        JobTitle.NURSE,
                        null,
                        null,
                        "123",
                        "nurse@example.com"
                )
        );
    }

    @Test
    void create_invalidEmail_throwsException() {
        assertThrows(
                InvalidStaffDataException.class,
                () -> Staff.create(
                        "Nguyen Van E",
                        UUID.randomUUID(),
                        JobTitle.NURSE,
                        null,
                        null,
                        "0912345678",
                        "invalid-email"
                )
        );
    }
}
