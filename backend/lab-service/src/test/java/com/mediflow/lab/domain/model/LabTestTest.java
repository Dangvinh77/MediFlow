package com.mediflow.lab.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.domain.exception.LabRuleException;

class LabTestTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final LocalDate REQUESTED_DATE = LocalDate.of(2026, 9, 1);

    @Test
    void create_blankLabType_throwsRequiredRule() {
        assertThatThrownBy(() -> LabTest.create(
                RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "  ", REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_TYPE_REQUIRED");
    }

    @Test
    void recordResults_pending_marksCompletedAndKeepsTextValue() {
        LabTest test = pending();

        test.recordResults(List.of(result("<0.01")), "Âm tính", REQUESTED_DATE);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.getPerformedDate()).isEqualTo(REQUESTED_DATE);
        assertThat(test.getConclusion()).isEqualTo("Âm tính");
        assertThat(test.getResults()).singleElement()
                .extracting(LabResult::getValue)
                .isEqualTo("<0.01");
    }

    @Test
    void create_assignsIdentityAndCreationTimestamps() {
        LabTest test = pending();

        assertThat(test.getTestId()).isNotNull();
        assertThat(test.getCreatedAt()).isNotNull();
        assertThat(test.getUpdatedAt()).isEqualTo(test.getCreatedAt());
        assertThat(result("normal").getResultId()).isNotNull();
    }

    @Test
    void recordResults_inProgress_marksCompleted() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.IN_PROGRESS);

        test.recordResults(List.of(result("3+")), "Dương tính", REQUESTED_DATE.plusDays(1));

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.getResults()).hasSize(1);
    }

    @Test
    void recordResults_updatesAggregateTimestamp() {
        Instant previous = Instant.parse("2026-09-01T00:00:00Z");
        LabTest test = LabTest.restore(UUID.randomUUID(), RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC",
                REQUESTED_DATE, null, LabTestStatus.PENDING, null, false, List.of(), previous, previous);

        test.recordResults(List.of(result("normal")), null, REQUESTED_DATE);

        assertThat(test.getUpdatedAt()).isAfter(previous);
    }

    @Test
    void result_blankIndicatorOrValue_throwsRequiredRule() {
        assertThatThrownBy(() -> LabResult.create(" ", "normal", null, null))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INDICATOR_REQUIRED");
        assertThatThrownBy(() -> LabResult.create("glucose", " ", null, null))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_VALUE_REQUIRED");
    }

    @Test
    void recordResults_afterCompleted_throwsAlreadyFinishedRule() {
        LabTest test = pending();
        test.recordResults(List.of(result("normal")), null, REQUESTED_DATE);

        assertThatThrownBy(() -> test.recordResults(List.of(result("changed")), null, REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ALREADY_FINISHED");
    }

    @Test
    void recordResults_afterCancelled_throwsAlreadyFinishedRule() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.CANCELLED);

        assertThatThrownBy(() -> test.recordResults(List.of(result("normal")), null, REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_ALREADY_FINISHED");
    }

    @Test
    void recordResults_emptyList_throwsEmptyResultRule() {
        assertThatThrownBy(() -> pending().recordResults(List.of(), null, REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_RESULT_EMPTY");
    }

    @Test
    void recordResults_nullElement_throwsEmptyResultRule() {
        List<LabResult> results = new ArrayList<>();
        results.add(null);

        assertThatThrownBy(() -> pending().recordResults(results, null, REQUESTED_DATE))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_RESULT_EMPTY");
    }

    @Test
    void recordResults_nullPerformedDate_throwsRequiredDateRule() {
        assertThatThrownBy(() -> pending().recordResults(List.of(result("normal")), null, null))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_PERFORMED_DATE_REQUIRED");
    }

    @Test
    void recordResults_dateBeforeRequest_throwsDateRule() {
        assertThatThrownBy(() -> pending().recordResults(
                List.of(result("normal")), null, REQUESTED_DATE.minusDays(1)))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_DATE_BEFORE_REQUEST");
    }

    @Test
    void changeStatus_inProgressToCompletedWithoutResults_rejectsBypass() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.IN_PROGRESS);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.COMPLETED))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_RESULT_EMPTY");
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_completedToPending_throwsInvalidTransitionRule() {
        LabTest test = pending();
        test.recordResults(List.of(result("normal")), null, REQUESTED_DATE);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.PENDING))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INVALID_TRANSITION");
    }

    @Test
    void changeStatus_pendingToInProgress_isAllowed() {
        LabTest test = pending();

        test.changeStatus(LabTestStatus.IN_PROGRESS);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_pendingToCancelled_isAllowedAndFinal() {
        LabTest test = pending();

        test.changeStatus(LabTestStatus.CANCELLED);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.CANCELLED);
        assertThat(test.isFinal()).isTrue();
    }

    @Test
    void changeStatus_inProgressToCancelled_isAllowedAndFinal() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.IN_PROGRESS);

        test.changeStatus(LabTestStatus.CANCELLED);

        assertThat(test.isFinal()).isTrue();
    }

    @Test
    void changeStatus_inProgressToCompletedWithResultsAndDate_isAllowed() {
        LabTest test = LabTest.restore(UUID.randomUUID(), RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC",
                REQUESTED_DATE, REQUESTED_DATE, LabTestStatus.IN_PROGRESS, null, false,
                List.of(result("normal")), null, null);

        test.changeStatus(LabTestStatus.COMPLETED);

        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.isFinal()).isTrue();
    }

    @Test
    void changeStatus_pendingToCompleted_throwsInvalidTransitionRule() {
        assertThatThrownBy(() -> pending().changeStatus(LabTestStatus.COMPLETED))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INVALID_TRANSITION");
    }

    @Test
    void changeStatus_inProgressToPending_throwsInvalidTransitionRule() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.IN_PROGRESS);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.PENDING))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INVALID_TRANSITION");
    }

    @Test
    void changeStatus_cancelledToAnyStatus_throwsInvalidTransitionRule() {
        LabTest test = pending();
        test.changeStatus(LabTestStatus.CANCELLED);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.PENDING))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_INVALID_TRANSITION");
    }

    @Test
    void changeStatus_inProgressToCompletedWithoutPerformedDate_rejectsBypass() {
        LabTest test = LabTest.restore(UUID.randomUUID(), RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC",
                REQUESTED_DATE, null, LabTestStatus.IN_PROGRESS, null, false,
                List.of(result("normal")), null, null);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.COMPLETED))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_PERFORMED_DATE_REQUIRED");
    }

    @Test
    void changeStatus_inProgressToCompletedWithDateBeforeRequest_rejectsBypass() {
        LabTest test = LabTest.restore(UUID.randomUUID(), RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC",
                REQUESTED_DATE, REQUESTED_DATE.minusDays(1), LabTestStatus.IN_PROGRESS, null, false,
                List.of(result("normal")), null, null);

        assertThatThrownBy(() -> test.changeStatus(LabTestStatus.COMPLETED))
                .isInstanceOf(LabRuleException.class)
                .extracting(LabRuleException.class::cast)
                .extracting(LabRuleException::getCode)
                .isEqualTo("LAB_DATE_BEFORE_REQUEST");
    }

    @Test
    void create_andRestore_defensivelyCopyResultList() {
        List<LabResult> source = new ArrayList<>();
        source.add(result("normal"));
        LabTest test = pendingWithResults(source);

        source.clear();

        assertThat(test.getResults()).hasSize(1);
        assertThatThrownBy(() -> test.getResults().add(result("extra")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void markPaid_setsPaidWithoutChangingLifecycle() {
        LabTest test = pending();
        test.recordResults(List.of(result("normal")), "Normal", REQUESTED_DATE);

        test.markPaid();

        assertThat(test.isPaid()).isTrue();
        assertThat(test.getStatus()).isEqualTo(LabTestStatus.COMPLETED);
        assertThat(test.getConclusion()).isEqualTo("Normal");
        assertThat(test.getResults()).hasSize(1);
    }

    private static LabTest pending() {
        return LabTest.create(RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC", REQUESTED_DATE);
    }

    private static LabTest pendingWithResults(List<LabResult> results) {
        return LabTest.restore(null, RECORD_ID, PATIENT_ID, DEPARTMENT_ID, "CBC",
                REQUESTED_DATE, null, LabTestStatus.PENDING, null, false, results, null, null);
    }

    private static LabResult result(String value) {
        return LabResult.create("glucose", value, "mmol/L", "3.9-5.6");
    }
}
