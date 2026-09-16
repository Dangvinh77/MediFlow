package com.mediflow.clinical.domain.model;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Appointment {
    public static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(17, 0);

    private final UUID appointmentId;
    private final UUID patientId;
    private final UUID doctorId;
    private final UUID departmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private AppointmentStatus status;
    private String reason;
    private final Instant createdAt;
    private Instant updatedAt;
    @Getter(AccessLevel.NONE)
    private final Clock clock;

    public static Appointment create(UUID patientId, UUID doctorId, UUID departmentId,
                                     LocalDate date, LocalTime time, String reason) {
        return create(patientId, doctorId, departmentId, date, time, reason, Clock.systemDefaultZone());
    }

    /** Clock overload makes BR-A1 deterministic without coupling domain to Spring. */
    public static Appointment create(UUID patientId, UUID doctorId, UUID departmentId,
                                     LocalDate date, LocalTime time, String reason, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        validateReferences(patientId, doctorId, departmentId);
        validateSchedule(date, time, clock);
        return new Appointment(UUID.randomUUID(), patientId, doctorId, departmentId, date, time,
                AppointmentStatus.PENDING, reason, clock.instant(), null, clock);
    }

    /** Loading history must not reapply the new-appointment 'not in the past' rule. */
    public static Appointment restore(UUID id, UUID patientId, UUID doctorId, UUID departmentId,
                                      LocalDate date, LocalTime time, AppointmentStatus status,
                                      String reason, Instant createdAt, Instant updatedAt) {
        validateReferences(patientId, doctorId, departmentId);
        return new Appointment(Objects.requireNonNull(id), patientId, doctorId, departmentId,
                Objects.requireNonNull(date), Objects.requireNonNull(time), Objects.requireNonNull(status),
                reason, Objects.requireNonNull(createdAt), updatedAt, Clock.systemDefaultZone());
    }

    public void update(LocalDate date, LocalTime time, String reason) {
        validateSchedule(date, time, clock);
        this.appointmentDate = date;
        this.appointmentTime = time;
        this.reason = reason;
        this.updatedAt = clock.instant();
    }

    /** BR-A5: only a pending appointment may arrive or be cancelled. */
    public void changeStatus(AppointmentStatus next) {
        if (status != AppointmentStatus.PENDING
                || (next != AppointmentStatus.ARRIVED && next != AppointmentStatus.CANCELLED)) {
            throw new InvalidClinicalDataException("APPOINTMENT_INVALID_TRANSITION", "Invalid appointment status transition");
        }
        status = next;
        updatedAt = clock.instant();
    }

    /** BR-R4: application invokes this inside the record creation transaction. */
    public void markArrived() { changeStatus(AppointmentStatus.ARRIVED); }

    public boolean isPending() { return status == AppointmentStatus.PENDING; }

    private static void validateReferences(UUID patientId, UUID doctorId, UUID departmentId) {
        if (patientId == null || doctorId == null || departmentId == null) {
            throw new InvalidClinicalDataException("APPOINTMENT_REF_REQUIRED", "Patient, doctor and department are required");
        }
    }

    private static void validateSchedule(LocalDate date, LocalTime time, Clock clock) {
        if (date == null) {
            throw new InvalidClinicalDataException("APPOINTMENT_DATE_REQUIRED", "Appointment date is required");
        }
        if (date.isBefore(LocalDate.now(clock))) {
            throw new InvalidClinicalDataException("APPOINTMENT_PAST_DATE", "Appointment date cannot be in the past");
        }
        if (time == null || time.isBefore(OPENING_TIME) || time.isAfter(CLOSING_TIME)) {
            throw new InvalidClinicalDataException("APPOINTMENT_TIME_OUT_OF_HOURS", "Appointment time must be between 07:00 and 17:00");
        }
    }
}
