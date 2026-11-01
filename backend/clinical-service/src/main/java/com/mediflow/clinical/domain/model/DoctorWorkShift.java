package com.mediflow.clinical.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** A work assignment, separate from bookable consultation sessions. */
public record DoctorWorkShift(UUID shiftId, int revision, Configuration configuration, Status status) {
    public enum Status { ACTIVE, CANCELLED }
    public DoctorWorkShift {
        Objects.requireNonNull(shiftId); Objects.requireNonNull(configuration); Objects.requireNonNull(status);
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
    }
    public static DoctorWorkShift active(UUID id, UUID doctor, UUID department, UUID room,
            LocalDate day, LocalTime start, LocalTime end) {
        return new DoctorWorkShift(id, 1, new Configuration(doctor, department, room, day, start, end), Status.ACTIVE);
    }
    public DoctorWorkShift update(int expectedRevision, Configuration replacement, String reason) {
        requireCommand(expectedRevision, reason);
        if (!configuration.departmentId().equals(replacement.departmentId()))
            throw new IllegalArgumentException("shift department cannot change");
        return new DoctorWorkShift(shiftId, revision + 1, replacement, status);
    }
    public DoctorWorkShift cancel(int expectedRevision, String reason) {
        requireCommand(expectedRevision, reason);
        return new DoctorWorkShift(shiftId, revision + 1, configuration, Status.CANCELLED);
    }
    private void requireCommand(int expectedRevision, String reason) {
        if (expectedRevision != revision) throw new IllegalStateException("REVISION_CONFLICT");
        if (status != Status.ACTIVE) throw new IllegalStateException("SHIFT_INACTIVE");
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IllegalArgumentException("reason must contain 1 to 500 characters");
    }
    public void requireContains(ConsultationSession.Configuration session) {
        if (status != Status.ACTIVE) throw new IllegalStateException("SHIFT_INACTIVE");
        var c = configuration;
        if (!c.doctorId().equals(session.doctorId()) || !c.departmentId().equals(session.departmentId())
                || !c.roomId().equals(session.roomId()) || !c.shiftDate().equals(session.sessionDate())
                || session.startTime().isBefore(c.startTime()) || session.endTime().isAfter(c.endTime()))
            throw new IllegalStateException("SHIFT_SESSION_MISMATCH");
    }
    public record Configuration(UUID doctorId, UUID departmentId, UUID roomId, LocalDate shiftDate,
            LocalTime startTime, LocalTime endTime) {
        public Configuration {
            Objects.requireNonNull(doctorId); Objects.requireNonNull(departmentId); Objects.requireNonNull(roomId);
            Objects.requireNonNull(shiftDate); Objects.requireNonNull(startTime); Objects.requireNonNull(endTime);
            if (startTime.isBefore(LocalTime.of(7, 0)) || endTime.isAfter(LocalTime.of(17, 0))
                    || !startTime.isBefore(endTime) || startTime.getSecond() != 0 || startTime.getNano() != 0
                    || endTime.getSecond() != 0 || endTime.getNano() != 0)
                throw new IllegalArgumentException("shift requires minute precision within 07:00-17:00");
        }
    }
}
