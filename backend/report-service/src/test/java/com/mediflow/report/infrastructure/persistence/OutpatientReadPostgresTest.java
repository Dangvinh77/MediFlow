package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.service.OutpatientReportReadService;
import com.mediflow.report.infrastructure.persistence.adapter.OutpatientReportReadPersistenceAdapter;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Isolated test exports only; not production history/coverage or owner acceptance. */
@Testcontainers(disabledWithoutDocker=true)
class OutpatientReadPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void exactDepartmentZoneBoundaryAndWholeCoverageWitnessFailClosed() throws Exception {
        String schema="scope_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        String url=PG.getJdbcUrl()+(PG.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var ds=new DriverManagerDataSource(url,PG.getUsername(),PG.getPassword());
        var jdbc=new JdbcTemplate(ds); var mapper=new ObjectMapper();
        var adapter=new OutpatientReportReadPersistenceAdapter(jdbc,mapper);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var zone=ZoneId.of("Asia/Bangkok"); var first=LocalDate.of(2026,10,1); var second=first.plusDays(1);
        var reader=new OutpatientReportReadService(adapter,zone,Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),zone));
        UUID department=UUID.randomUUID(), foreignDepartment=UUID.randomUUID(), coverage=UUID.randomUUID();
        var refs=new HashMap<String,String>();
        com.mediflow.report.domain.model.OutpatientCoverage.REQUIRED_SOURCES.forEach(k->refs.put(k,"ISOLATED_TEST_EXPORT:"+k));
        jdbc.update("""
                INSERT INTO report_outpatient_coverage(coverage_id,covered_from,covered_to,department_id,zone_id,currency,
                    source_references,source_observed_at,reconciled_at,accepted_at,accepted_by,acceptance_reference,local_evidence_fingerprint)
                VALUES (?,?,?,?,'Asia/Bangkok','VND',?::jsonb,TIMESTAMPTZ '2026-10-13T00:00:00Z',
                    TIMESTAMPTZ '2026-10-13T00:00:00Z',TIMESTAMPTZ '2026-10-13T00:00:00Z',?,'ISOLATED_TEST_ONLY',?)
                """,coverage,java.sql.Date.valueOf(first),java.sql.Date.valueOf(second),department,
                mapper.writeValueAsString(refs),UUID.randomUUID(),OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of()));
        assertThat(tx.execute(s->reader.daily(first,second,department)).periods()).hasSize(2);
        assertThatThrownBy(()->tx.execute(s->reader.daily(first,second,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        assertThatThrownBy(()->tx.execute(s->reader.daily(first,second,foreignDepartment))).isInstanceOf(ReportProjectionUnavailableException.class);
        assertThatThrownBy(()->tx.execute(s->reader.daily(first,second.plusDays(1),department))).isInstanceOf(ReportProjectionUnavailableException.class);
        // 17:00 UTC is exactly midnight of the NEXT business day in Bangkok.
        jdbc.update("""
                INSERT INTO operational_contribution(contribution_id,event_id,source_type,source_id,source_revision,
                    metric_type,department_id,care_episode_type,care_episode_id,metric_date,occurred_at,numeric_value,fact_fingerprint)
                VALUES (?,?,'LAB_RESULT',?,1,'LAB_TESTS',?,'OUTPATIENT_VISIT',?,DATE '2026-10-02',
                    TIMESTAMPTZ '2026-10-01T17:00:00Z',1,?)
                """,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),department,UUID.randomUUID(),"a".repeat(64));
        assertThatThrownBy(()->tx.execute(s->reader.daily(first,first,department))).isInstanceOf(ReportProjectionUnavailableException.class);
        String witness=tx.execute(s->adapter.load(first,second,department,zone.getId(),"VND").orElseThrow().currentFingerprint());
        jdbc.update("UPDATE report_outpatient_coverage SET local_evidence_fingerprint=? WHERE coverage_id=?",witness,coverage);
        var result=tx.execute(s->reader.daily(first,second,department));
        assertThat(result.periods().getFirst().labTests()).isZero();
        assertThat(result.periods().getLast().labTests()).isOne();
        assertThat(tx.execute(s->reader.daily(first,first,department)).periods().getFirst().labTests()).isZero();
        var utcReader=new OutpatientReportReadService(adapter,ZoneOffset.UTC,Clock.systemUTC());
        assertThatThrownBy(()->tx.execute(s->utcReader.daily(first,second,department))).isInstanceOf(ReportProjectionUnavailableException.class);
        // Even a subrange request must invalidate when evidence elsewhere in its accepted window drifts.
        jdbc.update("UPDATE operational_contribution SET numeric_value=2");
        assertThatThrownBy(()->tx.execute(s->reader.daily(first,first,department))).isInstanceOf(ReportProjectionUnavailableException.class);
    }
    @Test void freshSchemaCoverageZeroLateCashAndEarlyRefundAreCoherent()throws Exception{
        String schema="outpatient_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        String jdbcUrl=PG.getJdbcUrl()+(PG.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var ds=new DriverManagerDataSource(jdbcUrl,PG.getUsername(),PG.getPassword());
        var jdbc=new JdbcTemplate(ds);var adapter=new OutpatientReportReadPersistenceAdapter(jdbc,new ObjectMapper());
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var date=LocalDate.of(2026,10,1);var zone=ZoneId.of("Asia/Bangkok");
        var service=new OutpatientReportReadService(adapter,zone,Clock.fixed(Instant.parse("2026-10-14T01:00:00Z"),zone));
        assertThatThrownBy(()->tx.execute(s->service.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        var refs=new HashMap<String,String>();com.mediflow.report.domain.model.OutpatientCoverage.REQUIRED_SOURCES.forEach(k->refs.put(k,"TEST_EXPORT_ONLY:"+k));
        UUID coverage=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_outpatient_coverage(coverage_id,covered_from,covered_to,zone_id,currency,source_references,
                    source_observed_at,reconciled_at,accepted_at,accepted_by,acceptance_reference,local_evidence_fingerprint)
                VALUES (?,DATE '2026-10-01',DATE '2026-10-01','Asia/Bangkok','VND',?::jsonb,
                    TIMESTAMPTZ '2026-10-13T00:00:00Z',TIMESTAMPTZ '2026-10-13T00:00:00Z',TIMESTAMPTZ '2026-10-13T00:00:00Z',?,'ISOLATED_TEST_ONLY',?)
                """,coverage,new ObjectMapper().writeValueAsString(refs),UUID.randomUUID(),
                OutpatientReportReadPersistenceAdapter.evidenceFingerprint(List.of()));
        assertThat(tx.execute(s->service.daily(date,date,null)).periods().getFirst().cashReceived()).isZero();
        UUID original=UUID.randomUUID(),account=UUID.randomUUID(),patient=UUID.randomUUID(),department=UUID.randomUUID(),episode=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO report_cash_receipt(transaction_id,first_event_id,payment_request_id,account_id,patient_id,department_id,
                    care_episode_type,care_episode_id,classification,amount,currency,payment_method,completed_at,completed_at_iso,
                    business_date,report_zone,payload_fingerprint,fact_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,'SERVICE_PAYMENT',100,'VND','CASH',TIMESTAMPTZ '2026-10-01T01:00:00Z',
                    '2026-10-01T01:00:00Z',DATE '2026-10-01','Asia/Bangkok',?,?)
                """,original,UUID.randomUUID(),UUID.randomUUID(),account,patient,department,episode,"a".repeat(64),"b".repeat(64));
        assertThatThrownBy(()->tx.execute(s->service.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        String hash=tx.execute(s->adapter.load(date,date,null,zone.getId(),"VND").orElseThrow().currentFingerprint());
        jdbc.update("UPDATE report_outpatient_coverage SET local_evidence_fingerprint=? WHERE coverage_id=?",hash,coverage);
        assertThat(tx.execute(s->service.daily(date,date,null)).periods().getFirst().cashReceived()).isEqualByComparingTo("100.00");
        jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,account_id,patient_id,department_id,
                    care_episode_type,care_episode_id,amount,currency,completed_at_iso,business_date,report_zone,source_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,20,'VND','2026-10-01T02:00:00Z',DATE '2026-10-01','Asia/Bangkok',?)
                """,UUID.randomUUID(),original,UUID.randomUUID(),account,patient,department,episode,"c".repeat(64));
        hash=tx.execute(s->adapter.load(date,date,null,zone.getId(),"VND").orElseThrow().currentFingerprint());
        jdbc.update("UPDATE report_outpatient_coverage SET local_evidence_fingerprint=? WHERE coverage_id=?",hash,coverage);
        assertThatThrownBy(()->tx.execute(s->service.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        // Test-owned evidence transition only: producer/receiver recovery is proved separately by the Rabbit suite.
        jdbc.update("""
                UPDATE report_cash_refund SET state='APPLIED',classification='SERVICE_PAYMENT',
                    original_business_date=DATE '2026-10-01',applied_at=now()
                """);
        hash=tx.execute(s->adapter.load(date,date,null,zone.getId(),"VND").orElseThrow().currentFingerprint());
        jdbc.update("UPDATE report_outpatient_coverage SET local_evidence_fingerprint=? WHERE coverage_id=?",hash,coverage);
        var totals=tx.execute(s->service.daily(date,date,null)).periods().getFirst();
        assertThat(totals.cashReceived()).isEqualByComparingTo("100.00");
        assertThat(totals.refunds()).isEqualByComparingTo("20.00");
        assertThat(totals.netCash()).isEqualByComparingTo("80.00");
    }
}
