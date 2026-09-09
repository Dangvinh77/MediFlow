package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.Diagnosis;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.infrastructure.persistence.adapter.AppointmentPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;
import com.mediflow.common.api.PageQuery;

@DataJpaTest
@Import({AppointmentPersistenceAdapter.class, MedicalRecordPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ClinicalPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired AppointmentPersistenceAdapter appointments;
    @Autowired MedicalRecordPersistenceAdapter records;

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

    private static MedicalRecord recordFor(Appointment appointment, String diagnosis) {
        return MedicalRecord.create(appointment.getPatientId(), appointment.getDoctorId(),
                appointment.getDepartmentId(), LocalDate.now(), null, appointment.getAppointmentId(),
                List.of(Diagnosis.create(diagnosis, null, null)));
    }
}
