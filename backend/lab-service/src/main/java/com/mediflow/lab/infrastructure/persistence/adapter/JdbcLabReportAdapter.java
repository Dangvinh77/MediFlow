package com.mediflow.lab.infrastructure.persistence.adapter;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.lab.application.port.out.LabReportRepositoryPort;
import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.domain.model.LabReport;
import com.mediflow.lab.domain.model.LabReportStatus;
import com.mediflow.lab.domain.model.LabValueInput;

@Repository
public class JdbcLabReportAdapter implements LabReportRepositoryPort {
    private static final String COLUMNS = """
            report_id, test_id, result_version, status, draft_revision, template_code, template_version, conclusion,
            performed_date, amendment_reason, entered_by, submitted_by, submitted_at, returned_by, return_reason,
            released_by, released_at, created_at, updated_at, authors""";
    private final JdbcTemplate jdbc;

    public JdbcLabReportAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LabReport> current(UUID testId) {
        return one("SELECT " + COLUMNS + " FROM lab_report WHERE test_id = ? ORDER BY result_version DESC LIMIT 1",
                testId);
    }

    @Override
    public Optional<LabReport> forUpdate(UUID testId) {
        return one("SELECT " + COLUMNS
                + " FROM lab_report WHERE test_id = ? ORDER BY result_version DESC LIMIT 1 FOR UPDATE", testId);
    }

    @Override
    public List<LabReport> revisions(UUID testId) {
        List<LabReport> rows = new ArrayList<>();
        for (Row row : jdbc.query("SELECT " + COLUMNS + " FROM lab_report WHERE test_id = ? ORDER BY result_version",
                this::row, testId)) {
            rows.add(row.toReport(values(row.reportId()), flags(row.reportId())));
        }
        return rows;
    }

    @Override
    public void insert(LabReport r) {
        jdbc.update("INSERT INTO lab_report(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                r.getReportId(), r.getTestId(), r.getResultVersion(), r.getStatus().name(), r.getDraftRevision(),
                r.getTemplateCode(), r.getTemplateVersion(), r.getConclusion(), date(r), r.getAmendmentReason(),
                r.getEnteredBy(), r.getSubmittedBy(), ts(r.getSubmittedAt()), r.getReturnedBy(), r.getReturnReason(),
                r.getReleasedBy(), ts(r.getReleasedAt()), ts(r.getCreatedAt()), ts(r.getUpdatedAt()), authors(r));
        writeValues(r);
    }

    @Override
    public void update(LabReport r, int expectedDraftRevision) {
        int changed = jdbc.update("""
                UPDATE lab_report SET status = ?, draft_revision = ?, conclusion = ?, performed_date = ?, entered_by = ?,
                       authors = ?,
                       submitted_by = ?, submitted_at = ?, returned_by = ?, return_reason = ?, released_by = ?,
                       released_at = ?, updated_at = ?
                WHERE report_id = ? AND draft_revision = ?
                """, r.getStatus().name(), r.getDraftRevision(), r.getConclusion(), date(r), r.getEnteredBy(),
                authors(r), r.getSubmittedBy(), ts(r.getSubmittedAt()), r.getReturnedBy(), r.getReturnReason(), r.getReleasedBy(),
                ts(r.getReleasedAt()), ts(r.getUpdatedAt()), r.getReportId(), expectedDraftRevision);
        if (changed != 1) {
            throw new DuplicateResourceException("LAB_REPORT_STALE",
                    "Báo cáo đã được người khác cập nhật; hãy tải lại trước khi lưu");
        }
        jdbc.update("DELETE FROM lab_report_value WHERE report_id = ?", r.getReportId());
        writeValues(r);
    }

