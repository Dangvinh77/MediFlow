package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DoctorWorkShiftTest {
    private final UUID doctor = UUID.randomUUID(), department = UUID.randomUUID(), room = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 11, 2);
    private DoctorWorkShift shift() {
        return DoctorWorkShift.active(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(7, 0), LocalTime.of(12, 0));
    }
    @Test void outpatientWindowAndMinutePrecisionAreRequired() {
        assertThatThrownBy(() -> DoctorWorkShift.active(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(6, 59), LocalTime.of(12, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DoctorWorkShift.active(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(7, 0), LocalTime.of(17, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DoctorWorkShift.active(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(7, 0, 1), LocalTime.of(12, 0))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cancellationIsTerminalAndRequiresRevisionAndReason() {
        var original = shift();
        assertThatThrownBy(() -> original.cancel(2, "cancel")).hasMessage("REVISION_CONFLICT");
        assertThatThrownBy(() -> original.cancel(1, " ")).isInstanceOf(IllegalArgumentException.class);
        var cancelled = original.cancel(1, "Doctor unavailable");
        assertThat(cancelled.status()).isEqualTo(DoctorWorkShift.Status.CANCELLED);
        assertThat(cancelled.revision()).isEqualTo(2);
        assertThatThrownBy(() -> cancelled.update(2, original.configuration(), "reschedule"))
                .hasMessage("SHIFT_INACTIVE");
    }
    @Test void linkedSessionMustUseTheExactResourcesDayAndContainedTimes() {
        var shift = shift();
        var matching = ConsultationSession.open(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(8, 0), LocalTime.of(10, 0), List.of(), 10);
        assertThatCode(() -> shift.requireContains(matching.configuration())).doesNotThrowAnyException();
        var outside = ConsultationSession.open(UUID.randomUUID(), doctor, department, room, day,
                LocalTime.of(11, 0), LocalTime.of(13, 0), List.of(), 10);
        assertThatThrownBy(() -> shift.requireContains(outside.configuration())).hasMessage("SHIFT_SESSION_MISMATCH");
        assertThatThrownBy(() -> shift.cancel(1, "cancel").requireContains(matching.configuration()))
                .hasMessage("SHIFT_INACTIVE");
    }
}
