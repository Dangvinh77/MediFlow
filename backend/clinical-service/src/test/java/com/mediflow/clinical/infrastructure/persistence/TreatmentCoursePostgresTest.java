package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

import com.mediflow.clinical.domain.model.TreatmentCourse;
import com.mediflow.clinical.domain.model.TreatmentCourseDecision;
import com.mediflow.clinical.domain.model.TreatmentCourseStatus;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcTreatmentCourseAdapter;

@Testcontainers(disabledWithoutDocker = true)
class TreatmentCoursePostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcTreatmentCourseAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcTreatmentCourseAdapter(jdbc);
    }

    private UUID record(UUID patient, String date) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, patient, UUID.randomUUID(), UUID.randomUUID(), Date.valueOf(date));
        return id;
    }

    @Test void storesCourseVisitsAndLastVisitDate() {
        UUID patient = UUID.randomUUID();
        UUID first = record(patient, "2026-01-05");
        UUID second = record(patient, "2026-03-01");
        TreatmentCourse course = TreatmentCourse.open(patient, UUID.randomUUID(), "Tăng huyết áp", "Theo dõi",
                UUID.randomUUID(), first, AT);
        adapter.insert(course);
        assertThat(adapter.addVisit(course.getCourseId(), second, UUID.randomUUID(), AT)).isTrue();
        assertThat(adapter.addVisit(course.getCourseId(), second, UUID.randomUUID(), AT)).isFalse();

        var view = adapter.view(course.getCourseId()).orElseThrow();
        assertThat(view.course().getRecordIds()).containsExactly(first, second);
        assertThat(view.lastVisitDate()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(adapter.findByPatient(patient)).hasSize(1);
        assertThat(adapter.courseIdsByRecord(List.of(second))).containsEntry(second, List.of(course.getCourseId()));
    }

    @Test void closurePersistsAndLocks() {
        UUID patient = UUID.randomUUID();
        TreatmentCourse course = TreatmentCourse.open(patient, UUID.randomUUID(), "P", "R", UUID.randomUUID(),
                record(patient, "2026-02-02"), AT);
        adapter.insert(course);
        TreatmentCourse locked = adapter.findByIdForUpdate(course.getCourseId()).orElseThrow();
        locked.close(TreatmentCourseDecision.RESOLVED, "Ổn định", UUID.randomUUID(), AT);
        adapter.close(locked);
        var stored = adapter.view(course.getCourseId()).orElseThrow().course();
        assertThat(stored.getStatus()).isEqualTo(TreatmentCourseStatus.CLOSED);
        assertThat(stored.getClosureNote()).isEqualTo("Ổn định");
    }

    @Test void visitUniquePerCourse() {
        UUID patient = UUID.randomUUID();
        UUID first = record(patient, "2026-02-02");
        TreatmentCourse course = TreatmentCourse.open(patient, UUID.randomUUID(), "P", "R", UUID.randomUUID(), first, AT);
        adapter.insert(course);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO treatment_course_visit(course_id, record_id, added_by_staff_id, added_at) VALUES (?,?,?,now())
                """, course.getCourseId(), first, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void closureColumnsAllOrNothing() {
        UUID patient = UUID.randomUUID();
        TreatmentCourse course = TreatmentCourse.open(patient, UUID.randomUUID(), "P", "R", UUID.randomUUID(),
                record(patient, "2026-02-02"), AT);
        adapter.insert(course);
        assertThatThrownBy(() -> jdbc.update("UPDATE treatment_course SET status='CLOSED' WHERE course_id=?", course.getCourseId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
