package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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

import com.mediflow.lab.application.event.LabResultV2Payload;
import com.mediflow.lab.application.port.in.LabResultReleasePort;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort;
import com.mediflow.lab.application.port.out.LabOutboxPort;
import com.mediflow.lab.application.port.out.LabReleaseAuthorityPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.application.port.out.PatientDemographicsPort;
import com.mediflow.lab.application.port.out.PatientDemographicsPort.PatientDemographics;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.model.LabCriticalNotice;
import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.domain.model.LabReferenceRange;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTemplateField;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabValueInput;
import com.mediflow.lab.domain.model.LabValueType;

/** Sex- and age-bounded reference ranges apply once Patient tells Lab who the result belongs to. */
class LabReportReleaseDemographicsTest {
    static final Instant AT = Instant.parse("2026-11-12T03:00:00Z");
    static final LocalDate PERFORMED = LocalDate.of(2026, 11, 12);
    static final int ADULT_DAYS = 6570;
    final UUID testId = UUID.randomUUID();
    final UUID patientId = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    final UUID departmentId = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    final UUID writer = UUID.randomUUID();
    final UUID reviewer = UUID.randomUUID();
    final UUID reviewerAccount = UUID.randomUUID();
    final LabReportRepositoryPort reports = mock(LabReportRepositoryPort.class);
    final LabCatalogPort catalog = mock(LabCatalogPort.class);
    final AuthenticatedStaffContextPort staff = mock(AuthenticatedStaffContextPort.class);
    final CurrentReaderPort readers = mock(CurrentReaderPort.class);
    final LabReleaseAuthorityPort authority = mock(LabReleaseAuthorityPort.class);
    final LabResultReleasePort labs = mock(LabResultReleasePort.class);
    final LabTestRepositoryPort tests = mock(LabTestRepositoryPort.class);
    final LabCriticalNoticeRepositoryPort notices = mock(LabCriticalNoticeRepositoryPort.class);
    final LabOutboxPort outbox = mock(LabOutboxPort.class);
    final PatientDemographicsPort demographics = mock(PatientDemographicsPort.class);
    final CorrelationIdProvider correlations = () -> UUID.fromString("00000000-0000-4000-8000-0000000000cc");
    // Same shape as the demo CBC haemoglobin field: adult ranges only, split by sex.
    final LabResultTemplate template = new LabResultTemplate("CBC", 1, "Công thức máu", List.of(new LabTemplateField(
            "HGB", "Huyết sắc tố (HGB)", 1, true, LabValueType.QUANTITATIVE, "g/L", List.of(), false, List.of(
                    new LabReferenceRange("MALE", ADULT_DAYS, null, new BigDecimal("130"), new BigDecimal("170"),
                            new BigDecimal("70"), new BigDecimal("200"), false),
                    new LabReferenceRange("FEMALE", ADULT_DAYS, null, new BigDecimal("120"), new BigDecimal("150"),
                            new BigDecimal("70"), new BigDecimal("200"), false)))));

    LabReportReleaseService service(String hgb) {
        LabReport report = LabReport.open(testId, 1, "CBC", 1, null, AT.minusSeconds(600));
        report.saveDraft(0, List.of(new LabValueInput("HGB", null, hgb, null)), null, PERFORMED, writer,
                AT.minusSeconds(500));
        report.submit(1, template, writer, AT.minusSeconds(400));
        when(staff.currentStaff()).thenReturn(Optional.of(new AuthenticatedStaffContext(reviewer, "LAB_TECH")));
        when(readers.current()).thenReturn(Optional.of(new ReaderIdentity(reviewerAccount, reviewer, "LAB_TECH", null)));
        when(authority.canRelease(eq(reviewerAccount), eq(reviewer), any())).thenReturn(true);
        when(reports.forUpdate(testId)).thenReturn(Optional.of(report));
        when(catalog.template("CBC", 1)).thenReturn(Optional.of(template));
        LabTest test = mock(LabTest.class);
        when(test.getTestId()).thenReturn(testId);
        when(test.getPatientId()).thenReturn(patientId);
        when(test.getRequestingDepartmentId()).thenReturn(departmentId);
        when(test.getRecordId()).thenReturn(UUID.randomUUID());
        when(test.getCareEpisodeType()).thenReturn(com.mediflow.lab.domain.model.CareEpisodeType.OUTPATIENT_VISIT);
        when(test.getCareEpisodeId()).thenReturn(UUID.randomUUID());
        when(test.getLabType()).thenReturn("Công thức máu");
        when(tests.findById(testId)).thenReturn(Optional.of(test));
        when(tests.findByIdForUpdate(testId)).thenReturn(Optional.of(test));
        when(notices.insertIfAbsent(any())).thenReturn(true);
        return new LabReportReleaseService(reports, catalog, staff, readers, authority, labs, correlations,
                TransactionOperations.withoutTransaction(), Clock.fixed(AT, ZoneOffset.UTC), true, tests, notices,
                outbox, demographics);
    }

