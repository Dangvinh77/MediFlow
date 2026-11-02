package com.mediflow.report.infrastructure.security;

/** Credential marker produced only after local signature, expiry and access-type verification. */
public final class VerifiedReportBearer {
    private final String authorization;

    VerifiedReportBearer(String authorization) {
        this.authorization = authorization;
    }

    String authorization() {
        return authorization;
    }

    @Override
    public String toString() {
        return "[verified bearer]";
    }
}
