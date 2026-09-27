package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CareFinanceV2ContractDomainTest {

    private static final Instant NOW = Instant.parse("2026-09-27T03:00:00Z");

    @Test
    void clearance_matchingAppointmentEpisode_isAccepted() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        ExamClearance clearance = clearance(patientId, appointmentId,
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, ClearancePurpose.EXAM);

        Appointment appointment = Appointment.restore(appointmentId, patientId, UUID.randomUUID(),
                UUID.randomUUID(), java.time.LocalDate.now(), java.time.LocalTime.NOON,
                AppointmentStatus.AWAITING_PAYMENT, null, NOW, NOW, (short) 1, null, null,
                null, "OUTPATIENT_EXAM", NOW, null, null);

        assertThat(clearance.matchesAppointment(appointment)).isTrue();
    }

    @Test
    void clearance_wrongPurposeOrEpisode_rejects() {
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        assertThatThrownBy(() -> clearance(patientId, appointmentId,
                CareEpisodeType.OUTPATIENT_VISIT, appointmentId, ClearancePurpose.LAB_TEST))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_CLEARANCE_TARGET_MISMATCH");
        assertThatThrownBy(() -> clearance(patientId, appointmentId,
                CareEpisodeType.ADMISSION, UUID.randomUUID(), ClearancePurpose.EXAM))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_CLEARANCE_TARGET_MISMATCH");
    }

    @Test
    void emergencyOverride_missingAuditField_rejects() {
        assertThatThrownBy(() -> EmergencyOverride.create(UUID.randomUUID(), UUID.randomUUID(), null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DOCTOR", " ", NOW))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_OVERRIDE_INVALID");
    }

    private static ExamClearance clearance(UUID patientId, UUID appointmentId,
                                           CareEpisodeType episodeType, UUID episodeId,
                                           ClearancePurpose purpose) {
        return ExamClearance.grant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                appointmentId, null, patientId, episodeType, episodeId, purpose,
                new BigDecimal("150000.00"), "VND", null, false, NOW);
    }
}
