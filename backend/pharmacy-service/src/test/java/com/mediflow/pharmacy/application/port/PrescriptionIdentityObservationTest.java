package com.mediflow.pharmacy.application.port;

import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort.Observation;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.common.exception.BusinessRuleException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PrescriptionIdentityObservationTest {
    private final UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), department = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T01:00:00Z");
    private Observation proof(boolean exists, boolean staff, boolean eligible, UUID staffDepartment, boolean active, Instant at) {
        return new Observation(patient, exists, doctor, staff, eligible, staffDepartment, department, active, at);
    }
    @Test void require_exactCurrentIdentityAcceptsWithoutUsingAccountOrTeamRoles() {
        proof(true, true, true, department, true, now).requireExact(patient, doctor, department, now);
    }
    @ParameterizedTest @ValueSource(strings = {"patient", "doctor", "ineligible", "department", "inactive"})
    void require_confirmedAbsenceOrIneligibilityDenies(String defect) {
        var proof = proof(!defect.equals("patient"), !defect.equals("doctor"), !defect.equals("ineligible"),
                defect.equals("department") ? UUID.randomUUID() : department, !defect.equals("inactive"), now);
        assertThatThrownBy(() -> proof.requireExact(patient, doctor, department, now)).isInstanceOf(BusinessRuleException.class);
    }
    @ParameterizedTest @ValueSource(longs = {-31, 6})
    void require_staleOrFutureObservationCannotAuthorize(long seconds) {
        assertThatThrownBy(() -> proof(true, true, true, department, true, now.plusSeconds(seconds))
                .requireExact(patient, doctor, department, now)).isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
    @Test void require_wrongRequestedIdentityIsUnavailableNotPermission() {
        assertThatThrownBy(() -> proof(true, true, true, department, true, now)
                .requireExact(UUID.randomUUID(), doctor, department, now)).isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
}
