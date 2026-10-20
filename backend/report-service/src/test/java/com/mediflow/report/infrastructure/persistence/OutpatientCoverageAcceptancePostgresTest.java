package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest.SourceProof;
import com.mediflow.report.application.exception.*;
import com.mediflow.report.application.service.*;
import com.mediflow.report.infrastructure.persistence.adapter.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Synthetic reviewed exports exercise the writer, never certify external production completeness. */
@Testcontainers(disabledWithoutDocker=true)
class OutpatientCoverageAcceptancePostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    private final LocalDate date=LocalDate.of(2026,10,1);
    private final UUID actor=UUID.randomUUID();
    private final ZoneId zone=ZoneId.of("Asia/Bangkok");
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"),ZoneOffset.UTC);

    private record Fixture(JdbcTemplate jdbc, TransactionTemplate tx, OutpatientCoverageAcceptanceService approvals,
            OutpatientReportReadService reader, OutpatientCoverageAcceptancePersistenceAdapter store) { }
    private Fixture fixture() {
        String schema="approval_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        // Explicit upgrade from deployed V27, then validate the additive V28 migration.
        flyway.target("27").load().migrate();
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        String url=PG.getJdbcUrl()+(PG.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var ds=new DriverManagerDataSource(url,PG.getUsername(),PG.getPassword());
        var jdbc=new JdbcTemplate(ds);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var evidence=new OutpatientReportReadPersistenceAdapter(jdbc,mapper);
        var store=new OutpatientCoverageAcceptancePersistenceAdapter(jdbc,mapper);
        return new Fixture(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),
                new OutpatientCoverageAcceptanceService(evidence,store,zone,clock),
                new OutpatientReportReadService(evidence,zone,clock),store);
    }
    private AcceptOutpatientCoverageRequest reviewed(Fixture f, UUID department) {
        var preview=f.tx.execute(s->f.approvals.preview(date,date,department));
        var sources=new TreeMap<String,SourceProof>();
        preview.sources().forEach((source,witness)->sources.put(source,
                new SourceProof("ISOLATED_REVIEW:"+source,"b".repeat(64),witness.rowCount(),witness.fingerprint())));
        return new AcceptOutpatientCoverageRequest(UUID.randomUUID(),date,date,department,zone.getId(),"VND",sources,
                clock.instant().minusSeconds(60),clock.instant().minusSeconds(30),"ISOLATED_REVIEW_ONLY",preview.fingerprint());
    }
    private void lab(Fixture f, UUID department, String occurred) {
        f.jdbc.update("""
                INSERT INTO operational_contribution(contribution_id,event_id,source_type,source_id,source_revision,
                    metric_type,department_id,care_episode_type,care_episode_id,metric_date,occurred_at,numeric_value,fact_fingerprint)
                VALUES (?,?,'LAB_RESULT',?,1,'LAB_TESTS',?,'OUTPATIENT_VISIT',?,?,?::timestamptz,1,?)
                """,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),department,UUID.randomUUID(),Date.valueOf(date),occurred,"a".repeat(64));
    }
    @Test void acceptedZeroIsAuditedAndLateInputInvalidatesWithoutRetryReacceptance() {
        var f=fixture(); var request=reviewed(f,null);
        assertThatThrownBy(()->f.tx.execute(s->f.reader.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        var accepted=f.tx.execute(s->f.approvals.accept(request,actor));
        assertThat(f.tx.execute(s->f.reader.daily(date,date,null)).periods().getFirst().labTests()).isZero();
        assertThat(f.jdbc.queryForObject("SELECT actor_id FROM report_outpatient_coverage_acceptance",UUID.class)).isEqualTo(actor);
        lab(f,UUID.randomUUID(),"2026-10-01T01:00:00Z");
        assertThatThrownBy(()->f.tx.execute(s->f.reader.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        var replay=f.tx.execute(s->f.approvals.accept(request,actor));
        assertThat(replay).isEqualTo(accepted);
        assertThatThrownBy(()->f.tx.execute(s->f.reader.daily(date,date,null))).isInstanceOf(ReportProjectionUnavailableException.class);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isOne();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isOne();
        var fresh=reviewed(f,null); f.tx.execute(s->f.approvals.accept(fresh,actor));
        assertThat(f.tx.execute(s->f.reader.daily(date,date,null)).periods().getFirst().labTests()).isOne();
    }
    @Test void driftAndInvalidZoneFailClosedAndScopeDoesNotLeak() {
        var f=fixture(); UUID department=UUID.randomUUID(); var stale=reviewed(f,department);
        lab(f,department,"2026-10-01T01:00:00Z");
        assertThatThrownBy(()->f.tx.execute(s->f.approvals.accept(stale,actor))).isInstanceOf(OutpatientCoverageConflictException.class);
        var fresh=reviewed(f,department); f.tx.execute(s->f.approvals.accept(fresh,actor));
        assertThat(f.tx.execute(s->f.reader.daily(date,date,department)).periods().getFirst().labTests()).isOne();
        assertThatThrownBy(()->f.tx.execute(s->f.reader.daily(date,date,UUID.randomUUID()))).isInstanceOf(ReportProjectionUnavailableException.class);
        lab(f,department,"2026-10-01T17:00:00Z"); // next local day, wrong stored metric_date
        var invalid=reviewed(f,department);
        assertThatThrownBy(()->f.tx.execute(s->f.approvals.accept(invalid,actor))).isInstanceOf(OutpatientCoverageConflictException.class);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isOne();
    }
    @Test void duplicateConcurrentCommandsHaveOneReceiptAndAuditFailureRollsBackCoverage() throws Exception {
        var f=fixture(); var request=reviewed(f,null);
        f.jdbc.execute("ALTER TABLE report_outpatient_coverage_acceptance ADD CONSTRAINT reject_audit CHECK (false)");
        assertThatThrownBy(()->f.tx.execute(s->f.approvals.accept(request,actor))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isZero();
        f.jdbc.execute("ALTER TABLE report_outpatient_coverage_acceptance DROP CONSTRAINT reject_audit");
        try (var pool=Executors.newFixedThreadPool(2)) {
            var latch=new CountDownLatch(1);
            Callable<UUID> command=()-> { latch.await(); return f.tx.execute(s->f.approvals.accept(request,actor)).coverageId(); };
            var a=pool.submit(command); var b=pool.submit(command); latch.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isOne();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isOne();
    }
    @Test void fractionalMetricsAndPendingRefundCannotReceiveApprovalEvenWithMatchingHashes() {
        var f=fixture(); lab(f,UUID.randomUUID(),"2026-10-01T01:00:00Z");
        f.jdbc.update("UPDATE operational_contribution SET numeric_value=1.5");
        var fractional=reviewed(f,null);
        assertThatThrownBy(()->f.tx.execute(s->f.approvals.accept(fractional,actor))).isInstanceOf(OutpatientCoverageConflictException.class);
        f.jdbc.update("UPDATE operational_contribution SET numeric_value=1");
        f.jdbc.update("""
                INSERT INTO report_cash_refund(refund_transaction_id,original_transaction_id,first_event_id,account_id,
                    patient_id,department_id,care_episode_type,care_episode_id,amount,currency,completed_at_iso,
                    business_date,report_zone,source_fingerprint)
                VALUES (?,?,?,?,?,?,'OUTPATIENT_VISIT',?,10,'VND','2026-10-01T01:00:00Z',DATE '2026-10-01','Asia/Bangkok',?)
                """,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),"d".repeat(64));
        var pending=reviewed(f,null);
        assertThatThrownBy(()->f.tx.execute(s->f.approvals.accept(pending,actor))).isInstanceOf(OutpatientCoverageConflictException.class);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isZero();
    }
}
