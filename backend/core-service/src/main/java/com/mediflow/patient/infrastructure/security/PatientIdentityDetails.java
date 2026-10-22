package com.mediflow.patient.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import java.util.UUID;

/** Identity copied only from a locally verified access token. */
public final class PatientIdentityDetails extends WebAuthenticationDetails {
    private final UUID patientId;

    public PatientIdentityDetails(HttpServletRequest request, String patientId) {
        super(request);
        this.patientId = patientId == null ? null : UUID.fromString(patientId);
    }

    public UUID getPatientId() { return patientId; }
}
