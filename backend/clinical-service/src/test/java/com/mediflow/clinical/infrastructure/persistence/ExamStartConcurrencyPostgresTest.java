package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.application.service.ClinicalCareFinanceApplicationService;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.infrastructure.persistence.adapter.AppointmentPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;

/** Two devices pressing "start examination" together must leave one record behind. */
@DataJpaTest
@Import({AppointmentPersistenceAdapter.class, MedicalRecordPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExamStartConcurrencyPostgresTest {
    private static final Instant NOW = Instant.parse("2026-11-10T01:00:00Z");

    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AppointmentPersistenceAdapter appointments;
    @Autowired MedicalRecordPersistenceAdapter records;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test void concurrentStartCreatesOneRecord() throws Exception {
        UUID doctor = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID clearance = UUID.randomUUID();
        Appointment appointment = Appointment.createScheduled(UUID.randomUUID(), UUID.randomUUID(), doctor, department,
                LocalDate.of(2026, 11, 10), LocalTime.of(9, 0), "Khám", NOW, NOW);
        appointment.confirmScheduledPayment(clearance, NOW);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> appointments.save(appointment));

        CurrentClinicalActorPort actors = mock(CurrentClinicalActorPort.class);
        when(actors.current()).thenReturn(new ActorIdentity(UUID.randomUUID(), doctor, null, "DOCTOR"));
        StaffLookupPort staff = mock(StaffLookupPort.class);
        when(staff.departmentOf(doctor)).thenReturn(Optional.of(department));
        CorrelationIdProvider correlations = mock(CorrelationIdProvider.class);
        when(correlations.currentOrCreate()).thenReturn(UUID.randomUUID());
        var service = new ClinicalCareFinanceApplicationService(appointments, records, mock(ExamClearanceRepositoryPort.class),
                mock(FinancialExecutionAuthorityPort.class), mock(EmergencyOverrideRepositoryPort.class),
                mock(AdmissionReferralRepositoryPort.class), mock(ExamPricePolicyPort.class), mock(ClinicalOutboxPort.class), actors, staff,
                mock(ClinicalDtoMapper.class), correlations, Clock.fixed(NOW, ZoneOffset.UTC), false,
                mock(ClinicalLabOrderRepositoryPort.class), mock(LabOrderLifecyclePort.class), mock(OutpatientRegistrationApplicationService.class));

        CyclicBarrier together = new CyclicBarrier(2);
        Callable<Void> press = () -> {
            together.await(10, TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> service.startExam(appointment.getAppointmentId(), new StartExamRequest(null)));
            return null;
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (var pressed : pool.invokeAll(List.of(press, press), 30, TimeUnit.SECONDS)) pressed.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM medical_record WHERE appointment_id = ?", Integer.class,
                appointment.getAppointmentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment WHERE appointment_id = ?", String.class,
                appointment.getAppointmentId())).isEqualTo("IN_EXAM");
    }
}
