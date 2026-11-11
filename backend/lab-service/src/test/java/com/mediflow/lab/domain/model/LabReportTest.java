package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;

class LabReportTest {
    static final Instant AT = Instant.parse("2026-11-11T02:00:00Z");
    static final UUID TECH = UUID.randomUUID();
    static final LabResultTemplate GLU = new LabResultTemplate("GLU", 1, "Glucose", List.of(
            new LabTemplateField("GLU", "Glucose", 1, true, LabValueType.QUANTITATIVE, "mmol/L", null, true, List.of())));

    LabReport draft() {
        return LabReport.open(UUID.randomUUID(), 1, "GLU", 1, null, AT);
    }

    @Test void savingADraftBumpsTheRevision() {
        LabReport report = draft();
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "5.2", null)), "Bình thường",
                LocalDate.of(2026, 11, 11), TECH, AT);
        assertThat(report.getDraftRevision()).isEqualTo(1);
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.DRAFT);
        assertThat(report.getEnteredBy()).isEqualTo(TECH);
    }

    @Test void staleDraftRevisionIsAConflict() {
        LabReport report = draft();
        report.saveDraft(0, List.of(), null, null, TECH, AT);
        assertThatThrownBy(() -> report.saveDraft(0, List.of(), null, null, TECH, AT))
                .isInstanceOf(DuplicateResourceException.class).extracting("code").isEqualTo("LAB_REPORT_STALE");
    }

    @Test void submissionRunsTheTemplate() {
        LabReport report = draft();
        report.saveDraft(0, List.of(), null, LocalDate.of(2026, 11, 11), TECH, AT);
        assertThatThrownBy(() -> report.submit(1, GLU, TECH, AT))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("LAB_REPORT_INVALID");
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.DRAFT);
    }

    @Test void submissionNeedsThePerformedDate() {
        LabReport report = draft();
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "5.2", null)), null, null, TECH, AT);
        assertThatThrownBy(() -> report.submit(1, GLU, TECH, AT)).extracting("code").isEqualTo("LAB_REPORT_INVALID");
    }

    LabReport submitted() {
        LabReport report = draft();
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "5.2", null)), null, LocalDate.of(2026, 11, 11),
                TECH, AT);
        report.submit(1, GLU, TECH, AT);
        return report;
    }

    @Test void pendingReviewCannotBeEdited() {
        LabReport report = submitted();
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.PENDING_REVIEW);
        assertThatThrownBy(() -> report.saveDraft(report.getDraftRevision(), List.of(), null, null, TECH, AT))
                .isInstanceOf(DuplicateResourceException.class).extracting("code").isEqualTo("LAB_REPORT_NOT_EDITABLE");
    }

    @Test void returningNeedsAReasonAndReopensTheDraft() {
        LabReport report = submitted();
        UUID reviewer = UUID.randomUUID();
        assertThatThrownBy(() -> report.returnForCorrection(" ", reviewer, AT))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("LAB_REPORT_RETURN_REASON_REQUIRED");
        int before = report.getDraftRevision();
        report.returnForCorrection("Glucose cần đo lại", reviewer, AT);
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.DRAFT);
        assertThat(report.getReturnReason()).isEqualTo("Glucose cần đo lại");
        assertThat(report.getReturnedBy()).isEqualTo(reviewer);
        assertThat(report.getDraftRevision()).isEqualTo(before + 1);
    }

    @Test void amendmentRevisionNeedsAReason() {
        assertThatThrownBy(() -> LabReport.open(UUID.randomUUID(), 2, "GLU", 1, " ", AT))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("LAB_AMENDMENT_REASON_REQUIRED");
        assertThat(LabReport.open(UUID.randomUUID(), 2, "GLU", 1, "Nhập nhầm", AT).getAmendmentReason())
                .isEqualTo("Nhập nhầm");
    }
}
