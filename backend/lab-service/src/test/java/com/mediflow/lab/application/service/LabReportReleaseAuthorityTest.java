package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.event.LabResultV2Payload;
import com.mediflow.lab.application.port.in.LabResultReleasePort;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReleaseAuthorityPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.domain.model.LabReferenceRange;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabReportStatus;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTemplateField;
import com.mediflow.lab.domain.model.LabValueInput;
import com.mediflow.lab.domain.model.LabValueType;

/** Release needs a second LAB_TECH whom Organization authorises at the moment of release. */
class LabReportReleaseAuthorityTest {
    static final Instant AT = Instant.parse("2026-11-11T03:00:00Z");
    final UUID testId = UUID.randomUUID();
    final UUID writer = UUID.randomUUID();
    final UUID reviewer = UUID.randomUUID();
    final UUID reviewerAccount = UUID.randomUUID();
    final LabReportRepositoryPort reports = mock(LabReportRepositoryPort.class);
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final AuthenticatedStaffContextPort staff = mock(AuthenticatedStaffContextPort.class);
    final CurrentReaderPort readers = mock(CurrentReaderPort.class);
    final LabReleaseAuthorityPort authority = mock(LabReleaseAuthorityPort.class);
    final LabResultReleasePort labs = mock(LabResultReleasePort.class);
    final com.mediflow.lab.application.port.out.LabTestRepositoryPort tests =
            mock(com.mediflow.lab.application.port.out.LabTestRepositoryPort.class);
    final com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort notices =
            mock(com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort.class);
    final com.mediflow.lab.application.port.out.LabOutboxPort outbox =
            mock(com.mediflow.lab.application.port.out.LabOutboxPort.class);
    final CorrelationIdProvider correlations = () -> UUID.fromString("00000000-0000-4000-8000-0000000000cc");
    final LabResultTemplate template = new LabResultTemplate("GLU", 1, "Glucose", List.of(new LabTemplateField(
            "GLU", "Glucose", 1, true, LabValueType.QUANTITATIVE, "mmol/L", List.of(), true,
            List.of(new LabReferenceRange(null, null, null, new BigDecimal("3.9"), new BigDecimal("6.4"),
                    new BigDecimal("2.5"), new BigDecimal("25"), true)))));

