package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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

import com.mediflow.clinical.application.dto.command.LabResultCreatedCommand;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.service.ClinicalIntegrationService;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.infrastructure.persistence.adapter.AppointmentPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ProcessedEventPersistenceAdapter;
import com.mediflow.common.api.PageQuery;

/** PostgreSQL-specific persistence semantics; Testcontainers skips this slice when Docker is unavailable. */
@DataJpaTest
@Import({AppointmentPersistenceAdapter.class, MedicalRecordPersistenceAdapter.class,
        ExternalResultPersistenceAdapter.class, ProcessedEventPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AppointmentPersistenceAdapter appointments;
    @Autowired MedicalRecordPersistenceAdapter records;
    @Autowired ExternalResultPersistenceAdapter externalResults;
    @Autowired ProcessedEventPersistenceAdapter processedEvents;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void recordRoundTrip_preservesDiagnosesAndAppointmentReference() {
        Appointment appointment = appointments.save(Appointment.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now().plusDays(1), LocalTime.of(9, 0), "Checkup"));
        MedicalRecord saved = records.save(MedicalRecord.create(appointment.getPatientId(),
                appointment.getDoctorId(), appointment.getDepartmentId(), LocalDate.now(), "Fever",
                appointment.getAppointmentId(), List.of(Diagnosis.create("Influenza", null, "J10"))));

        MedicalRecord found = records.findById(saved.getRecordId()).orElseThrow();

        assertThat(found.getAppointmentId()).isEqualTo(appointment.getAppointmentId());
        assertThat(found.getDiagnoses()).singleElement()
                .satisfies(diagnosis -> assertThat(diagnosis.getIcdCode()).isEqualTo("J10"));
        assertThat(records.findByIdForUpdate(saved.getRecordId())).isPresent();
    }

    @Test
    void duplicatePendingAppointmentSameDay_isRejectedByDatabase() {
        UUID patient = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        appointments.save(Appointment.create(patient, UUID.randomUUID(), UUID.randomUUID(), date,
                LocalTime.of(9, 0), null));

        assertThatThrownBy(() -> appointments.save(Appointment.create(patient, UUID.randomUUID(),
                UUID.randomUUID(), date, LocalTime.of(10, 0), null)))
                .isInstanceOf(DuplicatePendingAppointmentException.class);
    }

    @Test
    void search_filtersDepartmentAndDateAndKeepsPageMetadata() {
        UUID department = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(2);
        Appointment matching = appointments.save(Appointment.create(UUID.randomUUID(), UUID.randomUUID(),
                department, date, LocalTime.of(8, 0), null));
        appointments.save(Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), date,
                LocalTime.of(9, 0), null));

        var page = appointments.search(department, date, new PageQuery(0, 10));

        assertThat(page.content()).extracting(Appointment::getAppointmentId)
                .containsExactly(matching.getAppointmentId());
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(appointments.search(null, date, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
        assertThat(appointments.search(department, null, new PageQuery(0, 10)).totalElements()).isEqualTo(1);
        assertThat(appointments.search(null, null, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
    }

    @Test
    void secondRecordForAppointment_isRejectedAsClinicalConflict() {
        Appointment appointment = appointments.save(Appointment.create(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now().plusDays(1), LocalTime.of(9, 0), null));
        records.save(recordFor(appointment, "First"));

        assertThatThrownBy(() -> records.save(recordFor(appointment, "Second")))
                .isInstanceOf(InvalidClinicalDataException.class)
                .hasFieldOrPropertyWithValue("code", "RECORD_DUPLICATE_APPOINTMENT");
    }

    @Test
    void repeatedExternalReference_isStoredOnce() {
        MedicalRecord record = records.save(MedicalRecord.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), null,
                null, List.of(Diagnosis.create("Influenza", null, "J10"))));
        UUID labId = UUID.randomUUID();

        externalResults.attach(record.getRecordId(), ExternalResultType.LAB, labId, "Normal");
        externalResults.attach(record.getRecordId(), ExternalResultType.LAB, labId, "Normal");

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM attached_result WHERE record_id = ? AND type = ? AND reference_id = ?",
                Integer.class, record.getRecordId(), "LAB", labId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void processedEvent_tryClaimInsertsOnceAndReturnsAffectedRowDecision() {
        UUID eventId = UUID.randomUUID();

        assertThat(processedEvents.tryClaim(eventId, "lab.result.created")).isTrue();
        assertThat(processedEvents.tryClaim(eventId, "lab.result.created")).isFalse();

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void attachmentFailure_rollsBackClaimInSameTransaction() {
        AttachExternalResultUseCase attachments = mock(AttachExternalResultUseCase.class);
        ClinicalIntegrationService integration = new ClinicalIntegrationService(attachments, processedEvents);
        LabResultCreatedCommand command = new LabResultCreatedCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Normal");
        doThrow(new IllegalStateException("database unavailable"))
                .when(attachments).attachLabResult(
                        command.recordId(), command.labId(), command.conclusion());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> integration.onLabResultCreated(command)))
                .isInstanceOf(IllegalStateException.class);

        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_event WHERE event_id = ?",
                Integer.class, command.eventId());
        assertThat(count).isZero();
    }

    private static MedicalRecord recordFor(Appointment appointment, String diagnosis) {
        return MedicalRecord.create(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(), null, appointment.getAppointmentId(),
                List.of(Diagnosis.create(diagnosis, null, null)));
    }
}
