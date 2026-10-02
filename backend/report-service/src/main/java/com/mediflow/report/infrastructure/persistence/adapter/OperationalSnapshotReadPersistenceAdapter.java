package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.mediflow.report.application.port.out.OperationalSnapshotReadPort;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import com.mediflow.report.domain.model.OperationalReportPublication;

@Repository
public class OperationalSnapshotReadPersistenceAdapter implements OperationalSnapshotReadPort {
    private final JdbcTemplate jdbc;
    public OperationalSnapshotReadPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<OperationalReportPublication> publication(String kind) {
        return jdbc.query("""
                SELECT p.*, g.completed_at FROM operational_report_publication p
                JOIN operational_replay_generation g ON g.generation_id = p.generation_id
                WHERE p.report_kind = ? AND g.status = 'VERIFIED' AND g.projector_version = 1
                  AND g.applied_events = g.source_events
                  AND NOT EXISTS (SELECT 1 FROM operational_replay_input i WHERE i.generation_id = g.generation_id AND NOT i.applied)
                """, (rs, row) -> new OperationalReportPublication(rs.getObject("generation_id", UUID.class),
                        rs.getDate("covered_from").toLocalDate(), rs.getDate("covered_to").toLocalDate(), rs.getString("zone_id"),
                        java.util.Arrays.stream((String[]) rs.getArray("metrics").getArray()).map(Metric::valueOf).collect(Collectors.toSet()),
                        rs.getTimestamp("completed_at").toInstant(), rs.getString("acceptance_reference")), kind).stream().findFirst();
    }
    @Override public List<Value> read(UUID generationId, LocalDate from, LocalDate to, UUID departmentId, Set<Metric> metrics) {
        String placeholders = metrics.stream().map(metric -> "?").collect(Collectors.joining(","));
        var parameters = new java.util.ArrayList<Object>(List.of(generationId, Date.valueOf(from), Date.valueOf(to)));
        parameters.add(departmentId);
        metrics.stream().sorted().forEach(metric -> parameters.add(metric.name()));
        return jdbc.query("SELECT metric_date, metric_type, numeric_value FROM operational_replay_scope WHERE generation_id = ?"
                + " AND metric_date BETWEEN ? AND ? AND department_id IS NOT DISTINCT FROM CAST(? AS UUID)"
                + " AND metric_type IN (" + placeholders + ") ORDER BY metric_date, metric_type",
                (rs, row) -> new Value(rs.getDate("metric_date").toLocalDate(), Metric.valueOf(rs.getString("metric_type")), rs.getLong("numeric_value")),
                parameters.toArray());
    }
}
