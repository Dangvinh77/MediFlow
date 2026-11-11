package com.mediflow.lab.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.domain.exception.LabRuleException;

/**
 * One revision of a Lab test's result under review: written as a draft, submitted, then released by a
 * second person. Revision 1 is the original result; 2 and above are amendments of a released result.
 */
public final class LabReport {
    private final UUID reportId;
    private final UUID testId;
    private final int resultVersion;
    private final String templateCode;
    private final int templateVersion;
    private final String amendmentReason;
    private final Instant createdAt;
    private LabReportStatus status;
    private int draftRevision;
    private List<LabValueInput> values;
    private Map<String, LabFlag> flags;
    private String conclusion;
    private LocalDate performedDate;
    private UUID enteredBy;
    private UUID submittedBy;
    private Instant submittedAt;
    private UUID returnedBy;
    private String returnReason;
    private UUID releasedBy;
    private Instant releasedAt;
    private Instant updatedAt;

    private LabReport(UUID reportId, UUID testId, int resultVersion, String templateCode, int templateVersion,
            String amendmentReason, Instant createdAt) {
        this.reportId = Objects.requireNonNull(reportId);
        this.testId = Objects.requireNonNull(testId);
        this.resultVersion = resultVersion;
        this.templateCode = Objects.requireNonNull(templateCode);
        this.templateVersion = templateVersion;
        this.amendmentReason = amendmentReason;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static LabReport open(UUID testId, int resultVersion, String templateCode, int templateVersion,
            String amendmentReason, Instant at) {
        if (resultVersion < 1) {
            throw new IllegalArgumentException("A Lab report revision starts at 1");
        }
        String reason = amendmentReason == null ? null : amendmentReason.strip();
        if (resultVersion > 1 && (reason == null || reason.isEmpty())) {
            throw new LabRuleException("LAB_AMENDMENT_REASON_REQUIRED", "Cần nêu lý do đính chính kết quả");
        }
        if (reason != null && reason.length() > 1000) {
            throw new LabRuleException("LAB_AMENDMENT_REASON_TOO_LONG", "Lý do đính chính tối đa 1000 ký tự");
        }
        LabReport report = new LabReport(UUID.randomUUID(), testId, resultVersion, templateCode, templateVersion,
                resultVersion == 1 ? null : reason, at);
        report.status = LabReportStatus.DRAFT;
        report.values = List.of();
        report.flags = Map.of();
        report.updatedAt = at;
        return report;
    }

    public static LabReport restore(UUID reportId, UUID testId, int resultVersion, LabReportStatus status,
            int draftRevision, String templateCode, int templateVersion, List<LabValueInput> values,
            Map<String, LabFlag> flags, String conclusion, LocalDate performedDate, String amendmentReason,
            UUID enteredBy, UUID submittedBy, Instant submittedAt, UUID returnedBy, String returnReason,
            UUID releasedBy, Instant releasedAt, Instant createdAt, Instant updatedAt) {
        LabReport report = new LabReport(reportId, testId, resultVersion, templateCode, templateVersion,
                amendmentReason, createdAt);
        report.status = Objects.requireNonNull(status);
        report.draftRevision = draftRevision;
        report.values = List.copyOf(values);
        report.flags = flags == null ? Map.of() : Map.copyOf(flags);
        report.conclusion = conclusion;
        report.performedDate = performedDate;
        report.enteredBy = enteredBy;
        report.submittedBy = submittedBy;
        report.submittedAt = submittedAt;
        report.returnedBy = returnedBy;
        report.returnReason = returnReason;
        report.releasedBy = releasedBy;
        report.releasedAt = releasedAt;
        report.updatedAt = updatedAt;
        return report;
    }

    /** Replaces the draft; {@code expectedDraftRevision} is the revision the writer last read. */
    public void saveDraft(int expectedDraftRevision, List<LabValueInput> values, String conclusion,
            LocalDate performedDate, UUID actor, Instant at) {
        requireDraft();
        requireRevision(expectedDraftRevision);
        if (conclusion != null && conclusion.length() > 4000) {
            throw new LabRuleException("LAB_REPORT_INVALID", "Kết luận tối đa 4000 ký tự");
        }
        this.values = values == null ? List.of() : List.copyOf(values);
        this.conclusion = conclusion;
        this.performedDate = performedDate;
        this.enteredBy = Objects.requireNonNull(actor);
        this.draftRevision++;
        this.updatedAt = at;
    }

    public void submit(int expectedDraftRevision, LabResultTemplate template, UUID actor, Instant at) {
        requireDraft();
        requireRevision(expectedDraftRevision);
        if (!template.templateCode().equals(templateCode) || template.version() != templateVersion) {
            throw new IllegalStateException("The report must be validated with its pinned template");
        }
        List<LabValueViolation> violations = template.validate(values);
        if (!violations.isEmpty() || performedDate == null) {
            String detail = violations.stream().map(v -> v.fieldCode() + ": " + v.code())
                    .collect(Collectors.joining(", "));
            throw new LabRuleException("LAB_REPORT_INVALID", performedDate == null
                    ? "Ngày thực hiện là bắt buộc" + (detail.isEmpty() ? "" : "; " + detail)
                    : "Kết quả chưa hợp lệ: " + detail);
        }
        this.status = LabReportStatus.PENDING_REVIEW;
        this.submittedBy = Objects.requireNonNull(actor);
        this.submittedAt = at;
        this.updatedAt = at;
    }

    public void returnForCorrection(String reason, UUID actor, Instant at) {
        if (status != LabReportStatus.PENDING_REVIEW) {
            throw new DuplicateResourceException("LAB_REPORT_NOT_PENDING", "Báo cáo không ở trạng thái chờ duyệt");
        }
        if (reason == null || reason.isBlank()) {
            throw new LabRuleException("LAB_REPORT_RETURN_REASON_REQUIRED", "Cần nêu lý do trả lại báo cáo");
        }
        this.status = LabReportStatus.DRAFT;
        this.returnReason = reason.strip();
        this.returnedBy = Objects.requireNonNull(actor);
        this.draftRevision++;
        this.updatedAt = at;
    }

    /** Releases the submitted revision; whoever entered or submitted it may not release it. */
    public void release(UUID actor, Map<String, LabFlag> flags, Instant at) {
        if (status != LabReportStatus.PENDING_REVIEW) {
            throw new DuplicateResourceException("LAB_REPORT_NOT_PENDING", "Báo cáo không ở trạng thái chờ duyệt");
        }
        Objects.requireNonNull(actor);
        if (actor.equals(enteredBy) || actor.equals(submittedBy)) {
            throw new ForbiddenOperationException("LAB_SELF_RELEASE",
                    "Người nhập hoặc gửi duyệt không được tự phát hành kết quả");
        }
        this.flags = flags == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(flags));
        this.status = LabReportStatus.RELEASED;
        this.releasedBy = actor;
        this.releasedAt = at;
        this.updatedAt = at;
    }

