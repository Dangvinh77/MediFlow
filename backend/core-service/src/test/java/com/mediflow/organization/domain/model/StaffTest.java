package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.DoctorLicenseRequiredException;
import com.mediflow.organization.domain.exception.InvalidStaffDataException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "12345678901", "1234567890123", "01234567890A", " 012345678901"})
    void create_requiresTwelveDigitIdentity(String identity) {
        assertThrows(InvalidStaffDataException.class, () -> Staff.create(
                "Staff", UUID.randomUUID(), JobTitle.NURSE, null, null, null, null, identity));
    }

    @Test
    void leadingZeroIdentitySurvivesProfileUpdate() {
        Staff staff = Staff.create("Staff", UUID.randomUUID(), JobTitle.NURSE,
                null, null, null, null, "012345678901");
        staff.update("Updated Staff", JobTitle.NURSE, null, null, null, null);
        org.junit.jupiter.api.Assertions.assertEquals("012345678901", staff.getIdentityNumber());
    }

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
                        "nurse@example.com",
                        "012345678901"
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
                        "doctor@example.com",
                        "012345678901"
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
                        "nurse@example.com",
                        "012345678901"
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
                        "nurse@example.com",
                        "012345678901"
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
                        "invalid-email",
                        "012345678901"
                )
        );
    }
}
