package com.mediflow.report.infrastructure.persistence.adapter;

import com.mediflow.report.application.port.out.OutpatientStatisticsReadPort;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Same outpatient fact predicates as {@link OutpatientReportReadPersistenceAdapter}, grouped for charts. */
@Repository @Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.report.outpatient-read.enabled"}, havingValue = "true")
@Transactional(transactionManager = "reportTransactionManager", propagation = Propagation.MANDATORY, readOnly = true)
public class OutpatientStatisticsPersistenceAdapter implements OutpatientStatisticsReadPort {
    private final JdbcTemplate jdbc;

    public OutpatientStatisticsPersistenceAdapter(
            @org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> load(LocalDate from, LocalDate to, UUID department, String zone, String currency) {
        return jdbc.query("""
                SELECT metric_date AS business_date, department_id, 'VISITS' AS kind, sum(numeric_value) AS amount
                FROM operational_contribution
                WHERE metric_date BETWEEN ? AND ? AND (?::uuid IS NULL OR department_id=?::uuid)
                    AND source_type='MEDICAL_RECORD' AND metric_type='COMPLETED_VISITS'
                    AND category IN ('OUTPATIENT_FOLLOW_UP','PRESCRIPTION','OTHER')
                GROUP BY 1, 2
                UNION ALL
                SELECT business_date, department_id, COALESCE(service_purpose, 'UNCLASSIFIED'), sum(amount)
                FROM report_cash_receipt
                WHERE business_date BETWEEN ? AND ? AND (?::uuid IS NULL OR department_id=?::uuid)
                    AND currency=? AND report_zone=? AND care_episode_type='OUTPATIENT_VISIT'
                    AND classification='SERVICE_PAYMENT'
                GROUP BY 1, 2, 3
                UNION ALL
                SELECT business_date, department_id, 'REFUND', sum(amount)
                FROM report_cash_refund
                WHERE business_date BETWEEN ? AND ? AND (?::uuid IS NULL OR department_id=?::uuid)
                    AND currency=? AND report_zone=? AND care_episode_type='OUTPATIENT_VISIT'
                    AND classification='SERVICE_PAYMENT' AND state='APPLIED'
                GROUP BY 1, 2
                """, (rs, n) -> new Row(rs.getDate("business_date").toLocalDate(),
                        rs.getObject("department_id", UUID.class), rs.getString("kind"), rs.getBigDecimal("amount")),
                Date.valueOf(from), Date.valueOf(to), department, department,
                Date.valueOf(from), Date.valueOf(to), department, department, currency, zone,
                Date.valueOf(from), Date.valueOf(to), department, department, currency, zone);
    }
}
