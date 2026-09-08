package com.mediflow.clinical.domain.model;

import com.mediflow.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AppointmentTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private final UUID patient = UUID.randomUUID();
    private final UUID doctor = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();

    private Appointment create(LocalDate date, LocalTime time) {
        return Appointment.create(patient, doctor, department, date, time, "Check-up", CLOCK);
    }

    @Test void create_pastDate_rejects() {
        assertThatThrownBy(() -> create(TODAY.minusDays(1), LocalTime.NOON))
                .isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", "APPOINTMENT_PAST_DATE");
    }

    @ParameterizedTest @CsvSource({"07:00", "17:00"})
    void create_openingAndClosingTime_accepts(LocalTime time) {
        Appointment appointment = create(TODAY, time);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(appointment.getAppointmentId()).isNotNull();
        assertThat(appointment.getCreatedAt()).isEqualTo(CLOCK.instant());
    }

    @ParameterizedTest @CsvSource({"06:59", "17:01", "18:00"})
    void create_outsideHours_rejects(LocalTime time) {
        assertThatThrownBy(() -> create(TODAY, time)).hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_OUT_OF_HOURS");
    }

    @Test void create_missingReference_rejects() {
        assertThatThrownBy(() -> Appointment.create(null, doctor, department, TODAY, LocalTime.NOON, null, CLOCK))
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_REF_REQUIRED");
    }

    @ParameterizedTest @CsvSource({"PENDING,ARRIVED", "PENDING,CANCELLED"})
    void changeStatus_allowedTransition_succeeds(AppointmentStatus from, AppointmentStatus to) {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        appointment.changeStatus(to);
        assertThat(appointment.getStatus()).isEqualTo(to);
        assertThat(appointment.isPending()).isFalse();
    }

    @ParameterizedTest @CsvSource({"PENDING,PENDING", "ARRIVED,PENDING", "ARRIVED,ARRIVED", "ARRIVED,CANCELLED", "CANCELLED,PENDING", "CANCELLED,ARRIVED", "CANCELLED,CANCELLED"})
    void changeStatus_invalidTransition_rejects(AppointmentStatus from, AppointmentStatus to) {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        if (from != AppointmentStatus.PENDING) appointment.changeStatus(from);
        assertThatThrownBy(() -> appointment.changeStatus(to)).hasFieldOrPropertyWithValue("code", "APPOINTMENT_INVALID_TRANSITION");
        assertThat(appointment.getStatus()).isEqualTo(from);
    }

    @Test void update_invalidTime_doesNotPartiallyMutate() {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        assertThatThrownBy(() -> appointment.update(TODAY.plusDays(1), LocalTime.of(18, 0), "changed"))
                .hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_OUT_OF_HOURS");
        assertThat(appointment.getAppointmentDate()).isEqualTo(TODAY);
        assertThat(appointment.getReason()).isEqualTo("Check-up");
        assertThat(appointment.getUpdatedAt()).isNull();
    }

    @Test void markArrived_pending_setsArrived() {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        appointment.markArrived();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
    }

    @Test void update_validSchedule_preservesIdentityAndReferences() {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        UUID id = appointment.getAppointmentId();
        appointment.update(TODAY.plusDays(2), LocalTime.of(9, 0), "Rescheduled");
        assertThat(appointment.getAppointmentId()).isEqualTo(id);
        assertThat(appointment.getPatientId()).isEqualTo(patient);
        assertThat(appointment.getDoctorId()).isEqualTo(doctor);
        assertThat(appointment.getDepartmentId()).isEqualTo(department);
        assertThat(appointment.getAppointmentDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(appointment.getAppointmentTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(appointment.getReason()).isEqualTo("Rescheduled");
        assertThat(appointment.getUpdatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test void create_missingDateOrTime_returnsTypedErrors() {
        assertThatThrownBy(() -> create(null, LocalTime.NOON)).hasFieldOrPropertyWithValue("code", "APPOINTMENT_DATE_REQUIRED");
        assertThatThrownBy(() -> create(TODAY, null)).hasFieldOrPropertyWithValue("code", "APPOINTMENT_TIME_OUT_OF_HOURS");
    }

    @Test void changeStatus_null_rejects() {
        Appointment appointment = create(TODAY, LocalTime.NOON);
        assertThatThrownBy(() -> appointment.changeStatus(null)).hasFieldOrPropertyWithValue("code", "APPOINTMENT_INVALID_TRANSITION");
        assertThat(appointment.isPending()).isTrue();
    }

    @Test void restore_historicalAppointment_preservesIdentityAndTimestamps() {
        UUID id = UUID.randomUUID();
        Instant created = CLOCK.instant().minusSeconds(86400 * 10);
        Appointment appointment = Appointment.restore(id, patient, doctor, department, TODAY.minusDays(10),
                LocalTime.NOON, AppointmentStatus.ARRIVED, null, created, CLOCK.instant());
        assertThat(appointment.getAppointmentId()).isEqualTo(id);
        assertThat(appointment.getAppointmentDate()).isEqualTo(TODAY.minusDays(10));
        assertThat(appointment.getCreatedAt()).isEqualTo(created);
    }
}
