package com.mediflow.pharmacy.application.port.out;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class OutpatientPrescriptionContextObservationTest {
    private final UUID record = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), department = UUID.randomUUID(), episode = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-09T01:00:00Z");
    private OutpatientPrescriptionContextPort.Observation observation(boolean exists) {
        return new OutpatientPrescriptionContextPort.Observation(exists, record, patient, doctor, department, "OUTPATIENT_VISIT", episode, "OPEN", null, now);
    }
    @Test void requireExact_doesNotReplaceAppointmentEpisodeWithRecordId() {
        assertThatCode(() -> observation(true).requireExact(record, patient, doctor, department, episode, now)).doesNotThrowAnyException();
        assertThatThrownBy(() -> observation(true).requireExact(record, patient, doctor, department, record, now)).isInstanceOf(BusinessRuleException.class);
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void requireExact_foreignRelationshipRejected(int field) {
        assertThatThrownBy(() -> observation(true).requireExact(field == 0 ? UUID.randomUUID() : record,
                field == 1 ? UUID.randomUUID() : patient, field == 2 ? UUID.randomUUID() : doctor,
                field == 3 ? UUID.randomUUID() : department, field == 4 ? UUID.randomUUID() : episode, now))
                .isInstanceOf(BusinessRuleException.class);
    }
    @Test void requireExact_missingIsBusinessDenialNotPermission() {
        assertThatThrownBy(() -> observation(false).requireExact(record, patient, doctor, department, episode, now)).isInstanceOf(BusinessRuleException.class);
    }
    @ParameterizedTest @ValueSource(longs = {31, -6})
    void requireExact_rechecksFreshnessAfterLockWait(long seconds) {
        assertThatThrownBy(() -> observation(true).requireExact(record, patient, doctor, department, episode, now.plusSeconds(seconds)))
                .isInstanceOf(PharmacyUpstreamUnavailableException.class);
    }
}
