package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrescriptionIdentityAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-09T01:00:00Z");
    private static final UUID PATIENT = UUID.randomUUID(), DOCTOR = UUID.randomUUID(), DEPARTMENT = UUID.randomUUID();
    private static final String CORRELATION = "identity-preflight-clock";
    private final PharmacyPatientFeignClient patients = mock(PharmacyPatientFeignClient.class);
    private final PharmacyOrganizationFeignClient organization = mock(PharmacyOrganizationFeignClient.class);
    private final Clock clock = mock(Clock.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PrescriptionIdentityAdapter adapter = new PrescriptionIdentityAdapter(patients, organization, clock,
            new JwtProperties("pharmacy-identity-unit-secret-at-least-32-bytes"), json);

    @BeforeEach void replies() throws Exception {
        when(patients.exists(eq(PATIENT), anyString(), eq(CORRELATION)))
                .thenReturn(response(Map.of("exists", true, "patientId", PATIENT.toString())));
        when(organization.staff(eq(DOCTOR), anyString(), eq(CORRELATION)))
                .thenReturn(response(Map.of("exists", true, "active", true, "jobTitle", "DOCTOR",
                        "departmentId", DEPARTMENT.toString(), "eligibleTeamRoles", List.of())));
        when(organization.doctorEligibility(eq(DOCTOR), anyString(), eq(CORRELATION)))
                .thenReturn(response(Map.of("exists", true, "eligibleDoctor", true,
                        "departmentId", DEPARTMENT.toString())));
        when(organization.department(eq(DEPARTMENT), anyString(), eq(CORRELATION)))
                .thenReturn(response(Map.of("exists", true, "active", true, "departmentId", DEPARTMENT.toString(),
                        "departmentName", "Clinical", "departmentType", "CLINICAL")));
    }

    @ParameterizedTest @ValueSource(ints = {0, 30, 31, -5, -6})
    void lookup_clockBoundsUseStartNotCompletionToFreshenSlowReads(int elapsedSeconds) {
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(elapsedSeconds));
        if (elapsedSeconds > 30 || elapsedSeconds < -5) {
            assertThatThrownBy(() -> adapter.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION))
                    .isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
        } else {
            var proof = adapter.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION);
            assertThat(proof.checkedAt()).isEqualTo(NOW);
            proof.requireExact(PATIENT, DOCTOR, DEPARTMENT, NOW.plusSeconds(elapsedSeconds));
        }
    }

    @Test
    void lookup_compatibilityDoctorEligibilityTrueConfirmsOnlyExactPrescriberIdentity() {
        when(clock.instant()).thenReturn(NOW, NOW);

        var proof = adapter.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION);

        assertThat(proof.eligibleDoctor()).isTrue();
        proof.requireExact(PATIENT, DOCTOR, DEPARTMENT, NOW);
        verify(organization).doctorEligibility(eq(DOCTOR), anyString(), eq(CORRELATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "absent"})
    void lookup_compatibilityConfirmedNegativeDoesNotFallbackToGenericDoctorJob(String state) throws Exception {
        var data = new HashMap<String, Object>();
        data.put("exists", state.equals("absent") ? false : true);
        data.put("eligibleDoctor", false);
        data.put("departmentId", null);
        when(organization.doctorEligibility(eq(DOCTOR), anyString(), eq(CORRELATION)))
                .thenReturn(response(data));
        when(clock.instant()).thenReturn(NOW, NOW);

        var proof = adapter.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION);

        assertThat(proof.eligibleDoctor()).isFalse();
        assertThatThrownBy(() -> proof.requireExact(PATIENT, DOCTOR, DEPARTMENT, NOW))
                .isInstanceOf(com.mediflow.common.exception.BusinessRuleException.class)
                .hasMessage("An active doctor is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent-eligible", "eligible-null", "ineligible-department", "malformed-boolean",
            "missing-eligible", "null-eligible", "wrong-department", "malformed-uuid", "wrong-correlation", "failure"})
    void lookup_compatibilityMalformedOrUnavailableResponseFailsClosed(String defect) throws Exception {
        var data = new HashMap<String, Object>();
        data.put("exists", true);
        data.put("eligibleDoctor", true);
        data.put("departmentId", DEPARTMENT.toString());
        String correlation = CORRELATION;
        ResponseEntity<String> response;
        switch (defect) {
            case "absent-eligible" -> { data.put("exists", false); response = response(data); }
            case "eligible-null" -> { data.put("departmentId", null); response = response(data); }
            case "ineligible-department" -> { data.put("eligibleDoctor", false); response = response(data); }
            case "malformed-boolean" -> { data.put("eligibleDoctor", "true"); response = response(data); }
            case "missing-eligible" -> { data.remove("eligibleDoctor"); response = response(data); }
            case "null-eligible" -> { data.put("eligibleDoctor", null); response = response(data); }
            case "wrong-department" -> { data.put("departmentId", UUID.randomUUID().toString()); response = response(data); }
            case "malformed-uuid" -> { data.put("departmentId", "not-a-uuid"); response = response(data); }
            case "wrong-correlation" -> { correlation = "different-correlation"; response = response(data, correlation); }
            case "failure" -> response = ResponseEntity.status(503).header("X-Correlation-Id", CORRELATION).body("unavailable");
            default -> throw new IllegalArgumentException("unknown defect: " + defect);
        }
        when(organization.doctorEligibility(eq(DOCTOR), anyString(), eq(CORRELATION))).thenReturn(response);

        assertThatThrownBy(() -> adapter.lookup(PATIENT, DOCTOR, DEPARTMENT, CORRELATION))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings = {"missing-patient", "missing-doctor", "missing-department", "empty", "control", "long"})
    void lookup_invalidReferenceOrCorrelationFailsBeforeRemoteReads(String defect) {
        String correlation = switch (defect) {
            case "empty" -> " ";
            case "control" -> "bad\ncorrelation";
            case "long" -> "a".repeat(121);
            default -> CORRELATION;
        };
        assertThatThrownBy(() -> adapter.lookup(defect.equals("missing-patient") ? null : PATIENT,
                defect.equals("missing-doctor") ? null : DOCTOR,
                defect.equals("missing-department") ? null : DEPARTMENT, correlation))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(clock, patients, organization);
    }

    private ResponseEntity<String> response(Map<String, Object> data) throws Exception {
        return response(data, CORRELATION);
    }

    private ResponseEntity<String> response(Map<String, Object> data, String correlation) throws Exception {
        var envelope = json.createObjectNode().put("success", true).put("correlationId", correlation).putNull("error");
        envelope.set("data", json.valueToTree(data));
        return ResponseEntity.ok().header("X-Correlation-Id", correlation).body(json.writeValueAsString(envelope));
    }
}
