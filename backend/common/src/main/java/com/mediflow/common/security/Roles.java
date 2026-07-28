package com.mediflow.common.security;

/**
 * Canonical role names shared across all services. See docs/ai/07-security-rbac.md.
 * Spring Security expects authorities prefixed with "ROLE_"; use these bare names
 * with hasAnyRole(...) / hasRole(...) which add the prefix automatically.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String DOCTOR = "DOCTOR";
    public static final String NURSE = "NURSE";
    public static final String PHARMACIST = "PHARMACIST";
    public static final String CASHIER = "CASHIER";
    public static final String LAB_TECH = "LAB_TECH";
    public static final String MANAGER = "MANAGER";
    public static final String PATIENT = "PATIENT";
    public static final String SYSTEM = "SYSTEM";

    private Roles() {
    }
}
