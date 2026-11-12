package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.authority.CurrentDoctorAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.service.ClinicQueueApplicationService;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.infrastructure.persistence.adapter.ClinicQueuePersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ConsultationSessionPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import com.mediflow.common.exception.DuplicateResourceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ClinicQueuePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static final UUID DEPARTMENT = UUID.randomUUID();
    static JdbcTemplate jdbc;
    static ClinicIntakeTestFixtures fixtures;
    static ClinicQueuePersistenceAdapter adapter;
    static ClinicQueueApplicationService service;

    @BeforeAll static void build() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        fixtures = new ClinicIntakeTestFixtures(jdbc);
        adapter = new ClinicQueuePersistenceAdapter(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var mapper = new ObjectMapper().findAndRegisterModules();

        UUID staff = UUID.randomUUID();
        var nurse = new ActorIdentity(UUID.randomUUID(), staff, null, "NURSE");
        var actors = mock(CurrentClinicalActorPort.class);
        when(actors.current()).thenReturn(nurse);
        var authority = mock(SchedulingAuthorityPort.class);
        when(authority.currentDoctorAuthority(any(), any())).thenReturn(new CurrentDoctorAuthoritySnapshot(nurse.accountId(), true, true,
                "NURSE", staff, true, DEPARTMENT, true, ClinicIntakeTestFixtures.NOW));
        when(authority.authority(any(), any(), any())).thenReturn(new SchedulingAuthoritySnapshot(nurse.accountId(), true, true,
                "NURSE", staff, true, DEPARTMENT, true, false, 3L, ClinicIntakeTestFixtures.NOW));
        var correlations = mock(CorrelationIdProvider.class);
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        var idempotency = mock(IdempotentCommandPort.class);
        when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenAnswer(call -> {
                    Supplier<?> work = call.getArgument(8);
                    return new IdempotentCommandPort.Result<>(tx.execute(status -> work.get()), call.getArgument(6), null, false);
                });
        service = new ClinicQueueApplicationService(new ConsultationSessionPersistenceAdapter(jdbc, mapper),
                new OutpatientRegistrationPersistenceAdapter(jdbc, mapper), adapter, authority, actors, correlations, idempotency,
                Clock.fixed(ClinicIntakeTestFixtures.NOW, ZoneOffset.UTC));
    }

    @BeforeEach void clear() {
        jdbc.execute("TRUNCATE consultation_session, appointment, clinic_queue_ticket, clinic_room_queue_counter CASCADE");
    }

    private UUID arrived(ConsultationSession session) {
        var registration = fixtures.confirmedWithNumber(session, (int) (Math.random() * 1_000_000) + 1);
        jdbc.update("UPDATE outpatient_registration SET checked_in_at=? WHERE registration_id=?",
                java.sql.Timestamp.from(ClinicIntakeTestFixtures.NOW), registration.registrationId());
        return registration.registrationId();
    }

    private ConsultationSession session(UUID doctor, UUID room) {
        return fixtures.session(doctor, DEPARTMENT, room, DAY, 50);
    }

    @Test void twoRoomsOfOneDepartmentNumberIndependently() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(adapter.allocateRoomQueueNumber(a, DAY)).isEqualTo(1);
        assertThat(adapter.allocateRoomQueueNumber(a, DAY)).isEqualTo(2);
        assertThat(adapter.allocateRoomQueueNumber(b, DAY)).isEqualTo(1);
    }

    @Test void twoDoctorsSharingRoomInDifferentSessionsShareSequence() {
        UUID room = UUID.randomUUID();
        assertThat(service.issue(arrived(session(UUID.randomUUID(), room)), "a").data().roomQueueNumber()).isEqualTo(1);
        var afternoon = fixtures.session(UUID.randomUUID(), DEPARTMENT, room, DAY, 50, java.time.LocalTime.of(13, 0), java.time.LocalTime.of(16, 0));
        assertThat(service.issue(arrived(afternoon), "b").data().roomQueueNumber()).isEqualTo(2);
    }

    @Test void sequenceRestartsNextDay() {
        UUID room = UUID.randomUUID();
        adapter.allocateRoomQueueNumber(room, DAY);
        assertThat(adapter.allocateRoomQueueNumber(room, DAY.plusDays(1))).isEqualTo(1);
    }

    @Test void concurrentIssueWithDifferentKeysBurnsNoNumber() throws Exception {
        UUID room = UUID.randomUUID();
        UUID registration = arrived(session(UUID.randomUUID(), room));
        var tasks = new ArrayList<Callable<String>>();
        for (String key : List.of("desk-1", "desk-2")) {
            tasks.add(() -> {
                try {
                    return String.valueOf(service.issue(registration, key).data().roomQueueNumber());
                } catch (DuplicateResourceException rejected) {
                    return rejected.getCode();
                }
            });
        }
        var results = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var future : pool.invokeAll(tasks)) results.add(future.get());
        }
        assertThat(results).containsExactlyInAnyOrder("1", "ROOM_TICKET_EXISTS");
        assertThat(adapter.allocateRoomQueueNumber(room, DAY)).isEqualTo(2);
    }

    @Test void concurrentIssueForDifferentPatientsNeverRepeats() throws Exception {
        UUID room = UUID.randomUUID();
        var session = session(UUID.randomUUID(), room);
        var tasks = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 12; i++) {
            UUID registration = arrived(session);
            String key = "desk-" + i;
            tasks.add(() -> service.issue(registration, key).data().roomQueueNumber());
        }
        var numbers = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (var future : pool.invokeAll(tasks)) numbers.add(future.get());
        }
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 12).boxed().toList());
    }

    @Test void boardRowsJoinActiveTicketAndLeaveTerminalRegistrationsOut() {
        var session = session(UUID.randomUUID(), UUID.randomUUID());
        UUID ticketed = arrived(session);
        service.issue(ticketed, "a");
        UUID noTicket = arrived(session);
        UUID held = fixtures.registration(session).registrationId();
        UUID gone = fixtures.registration(session).registrationId();
        jdbc.update("UPDATE outpatient_registration SET status='EXPIRED',reservation_status='EXPIRED',revision=revision+1 WHERE registration_id=?", gone);

        var rows = adapter.boardRows(List.of(session.sessionId()));
        assertThat(rows).extracting(ClinicQueueRepositoryPort.BoardRow::registrationId).containsExactlyInAnyOrder(ticketed, noTicket, held);
        var withTicket = rows.stream().filter(r -> r.registrationId().equals(ticketed)).findFirst().orElseThrow();
        assertThat(withTicket.ticket().roomQueueNumber()).isEqualTo(1);
        assertThat(withTicket.cleared()).isTrue();
        assertThat(rows.stream().filter(r -> r.registrationId().equals(noTicket)).findFirst().orElseThrow().ticket()).isNull();
        assertThat(rows.stream().filter(r -> r.registrationId().equals(held)).findFirst().orElseThrow().cleared()).isFalse();
    }

    @Test void vitalSignsStayAppendOnlyAndMarkTheBoard() {
        var session = session(UUID.randomUUID(), UUID.randomUUID());
        UUID registration = arrived(session);
        var store = new com.mediflow.clinical.infrastructure.persistence.adapter.VitalSignPersistenceAdapter(jdbc);
        for (int pulse : List.of(70, 90)) {
            store.insert(com.mediflow.clinical.domain.model.VitalSignObservation.record(UUID.randomUUID(), registration, UUID.randomUUID(), DEPARTMENT,
                    ClinicIntakeTestFixtures.NOW.plusSeconds(pulse), UUID.randomUUID(), null, pulse, 120, 80, new java.math.BigDecimal("37.5"), 18, 98,
                    new java.math.BigDecimal("60.25"), null));
        }
        var rows = store.listByRegistration(registration);
        assertThat(rows).extracting(o -> o.pulseBpm()).containsExactly(90, 70);
        assertThat(rows.get(0).temperatureC()).isEqualByComparingTo("37.5");
        assertThat(rows.get(0).weightKg()).isEqualByComparingTo("60.25");
        assertThat(adapter.boardRows(List.of(session.sessionId())).get(0).vitalSignsRecorded()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM vital_sign_observation WHERE registration_id=?", registration))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void adjustmentRowsAreKept() {
        UUID registration = arrived(session(UUID.randomUUID(), UUID.randomUUID()));
        var ticket = service.issue(registration, "a").data();
        adapter.appendAdjustment(ticket.ticketId(), "CALLED", null, UUID.randomUUID(), null, ClinicIntakeTestFixtures.NOW);
        adapter.appendAdjustment(ticket.ticketId(), "NO_SHOW", "Vắng", UUID.randomUUID(), null, ClinicIntakeTestFixtures.NOW);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM queue_adjustment WHERE ticket_id=?", Integer.class, ticket.ticketId())).isEqualTo(2);
    }

    @Test void findOnDateAppliesEachFilter() {
        UUID doctor = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        var match = session(doctor, room);
        session(UUID.randomUUID(), UUID.randomUUID());
        var sessions = new ConsultationSessionPersistenceAdapter(jdbc, new ObjectMapper().findAndRegisterModules());
        assertThat(sessions.findOnDate(DAY, DEPARTMENT, null, null)).hasSize(2);
        assertThat(sessions.findOnDate(DAY, null, doctor, null)).extracting(ConsultationSession::sessionId).containsExactly(match.sessionId());
        assertThat(sessions.findOnDate(DAY, null, null, room)).extracting(ConsultationSession::sessionId).containsExactly(match.sessionId());
        assertThat(sessions.findOnDate(DAY.plusDays(1), DEPARTMENT, null, null)).isEmpty();
    }

    @Test void activeNumbersIgnoreCancelledTickets() {
        UUID room = UUID.randomUUID();
        UUID registration = arrived(session(UUID.randomUUID(), room));
        service.issue(registration, "a");
        assertThat(adapter.activeNumbers(List.of(registration))).containsEntry(registration, 1);
        var active = adapter.findActiveByRegistration(registration).orElseThrow();
        adapter.update(active, active.cancel(UUID.randomUUID(), "Đổi lịch", ClinicIntakeTestFixtures.NOW));
        assertThat(adapter.activeNumbers(List.of(registration))).isEmpty();
        assertThat(adapter.findActiveByRegistration(registration)).isEmpty();
    }
}
