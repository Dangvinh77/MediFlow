package com.mediflow.surgery.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.surgery.application.dto.response.SurgeryCaseDetails;
import com.mediflow.surgery.application.exception.*;
import com.mediflow.surgery.application.mapper.SurgeryReadMapper;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryQueryApplicationServiceTest {
    private final SurgeryCaseRepositoryPort cases = mock(SurgeryCaseRepositoryPort.class);
    private final SurgeryCaseQueryPort board = mock(SurgeryCaseQueryPort.class);
    private final SurgeryChecklistRepositoryPort checklists = mock(SurgeryChecklistRepositoryPort.class);
    private final SurgeryConsentRepositoryPort consents = mock(SurgeryConsentRepositoryPort.class);
    private final SurgeryScheduleRepositoryPort schedules = mock(SurgeryScheduleRepositoryPort.class);
    private final SurgeryResultRepositoryPort results = mock(SurgeryResultRepositoryPort.class);
    private final OrganizationLookupPort organization = mock(OrganizationLookupPort.class);
    private final Instant now = Instant.parse("2026-10-05T08:00:00Z");
    private final UUID account = UUID.randomUUID(), staff = UUID.randomUUID(), department = UUID.randomUUID();
    private final SurgeryQueryApplicationService service = new SurgeryQueryApplicationService(cases, board,
            checklists, consents, schedules, results, organization, () -> now, Mappers.getMapper(SurgeryReadMapper.class));
    private SurgeryCase surgeryCase;

    @BeforeEach void setup() {
        surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), department, staff, "TEST-PROC", "Never expose this narrative",
                SurgeryPriority.ROUTINE, now.minusSeconds(120), SurgeryAuditActor.human(account, staff), "query-test");
        when(cases.findById(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(surgeryCase));
        when(consents.findByCaseId(any())).thenReturn(List.of());
        when(organization.findStaff(eq(staff), any())).thenReturn(authority(
                OrganizationLookupPort.ReferenceState.ACTIVE, department, now));
    }

    @ParameterizedTest @EnumSource(QuerySurgeryCasesUseCase.ReadRole.class)
    void detail_mapsAllAllowedReadersWithoutMutatingCase(QuerySurgeryCasesUseCase.ReadRole role) {
        var view = service.detail(surgeryCase.getSurgeryCaseId(), viewer(role), "query-test");
        assertThat(view.caseDetails().surgeryCaseId()).isEqualTo(surgeryCase.getSurgeryCaseId());
        assertThat(view.caseDetails().episodeId()).isEqualTo(surgeryCase.getCareEpisode().episodeId());
        assertThat(view.readiness().blockingReasons()).containsExactly("READINESS_NOT_EVALUATED");
        assertThat(view.actualResult()).isNull();
        assertThat(view.statusHistory()).hasSize(1);
        assertThat(view.toString()).doesNotContain("Never expose this narrative");
        verify(cases, never()).save(any(), anyLong());
        verify(cases, never()).lockById(any());
    }

    @Test void clinicianCannotReadForeignCaseAndDoesNotLoadItsChildren() {
        when(organization.findStaff(eq(staff), any())).thenReturn(authority(
                OrganizationLookupPort.ReferenceState.ACTIVE, UUID.randomUUID(), now));
        assertThatThrownBy(() -> service.detail(surgeryCase.getSurgeryCaseId(),
                viewer(QuerySurgeryCasesUseCase.ReadRole.DOCTOR), "query-test"))
                .isInstanceOf(SurgeryCaseNotFoundException.class);
        verifyNoInteractions(checklists, consents, schedules, results);
    }

    @Test void missingStaffIdentityDoesNotFallBackToAccountOrDepartmentClaims() {
        assertThatThrownBy(() -> service.list(filter(null), new QuerySurgeryCasesUseCase.Viewer(
                account, null, QuerySurgeryCasesUseCase.ReadRole.NURSE), "query-test"))
                .isInstanceOf(SurgeryReadAccessDeniedException.class);
        verifyNoInteractions(board, organization);
    }

    @Test void listForClinicianUsesAuthoritativeDepartmentNotCallerFilter() {
        when(board.search(any())).thenReturn(PageResult.empty(PageQuery.of(0,20)));
        service.list(filter(null), viewer(QuerySurgeryCasesUseCase.ReadRole.DOCTOR), "query-test");
        var query = org.mockito.ArgumentCaptor.forClass(QuerySurgeryCasesUseCase.Filter.class);
        verify(board).search(query.capture());
        assertThat(query.getValue().departmentId()).isEqualTo(department);
    }

    @Test void foreignDepartmentFilterIsEmptyWithoutQueryingForeignData() {
        assertThat(service.list(filter(UUID.randomUUID()), viewer(QuerySurgeryCasesUseCase.ReadRole.NURSE),
                "query-test").content()).isEmpty();
        verifyNoInteractions(board);
    }

    @Test void administratorNeedsNoStaffLookup() {
        when(board.search(any())).thenReturn(PageResult.empty(PageQuery.of(0,20)));
        service.list(filter(null), new QuerySurgeryCasesUseCase.Viewer(account, null,
                QuerySurgeryCasesUseCase.ReadRole.ADMIN), "query-test");
        verifyNoInteractions(organization);
    }

    @Test void inactiveStaffIsDeniedNotAnUnavailableDependency() {
        when(organization.findStaff(eq(staff), any())).thenReturn(authority(
                OrganizationLookupPort.ReferenceState.INACTIVE, department, now));
        assertThatThrownBy(() -> service.list(filter(null), viewer(QuerySurgeryCasesUseCase.ReadRole.DOCTOR),
                "query-test")).isInstanceOf(SurgeryReadAccessDeniedException.class);
    }

    @Test void staleOrFutureAuthorityFailsClosed() {
        for (Instant timestamp : List.of(now.minusSeconds(31), now.plusSeconds(6))) {
            when(organization.findStaff(eq(staff), any())).thenReturn(authority(
                    OrganizationLookupPort.ReferenceState.ACTIVE, department, timestamp));
            assertThatThrownBy(() -> service.list(filter(null), viewer(QuerySurgeryCasesUseCase.ReadRole.NURSE),
                    "query-test")).isInstanceOf(UpstreamUnavailableException.class);
        }
        verifyNoInteractions(board);
    }

    @Test void expiredReadinessIsNotPresentedAsCurrentAuthority() {
        surgeryCase.beginPreop(SurgeryAuditActor.human(account, staff), "query-test", now.minusSeconds(100));
        var snapshot = ReadinessSnapshot.evaluate(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(),
                true,true,true,true,true,true,true,now.minusSeconds(90),
                Arrays.stream(SurgeryDependencyType.values()).map(type ->
                        new SurgeryDependencyRevision(type, UUID.randomUUID(),1)).toList(), now);
        surgeryCase.markReady(snapshot, SurgeryAuditActor.human(account, staff), "query-test");
        var view = service.detail(surgeryCase.getSurgeryCaseId(), viewer(QuerySurgeryCasesUseCase.ReadRole.ADMIN), "query-test");
        assertThat(view.readiness().snapshotValidNow()).isFalse();
        assertThat(view.readiness().blockingReasons()).containsExactly("READINESS_EXPIRED");
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.READY);
    }

    @Test void missingCaseReturnsNotFound() {
        assertThatThrownBy(() -> service.detail(UUID.randomUUID(), viewer(QuerySurgeryCasesUseCase.ReadRole.ADMIN),
                "query-test")).isInstanceOf(SurgeryCaseNotFoundException.class);
    }

    @Test void detail_actualPerformedItemsRemainSeparateFromPlannedProcedureAndNeverContainMoney() {
        UUID resultId = UUID.randomUUID(), lineId = UUID.randomUUID();
        when(results.findByCaseId(surgeryCase.getSurgeryCaseId())).thenReturn(Optional.of(new SurgeryResult(
                resultId, surgeryCase.getSurgeryCaseId(), "ACTUAL-PROC", "METHOD", "OUTCOME", null,
                now.minusSeconds(60), now.minusSeconds(10),
                List.of(new SurgeryPerformedItem(lineId, "PERFORMED-ITEM", "CATALOGUE-CODE",
                        new java.math.BigDecimal("2.50"))),
                now, SurgeryAuditActor.human(account, staff), "query-test")));
        var view = service.detail(surgeryCase.getSurgeryCaseId(), viewer(QuerySurgeryCasesUseCase.ReadRole.ADMIN),
                "query-test");
        assertThat(view.caseDetails().procedureCode()).isEqualTo("TEST-PROC");
        assertThat(view.actualResult().procedureCode()).isEqualTo("ACTUAL-PROC");
        assertThat(view.actualResult().performedItems()).containsExactly(new SurgeryCaseDetails.PerformedItem(
                lineId, "PERFORMED-ITEM", "CATALOGUE-CODE", new java.math.BigDecimal("2.5")));
        verify(cases, never()).save(any(), anyLong());
    }

    @Test void pageAndIntervalAreBoundedAndImmutable() {
        var value = new QuerySurgeryCasesUseCase.Filter(null,null,null,null,null,null,new PageQuery(-1,10000));
        assertThat(value.page()).isEqualTo(PageQuery.of(0,100));
        assertThatThrownBy(() -> new QuerySurgeryCasesUseCase.Filter(null,null,now,now,null,null,PageQuery.of(0,20)))
                .hasMessage("Query end must be after start");
    }

    private QuerySurgeryCasesUseCase.Viewer viewer(QuerySurgeryCasesUseCase.ReadRole role) {
        return new QuerySurgeryCasesUseCase.Viewer(account,staff,role);
    }
    private QuerySurgeryCasesUseCase.Filter filter(UUID dept) {
        return new QuerySurgeryCasesUseCase.Filter(dept,null,null,null,null,null,PageQuery.of(0,20));
    }
    private OrganizationLookupPort.OrganizationLookupSnapshot authority(
            OrganizationLookupPort.ReferenceState state, UUID dept, Instant at) {
        return new OrganizationLookupPort.OrganizationLookupSnapshot(OrganizationLookupPort.ReferenceKind.STAFF,
                staff,state,at,null,"DOCTOR",dept);
    }
}
