package com.mediflow.common.security;

/** Claim names carried in the MediFlow JWT. Keep every service in agreement. */
public final class JwtClaims {

    /** Subject = user id. */
    public static final String SUBJECT = "sub";
    /** Single role claim, e.g. "DOCTOR". */
    public static final String ROLE = "role";
    /** Correlation id propagated for tracing. */
    public static final String CORRELATION_ID = "cid";

    /** Header the gateway uses to propagate the correlation id downstream. */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private JwtClaims() {
    }
}
