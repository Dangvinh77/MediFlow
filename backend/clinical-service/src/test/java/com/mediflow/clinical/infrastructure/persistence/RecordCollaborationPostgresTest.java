package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.domain.model.RecordHandover;
import com.mediflow.clinical.domain.model.RecordHandoverStatus;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcPatientTimelineAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcRecordHandoverAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.PrescriptionContextReadAdapter;

@Testcontainers(disabledWithoutDocker = true)
class RecordCollaborationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcRecordHandoverAdapter handovers;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        handovers = new JdbcRecordHandoverAdapter(jdbc);
    }

    private UUID record(UUID patient, UUID doctor) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, patient, doctor, UUID.randomUUID(), Date.valueOf("2026-11-10"));
        return id;
    }

    @Test void handoverRoundTripAndOnePending() {
        UUID doctor = UUID.randomUUID();
        UUID recordId = record(UUID.randomUUID(), doctor);
        RecordHandover first = RecordHandover.request(recordId, doctor, UUID.randomUUID(), "Hết ca", "Chờ", AT);
        handovers.insert(first);
        assertThat(handovers.findPending(recordId)).map(RecordHandover::getHandoverId).contains(first.getHandoverId());
        assertThatThrownBy(() -> handovers.insert(RecordHandover.request(recordId, doctor, UUID.randomUUID(), "Hết ca", "Chờ", AT)))
                .isInstanceOf(DataIntegrityViolationException.class);

        RecordHandover locked = handovers.findByIdForUpdate(first.getHandoverId()).orElseThrow();
        locked.accept(first.getToDoctorId(), AT);
        handovers.accept(locked);
        assertThat(handovers.findPending(recordId)).isEmpty();
        assertThat(handovers.findByRecord(recordId)).singleElement()
                .satisfies(h -> assertThat(h.getStatus()).isEqualTo(RecordHandoverStatus.ACCEPTED));
    }

    @Test void prescriptionContextReturnsResponsibleDoctor() {
        UUID opener = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID recordId = record(UUID.randomUUID(), opener);
        var context = new PrescriptionContextReadAdapter(jdbc);
        assertThat(context.find(recordId).doctorId()).isEqualTo(opener);
        jdbc.update("UPDATE medical_record SET responsible_doctor_id=? WHERE record_id=?", receiver, recordId);
        assertThat(context.find(recordId).doctorId()).isEqualTo(receiver);
    }

    @Test void timelineShowsResponsibleDoctor() {
        UUID patient = UUID.randomUUID();
        UUID opener = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID recordId = record(patient, opener);
        jdbc.update("UPDATE medical_record SET responsible_doctor_id=? WHERE record_id=?", receiver, recordId);
        var row = new JdbcPatientTimelineAdapter(jdbc).page(patient, null, null, null, 0, 20).rows().get(0);
        assertThat(row.doctorId()).isEqualTo(opener);
        assertThat(row.responsibleDoctorId()).isEqualTo(receiver);
    }

    @Test void consultationNoteTableExists() {
        UUID recordId = record(UUID.randomUUID(), UUID.randomUUID());
        assertThat(jdbc.update("INSERT INTO consultation_note(note_id,record_id,author_staff_id,opinion,created_at) VALUES (?,?,?,?,now())",
                UUID.randomUUID(), recordId, UUID.randomUUID(), "Đồng ý")).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO consultation_note(note_id,record_id,author_staff_id,opinion,created_at) VALUES (?,?,?,?,now())",
                UUID.randomUUID(), recordId, UUID.randomUUID(), "")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
