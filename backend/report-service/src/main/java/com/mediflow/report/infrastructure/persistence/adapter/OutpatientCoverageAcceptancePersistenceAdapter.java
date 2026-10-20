package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.port.out.OutpatientCoverageAcceptancePort;
import com.mediflow.report.domain.model.OutpatientCoverage;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.outpatient-read.enabled",
        "mediflow.report.outpatient-coverage-acceptance.enabled"}, havingValue="true")
@Transactional(transactionManager="reportTransactionManager", propagation=Propagation.MANDATORY)
public class OutpatientCoverageAcceptancePersistenceAdapter implements OutpatientCoverageAcceptancePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OutpatientCoverageAcceptancePersistenceAdapter(@Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc=jdbc; this.mapper=mapper;
    }
    @Override public void lock() {
        jdbc.execute("SET LOCAL lock_timeout = '2s'");
        jdbc.execute("SET LOCAL statement_timeout = '10s'");
        jdbc.execute("LOCK TABLE report_outpatient_coverage IN SHARE ROW EXCLUSIVE MODE");
        jdbc.execute("LOCK TABLE operational_contribution, report_cash_receipt, report_cash_refund IN SHARE MODE");
    }
    @Override public Optional<Saved> find(UUID requestId) {
        return jdbc.query("SELECT * FROM report_outpatient_coverage_acceptance WHERE request_id=?", (rs,n) -> {
            try {
                return new Saved(mapper.readValue(rs.getString("request_payload"), AcceptOutpatientCoverageRequest.class),
                        rs.getObject("actor_id",UUID.class), mapper.readValue(rs.getString("coverage_payload"),OutpatientCoverage.class));
            } catch (JsonProcessingException invalid) { throw new IllegalStateException("Invalid coverage audit receipt",invalid); }
        },requestId).stream().findFirst();
    }
    @Override public void save(AcceptOutpatientCoverageRequest request, UUID actor, OutpatientCoverage coverage) {
        jdbc.update("""
                INSERT INTO report_outpatient_coverage(coverage_id,covered_from,covered_to,department_id,zone_id,currency,
                    source_references,source_observed_at,reconciled_at,accepted_at,accepted_by,acceptance_reference,local_evidence_fingerprint)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)
                """,coverage.coverageId(),Date.valueOf(coverage.from()),Date.valueOf(coverage.to()),coverage.departmentId(),
                coverage.zoneId(),coverage.currency(),json(coverage.sourceReferences()),Timestamp.from(coverage.sourceObservedAt()),
                Timestamp.from(coverage.reconciledAt()),Timestamp.from(coverage.acceptedAt()),actor,coverage.acceptanceReference(),coverage.evidenceFingerprint());
        jdbc.update("""
                INSERT INTO report_outpatient_coverage_acceptance(request_id,coverage_id,actor_id,request_payload,coverage_payload)
                VALUES (?,?,?,?::jsonb,?::jsonb)
                """,request.requestId(),coverage.coverageId(),actor,json(request),json(coverage));
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw new IllegalStateException("Cannot encode coverage audit receipt",invalid); }
    }
}
