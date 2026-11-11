package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.request.LabReportRequests.LabValueRequest;
import com.mediflow.lab.application.dto.request.LabReportRequests.SaveLabReportDraftRequest;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;

class LabReportApplicationServiceTest {
    static final Instant AT = Instant.parse("2026-11-11T02:00:00Z");
    final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    final LabReportRepositoryPort reports = mock(LabReportRepositoryPort.class);
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final LabOutboxPort outbox = mock(LabOutboxPort.class);
    final AuthenticatedStaffContextPort staff = mock(AuthenticatedStaffContextPort.class);
    final UUID testId = UUID.randomUUID();
    final UUID tech = UUID.randomUUID();
    final LabTest test = mock(LabTest.class);

    LabReportApplicationService service(boolean enabled) {
        when(staff.currentStaff()).thenReturn(Optional.of(new AuthenticatedStaffContext(tech, "LAB_TECH")));
        when(test.getTestId()).thenReturn(testId);
        when(test.getCareContractVersion()).thenReturn(1);
        when(test.getStatus()).thenReturn(LabTestStatus.IN_PROGRESS);
        when(tests.findByIdForUpdate(testId)).thenReturn(Optional.of(test));
        when(catalog.binding(testId)).thenReturn(Optional.of(new LabCatalogPort.TemplateBinding(testId, "GLU", "GLU", 1)));
        when(catalog.template("GLU", 1)).thenReturn(Optional.of(new LabResultTemplate("GLU", 1, "Glucose", List.of())));
        return new LabReportApplicationService(tests, reports, catalog, staff, Clock.fixed(AT, ZoneOffset.UTC), enabled);
    }

    @Test void submissionNeedsAnAcceptedSpecimen() {
        service(true);
        com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort specimens =
                mock(com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort.class);
        when(specimens.hasAcceptedSpecimen(testId)).thenReturn(false);
        LabReportApplicationService gated = new LabReportApplicationService(tests, reports, catalog, staff,
                Clock.fixed(AT, ZoneOffset.UTC), true, specimens);
        LabReport draft = LabReport.open(testId, 1, "GLU", 1, null, AT);
        draft.saveDraft(0, List.of(), null, LocalDate.of(2026, 11, 11), tech, AT);
        when(reports.forUpdate(testId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> gated.submit(testId,
                new com.mediflow.lab.application.dto.request.LabReportRequests.SubmitLabReportRequest(1)))
                .isInstanceOfSatisfying(com.mediflow.lab.domain.exception.LabRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SPECIMEN_REQUIRED"));
        assertThat(draft.getStatus()).isEqualTo(com.mediflow.lab.domain.model.LabReportStatus.DRAFT);
        verify(reports, never()).update(any(), anyInt());
    }

    @Test void disabledWorkflowIsNotFound() {
        assertThatThrownBy(() -> service(false).saveDraft(testId, new SaveLabReportDraftRequest(0, List.of(), null, null)))
                .isInstanceOf(ResourceNotFoundException.class).extracting("code").isEqualTo("LAB_REVIEW_DISABLED");
        verifyNoInteractions(reports);
    }

    @Test void firstDraftOpensRevisionOneWithoutTouchingTheTestOrTheOutbox() {
        var service = service(true);
        when(reports.forUpdate(testId)).thenReturn(Optional.empty());
        var dto = service.saveDraft(testId, new SaveLabReportDraftRequest(0,
                List.of(new LabValueRequest("GLU", null, "5.2", null)), null, LocalDate.of(2026, 11, 11)));
        assertThat(dto.resultVersion()).isEqualTo(1);
        assertThat(dto.draftRevision()).isEqualTo(1);
        assertThat(dto.status()).isEqualTo("DRAFT");
        verify(reports).insert(any(LabReport.class));
        verify(reports).update(any(LabReport.class), anyInt());
        verify(reports).enterWorkflow(testId);
        verify(tests, never()).save(any());
        verify(test, never()).changeStatus(any());
        verifyNoInteractions(outbox);
    }

    @Test void testNotInProgressCannotBeDrafted() {
        var service = service(true);
        when(test.getStatus()).thenReturn(LabTestStatus.READY);
        assertThatThrownBy(() -> service.saveDraft(testId, new SaveLabReportDraftRequest(0, List.of(), null, null)))
                .extracting("code").isEqualTo("LAB_REPORT_TEST_NOT_IN_PROGRESS");
    }

    @Test void testWithoutTemplateCannotUseTheWorkflow() {
        var service = service(true);
        when(catalog.binding(testId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveDraft(testId, new SaveLabReportDraftRequest(0, List.of(), null, null)))
                .extracting("code").isEqualTo("LAB_REPORT_TEMPLATE_REQUIRED");
    }
}
