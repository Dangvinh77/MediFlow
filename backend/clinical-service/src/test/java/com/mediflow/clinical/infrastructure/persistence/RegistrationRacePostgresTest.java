package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Time;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.service.ClinicIntakeSettings;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.infrastructure.persistence.adapter.ConsultationSessionPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import com.mediflow.common.exception.DuplicateResourceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RegistrationRacePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    static JdbcTemplate jdbc;
    static ClinicIntakeTestFixtures fixtures;
    static TransactionTemplate tx;
    static OutpatientRegistrationApplicationService service;
    static CountDownLatch bothStarted;
    static OutpatientRegistrationPersistenceAdapter registrations;
    static com.mediflow.clinical.application.service.RegistrationRescheduleApplicationService reschedules;

    @BeforeAll static void build() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        fixtures = new ClinicIntakeTestFixtures(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var mapper = new ObjectMapper().findAndRegisterModules();
        registrations = new OutpatientRegistrationPersistenceAdapter(jdbc, mapper);

        UUID nurseStaff = UUID.randomUUID();
        var nurse = new ActorIdentity(UUID.randomUUID(), nurseStaff, null, "NURSE");
        var actors = mock(CurrentClinicalActorPort.class);
        when(actors.current()).thenReturn(nurse);
        var authority = mock(SchedulingAuthorityPort.class);
        when(authority.authority(any(), any(), any())).thenAnswer(call -> new SchedulingAuthoritySnapshot(nurse.accountId(), true, true,
                "NURSE", nurseStaff, true, call.getArgument(1), true, false, 1L, ClinicIntakeTestFixtures.NOW));
        when(authority.consultationEligibility(any(), any(), any(), any())).thenAnswer(call -> {
            UUID department = call.getArgument(1);
            return new ConsultationEligibilitySnapshot(call.getArgument(0), true, true, true, department, department, true, true,
                    call.getArgument(2), true, true, department, "CONSULTATION", true, ClinicIntakeTestFixtures.NOW);
        });
        var patients = mock(PatientLookupPort.class);
        when(patients.exists(any())).thenReturn(true);
        var correlations = mock(CorrelationIdProvider.class);
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        var appointments = mock(AppointmentRepositoryPort.class);
        when(appointments.save(any())).thenAnswer(call -> {
            Appointment a = call.getArgument(0);
            jdbc.update("""
                    INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,appointment_date,appointment_time,status)
                    VALUES (?,?,?,?,?,?,'CANCELLED')
                    """, a.getAppointmentId(), a.getPatientId(), a.getDoctorId(), a.getDepartmentId(), Date.valueOf(DAY), Time.valueOf("08:00:00"));
            return a;
        });
        var idempotency = mock(IdempotentCommandPort.class);
        when(idempotency.execute(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyInt(), any(), any()))
                .thenAnswer(call -> {
                    bothStarted.countDown();
                    bothStarted.await();
                    Supplier<?> work = call.getArgument(8);
                    return new IdempotentCommandPort.Result<>(tx.execute(status -> work.get()), call.getArgument(6), null, false);
                });
        service = new OutpatientRegistrationApplicationService(new ConsultationSessionPersistenceAdapter(jdbc, mapper),
                new OutpatientRegistrationPersistenceAdapter(jdbc, mapper), appointments, patients, authority, actors,
                mock(ClinicalOutboxPort.class), correlations, idempotency, Clock.fixed(ClinicIntakeTestFixtures.NOW, ZoneOffset.UTC),
                null, null, new ClinicIntakeSettings(true));
        var appointmentStore = mock(AppointmentRepositoryPort.class);
        when(appointmentStore.save(any())).thenAnswer(call -> call.getArgument(0));
        when(appointmentStore.findByIdForUpdate(any())).thenAnswer(call -> {
            var a = Appointment.createScheduled(call.getArgument(0), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DAY,
                    java.time.LocalTime.of(8, 0), "Khám", ClinicIntakeTestFixtures.NOW, null);
            return java.util.Optional.of(a);
        });
        reschedules = new com.mediflow.clinical.application.service.RegistrationRescheduleApplicationService(
                new ConsultationSessionPersistenceAdapter(jdbc, mapper), new OutpatientRegistrationPersistenceAdapter(jdbc, mapper), appointmentStore,
                mock(com.mediflow.clinical.application.port.out.ClinicQueueRepositoryPort.class), service, idempotency,
                Clock.fixed(ClinicIntakeTestFixtures.NOW, ZoneOffset.UTC));
    }

    @Test void twoReschedulesForLastSeatOnlyOneWins() throws Exception {
        UUID department = UUID.randomUUID();
        var target = fixtures.session(UUID.randomUUID(), department, UUID.randomUUID(), DAY, 1);
        var outcomes = new ArrayList<Callable<String>>();
        int counter = 0;
        for (String key : List.of("move-a", "move-b")) {
            var source = fixtures.session(UUID.randomUUID(), department, UUID.randomUUID(), DAY, 5);
            var moving = fixtures.confirmedWithNumber(source, 100 + counter++);
            jdbc.update("UPDATE consultation_session SET confirmed_count=1, available_count=available_count-1 WHERE session_id=?", source.sessionId());
            outcomes.add(() -> {
                try {
                    reschedules.reschedule(moving.registrationId(), new com.mediflow.clinical.application.dto.request.RegistrationRescheduleRequest(
                            moving.revision() + 1, target.sessionId(), "Đổi bác sĩ", true), key);
                    return "MOVED";
                } catch (DuplicateResourceException rejected) {
                    return rejected.getCode();
                }
            });
        }
        bothStarted = new CountDownLatch(2);
        var results = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var future : pool.invokeAll(outcomes)) results.add(future.get());
        }
        assertThat(results).containsExactlyInAnyOrder("MOVED", "SESSION_CAPACITY_EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT available_count FROM consultation_session WHERE session_id=?", Integer.class, target.sessionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_reschedule_log WHERE new_session_id=?", Integer.class, target.sessionId())).isEqualTo(1);
    }

    @Test void onlyHoldsPastTheirExpiryAreDue() {
        var held = fixtures.registration(fixtures.session(UUID.randomUUID(), DAY));
        var expiry = held.reservation().expiresAt();
        assertThat(registrations.findExpiredHeld(expiry.minusSeconds(1), 50)).doesNotContain(held.registrationId());
        assertThat(registrations.findExpiredHeld(expiry, 50)).contains(held.registrationId());
    }

    @Test void twoCountersRaceForLastSeatOnlyOneWins() throws Exception {
        var session = fixtures.session(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DAY, 1);
        bothStarted = new CountDownLatch(2);
        var outcomes = new ArrayList<Callable<String>>();
        for (String key : List.of("counter-a", "counter-b")) {
            outcomes.add(() -> {
                try {
                    service.create(new RegistrationCreateRequest(UUID.randomUUID(), session.sessionId(), session.revision(),
                            OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.NEW_VISIT, null, "Khám"), key);
                    return "CREATED";
                } catch (DuplicateResourceException rejected) {
                    return rejected.getCode();
                }
            });
        }
        var results = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var future : pool.invokeAll(outcomes)) results.add(future.get());
        }
        assertThat(results).containsExactlyInAnyOrder("CREATED", "SESSION_CAPACITY_EXHAUSTED");
        assertThat(jdbc.queryForObject("SELECT available_count FROM consultation_session WHERE session_id=?", Integer.class, session.sessionId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_registration WHERE session_id=?", Integer.class, session.sessionId())).isEqualTo(1);
    }
}
