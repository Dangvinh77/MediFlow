package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.request.LabReportRequests.OpenAmendmentRequest;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabReportStatus;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.domain.model.LabValueInput;

/** An amendment is a new reviewed revision; revision 1 stays as it was released. */
class LabReportAmendmentTest {
    static final Instant AT = Instant.parse("2026-11-11T05:00:00Z");
    final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    final LabReportRepositoryPort reports = mock(LabReportRepositoryPort.class);
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final AuthenticatedStaffContextPort staff = mock(AuthenticatedStaffContextPort.class);
    final UUID testId = UUID.randomUUID();
    final UUID tech = UUID.randomUUID();
    final LabTest test = mock(LabTest.class);

    LabReport released() {
        LabReport report = LabReport.open(testId, 1, "GLU", 1, null, AT.minusSeconds(900));
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "7.1", null)), "Tăng nhẹ",
                LocalDate.of(2026, 11, 10), UUID.randomUUID(), AT.minusSeconds(800));
        report.submit(1, new LabResultTemplate("GLU", 1, "Glucose", List.of(new com.mediflow.lab.domain.model.LabTemplateField("GLU", "Glucose", 1, false, com.mediflow.lab.domain.model.LabValueType.QUANTITATIVE, "mmol/L", List.of(), true, List.of()))), UUID.randomUUID(), AT.minusSeconds(700));
        report.release(UUID.randomUUID(), Map.of(), AT.minusSeconds(600));
        return report;
    }

    LabReportApplicationService service(boolean amendments) {
        when(staff.currentStaff()).thenReturn(Optional.of(new AuthenticatedStaffContext(tech, "LAB_TECH")));
        when(test.getTestId()).thenReturn(testId);
        when(test.getCareContractVersion()).thenReturn(1);
        when(test.getStatus()).thenReturn(LabTestStatus.COMPLETED);
        when(tests.findByIdForUpdate(testId)).thenReturn(Optional.of(test));
        return new LabReportApplicationService(tests, reports, catalog, staff, Clock.fixed(AT, ZoneOffset.UTC), true,
                null, amendments);
    }

    @Test void disabledAmendmentsAreNotFound() {
        assertThatThrownBy(() -> service(false).openAmendment(testId, new OpenAmendmentRequest("Nhập nhầm")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(reports, never()).insert(any());
    }

    @Test void amendmentNeedsAReason() {
        LabReport released = released();
        LabReportApplicationService service = service(true);
        when(reports.forUpdate(testId)).thenReturn(Optional.of(released));
        assertThatThrownBy(() -> service.openAmendment(testId, new OpenAmendmentRequest("  ")))
                .isInstanceOfSatisfying(LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_AMENDMENT_REASON_REQUIRED"));
        verify(reports, never()).insert(any());
    }

    @Test void amendmentOpensTheNextRevisionFromTheReleasedValues() {
        LabReport released = released();
        LabReportApplicationService service = service(true);
        when(reports.forUpdate(testId)).thenReturn(Optional.of(released));

        var dto = service.openAmendment(testId, new OpenAmendmentRequest("Nhập nhầm đơn vị"));

        ArgumentCaptor<LabReport> opened = ArgumentCaptor.forClass(LabReport.class);
        verify(reports).insert(opened.capture());
        LabReport revision = opened.getValue();
        assertThat(revision.getResultVersion()).isEqualTo(2);
        assertThat(revision.getStatus()).isEqualTo(LabReportStatus.DRAFT);
        assertThat(revision.getAmendmentReason()).isEqualTo("Nhập nhầm đơn vị");
        assertThat(revision.getValues()).extracting(LabValueInput::value).containsExactly("7.1");
        assertThat(revision.getConclusion()).isEqualTo("Tăng nhẹ");
        assertThat(dto.resultVersion()).isEqualTo(2);
        assertThat(released.getStatus()).isEqualTo(LabReportStatus.RELEASED);
        verify(reports, never()).update(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test void onlyAReleasedResultCanBeAmended() {
        LabReport draft = LabReport.open(testId, 1, "GLU", 1, null, AT);
        LabReportApplicationService service = service(true);
        when(reports.forUpdate(testId)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.openAmendment(testId, new OpenAmendmentRequest("x")))
                .isInstanceOfSatisfying(DuplicateResourceException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_AMENDMENT_NOT_RELEASED"));
    }
}