    LabReport submitted(UUID by) {
        LabReport report = LabReport.open(testId, 1, "GLU", 1, null, AT.minusSeconds(600));
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "7.1", null)), "Tăng nhẹ",
                LocalDate.of(2026, 11, 11), by, AT.minusSeconds(500));
        report.submit(1, template, by, AT.minusSeconds(400));
        return report;
    }

    LabReportReleaseService service(UUID actor, LabReport report) {
        when(staff.currentStaff()).thenReturn(Optional.of(new AuthenticatedStaffContext(actor, "LAB_TECH")));
        when(readers.current()).thenReturn(Optional.of(new ReaderIdentity(reviewerAccount, actor, "LAB_TECH", null)));
        when(reports.forUpdate(testId)).thenReturn(Optional.of(report));
        when(catalog.template("GLU", 1)).thenReturn(Optional.of(template));
        com.mediflow.lab.domain.model.LabTest test = org.mockito.Mockito.mock(com.mediflow.lab.domain.model.LabTest.class);
        when(test.getTestId()).thenReturn(testId);
        when(test.getPatientId()).thenReturn(UUID.fromString("00000000-0000-4000-8000-0000000000a1"));
        when(test.getRequestingDepartmentId()).thenReturn(UUID.fromString("00000000-0000-4000-8000-0000000000d1"));
        when(test.getRecordId()).thenReturn(UUID.randomUUID());
        when(test.getCareEpisodeType()).thenReturn(com.mediflow.lab.domain.model.CareEpisodeType.OUTPATIENT_VISIT);
        when(test.getCareEpisodeId()).thenReturn(UUID.randomUUID());
        when(test.getLabType()).thenReturn("Glucose");
        when(tests.findById(testId)).thenReturn(Optional.of(test));
        when(notices.insertIfAbsent(any())).thenReturn(true);
        return new LabReportReleaseService(reports, catalog, staff, readers, authority, labs, correlations,
                TransactionOperations.withoutTransaction(), Clock.fixed(AT, ZoneOffset.UTC), true, tests, notices, outbox);
    }

    LabReport submittedAmendment(UUID by) {
        LabReport report = LabReport.open(testId, 2, "GLU", 1, "Nhập nhầm", AT.minusSeconds(300));
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "5.0", null)), "Bình thường",
                LocalDate.of(2026, 11, 11), by, AT.minusSeconds(200));
        report.submit(1, template, by, AT.minusSeconds(100));
        return report;
    }

    @Test void amendmentWriterCannotReleaseIt() {
        LabReport report = submittedAmendment(writer);
        when(authority.canRelease(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service(writer, report).release(testId))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SELF_RELEASE"));
        verify(labs, never()).releaseAmendment(any());
    }

    @Test void releasedAmendmentIsPublishedAsTheNextRevision() {
        LabReport report = submittedAmendment(writer);
        when(authority.canRelease(eq(reviewerAccount), eq(reviewer), any())).thenReturn(true);

        service(reviewer, report).release(testId);

        ArgumentCaptor<LabResultReleasePort.AmendmentRelease> amendment =
                ArgumentCaptor.forClass(LabResultReleasePort.AmendmentRelease.class);
        verify(labs).releaseAmendment(amendment.capture());
        assertThat(amendment.getValue().resultVersion()).isEqualTo(2);
        assertThat(amendment.getValue().amendmentReason()).isEqualTo("Nhập nhầm");
        assertThat(amendment.getValue().releasedBy()).isEqualTo(reviewer);
        assertThat(amendment.getValue().values()).singleElement()
                .satisfies(v -> assertThat(v.flag()).isEqualTo("NORMAL"));
        verify(labs, never()).releaseReviewed(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void writerCannotReleaseOwnReport() {
        LabReport report = submitted(writer);
        when(authority.canRelease(any(), any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service(writer, report).release(testId))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_SELF_RELEASE"));
        verify(labs, never()).releaseReviewed(any(), any(), any(), any(), any(), any(), any());
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.PENDING_REVIEW);
    }

    @Test void deniedOrRevokedOrUnreachableAuthorityIsForbidden() {
        LabReport report = submitted(writer);
        when(authority.canRelease(eq(reviewerAccount), eq(reviewer), any())).thenReturn(false);
        assertThatThrownBy(() -> service(reviewer, report).release(testId))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_RELEASE_DENIED"));
        verify(labs, never()).releaseReviewed(any(), any(), any(), any(), any(), any(), any());
        verify(reports, never()).update(any(), anyInt());
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.PENDING_REVIEW);
    }

    @Test void disabledWorkflowNeverAsksOrganization() {
        LabReport report = submitted(writer);
        LabReportReleaseService disabled = new LabReportReleaseService(reports, catalog, staff, readers, authority,
                labs, correlations, TransactionOperations.withoutTransaction(), Clock.fixed(AT, ZoneOffset.UTC), false,
                tests, notices, outbox);
        assertThatThrownBy(() -> disabled.release(testId))
                .isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
        verify(authority, never()).canRelease(any(), any(), any());
        assertThat(report.getStatus()).isEqualTo(LabReportStatus.PENDING_REVIEW);
    }

    @Test void authorisedSecondPersonReleasesAndCompletesTheTest() {
        LabReport report = submitted(writer);
        when(authority.canRelease(eq(reviewerAccount), eq(reviewer), any())).thenReturn(true);

        var released = service(reviewer, report).release(testId);

        assertThat(released.status()).isEqualTo("RELEASED");
        assertThat(released.releasedBy()).isEqualTo(reviewer);
        assertThat(report.getFlags()).containsEntry("GLU", LabFlag.HIGH);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LabResultV2Payload.ReleasedValue>> values = ArgumentCaptor.forClass(List.class);
        verify(labs).releaseReviewed(eq(testId), any(), eq("Tăng nhẹ"), eq(LocalDate.of(2026, 11, 11)),
                eq(reviewer), values.capture(), eq(AT));
        assertThat(values.getValue()).singleElement().satisfies(v -> {
            assertThat(v.fieldCode()).isEqualTo("GLU");
            assertThat(v.value()).isEqualTo("7.1");
            assertThat(v.unit()).isEqualTo("mmol/L");
            assertThat(v.flag()).isEqualTo("HIGH");
            assertThat(v.referenceRange()).isEqualTo("3.9 - 6.4");
        });
        verify(reports).update(report, 1);
        verify(notices, never()).insertIfAbsent(any());
        verify(outbox, never()).append(any());
    }

    @Test void criticalValueRaisesExactlyOneNoticeForTheDepartment() {
        LabReport report = LabReport.open(testId, 1, "GLU", 1, null, AT.minusSeconds(600));
        report.saveDraft(0, List.of(new LabValueInput("GLU", null, "30", null)), null, LocalDate.of(2026, 11, 11),
                writer, AT.minusSeconds(500));
        report.submit(1, template, writer, AT.minusSeconds(400));
        when(authority.canRelease(eq(reviewerAccount), eq(reviewer), any())).thenReturn(true);

        service(reviewer, report).release(testId);

        ArgumentCaptor<com.mediflow.lab.domain.model.LabCriticalNotice> notice =
                ArgumentCaptor.forClass(com.mediflow.lab.domain.model.LabCriticalNotice.class);
        verify(notices).insertIfAbsent(notice.capture());
        assertThat(notice.getValue().getFlag()).isEqualTo(LabFlag.CRITICAL_HIGH);
        assertThat(notice.getValue().getDepartmentId())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-0000000000d1"));
        ArgumentCaptor<com.mediflow.lab.application.event.DomainEventEnvelope<?>> event =
                ArgumentCaptor.forClass(com.mediflow.lab.application.event.DomainEventEnvelope.class);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("lab.critical.raised");
        var payload = (com.mediflow.lab.application.event.LabCriticalRaisedPayload) event.getValue().payload();
        assertThat(payload.noticeId()).isEqualTo(notice.getValue().getNoticeId());
        assertThat(payload.departmentId()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-0000000000d1"));
        assertThat(payload.value()).isEqualTo("30");
    }
}
