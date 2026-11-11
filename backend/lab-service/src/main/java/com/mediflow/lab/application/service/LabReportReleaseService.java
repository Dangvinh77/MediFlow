package com.mediflow.lab.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.support.TransactionOperations;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.lab.application.dto.response.LabReportDTO;
import com.mediflow.lab.application.event.LabResultV2Payload.ReleasedValue;
import com.mediflow.lab.application.port.in.LabResultReleasePort;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContext;
import com.mediflow.lab.application.port.out.AuthenticatedStaffContextPort;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.application.port.out.LabReleaseAuthorityPort;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.domain.model.LabReferenceRange;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabReportStatus;
import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabTemplateField;
import com.mediflow.lab.domain.model.LabValueInput;

/**
 * Second-person release of a submitted Lab report. Organization is asked first, outside any transaction;
 * the report, the test's completion and the {@code lab.result.created} outbox row then commit together.
 */
public class LabReportReleaseService {
    private final LabReportRepositoryPort reports;
    private final LabCatalogPort catalog;
    private final AuthenticatedStaffContextPort staff;
    private final CurrentReaderPort readers;
    private final LabReleaseAuthorityPort authority;
    private final LabResultReleasePort labs;
    private final CorrelationIdProvider correlationIds;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final boolean enabled;
    private final com.mediflow.lab.application.port.out.LabTestRepositoryPort tests;
    private final com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort notices;
    private final com.mediflow.lab.application.port.out.LabOutboxPort outbox;

    public LabReportReleaseService(LabReportRepositoryPort reports, LabCatalogPort catalog,
            AuthenticatedStaffContextPort staff, CurrentReaderPort readers, LabReleaseAuthorityPort authority,
            LabResultReleasePort labs, CorrelationIdProvider correlationIds, TransactionOperations transactions,
            Clock clock, boolean enabled, com.mediflow.lab.application.port.out.LabTestRepositoryPort tests,
            com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort notices,
            com.mediflow.lab.application.port.out.LabOutboxPort outbox) {
        this.tests = tests;
        this.notices = notices;
        this.outbox = outbox;
        this.reports = reports;
        this.catalog = catalog;
        this.staff = staff;
        this.readers = readers;
        this.authority = authority;
        this.labs = labs;
        this.correlationIds = correlationIds;
        this.transactions = transactions;
        this.clock = clock;
        this.enabled = enabled;
    }

    public LabReportDTO release(UUID testId) {
        if (!enabled) {
            throw new ResourceNotFoundException("LAB_REVIEW_DISABLED", "Quy trình duyệt kết quả chưa được bật");
        }
        UUID actor = staff.currentStaff().map(AuthenticatedStaffContext::staffId)
                .orElseThrow(() -> new LabRuleException("LAB_STAFF_REQUIRED",
                        "Người phát hành kết quả phải là nhân viên đã xác thực"));
        UUID account = readers.current().map(ReaderIdentity::accountId)
                .orElseThrow(() -> denied());
        if (!authority.canRelease(account, actor, correlationIds.currentOrCreate().toString())) {
            throw denied();
        }
        return transactions.execute(status -> releaseLocked(testId, actor));
    }

