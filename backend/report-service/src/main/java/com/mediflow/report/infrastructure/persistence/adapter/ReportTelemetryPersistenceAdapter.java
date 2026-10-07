package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot;
import com.mediflow.report.application.dto.response.ReportTelemetrySnapshot.Replay;
import com.mediflow.report.application.port.out.ReportTelemetryReadPort;

/** One MVCC statement prevents mixing before/after states of concurrent replay commits. */
@Component
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class ReportTelemetryPersistenceAdapter implements ReportTelemetryReadPort {
    private static final String SQL = """
            WITH pending AS (
                SELECT count(*) AS count, min(c.created_at) AS oldest
                FROM report_admission_fact c
                WHERE c.fact_type='CLOSED' AND NOT EXISTS (
                    SELECT 1 FROM report_admission_fact s
                    WHERE s.admission_id=c.admission_id AND s.fact_type='STARTED')
            ), operational AS (
                SELECT count(*) FILTER (WHERE status='BUILDING') AS building,
                    count(*) FILTER (WHERE status='VERIFIED') AS verified,
                    count(*) FILTER (WHERE status='FAILED') AS failed,
                    coalesce(sum(source_events) FILTER (WHERE status='BUILDING'),0) AS sources,
                    coalesce(sum(applied_events) FILTER (WHERE status='BUILDING'),0) AS applied,
                    min(created_at) FILTER (WHERE status='BUILDING') AS oldest
                FROM operational_replay_generation
            ), cash AS (
                SELECT count(*) FILTER (WHERE status='BUILDING') AS building,
                    count(*) FILTER (WHERE status='VERIFIED') AS verified,
                    count(*) FILTER (WHERE status='FAILED') AS failed,
                    coalesce(sum(source_receipts) FILTER (WHERE status='BUILDING'),0) AS sources,
                    coalesce(sum(applied_receipts) FILTER (WHERE status='BUILDING'),0) AS applied,
                    min(created_at) FILTER (WHERE status='BUILDING') AS oldest
                FROM cash_replay_generation
            )
            SELECT statement_timestamp() AS sampled_at, p.count AS pending_count, p.oldest AS pending_oldest,
                (SELECT count(*) FROM operational_source_snapshot
                    WHERE evidence_state='LEGACY_UNVERIFIED') AS legacy_count,
                o.building AS o_building, o.verified AS o_verified, o.failed AS o_failed,
                o.sources AS o_sources, o.applied AS o_applied, o.oldest AS o_oldest,
                c.building AS c_building, c.verified AS c_verified, c.failed AS c_failed,
                c.sources AS c_sources, c.applied AS c_applied, c.oldest AS c_oldest
            FROM pending p CROSS JOIN operational o CROSS JOIN cash c
            """;
    private final JdbcTemplate jdbc;

    public ReportTelemetryPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ReportTelemetrySnapshot capture() {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(SQL);
            statement.setQueryTimeout(5);
            return statement;
        }, result -> {
            if (!result.next()) throw new SQLException("Missing aggregate telemetry row");
            return new ReportTelemetrySnapshot(instant(result, "sampled_at"), result.getLong("pending_count"),
                    instant(result, "pending_oldest"), result.getLong("legacy_count"),
                    replay(result, "o"), replay(result, "c"));
        });
    }

    private static Replay replay(ResultSet row, String prefix) throws SQLException {
        // PostgreSQL SUM(bigint) is numeric. Overflow must fail the sample, not wrap to healthy zero.
        return new Replay(row.getLong(prefix + "_building"), row.getLong(prefix + "_verified"),
                row.getLong(prefix + "_failed"), row.getBigDecimal(prefix + "_sources").longValueExact(),
                row.getBigDecimal(prefix + "_applied").longValueExact(), instant(row, prefix + "_oldest"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
