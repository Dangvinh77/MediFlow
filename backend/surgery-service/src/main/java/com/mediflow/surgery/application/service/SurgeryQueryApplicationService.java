package com.mediflow.surgery.application.service;

import com.mediflow.common.api.PageResult;
import com.mediflow.surgery.application.dto.response.SurgeryCaseBoardItem;
import com.mediflow.surgery.application.dto.response.SurgeryCaseDetails;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.exception.SurgeryReadAccessDeniedException;
import com.mediflow.surgery.application.mapper.SurgeryReadMapper;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.exception.SurgeryCaseNotFoundException;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** ADMIN/MANAGER see all departments; clinicians use current Organization staff authority. */
public class SurgeryQueryApplicationService implements QuerySurgeryCasesUseCase {
    private static final Duration AUTHORITY_MAX_AGE = Duration.ofSeconds(30);
    private static final Duration FUTURE_TOLERANCE = Duration.ofSeconds(5);
    private final SurgeryCaseRepositoryPort cases;
    private final SurgeryCaseQueryPort board;
    private final SurgeryChecklistRepositoryPort checklists;
    private final SurgeryConsentRepositoryPort consents;
    private final SurgeryScheduleRepositoryPort schedules;
    private final SurgeryResultRepositoryPort results;
    private final OrganizationLookupPort organization;
    private final SurgeryClockPort clock;
    private final SurgeryReadMapper mapper;

    public SurgeryQueryApplicationService(SurgeryCaseRepositoryPort cases, SurgeryCaseQueryPort board,
            SurgeryChecklistRepositoryPort checklists, SurgeryConsentRepositoryPort consents,
            SurgeryScheduleRepositoryPort schedules, SurgeryResultRepositoryPort results,
            OrganizationLookupPort organization, SurgeryClockPort clock, SurgeryReadMapper mapper) {
        this.cases = cases; this.board = board; this.checklists = checklists;
        this.consents = consents; this.schedules = schedules; this.results = results;
        this.organization = organization; this.clock = clock; this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SurgeryCaseDetails detail(UUID caseId, Viewer viewer, String correlationId) {
        UUID scope = scope(viewer, correlationId);
        var value = cases.findById(caseId).orElseThrow(() -> new SurgeryCaseNotFoundException(caseId));
        if (scope != null && !scope.equals(value.getDepartmentId())) {
            // Same response as absent: an opaque identifier must not become an existence oracle.
            throw new SurgeryCaseNotFoundException(caseId);
        }
        var checklist = checklists.findSnapshotByCaseId(caseId).map(snapshot ->
                new SurgeryCaseDetails.Checklist(snapshot.checklistSnapshotId(), snapshot.templateRevision(),
                        snapshot.revision(), snapshot.mandatoryChecklistComplete(),
                        snapshot.items().stream().map(mapper::item).toList())).orElse(null);
        var consentViews = consents.findByCaseId(caseId).stream().map(consent ->
                new SurgeryCaseDetails.Consent(consent.consentId(), consent.consentType(),
                        consent.isActive(), consent.signedAt())).toList();
        var readiness = value.getReadinessSnapshot();
        Instant now = clock.now();
        List<String> reasons = readiness == null ? List.of("READINESS_NOT_EVALUATED")
                : new ArrayList<>(readiness.blockingReasons());
        if (readiness != null && readiness.validUntil() != null && !now.isBefore(readiness.validUntil())) {
            reasons.add("READINESS_EXPIRED");
        }
        var view = new SurgeryCaseDetails.Readiness(readiness == null ? null : readiness.readinessSnapshotId(),
                readiness != null && readiness.isValidAt(now), readiness == null ? null : readiness.evaluatedAt(),
                readiness == null ? null : readiness.validUntil(), reasons);
        return new SurgeryCaseDetails(mapper.core(value), checklist, consentViews,
                schedules.findByCaseId(caseId).map(mapper::schedule).orElse(null),
                results.findByCaseId(caseId).map(mapper::result).orElse(null), view,
                value.getStatusHistory().stream().map(mapper::transition).toList());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<SurgeryCaseBoardItem> list(Filter filter, Viewer viewer, String correlationId) {
        UUID scope = scope(viewer, correlationId);
        if (scope != null && filter.departmentId() != null && !scope.equals(filter.departmentId())) {
            return PageResult.empty(filter.page());
        }
        return board.search(scope == null ? filter : filter.inDepartment(scope));
    }

    private UUID scope(Viewer viewer, String correlationId) {
        if (viewer == null) throw new IllegalArgumentException("Verified reader required");
        if (viewer.role() == ReadRole.ADMIN || viewer.role() == ReadRole.MANAGER) return null;
        if (viewer.staffId() == null) throw denied();
        var staff = organization.findStaff(viewer.staffId(), correlationId);
        Instant now = clock.now();
        if (staff == null || staff.kind() != OrganizationLookupPort.ReferenceKind.STAFF
                || !viewer.staffId().equals(staff.referenceId())
                || staff.state() == OrganizationLookupPort.ReferenceState.UNKNOWN
                || staff.observedAt().isBefore(now.minus(AUTHORITY_MAX_AGE))
                || staff.observedAt().isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new UpstreamUnavailableException("Current staff read authority unavailable");
        }
        if (staff.state() != OrganizationLookupPort.ReferenceState.ACTIVE
                || staff.staffDepartmentId() == null) throw denied();
        return staff.staffDepartmentId();
    }

    private static SurgeryReadAccessDeniedException denied() {
        return new SurgeryReadAccessDeniedException();
    }
}
