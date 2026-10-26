package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ConsultationSessionTest {

    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID DEPARTMENT = UUID.randomUUID();
    private static final UUID ROOM = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 11, 4);

    @Test
    void openSessionAcceptsValidBreaksAndStartsWithOneConfigurationRevision() {
        ConsultationSession session = ConsultationSession.open(
                UUID.randomUUID(), DOCTOR, DEPARTMENT, ROOM, DATE,
                LocalTime.of(8, 0), LocalTime.of(12, 0),
                List.of(new ConsultationBreak(LocalTime.of(10, 0), LocalTime.of(10, 15))), 12);

        assertThat(session.revision()).isEqualTo(1);
        assertThat(session.status()).isEqualTo(ConsultationSessionStatus.OPEN);
        assertThat(session.counts().availableCount()).isEqualTo(12);
    }

    @Test
    void openSessionRejectsOverlappingBreaks() {
        assertThatThrownBy(() -> ConsultationSession.open(
                UUID.randomUUID(), DOCTOR, DEPARTMENT, ROOM, DATE,
                LocalTime.of(8, 0), LocalTime.of(12, 0),
                List.of(
                        new ConsultationBreak(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                        new ConsultationBreak(LocalTime.of(9, 29), LocalTime.of(10, 0))), 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adjacentSessionsDoNotOverlapButSharedDoctorOrRoomWithTimeOverlapDoes() {
        ConsultationSession morning = session(ROOM, LocalTime.of(8, 0), LocalTime.of(9, 0));
        ConsultationSession adjacent = session(ROOM, LocalTime.of(9, 0), LocalTime.of(10, 0));
        ConsultationSession overlappingRoom = session(ROOM, LocalTime.of(8, 59), LocalTime.of(10, 0));
        ConsultationSession overlappingDoctor = new ConsultationSession(
                UUID.randomUUID(), 1,
                new ConsultationSession.Configuration(DOCTOR, DEPARTMENT, UUID.randomUUID(), DATE,
                        LocalTime.of(8, 59), LocalTime.of(10, 0), List.of(), 10),
                ConsultationSessionStatus.STOPPED, ConsultationSession.Counts.empty(10));

        assertThat(morning.overlapsWith(adjacent)).isFalse();
        assertThat(morning.overlapsWith(overlappingRoom)).isTrue();
        assertThat(morning.overlapsWith(overlappingDoctor)).isTrue();
    }

    @Test
    void updateWithRegistrationsMayOnlyChangeCapacityWithoutDroppingOccupiedSeats() {
        ConsultationSession session = session(ROOM, LocalTime.of(8, 0), LocalTime.of(9, 0))
                .withCounts(new ConsultationSession.Counts(1, 1, 1, 0, 7));

        ConsultationSession resized = session.updateConfiguration(1,
                new ConsultationSession.Configuration(DOCTOR, DEPARTMENT, ROOM, DATE,
                        LocalTime.of(8, 0), LocalTime.of(9, 0), List.of(), 12), "staffing update");

        assertThat(resized.revision()).isEqualTo(2);
        assertThat(resized.counts().availableCount()).isEqualTo(9);
        assertThatThrownBy(() -> session.updateConfiguration(1,
                new ConsultationSession.Configuration(DOCTOR, DEPARTMENT, ROOM, DATE,
                        LocalTime.of(8, 30), LocalTime.of(9, 30), List.of(), 10), "time change"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.updateConfiguration(1,
                new ConsultationSession.Configuration(DOCTOR, DEPARTMENT, ROOM, DATE,
                        LocalTime.of(8, 0), LocalTime.of(9, 0), List.of(), 2), "reduce capacity"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staleConfigurationRevisionIsRejected() {
        ConsultationSession session = session(ROOM, LocalTime.of(8, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> session.updateConfiguration(0,
                session.configuration(), "stale update"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingOccupiedSessionIsRejectedAndStoppedSessionsStillOverlap() {
        ConsultationSession occupied = session(ROOM, LocalTime.of(8, 0), LocalTime.of(9, 0))
                .withCounts(new ConsultationSession.Counts(0, 0, 1, 0, 9));
        ConsultationSession stopped = session(ROOM, LocalTime.of(8, 30), LocalTime.of(9, 30))
                .changeStatus(1, ConsultationSessionStatus.STOPPED, "temporary stop");

        assertThatThrownBy(() -> occupied.changeStatus(1, ConsultationSessionStatus.CANCELLED, "cancel"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(occupied.overlapsWith(stopped)).isTrue();
    }

    private static ConsultationSession session(UUID room, LocalTime start, LocalTime end) {
        return ConsultationSession.open(UUID.randomUUID(), DOCTOR, DEPARTMENT, room, DATE,
                start, end, List.of(), 10);
    }
}
