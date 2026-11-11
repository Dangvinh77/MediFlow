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

import com.mediflow.clinical.domain.model.AmendmentAssignment;
import com.mediflow.clinical.domain.model.AmendmentSection;
import com.mediflow.clinical.domain.model.RecordAmendment;
import com.mediflow.clinical.domain.model.RecordAmendmentStatus;
import com.mediflow.clinical.infrastructure.persistence.adapter.JdbcRecordAmendmentAdapter;

@Testcontainers(disabledWithoutDocker = true)
class RecordAmendmentPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcRecordAmendmentAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcRecordAmendmentAdapter(jdbc);
    }

    private UUID record(UUID department) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date) VALUES (?,?,?,?,?)",
                id, UUID.randomUUID(), UUID.randomUUID(), department, Date.valueOf("2026-11-09"));
        return id;
    }

    @Test void amendmentRoundTripAndPendingQueue() {
        UUID department = UUID.randomUUID();
        UUID recordId = record(department);
        RecordAmendment later = RecordAmendment.request(recordId, AmendmentSection.ADVICE, "", "Tái khám sau 7 ngày",
                "Bổ sung lời dặn", UUID.randomUUID(), null, AT.plusSeconds(60));
        RecordAmendment earlier = RecordAmendment.request(recordId, AmendmentSection.SYMPTOMS, "Sốt", "Sốt cao",
                "Ghi thiếu", UUID.randomUUID(), UUID.randomUUID(), AT);
        adapter.insert(later);
        adapter.insert(earlier);
        assertThat(adapter.findPending()).filteredOn(p -> p.amendment().getRecordId().equals(recordId))
                .extracting(p -> p.amendment().getAmendmentId()).containsExactly(earlier.getAmendmentId(), later.getAmendmentId());
        assertThat(adapter.findPending()).filteredOn(p -> p.amendment().getRecordId().equals(recordId))
                .allSatisfy(p -> assertThat(p.departmentId()).isEqualTo(department));

        RecordAmendment locked = adapter.findByIdForUpdate(earlier.getAmendmentId()).orElseThrow();
        locked.approve(UUID.randomUUID(), "Đồng ý", AT);
        adapter.decide(locked);
        RecordAmendment stored = adapter.findByRecord(recordId).get(0);
        assertThat(stored.getStatus()).isEqualTo(RecordAmendmentStatus.APPROVED);
        assertThat(stored.getContentHash()).isEqualTo(earlier.contentHash()).hasSize(64);
        assertThat(stored.getAssignedByStaffId()).isEqualTo(earlier.getAssignedByStaffId());
        assertThat(adapter.findPending()).noneMatch(p -> p.amendment().getAmendmentId().equals(earlier.getAmendmentId()));
    }

    @Test void decisionColumnsAllOrNothing() {
        UUID recordId = record(UUID.randomUUID());
        RecordAmendment amendment = RecordAmendment.request(recordId, AmendmentSection.SYMPTOMS, "Sốt", "Sốt cao",
                "Ghi thiếu", UUID.randomUUID(), null, AT);
        adapter.insert(amendment);
        assertThatThrownBy(() -> jdbc.update("UPDATE record_amendment SET status='APPROVED' WHERE amendment_id=?",
                amendment.getAmendmentId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE record_amendment SET status='REJECTED', decided_by_staff_id=?, "
                + "decision_note='x', decided_at=now(), content_hash=? WHERE amendment_id=?", UUID.randomUUID(),
                "a".repeat(64), amendment.getAmendmentId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void assignmentRoundTrip() {
        UUID recordId = record(UUID.randomUUID());
        UUID assignee = UUID.randomUUID();
        adapter.insertAssignment(AmendmentAssignment.create(recordId, assignee, UUID.randomUUID(), "BS cũ nghỉ", AT));
        assertThat(adapter.findAssignment(recordId, assignee)).isPresent();
        assertThat(adapter.findAssignment(recordId, UUID.randomUUID())).isEmpty();
    }

    @Test void revokedAssignmentNoLongerCounts() {
        UUID recordId = record(UUID.randomUUID());
        UUID assignee = UUID.randomUUID();
        AmendmentAssignment assignment = AmendmentAssignment.create(recordId, assignee, UUID.randomUUID(), "BS cũ nghỉ", AT);
        adapter.insertAssignment(assignment);
        assertThat(adapter.assignmentExists(recordId, assignment.getAssignmentId())).isTrue();
        assertThat(adapter.assignmentExists(UUID.randomUUID(), assignment.getAssignmentId())).isFalse();
        adapter.revokeAssignment(recordId, assignment.getAssignmentId(), UUID.randomUUID(), AT.plusSeconds(60));
        adapter.revokeAssignment(recordId, assignment.getAssignmentId(), UUID.randomUUID(), AT.plusSeconds(120));
        assertThat(adapter.findAssignment(recordId, assignee)).isEmpty();
    }
}