    private LabReportDTO releaseLocked(UUID testId, UUID actor) {
        // Same lock order as draft, submit and return: lab_test row first, then lab_report.
        if (tests != null) {
            tests.findByIdForUpdate(testId);
        }
        LabReport report = reports.forUpdate(testId).filter(r -> r.getStatus() != LabReportStatus.RELEASED)
                .orElseThrow(() -> new ResourceNotFoundException("LAB_REPORT_NOT_FOUND",
                        "Không có báo cáo đang chờ duyệt"));
        LabResultTemplate template = catalog.template(report.getTemplateCode(), report.getTemplateVersion())
                .orElseThrow(() -> new IllegalStateException("Pinned template disappeared: "
                        + report.getTemplateCode() + " v" + report.getTemplateVersion()));
        Instant at = clock.instant();
        int expected = report.getDraftRevision();
        Map<String, LabFlag> flags = new LinkedHashMap<>();
        List<ReleasedValue> values = new ArrayList<>();
        List<LabResult> results = new ArrayList<>();
        for (LabValueInput input : report.getValues()) {
            LabTemplateField field = template.field(input.fieldCode()).orElseThrow(() -> new LabRuleException(
                    "LAB_REPORT_INVALID", "Trường không thuộc mẫu: " + input.fieldCode()));
            // Lab holds no patient sex or age: only ranges without those bounds apply here.
            LabFlag flag = field.flag(input, null, null);
            if (flag != null) {
                flags.put(field.fieldCode(), flag);
            }
            String range = rangeText(field.rangeFor(null, null));
            values.add(new ReleasedValue(field.fieldCode(), field.label(), input.operator(), input.value(),
                    field.unit(), input.noResultReason(), flag == null ? null : flag.name(), range));
            results.add(LabResult.create(field.label(), displayValue(input), field.unit(), range));
        }
        report.release(actor, flags, at);
        if (report.getResultVersion() == 1) {
            labs.releaseReviewed(testId, results, report.getConclusion(), report.getPerformedDate(), actor, values, at);
        } else {
            labs.releaseAmendment(new LabResultReleasePort.AmendmentRelease(testId, results, report.getConclusion(),
                    report.getPerformedDate(), actor, values, report.getResultVersion(), report.getAmendmentReason(),
                    report.getTemplateCode(), report.getTemplateVersion(), at));
        }
        reports.update(report, expected);
        raiseCriticalNotices(testId, report, values, at);
        return LabReportDTO.of(report);
    }

    private void raiseCriticalNotices(UUID testId, LabReport report, List<ReleasedValue> values, Instant at) {
        List<ReleasedValue> critical = values.stream()
                .filter(v -> v.flag() != null && LabFlag.valueOf(v.flag()).critical()).toList();
        if (critical.isEmpty()) {
            return;
        }
        var test = tests.findById(testId).orElseThrow(() -> new IllegalStateException("Released test vanished"));
        String correlationId = correlationIds.currentOrCreate().toString();
        for (ReleasedValue v : critical) {
            var notice = com.mediflow.lab.domain.model.LabCriticalNotice.raise(testId, report.getResultVersion(),
                    v.fieldCode(), v.label(), v.value(), v.unit(), LabFlag.valueOf(v.flag()), test.getPatientId(),
                    test.getRequestingDepartmentId(), at);
            if (!notices.insertIfAbsent(notice)) {
                continue;
            }
            var payload = new com.mediflow.lab.application.event.LabCriticalRaisedPayload(notice.getNoticeId(), testId,
                    test.getPatientId(), test.getRecordId(), test.getRequestingDepartmentId(), test.getCareEpisodeType(),
                    test.getCareEpisodeId(), test.getLabType(), report.getResultVersion(), v.fieldCode(), v.label(),
                    v.value(), v.unit(), v.flag(), at);
            outbox.append(new com.mediflow.lab.application.event.DomainEventEnvelope<>(UUID.randomUUID(),
                    "lab.critical.raised", 1, at, correlationId, "lab-service", payload));
        }
    }

    private static String displayValue(LabValueInput input) {
        if (!input.hasValue()) {
            return "Không có kết quả: " + input.noResultReason();
        }
        return input.operator() == null || input.operator().isBlank()
                ? input.value() : input.operator() + " " + input.value();
    }

    static String rangeText(LabReferenceRange range) {
        if (range == null || range.low() == null && range.high() == null) {
            return null;
        }
        if (range.low() == null) {
            return "<= " + plain(range.high());
        }
        if (range.high() == null) {
            return ">= " + plain(range.low());
        }
        return plain(range.low()) + " - " + plain(range.high());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static ForbiddenOperationException denied() {
        return new ForbiddenOperationException("LAB_RELEASE_DENIED",
                "Bạn chưa được cấp quyền phát hành kết quả xét nghiệm");
    }
}
