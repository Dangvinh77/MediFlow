package com.mediflow.organization.domain.model;

import com.mediflow.organization.domain.exception.InvalidDepartmentException;

public record DepartmentProfile(String description, String phoneNumber, String email) {
    public static final DepartmentProfile EMPTY = new DepartmentProfile(null, null, null);
    public DepartmentProfile {
        description = clean(description); phoneNumber = clean(phoneNumber); email = clean(email);
        if (description != null && description.length() > 2000) throw new InvalidDepartmentException("Description must not exceed 2000 characters");
        if (phoneNumber != null && !phoneNumber.matches("[0-9+() .-]{3,30}")) throw new InvalidDepartmentException("Department phone or extension is invalid");
        if (email != null && (email.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw new InvalidDepartmentException("Department email is invalid");
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
