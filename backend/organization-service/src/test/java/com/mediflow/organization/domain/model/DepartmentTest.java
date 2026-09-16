package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.InvalidDepartmentException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepartmentTest {

    @Test
    void create_validDepartment_succeeds() {
        assertDoesNotThrow(() ->
                Department.create(
                        UUID.randomUUID(),
                        "Internal Medicine Department",
                        "NOI",
                        DepartmentType.CLINICAL,
                        "Building A, Floor 3"
                )
        );
    }

    @Test
    void create_blankName_throwsException() {
        assertThrows(
                InvalidDepartmentException.class,
                () -> Department.create(
                        UUID.randomUUID(),
                        " ",
                        "NOI",
                        DepartmentType.CLINICAL,
                        "Tang 3"
                )
        );
    }

    @Test
    void create_lowercaseAbbreviation_throwsException() {
        assertThrows(
                InvalidDepartmentException.class,
                () -> Department.create(
                        UUID.randomUUID(),
                        "Internal Medicine",
                        "noi",
                        DepartmentType.CLINICAL,
                        "Building A, Floor 3"
                )
        );
    }

    @Test
    void create_nullDepartmentType_throwsException() {
        assertThrows(
                InvalidDepartmentException.class,
                () -> Department.create(
                        UUID.randomUUID(),
                        "Internal Medicine",
                        "NOI",
                        null,
                        "Building A, Floor 3"
                )
        );
    }
}
