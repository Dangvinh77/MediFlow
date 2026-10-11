package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Repository;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.domain.model.FinancialAmounts;
import com.mediflow.report.domain.model.FinancialReportPublication;

/** Reads ONLY complete, explicitly accepted immutable five-metric generations, never V6/live/cash-only totals. */
@Repository
public class FinancialSnapshotReadPersistenceAdapter implements FinancialSnapshotReadPort {
    private final JdbcTemplate jdbc;
    public FinancialSnapshotReadPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<FinancialReportPublication> publication(String currency,String reportZone) {
        try {
            return jdbc.query(connection -> {
                var statement=connection.prepareStatement("""
                        SELECT g.*,p.acceptance_reference,p.published_at
                        FROM financial_report_publication p JOIN financial_report_generation g USING(generation_id)
                        WHERE p.currency=? AND p.report_zone=? AND g.currency=p.currency AND g.report_zone=p.report_zone
                          AND g.status='VERIFIED' AND g.projector_version=1 AND g.snapshot_version=1
                          AND g.applied_inputs=g.source_inputs AND g.unresolved_inputs=0
                          AND g.reconciled_at IS NOT NULL
                        """);
                statement.setString(1,currency); statement.setString(2,reportZone);
                statement.setQueryTimeout(3); statement.setMaxRows(1); return statement;
            },(rs,row) -> new FinancialReportPublication(rs.getObject("generation_id",UUID.class),
                    rs.getDate("covered_from").toLocalDate(),rs.getDate("covered_to").toLocalDate(),rs.getString("currency").trim(),
                    rs.getString("report_zone"),rs.getTimestamp("source_observed_at").toInstant(),
                    rs.getTimestamp("reconciled_at").toInstant(),rs.getTimestamp("published_at").toInstant(),
                    rs.getString("acceptance_reference"))).stream().findFirst();
        } catch (IllegalArgumentException | java.time.DateTimeException | QueryTimeoutException | DataAccessResourceFailureException exception) {
            throw new ReportProjectionUnavailableException();
        }
    }
    @Override public List<Day> read(UUID generationId,LocalDate from,LocalDate to,UUID departmentId) {
        long days=ChronoUnit.DAYS.between(from,to)+1;
        if (generationId==null || days<1 || days>366) throw new IllegalArgumentException("Bounded financial snapshot required");
        String sql="SELECT business_date,cash_received,deposit_liability,earned_revenue,refunds,outstanding_receivable"
                +" FROM financial_report_day WHERE generation_id=?"
                +(departmentId==null?" AND department_id IS NULL":" AND department_id=?")
                +" AND business_date BETWEEN ? AND ? ORDER BY business_date";
        try {
            return jdbc.query(connection -> {
                var statement=connection.prepareStatement(sql); int index=1;
                statement.setObject(index++,generationId);
                if (departmentId!=null) statement.setObject(index++,departmentId);
                statement.setDate(index++,Date.valueOf(from)); statement.setDate(index,Date.valueOf(to));
                statement.setQueryTimeout(3); statement.setFetchSize(128); statement.setMaxRows(Math.toIntExact(days+1));
                return statement;
            },(rs,row) -> new Day(rs.getDate("business_date").toLocalDate(),new FinancialAmounts(
                    rs.getBigDecimal("cash_received"),rs.getBigDecimal("deposit_liability"),rs.getBigDecimal("earned_revenue"),
                    rs.getBigDecimal("refunds"),rs.getBigDecimal("outstanding_receivable"))));
        } catch (IllegalArgumentException | QueryTimeoutException | DataAccessResourceFailureException exception) {
            throw new ReportProjectionUnavailableException();
        }
    }
}