    @Override
    public int workflowVersion(UUID testId) {
        Integer version = jdbc.query("SELECT result_workflow_version FROM lab_test WHERE test_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, testId);
        return version == null ? 0 : version;
    }

    @Override
    public void enterWorkflow(UUID testId) {
        jdbc.update("UPDATE lab_test SET result_workflow_version = 1 WHERE test_id = ? AND result_workflow_version = 0",
                testId);
    }

    private void writeValues(LabReport r) {
        int position = 0;
        for (LabValueInput v : r.getValues()) {
            LabFlag flag = r.getFlags().get(v.fieldCode());
            jdbc.update("""
                    INSERT INTO lab_report_value(report_id, field_code, position, operator, value, no_result_reason, flag)
                    VALUES (?,?,?,?,?,?,?)
                    """, r.getReportId(), v.fieldCode(), ++position, v.operator(), v.value(), v.noResultReason(),
                    flag == null ? null : flag.name());
        }
    }

    private Optional<LabReport> one(String sql, UUID testId) {
        List<Row> rows = jdbc.query(sql, this::row, testId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Row row = rows.get(0);
        return Optional.of(row.toReport(values(row.reportId()), flags(row.reportId())));
    }

    private List<LabValueInput> values(UUID reportId) {
        return jdbc.query("""
                SELECT field_code, operator, value, no_result_reason FROM lab_report_value
                WHERE report_id = ? ORDER BY position
                """, (rs, n) -> new LabValueInput(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                reportId);
    }

    private Map<String, LabFlag> flags(UUID reportId) {
        Map<String, LabFlag> flags = new LinkedHashMap<>();
        jdbc.query("SELECT field_code, flag FROM lab_report_value WHERE report_id = ? AND flag IS NOT NULL ORDER BY position",
                rs -> { flags.put(rs.getString(1), LabFlag.valueOf(rs.getString(2))); }, reportId);
        return flags;
    }

    private Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("report_id", UUID.class), rs.getObject("test_id", UUID.class),
                rs.getInt("result_version"), LabReportStatus.valueOf(rs.getString("status")),
                rs.getInt("draft_revision"), rs.getString("template_code"), rs.getInt("template_version"),
                rs.getString("conclusion"),
                rs.getDate("performed_date") == null ? null : rs.getDate("performed_date").toLocalDate(),
                rs.getString("amendment_reason"), rs.getObject("entered_by", UUID.class),
                rs.getObject("submitted_by", UUID.class), instant(rs, "submitted_at"),
                rs.getObject("returned_by", UUID.class), rs.getString("return_reason"),
                rs.getObject("released_by", UUID.class), instant(rs, "released_at"), instant(rs, "created_at"),
                instant(rs, "updated_at"), authors(rs));
    }

    private static java.util.Set<UUID> authors(ResultSet rs) throws SQLException {
        java.sql.Array array = rs.getArray("authors");
        if (array == null) {
            return java.util.Set.of();
        }
        return java.util.Set.of((UUID[]) array.getArray());
    }

    private UUID[] authors(LabReport r) {
        return r.getAuthors().toArray(UUID[]::new);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Date date(LabReport r) {
        return r.getPerformedDate() == null ? null : Date.valueOf(r.getPerformedDate());
    }

    private record Row(UUID reportId, UUID testId, int resultVersion, LabReportStatus status, int draftRevision,
            String templateCode, int templateVersion, String conclusion, java.time.LocalDate performedDate,
            String amendmentReason, UUID enteredBy, UUID submittedBy, Instant submittedAt, UUID returnedBy,
            String returnReason, UUID releasedBy, Instant releasedAt, Instant createdAt, Instant updatedAt,
            java.util.Set<UUID> authors) {
        LabReport toReport(List<LabValueInput> values, Map<String, LabFlag> flags) {
            return LabReport.restore(reportId, testId, resultVersion, status, draftRevision, templateCode,
                    templateVersion, values, flags, conclusion, performedDate, amendmentReason, enteredBy, authors,
                    submittedBy, submittedAt, returnedBy, returnReason, releasedBy, releasedAt, createdAt, updatedAt);
        }
    }
}