    void patient(String sex, LocalDate dateOfBirth) {
        when(demographics.find(eq(patientId), any())).thenReturn(Optional.of(new PatientDemographics(sex, dateOfBirth)));
    }

    LabResultV2Payload.ReleasedValue released() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LabResultV2Payload.ReleasedValue>> values = ArgumentCaptor.forClass(List.class);
        verify(labs).releaseReviewed(eq(testId), any(), any(), any(), eq(reviewer), values.capture(), eq(AT));
        return values.getValue().get(0);
    }

    @Test void adultWomanWithVeryLowHaemoglobinRaisesACriticalNotice() {
        patient("FEMALE", LocalDate.of(1990, 1, 1));

        service("60").release(testId);

        assertThat(released().flag()).isEqualTo("CRITICAL_LOW");
        assertThat(released().referenceRange()).isEqualTo("120 - 150");
        ArgumentCaptor<LabCriticalNotice> notice = ArgumentCaptor.forClass(LabCriticalNotice.class);
        verify(notices).insertIfAbsent(notice.capture());
        assertThat(notice.getValue().getFlag()).isEqualTo(LabFlag.CRITICAL_LOW);
        assertThat(notice.getValue().getDepartmentId()).isEqualTo(departmentId);
    }

    @Test void theSameValueIsLowForAManAndNormalForAWoman() {
        patient("MALE", LocalDate.of(1990, 1, 1));
        service("125").release(testId);
        assertThat(released().flag()).isEqualTo("LOW");
        assertThat(released().referenceRange()).isEqualTo("130 - 170");
    }

    @Test void ageIsCountedOnThePerformedDate() {
        // Turns 18 (6570 days) on the performed date: the adult range applies from that day.
        patient("FEMALE", PERFORMED.minusDays(ADULT_DAYS));
        service("130").release(testId);
        assertThat(released().flag()).isEqualTo("NORMAL");
    }

    @Test void aChildHasNoAdultRangeSoTheFlagStaysUndetermined() {
        patient("FEMALE", PERFORMED.minusDays(ADULT_DAYS - 1));
        service("60").release(testId);
        assertThat(released().flag()).isEqualTo("UNDETERMINED");
        verify(notices, never()).insertIfAbsent(any());
    }

    @Test void unreachablePatientNeverBlocksReleaseAndLeavesTheFlagUndetermined() {
        when(demographics.find(eq(patientId), any())).thenReturn(Optional.empty());
        var result = service("60").release(testId);
        assertThat(result.status()).isEqualTo("RELEASED");
        assertThat(released().flag()).isEqualTo("UNDETERMINED");
        verify(notices, never()).insertIfAbsent(any());
    }

    @Test void patientIsAskedBeforeAnyLabRowIsLocked() {
        patient("FEMALE", LocalDate.of(1990, 1, 1));
        service("130").release(testId);
        var order = inOrder(demographics, tests);
        order.verify(demographics).find(eq(patientId), eq("00000000-0000-4000-8000-0000000000cc"));
        order.verify(tests).findByIdForUpdate(testId);
    }
}
