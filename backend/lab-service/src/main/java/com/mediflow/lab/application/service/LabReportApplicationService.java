package com.mediflow.lab.application.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.request.LabReportRequests.ReturnLabReportRequest;
import com.mediflow.lab.application.dto.request.LabReportRequests.SaveLabReportDraftRequest;
import com.mediflow.lab.application.dto.request.LabReportRequests.SubmitLabReportRequest;
import com.mediflow.lab.application.dto.response.LabReportDTO;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.LabTestRepositoryPort;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabReportStatus;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.domain.model.LabValueInput;

/**
 * Draft and submission side of the Lab review workflow ({@code mediflow.v2.lab-review}). Drafting never
 * changes the {@link LabTest} nor publishes anything: only a release does.
 */
@Service
public class LabReportApplicationService {
    private final LabTestRepositoryPort tests;
    private final LabReportRepositoryPort reports;
    private final LabCatalogPort catalog;
    private final AuthenticatedStaffContextPort staff;
    private final Clock clock;
    private final boolean enabled;
    private final com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort specimens;

    private final boolean amendmentsEnabled;

    /** Unit fixtures that do not exercise specimens. */
    public LabReportApplicationService(LabTestRepositoryPort tests, LabReportRepositoryPort reports,
            LabCatalogPort catalog, AuthenticatedStaffContextPort staff, Clock clock, boolean enabled) {
        this(tests, reports, catalog, staff, clock, enabled, null, false);
    }

