package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.application.port.out.PatientTimelineReadPort;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcPatientTimelineAdapter;

@Testcontainers(disabledWithoutDocker = true)
class PatientTimelinePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static JdbcPatientTimelineAdapter timeline;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        timeline = new JdbcPatientTimelineAdapter(jdbc);
    }

    private UUID legacy(UUID patient, UUID department, String date, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date,status,
                    disposition,completed_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, patient, UUID.randomUUID(), department, Date.valueOf(date), status,
                "COMPLETED".equals(status) ? "OTHER" : null,
                "COMPLETED".equals(status) ? java.sql.Timestamp.valueOf(date + " 10:00:00") : null);
        return id;
    }

    private UUID registered(UUID patient, UUID department) {
        UUID registration = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment(appointment_id, patient_id, doctor_id, department_id, appointment_date,
                    appointment_time, status, reason, created_at, care_contract_version, exam_clearance_id,
                    exam_price_code, completed_at)
                VALUES (?, ?, ?, ?, DATE '2026-10-20', TIME '10:00', 'COMPLETED', NULL,
                    TIMESTAMPTZ '2026-10-20 10:00:00Z', 2, ?, 'OUTPATIENT_EXAM', TIMESTAMPTZ '2026-10-20 11:00:00Z')
                """, registration, patient, doctor, department, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO consultation_session(session_id, revision, doctor_id, department_id, room_id,
                    session_date, start_time, end_time, breaks, capacity, status, available_count, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, DATE '2026-10-20', TIME '09:00', TIME '11:00', '[]'::jsonb,
                    1, 'OPEN', 1, TIMESTAMPTZ '2026-10-20 08:00:00Z', TIMESTAMPTZ '2026-10-20 08:00:00Z')
                """, session, doctor, department, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO outpatient_registration(registration_id, session_id, patient_id, revision, reception_mode,
                    visit_type, status, reservation_id, reservation_revision, reservation_status,
                    reservation_expires_at, queue_number, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'AT_COUNTER', 'NEW_VISIT', 'COMPLETED', ?, 1, 'CONSUMED',
                    TIMESTAMPTZ '2026-10-20 09:00:00Z', 1, TIMESTAMPTZ '2026-10-20 08:00:00Z',
                    TIMESTAMPTZ '2026-10-20 11:00:00Z')
                """, registration, session, patient, UUID.randomUUID());
        UUID record = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medical_record(record_id, patient_id, doctor_id, department_id, examination_date,
                    appointment_id, created_at, status)
                VALUES (?, ?, ?, ?, DATE '2026-10-20', ?, TIMESTAMPTZ '2026-10-20 10:00:00Z', 'OPEN')
                """, record, patient, doctor, department, registration);
        return record;
    }

    @Test void newestFirstWithVisitTypeAndTotal() {
        UUID patient = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID old = legacy(patient, department, "2026-01-05", "COMPLETED");
        UUID booked = registered(patient, department);
        UUID latest = legacy(patient, department, "2026-11-01", "COMPLETED");
        legacy(UUID.randomUUID(), department, "2026-11-02", "COMPLETED");

        var page = timeline.page(patient, null, null, null, 0, 20);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.rows()).extracting(PatientTimelineReadPort.TimelineRow::recordId).containsExactly(latest, booked, old);
        assertThat(page.rows().get(1).visitType()).isEqualTo("NEW_VISIT");
        assertThat(page.rows().get(0).visitType()).isNull();
    }

    @Test void filtersPagesAndNurseDepartment() {
        UUID patient = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        legacy(patient, mine, "2026-11-01", "COMPLETED");
        UUID open = legacy(patient, mine, "2026-11-03", "OPEN");
        legacy(patient, other, "2026-11-02", "COMPLETED");

        assertThat(timeline.page(patient, mine, null, "OPEN", 0, 20).rows())
                .extracting(PatientTimelineReadPort.TimelineRow::recordId).containsExactly(open);
        assertThat(timeline.page(patient, null, other, null, 0, 20).total()).isEqualTo(1);
        var second = timeline.page(patient, null, null, null, 1, 2);
        assertThat(second.total()).isEqualTo(3);
        assertThat(second.rows()).hasSize(1);
    }

    @Test void attachedDocumentsCarryTime() {
        UUID record = legacy(UUID.randomUUID(), UUID.randomUUID(), "2026-11-01", "COMPLETED");
        var results = new ExternalResultPersistenceAdapter(null, jdbc);
        UUID lab = UUID.randomUUID();
        jdbc.update("INSERT INTO attached_result(record_id,type,reference_id,summary) VALUES (?,?,?,?)",
                record, ExternalResultType.LAB.name(), lab, "Bình thường");
        assertThat(results.findByRecord(record)).singleElement().satisfies(document -> {
            assertThat(document.type()).isEqualTo("LAB");
            assertThat(document.referenceId()).isEqualTo(lab);
            assertThat(document.attachedAt()).isNotNull();
        });
    }
}
