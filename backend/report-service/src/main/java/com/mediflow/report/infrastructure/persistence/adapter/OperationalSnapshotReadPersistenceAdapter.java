package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Repository;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.out.OperationalSnapshotReadPort;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.domain.model.OperationalReportPublication;

@Repository
public class OperationalSnapshotReadPersistenceAdapter implements OperationalSnapshotReadPort {
    private static final int QUERY_TIMEOUT_SECONDS = 3;
    private final JdbcTemplate jdbc;
    public OperationalSnapshotReadPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<OperationalReportPublication> publication(String kind) {
        try {
            return jdbc.query(connection -> {
                var statement = connection.prepareStatement("""
                SELECT p.*, g.completed_at FROM operational_report_publication p
                JOIN operational_replay_generation g ON g.generation_id = p.generation_id
                WHERE p.report_kind = ? AND g.status = 'VERIFIED' AND g.projector_version = 1
                  AND g.applied_events = g.source_events
                  AND NOT EXISTS (SELECT 1 FROM operational_replay_input i WHERE i.generation_id = g.generation_id AND NOT i.applied)
                  AND (p.report_kind <> 'SURGERY' OR EXISTS (
                      SELECT 1 FROM operational_generation_acceptance a WHERE a.generation_id=p.generation_id
                      AND a.report_kind=p.report_kind AND a.covered_from=p.covered_from AND a.covered_to=p.covered_to
                      AND a.zone_id=p.zone_id AND a.acceptance_reference=p.acceptance_reference
                      AND p.metrics=ARRAY['SURGERIES_COMPLETED','SURGERIES_CANCELLED','SURGERY_DURATION_MINUTES']::varchar[]
                      AND EXISTS(SELECT 1 FROM operational_publication_audit h
                          WHERE h.generation_id=a.generation_id AND h.action='PUBLISH')))
                """);
                statement.setString(1, kind);
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(1);
                return statement;
            }, (rs, row) -> new OperationalReportPublication(rs.getObject("generation_id", UUID.class),
                        rs.getDate("covered_from").toLocalDate(), rs.getDate("covered_to").toLocalDate(), rs.getString("zone_id"),
                        java.util.Arrays.stream((String[]) rs.getArray("metrics").getArray()).map(Metric::valueOf).collect(Collectors.toSet()),
                        rs.getTimestamp("completed_at").toInstant(), rs.getString("acceptance_reference"))).stream().findFirst();
        } catch (IllegalArgumentException | QueryTimeoutException | DataAccessResourceFailureException exception) {
            // Invalid publication metadata or an unavailable read must never become successful zeroes.
            throw new ReportProjectionUnavailableException();
        }
    }
    @Override public List<Value> read(UUID generationId, LocalDate from, LocalDate to, UUID departmentId, Set<Metric> metrics) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days < 1 || days > 366 || metrics.isEmpty() || metrics.size() > Metric.values().length) {
            throw new IllegalArgumentException("Bounded snapshot scope is required");
        }
        String placeholders = metrics.stream().map(metric -> "?").collect(Collectors.joining(","));
        String sql = "SELECT metric_date, metric_type, numeric_value FROM operational_replay_scope WHERE generation_id = ?"
                + (departmentId == null ? " AND department_id IS NULL" : " AND department_id = ?")
                + " AND metric_date BETWEEN ? AND ?"
                + " AND metric_type IN (" + placeholders + ") ORDER BY metric_date, metric_type";
        // Predicate/order align with the covering index, including the real NULL hospital scope.
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(generationId);
        if (departmentId != null) parameters.add(departmentId);
        parameters.add(Date.valueOf(from));
        parameters.add(Date.valueOf(to));
        metrics.stream().sorted().forEach(metric -> parameters.add(metric.name()));
        try {
            return jdbc.query(connection -> {
                PreparedStatement statement = connection.prepareStatement(sql);
                for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setFetchSize(128);
                // One extra row lets the service detect duplicate/corrupt scope rows instead of truncating silently.
                statement.setMaxRows(Math.toIntExact(days * metrics.size() + 1));
                return statement;
            }, (rs, row) -> new Value(rs.getDate("metric_date").toLocalDate(),
                    Metric.valueOf(rs.getString("metric_type")), rs.getLong("numeric_value")));
        } catch (IllegalArgumentException | QueryTimeoutException | DataAccessResourceFailureException exception) {
            throw new ReportProjectionUnavailableException();
        }
    }
}
