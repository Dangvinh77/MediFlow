package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.infrastructure.persistence.adapter.AppointmentPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;
import com.mediflow.common.api.PageQuery;

/** PostgreSQL-specific persistence semantics; Testcontainers skips this slice when Docker is unavailable. */
@DataJpaTest
@Import({AppointmentPersistenceAdapter.class, MedicalRecordPersistenceAdapter.class,
        ExternalResultPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AppointmentPersistenceAdapter appointments;
    @Autowired MedicalRecordPersistenceAdapter records;
    @Autowired ExternalResultPersistenceAdapter externalResults;
    @Autowired JdbcTemplate jdbc;

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
    void portalPagesOnlyCompletedRecordsOwnedByPatient() {
        UUID patient = UUID.randomUUID();
        var diagnosis = Diagnosis.create("Influenza", null, "J10");
        MedicalRecord first = MedicalRecord.create(patient, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "Fever", null, List.of(diagnosis));
        first.complete(com.mediflow.clinical.domain.model.RecordDisposition.OTHER, "Rest",
                Instant.parse("2026-10-26T00:00:00Z"));
        records.save(first);
        MedicalRecord second = MedicalRecord.create(patient, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, null, List.of(Diagnosis.create("Follow-up", null, null)));
        second.complete(com.mediflow.clinical.domain.model.RecordDisposition.OTHER, "Normal",
                Instant.parse("2026-10-27T00:00:00Z"));
        records.save(second);
        records.save(MedicalRecord.create(patient, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "Draft", null, List.of(Diagnosis.create("Draft", null, null))));
        MedicalRecord foreign = MedicalRecord.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, null, List.of(Diagnosis.create("Private", null, null)));
        foreign.complete(com.mediflow.clinical.domain.model.RecordDisposition.OTHER, "Private", Instant.now());
        records.save(foreign);
        var page = records.findCompletedByPatient(patient, PageQuery.of(0, 1));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(MedicalRecord::getRecordId).containsExactly(second.getRecordId());
        assertThat(records.findCompletedByPatient(patient, PageQuery.of(1, 1)).content())
                .extracting(MedicalRecord::getRecordId).containsExactly(first.getRecordId());
        assertThat(page.content().get(0).getDiagnoses()).hasSize(1);
    }

    @Test
    void cancelledCareFinanceAppointmentRoundTrip_preservesAuditEvidence() {
        Instant now = Instant.now();
        Appointment appointment = Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalTime.of(9, 0), "Checkup",
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        appointment.checkIn("OUTPATIENT_EXAM", now);
        UUID cancelledBy = UUID.randomUUID();
        appointment.cancelCareFinance("Patient declined before examination", cancelledBy, now);

        Appointment saved = appointments.save(appointment);
        Appointment loaded = appointments.findById(saved.getAppointmentId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(com.mediflow.clinical.domain.model.AppointmentStatus.CANCELLED);
        assertThat(loaded.getCancelledBy()).isEqualTo(cancelledBy);
        assertThat(loaded.getCancellationReason()).isEqualTo("Patient declined before examination");
        assertThat(loaded.getCancelledAt()).isEqualTo(now);
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

    private static MedicalRecord recordFor(Appointment appointment, String diagnosis) {
        return MedicalRecord.create(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(), null, appointment.getAppointmentId(),
                List.of(Diagnosis.create(diagnosis, null, null)));
    }
}