    private void requireDraft() {
        if (status != LabReportStatus.DRAFT) {
            throw new DuplicateResourceException("LAB_REPORT_NOT_EDITABLE",
                    "Báo cáo đang chờ duyệt hoặc đã phát hành, không sửa được");
        }
    }

    private void requireRevision(int expected) {
        if (expected != draftRevision) {
            throw new DuplicateResourceException("LAB_REPORT_STALE",
                    "Báo cáo đã được người khác cập nhật; hãy tải lại trước khi lưu");
        }
    }

    public UUID getReportId() { return reportId; }
    public UUID getTestId() { return testId; }
    public int getResultVersion() { return resultVersion; }
    public LabReportStatus getStatus() { return status; }
    public int getDraftRevision() { return draftRevision; }
    public String getTemplateCode() { return templateCode; }
    public int getTemplateVersion() { return templateVersion; }
    public List<LabValueInput> getValues() { return values; }
    public Map<String, LabFlag> getFlags() { return flags; }
    public String getConclusion() { return conclusion; }
    public LocalDate getPerformedDate() { return performedDate; }
    public String getAmendmentReason() { return amendmentReason; }
    public UUID getEnteredBy() { return enteredBy; }
    public UUID getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UUID getReturnedBy() { return returnedBy; }
    public String getReturnReason() { return returnReason; }
    public UUID getReleasedBy() { return releasedBy; }
    public Instant getReleasedAt() { return releasedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
