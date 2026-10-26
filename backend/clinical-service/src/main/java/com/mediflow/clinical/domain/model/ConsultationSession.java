package com.mediflow.clinical.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Clinical-owned session configuration and the serialized capacity snapshot. */
public record ConsultationSession(
        UUID sessionId,
        int revision,
        Configuration configuration,
        ConsultationSessionStatus status,
        Counts counts) {

    private static final LocalTime EARLIEST_START = LocalTime.of(7, 0);
    private static final LocalTime LATEST_END = LocalTime.of(17, 0);

    public ConsultationSession {
        Objects.requireNonNull(sessionId, "session id is required");
        Objects.requireNonNull(configuration, "session configuration is required");
        Objects.requireNonNull(status, "session status is required");
        Objects.requireNonNull(counts, "session counts are required");
        if (revision < 1) throw new IllegalArgumentException("session revision must be positive");
        counts = counts.forCapacity(configuration.capacity());
    }

    public static ConsultationSession open(
            UUID sessionId,
            UUID doctorId,
            UUID departmentId,
            UUID roomId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            List<ConsultationBreak> breaks,
            int capacity) {
        Configuration configuration = new Configuration(
                doctorId, departmentId, roomId, sessionDate, startTime, endTime, breaks, capacity);
        return new ConsultationSession(sessionId, 1, configuration,
                ConsultationSessionStatus.OPEN, Counts.empty(capacity));
    }

    public ConsultationSession updateConfiguration(int expectedRevision, Configuration replacement, String reason) {
        requireRevision(expectedRevision);
        requireReason(reason);
        Objects.requireNonNull(replacement, "replacement configuration is required");
        if (!configuration.departmentId().equals(replacement.departmentId())) {
            throw new IllegalArgumentException("session department cannot be changed");
        }
        if (counts.occupiedCount() > 0 && !sameExceptCapacity(configuration, replacement)) {
            throw new IllegalStateException("SESSION_HAS_REGISTRATIONS");
        }
        if (counts.occupiedCount() > replacement.capacity()) {
            throw new IllegalStateException("session capacity cannot be lower than occupied seats");
        }
        return new ConsultationSession(sessionId, revision + 1, replacement, status,
                counts.forCapacity(replacement.capacity()));
    }

    public ConsultationSession changeStatus(int expectedRevision, ConsultationSessionStatus replacement, String reason) {
        requireRevision(expectedRevision);
        requireReason(reason);
        Objects.requireNonNull(replacement, "session status is required");
        if (status == ConsultationSessionStatus.CANCELLED && replacement != status) {
            throw new IllegalStateException("cancelled sessions are terminal");
        }
        if (replacement == ConsultationSessionStatus.CANCELLED && counts.occupiedCount() > 0) {
            throw new IllegalStateException("SESSION_HAS_REGISTRATIONS");
        }
        return new ConsultationSession(sessionId, revision + 1, configuration, replacement, counts);
    }

    public ConsultationSession withCounts(Counts replacement) {
        return new ConsultationSession(sessionId, revision, configuration, status, replacement);
    }

    public boolean overlapsWith(ConsultationSession other) {
        if (other == null || !configuration.sessionDate().equals(other.configuration.sessionDate())
                || status == ConsultationSessionStatus.CANCELLED
                || other.status == ConsultationSessionStatus.CANCELLED) {
            return false;
        }
        boolean sameResource = configuration.doctorId().equals(other.configuration.doctorId())
                || configuration.roomId().equals(other.configuration.roomId());
        return sameResource
                && configuration.startTime().isBefore(other.configuration.endTime())
                && other.configuration.startTime().isBefore(configuration.endTime());
    }

    private void requireRevision(int expectedRevision) {
        if (expectedRevision != revision) throw new IllegalStateException("REVISION_CONFLICT");
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("reason must contain 1 to 500 characters");
        }
    }

    private static boolean sameExceptCapacity(Configuration current, Configuration replacement) {
        return current.doctorId().equals(replacement.doctorId())
                && current.departmentId().equals(replacement.departmentId())
                && current.roomId().equals(replacement.roomId())
                && current.sessionDate().equals(replacement.sessionDate())
                && current.startTime().equals(replacement.startTime())
                && current.endTime().equals(replacement.endTime())
                && current.breaks().equals(replacement.breaks());
    }

    public record Configuration(
            UUID doctorId,
            UUID departmentId,
            UUID roomId,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            List<ConsultationBreak> breaks,
            int capacity) {
        public Configuration {
            Objects.requireNonNull(doctorId, "doctor id is required");
            Objects.requireNonNull(departmentId, "department id is required");
            Objects.requireNonNull(roomId, "room id is required");
            Objects.requireNonNull(sessionDate, "session date is required");
            Objects.requireNonNull(startTime, "session start time is required");
            Objects.requireNonNull(endTime, "session end time is required");
            Objects.requireNonNull(breaks, "breaks are required");
            breaks = List.copyOf(breaks);
            if (startTime.isBefore(EARLIEST_START) || !endTime.isAfter(startTime) || endTime.isAfter(LATEST_END)) {
                throw new IllegalArgumentException("session time must satisfy 07:00 <= start < end <= 17:00");
            }
            if (capacity < 1 || capacity > 200) throw new IllegalArgumentException("capacity must be from 1 to 200");
            if (breaks.size() > 20) throw new IllegalArgumentException("at most 20 breaks are allowed");
            for (int index = 0; index < breaks.size(); index++) {
                ConsultationBreak current = Objects.requireNonNull(breaks.get(index), "break is required");
                if (current.startTime().isBefore(startTime) || current.endTime().isAfter(endTime)
                        || current.startTime().equals(startTime) && current.endTime().equals(endTime)) {
                    throw new IllegalArgumentException("break must be inside the session and not cover it");
                }
                for (int otherIndex = index + 1; otherIndex < breaks.size(); otherIndex++) {
                    if (current.overlaps(Objects.requireNonNull(breaks.get(otherIndex), "break is required"))) {
                        throw new IllegalArgumentException("breaks must not overlap");
                    }
                }
            }
        }
    }

    public record Counts(int heldCount, int paymentLockedCount, int confirmedCount, int consumedCount, int availableCount) {
        public Counts {
            if (heldCount < 0 || paymentLockedCount < 0 || confirmedCount < 0 || consumedCount < 0
                    || availableCount < 0) {
                throw new IllegalArgumentException("session counters cannot be negative");
            }
        }

        public static Counts empty(int capacity) {
            return new Counts(0, 0, 0, 0, capacity);
        }

        public int occupiedCount() {
            return heldCount + paymentLockedCount + confirmedCount + consumedCount;
        }

        private Counts forCapacity(int capacity) {
            int available = capacity - occupiedCount();
            if (available < 0) throw new IllegalStateException("session capacity cannot be lower than occupied seats");
            return new Counts(heldCount, paymentLockedCount, confirmedCount, consumedCount, available);
        }
    }
}