    public LabReportApplicationService(LabTestRepositoryPort tests, LabReportRepositoryPort reports,
            LabCatalogPort catalog, AuthenticatedStaffContextPort staff, Clock clock, boolean enabled,
            com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort specimens) {
        this(tests, reports, catalog, staff, clock, enabled, specimens, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LabReportApplicationService(LabTestRepositoryPort tests, LabReportRepositoryPort reports,
            LabCatalogPort catalog, AuthenticatedStaffContextPort staff, @Qualifier("labClock") Clock clock,
            @Value("${mediflow.v2.lab-review:false}") boolean enabled,
            com.mediflow.lab.application.port.out.LabSpecimenRepositoryPort specimens,
            @Value("${mediflow.v2.lab-amendment:false}") boolean amendmentsEnabled) {
        this.specimens = specimens;
        this.amendmentsEnabled = amendmentsEnabled;
        this.tests = tests;
        this.reports = reports;
        this.catalog = catalog;
        this.staff = staff;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public LabReportDTO current(UUID testId) {
        requireEnabled();
        if (tests.findById(testId).isEmpty()) {
            throw new LabTestNotFoundException(testId);
        }
        return reports.current(testId).map(LabReportDTO::of)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_REPORT_NOT_FOUND", "Chưa có báo cáo kết quả"));
    }

    @Transactional
    public LabReportDTO saveDraft(UUID testId, SaveLabReportDraftRequest request) {
        requireEnabled();
        UUID actor = actor();
        LabTest test = tests.findByIdForUpdate(testId).orElseThrow(() -> new LabTestNotFoundException(testId));
        LabReport report = reports.forUpdate(testId).filter(r -> r.getStatus() != LabReportStatus.RELEASED)
                .orElseGet(() -> openOriginal(test));
        int expected = request.draftRevision();
        report.saveDraft(expected, inputs(request), request.conclusion(), request.performedDate(), actor,
                clock.instant());
        reports.update(report, expected);
        return LabReportDTO.of(report);
    }

    @Transactional
    public LabReportDTO submit(UUID testId, SubmitLabReportRequest request) {
        requireEnabled();
        UUID actor = actor();
        tests.findByIdForUpdate(testId).orElseThrow(() -> new LabTestNotFoundException(testId));
        LabReport report = openReport(testId);
        int expected = request.draftRevision();
        if (specimens != null && !specimens.hasAcceptedSpecimen(testId)) {
            throw new LabRuleException("LAB_SPECIMEN_REQUIRED", "Chưa có mẫu bệnh phẩm được tiếp nhận cho xét nghiệm này");
        }
        report.submit(expected, template(report), actor, clock.instant());
        reports.update(report, expected);
        return LabReportDTO.of(report);
    }

    @Transactional
    public LabReportDTO returnForCorrection(UUID testId, ReturnLabReportRequest request) {
        requireEnabled();
        UUID actor = actor();
        tests.findByIdForUpdate(testId).orElseThrow(() -> new LabTestNotFoundException(testId));
        LabReport report = openReport(testId);
        int expected = report.getDraftRevision();
        report.returnForCorrection(request.reason(), actor, clock.instant());
        reports.update(report, expected);
        return LabReportDTO.of(report);
    }

    /** Opens revision n+1 as a draft prefilled from the released revision n; n stays untouched. */
    @Transactional
    public LabReportDTO openAmendment(UUID testId,
            com.mediflow.lab.application.dto.request.LabReportRequests.OpenAmendmentRequest request) {
        requireEnabled();
        if (!amendmentsEnabled) {
            throw new ResourceNotFoundException("LAB_AMENDMENT_DISABLED", "Chức năng đính chính kết quả chưa được bật");
        }
        UUID actor = actor();
        tests.findByIdForUpdate(testId).orElseThrow(() -> new LabTestNotFoundException(testId));
        LabReport released = reports.forUpdate(testId)
                .filter(r -> r.getStatus() == LabReportStatus.RELEASED)
                .orElseThrow(() -> new com.mediflow.common.exception.DuplicateResourceException(
                        "LAB_AMENDMENT_NOT_RELEASED", "Chỉ đính chính kết quả đã phát hành và chưa có bản đính chính mở"));
        LabReport amendment = LabReport.open(testId, released.getResultVersion() + 1, released.getTemplateCode(),
                released.getTemplateVersion(), request.reason(), clock.instant());
        amendment.saveDraft(0, released.getValues(), released.getConclusion(), released.getPerformedDate(), actor,
                clock.instant());
        reports.insert(amendment);
        return LabReportDTO.of(amendment);
    }

    private LabReport openOriginal(LabTest test) {
        if (test.getCareContractVersion() != 1 || test.getStatus() != LabTestStatus.IN_PROGRESS) {
            throw new LabRuleException("LAB_REPORT_TEST_NOT_IN_PROGRESS",
                    "Chỉ nhập báo cáo cho xét nghiệm đang thực hiện");
        }
        LabCatalogPort.TemplateBinding binding = catalog.binding(test.getTestId())
                .orElseThrow(() -> new LabRuleException("LAB_REPORT_TEMPLATE_REQUIRED",
                        "Xét nghiệm này được tạo trước danh mục, hãy nhập kết quả theo cách cũ"));
        LabReport report = LabReport.open(test.getTestId(), 1, binding.templateCode(), binding.templateVersion(), null,
                clock.instant());
        reports.insert(report);
        reports.enterWorkflow(test.getTestId());
        return report;
    }

    LabReport openReport(UUID testId) {
        return reports.forUpdate(testId).filter(r -> r.getStatus() != LabReportStatus.RELEASED)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_REPORT_NOT_FOUND",
                        "Không có báo cáo đang soạn hoặc chờ duyệt"));
    }

    LabResultTemplate template(LabReport report) {
        return catalog.template(report.getTemplateCode(), report.getTemplateVersion())
                .orElseThrow(() -> new IllegalStateException("Pinned template disappeared: "
                        + report.getTemplateCode() + " v" + report.getTemplateVersion()));
    }

    UUID actor() {
        return staff.currentStaff().map(AuthenticatedStaffContext::staffId)
                .orElseThrow(() -> new LabRuleException("LAB_STAFF_REQUIRED",
                        "Người thao tác báo cáo phải là nhân viên đã xác thực"));
    }

    void requireEnabled() {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_REVIEW_DISABLED", "Quy trình duyệt kết quả chưa được bật");
        }
    }

    private static List<LabValueInput> inputs(SaveLabReportDraftRequest request) {
        return request.values() == null ? List.of() : request.values().stream()
                .map(v -> new LabValueInput(v.fieldCode().strip(), blankToNull(v.operator()), blankToNull(v.value()),
                        blankToNull(v.noResultReason())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
