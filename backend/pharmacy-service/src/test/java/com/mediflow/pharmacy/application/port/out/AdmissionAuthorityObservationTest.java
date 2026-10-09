package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AdmissionAuthorityObservationTest {
    private final UUID admission = UUID.randomUUID(), patient = UUID.randomUUID(), department = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-08T01:00:00Z");
    @Test void exactActiveTuple_passesNecessaryCheckOnly() {
        observation(true, true, "ADMITTED", now).requireExactActive(patient, department, now);
    }
    @Test void differentPatientOrDepartment_rejectsWithoutFallback() {
        var value = observation(true, true, "ADMITTED", now);
        assertThatThrownBy(() -> value.requireExactActive(UUID.randomUUID(), department, now)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> value.requireExactActive(patient, UUID.randomUUID(), now)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void missingDischargedOrClosed_rejectsEvenBeforeAdministrativeClose() {
        for (String state : new String[]{"MEDICALLY_DISCHARGED", "CLOSED", "CANCELLED", "READY"})
            assertThatThrownBy(() -> observation(true, false, state, now).requireExactActive(patient, department, now)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> observation(false, false, null, now).requireExactActive(patient, department, now)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void proofAgesWhileWaitingForLocks_failClosedAtMutationTime() {
        var value = observation(true, true, "ADMITTED", now);
        value.requireExactActive(patient, department, now.plusSeconds(30));
        assertThatThrownBy(() -> value.requireExactActive(patient, department, now.plusSeconds(30).plusNanos(1)))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
    @Test void futureBeyondApprovedSkew_failsUnavailableNotInactive() {
        assertThatThrownBy(() -> observation(true, true, "ADMITTED", now.plusSeconds(6)).requireExactActive(patient, department, now))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
    private AdmissionAuthorityPort.Observation observation(boolean exists, boolean eligible, String status, Instant at) {
        return new AdmissionAuthorityPort.Observation(admission, patient, department, UUID.randomUUID(), exists, eligible, status, "0", at);
    }
}
