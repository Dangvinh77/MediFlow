package com.mediflow.common.security;

/** Claim names carried in the MediFlow JWT. Keep every service in agreement. */
public final class JwtClaims {

    /** Subject = account id for human tokens, service identity for service tokens. */
    public static final String SUBJECT = "sub";
    /** Single role claim, e.g. "DOCTOR". */
    public static final String ROLE = "role";
    /** Token purpose: access, refresh, or service. */
    public static final String TYPE = "type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    public static final String SERVICE_TOKEN_TYPE = "service";
    /** Optional authenticated staff identity. */
    public static final String STAFF_ID = "staffId";
    /** Optional authenticated department identity. */
    public static final String DEPARTMENT_ID = "departmentId";
    /** Optional authenticated patient identity. */
    public static final String PATIENT_ID = "patientId";
    /** Correlation id propagated for tracing. */
    public static final String CORRELATION_ID = "cid";

    /** Header the gateway uses to propagate the correlation id downstream. */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private JwtClaims() {
    }
}
