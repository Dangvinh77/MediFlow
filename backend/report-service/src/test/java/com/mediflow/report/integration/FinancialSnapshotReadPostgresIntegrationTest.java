package com.mediflow.report.integration;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import com.mediflow.report.infrastructure.persistence.adapter.FinancialSnapshotReadPersistenceAdapter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic read-boundary seeds test queries, NOT Billing fixture acceptance or production publication. */
@Testcontainers
@AutoConfigureMockMvc
@Import(FinancialSnapshotReadPostgresIntegrationTest.TestClock.class)
@SpringBootTest(properties={"eureka.client.enabled=false","mediflow.jwt.secret=finance-postgres-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class FinancialSnapshotReadPostgresIntegrationTest {
    @TestConfiguration(proxyBeanMethods=false)
    static class TestClock {
        @Bean Clock testFinancialReadClock() { return Clock.fixed(Instant.parse("2027-01-02T00:00:00Z"),ZoneOffset.UTC); }
    }
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @Autowired ReadFinancialSnapshotUseCase reader;
    private final LocalDate day=LocalDate.of(2026,10,10);
    private final Instant seen=Instant.parse("2027-01-01T00:00:00Z");
    @BeforeEach void clean() { jdbc.execute("TRUNCATE financial_report_generation CASCADE"); }
    private UUID generation(LocalDate from,LocalDate to,String currency,String zone) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO financial_report_generation(generation_id,status,snapshot_version,projector_version,
                  covered_from,covered_to,currency,report_zone,source_observed_at,source_inputs,applied_inputs,
                  unresolved_inputs,manifest_fingerprint) VALUES (?,'BUILDING',1,1,?,?,?,?,?,0,0,0,?)
                """,id,Date.valueOf(from),Date.valueOf(to),currency,zone,Timestamp.from(seen),"a".repeat(64));
        return id;
    }
    private void row(UUID id,LocalDate date,UUID department,String cash,String liability,String earned,String refunds,String debt) {
        jdbc.update("""
                INSERT INTO financial_report_day VALUES (?,?,?,?,?,?,?,?)
                """,id,Date.valueOf(date),department,new BigDecimal(cash),new BigDecimal(liability),
                new BigDecimal(earned),new BigDecimal(refunds),new BigDecimal(debt));
    }
    private void verifyAndTestPublish(UUID id,String currency,String zone) {
        jdbc.update("UPDATE financial_report_generation SET status='VERIFIED',reconciled_at=?,aggregate_fingerprint=? WHERE generation_id=?",
                Timestamp.from(seen),"b".repeat(64),id);
        jdbc.update("""
                INSERT INTO financial_report_publication VALUES (?,?,?,?,?)
                ON CONFLICT(currency,report_zone) DO UPDATE SET generation_id=EXCLUDED.generation_id,
                  acceptance_reference=EXCLUDED.acceptance_reference,published_at=EXCLUDED.published_at
                """,currency,zone,id,"SYNTHETIC-READ-BOUNDARY-TEST-ONLY",Timestamp.from(seen));
    }
    @Test void api_emptyMigrationAndExistingCashEvidence_neverPublishesOrInventsFiveMetrics() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication",Integer.class)).isZero();
        mvc.perform(get("/api/v1/reports/financial/daily").param("from",day.toString()).param("to",day.toString())
                        .with(user("reader").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_generation",Integer.class)).isZero();
    }
    @Test void api_completeDay_exactNullHospitalAndDepartmentScopesPreserveFiveIndependentAmounts() throws Exception {
        UUID id=generation(day,day,"VND","Asia/Bangkok"),department=UUID.randomUUID();
        row(id,day,null,"100","40","60","15","25"); row(id,day,department,"60","20","36","9","15");
        verifyAndTestPublish(id,"VND","Asia/Bangkok");
        mvc.perform(get("/api/v1/reports/financial/daily").param("from",day.toString()).param("to",day.toString())
                        .with(user("reader").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.periods[0].cashReceived").value(100))
                .andExpect(jsonPath("$.data.periods[0].earnedRevenue").value(60))
                .andExpect(jsonPath("$.data.periods[0].depositLiability").value(40))
                .andExpect(jsonPath("$.data.periods[0].refunds").value(15))
                .andExpect(jsonPath("$.data.periods[0].outstandingReceivable").value(25));
        assertThat(reader.daily(day,day,department).periods().getFirst().cashReceived()).isEqualByComparingTo("60");
        assertThatThrownBy(() -> reader.daily(day,day,UUID.randomUUID()))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }
    @Test void api_fullYearMonthly_exactFlowSumsAndClosingStocks() throws Exception {
        LocalDate start=LocalDate.of(2026,1,1),end=LocalDate.of(2026,12,31);
        UUID id=generation(start,end,"VND","Asia/Bangkok");
        for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1)) row(id,date,null,"1","10","2","0.25","20");
        verifyAndTestPublish(id,"VND","Asia/Bangkok");
        mvc.perform(get("/api/v1/reports/financial/monthly").param("year","2026").with(user("reader").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.periods.length()").value(12))
                .andExpect(jsonPath("$.data.periods[0].cashReceived").value(31))
                .andExpect(jsonPath("$.data.periods[0].depositLiability").value(10))
                .andExpect(jsonPath("$.data.periods[0].outstandingReceivable").value(20))
                .andExpect(jsonPath("$.data.periods[0].refunds").value(7.75));
    }
    @Test void api_missingOneDay_notZeroFilledEvenWithVerifiedAcceptedPointer() {
        UUID id=generation(day,day.plusDays(1),"VND","Asia/Bangkok");
        row(id,day,null,"0","0","0","0","0"); verifyAndTestPublish(id,"VND","Asia/Bangkok");
        assertThatThrownBy(() -> reader.daily(day,day.plusDays(1),null))
                .isInstanceOf(com.mediflow.report.application.exception.ReportProjectionUnavailableException.class);
    }
    @Test void publication_buildingOrFailedGeneration_isNeverReadable() {
        UUID id=generation(day,day,"VND","Asia/Bangkok"); row(id,day,null,"1","0","1","0","0");
        jdbc.update("INSERT INTO financial_report_publication VALUES (?,?,?,?,?)", "VND","Asia/Bangkok",id,"test-only",Timestamp.from(seen));
        var adapter=new FinancialSnapshotReadPersistenceAdapter(jdbc);
        assertThat(adapter.publication("VND","Asia/Bangkok")).isEmpty();
        jdbc.update("UPDATE financial_report_generation SET status='FAILED' WHERE generation_id=?",id);
        assertThat(adapter.publication("VND","Asia/Bangkok")).isEmpty();
    }
    @Test void publication_verifiedSnapshotAndMetadata_areImmutableInDatabase() {
        UUID id=generation(day,day,"VND","Asia/Bangkok"); row(id,day,null,"1","0","1","0","0");
        verifyAndTestPublish(id,"VND","Asia/Bangkok");
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_report_day SET cash_received=2 WHERE generation_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> row(id,day.plusDays(1),null,"1","0","1","0","0")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM financial_report_day WHERE generation_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE financial_report_generation SET status='BUILDING' WHERE generation_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(reader.daily(day,day,null).periods().getFirst().cashReceived()).isEqualByComparingTo("1");
    }
    @Test void publication_mismatchedCurrencyOrZonePointer_cannotMixFinancialUnits() {
        UUID id=generation(day,day,"USD","UTC"); row(id,day,null,"100","0","0","0","0");
        verifyAndTestPublish(id,"USD","UTC");
        jdbc.update("INSERT INTO financial_report_publication VALUES (?,?,?,?,?)","VND","Asia/Bangkok",id,"test-only",Timestamp.from(seen));
        assertThat(new FinancialSnapshotReadPersistenceAdapter(jdbc).publication("VND","Asia/Bangkok")).isEmpty();
    }
    @Test void publication_unsupportedProjectorOrSnapshotVersion_isUnavailable() {
        UUID id=generation(day,day,"VND","Asia/Bangkok"); row(id,day,null,"1","0","1","0","0");
        jdbc.update("UPDATE financial_report_generation SET projector_version=2,snapshot_version=2 WHERE generation_id=?",id);
        verifyAndTestPublish(id,"VND","Asia/Bangkok");
        assertThat(new FinancialSnapshotReadPersistenceAdapter(jdbc).publication("VND","Asia/Bangkok")).isEmpty();
    }
    @Test void schema_pendingOrUnappliedInputs_cannotBePromotedToVerified() {
        UUID id=generation(day,day,"VND","Asia/Bangkok");
        jdbc.update("UPDATE financial_report_generation SET source_inputs=1,unresolved_inputs=1 WHERE generation_id=?",id);
        assertThatThrownBy(() -> verifyAndTestPublish(id,"VND","Asia/Bangkok")).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_publication",Integer.class)).isZero();
    }
    @Test void publication_pointerSwitchAndRollback_selectsOnlyOneImmutableGeneration() {
        UUID first=generation(day,day,"VND","Asia/Bangkok"); row(first,day,null,"60","60","0","0","40"); verifyAndTestPublish(first,"VND","Asia/Bangkok");
        UUID second=generation(day,day,"VND","Asia/Bangkok"); row(second,day,null,"100","0","100","15","15"); verifyAndTestPublish(second,"VND","Asia/Bangkok");
        assertThat(reader.daily(day,day,null).generationId()).isEqualTo(second);
        jdbc.update("UPDATE financial_report_publication SET generation_id=?",first);
        var result=reader.daily(day,day,null); assertThat(result.generationId()).isEqualTo(first);
        assertThat(result.periods().getFirst().depositLiability()).isEqualByComparingTo("60");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financial_report_generation",Integer.class)).isEqualTo(2);
        // This is storage/read behavior only; there is still no approved production publication writer.
    }
}
