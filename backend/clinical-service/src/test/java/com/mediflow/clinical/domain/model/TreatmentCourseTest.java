package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

class TreatmentCourseTest {
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    final UUID patient = UUID.randomUUID();
    final UUID department = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID();
    final UUID firstVisit = UUID.randomUUID();

    private TreatmentCourse course() {
        return TreatmentCourse.open(patient, department, "Tăng huyết áp", "Theo dõi huyết áp", doctor, firstVisit, AT);
    }

    @Test void openNeedsProblemReasonAndFirstVisit() {
        assertThatThrownBy(() -> TreatmentCourse.open(patient, department, " ", "Lý do", doctor, firstVisit, AT))
                .isInstanceOf(InvalidClinicalDataException.class).extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        assertThatThrownBy(() -> TreatmentCourse.open(patient, department, "Vấn đề", "", doctor, firstVisit, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        assertThatThrownBy(() -> TreatmentCourse.open(patient, department, "Vấn đề", "Lý do", doctor, null, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        assertThatThrownBy(() -> TreatmentCourse.open(patient, department, "x".repeat(501), "Lý do", doctor, firstVisit, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        TreatmentCourse course = course();
        assertThat(course.getRecordIds()).containsExactly(firstVisit);
        assertThat(course.getStatus()).isEqualTo(TreatmentCourseStatus.OPEN);
        assertThat(course.getResponsibleDoctorId()).isEqualTo(doctor);
    }

    @Test void sameVisitTwiceIsIdempotent() {
        TreatmentCourse course = course();
        UUID second = UUID.randomUUID();
        assertThat(course.addVisit(second, doctor, AT)).isTrue();
        assertThat(course.addVisit(second, doctor, AT)).isFalse();
        assertThat(course.getRecordIds()).containsExactly(firstVisit, second);
    }

    @Test void closeNeedsDecisionAndNote() {
        TreatmentCourse course = course();
        assertThatThrownBy(() -> course.close(null, "Ổn định", doctor, AT)).extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        assertThatThrownBy(() -> course.close(TreatmentCourseDecision.RESOLVED, " ", doctor, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_INVALID");
        course.close(TreatmentCourseDecision.RESOLVED, "Ổn định", doctor, AT);
        assertThat(course.getStatus()).isEqualTo(TreatmentCourseStatus.CLOSED);
        assertThat(course.getClosedByStaffId()).isEqualTo(doctor);
    }

    @Test void closedCourseRefusesVisits() {
        TreatmentCourse course = course();
        course.close(TreatmentCourseDecision.PATIENT_STOPPED, "Không quay lại", doctor, AT);
        assertThatThrownBy(() -> course.addVisit(UUID.randomUUID(), doctor, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_CLOSED");
        assertThatThrownBy(() -> course.close(TreatmentCourseDecision.OTHER, "Lần hai", doctor, AT))
                .extracting("code").isEqualTo("TREATMENT_COURSE_CLOSED");
    }

    @Test void noAutomaticClosure() {
        TreatmentCourse course = course();
        assertThat(TreatmentCourse.reviewDue(LocalDate.parse("2030-01-01"), LocalDate.parse("2026-11-10"))).isTrue();
        assertThat(course.getStatus()).isEqualTo(TreatmentCourseStatus.OPEN);
    }

    @Test void reviewDueAfter180Days() {
        assertThat(TreatmentCourse.reviewDue(LocalDate.parse("2026-11-10").plusDays(181), LocalDate.parse("2026-11-10"))).isTrue();
    }

    @Test void reviewNotDueAt180Days() {
        assertThat(TreatmentCourse.reviewDue(LocalDate.parse("2026-11-10").plusDays(180), LocalDate.parse("2026-11-10"))).isFalse();
        assertThat(TreatmentCourse.reviewDue(LocalDate.parse("2026-11-10"), null)).isFalse();
    }

    @Test void restoreKeepsClosure() {
        TreatmentCourse restored = TreatmentCourse.restore(UUID.randomUUID(), patient, department, "P", "R", doctor,
                TreatmentCourseStatus.CLOSED, java.util.List.of(firstVisit), AT, TreatmentCourseDecision.TRANSFERRED,
                "Chuyển", doctor, AT);
        assertThat(restored.getClosureDecision()).isEqualTo(TreatmentCourseDecision.TRANSFERRED);
    }
}
