package com.mediflow.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.*;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest.SourceProof;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.Snapshot;
import com.mediflow.report.infrastructure.config.*;
import com.mediflow.report.infrastructure.persistence.adapter.*;
import com.mediflow.report.web.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Actual Report HTTP + signed JWT + proxied transaction + PostgreSQL; no Gateway/source acceptance claim. */
@Testcontainers(disabledWithoutDocker=true)
@ActiveProfiles({"outpatient-mvp","coverage-http-test"})
@SpringBootTest(classes=OutpatientCoverageHttpPostgresTest.Harness.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes","mediflow.features.care-finance-v2=true",
                "mediflow.report.outpatient-read.enabled=true","mediflow.report.outpatient-coverage-acceptance.enabled=true",
                "eureka.client.enabled=false","spring.cloud.discovery.enabled=false","spring.cloud.service-registry.auto-registration.enabled=false"})
class OutpatientCoverageHttpPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Configuration(proxyBeanMethods=false)
    @Profile("coverage-http-test")
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,RabbitAutoConfiguration.class,FlywayAutoConfiguration.class})
    @EnableTransactionManagement
    @Import({SecurityConfig.class,GlobalExceptionHandler.class,OutpatientCoverageAcceptanceController.class,
            OutpatientReportController.class,ReportOutpatientReadConfiguration.class,
            ReportOutpatientCoverageAcceptanceConfiguration.class,OutpatientReportReadPersistenceAdapter.class,
            OutpatientCoverageAcceptancePersistenceAdapter.class})
    static class Harness {
        @Bean DataSource reportDataSource() { return new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()); }
        @Bean(initMethod="migrate") Flyway reportFlyway(DataSource ds) {
            return Flyway.configure().dataSource(ds).locations("classpath:mediflow/report/db/migration").load();
        }
        @Bean @DependsOn("reportFlyway") JdbcTemplate reportJdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager reportTransactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ZoneId reportZoneId() { return ZoneId.of("Asia/Bangkok"); }
        @Bean Clock reportClock() { return Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final UUID actor=UUID.randomUUID();
    private String bearer(String role) {
        return "Bearer "+Jwts.builder().subject(actor.toString()).claim("type","access").claim("role",role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
    private ResponseEntity<JsonNode> call(HttpMethod method,String path,String role,Object body) {
        var headers=new HttpHeaders(); headers.set("Authorization",bearer(role)); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    @Test void signedHttpApprovalEnablesDailyReadAndRetryIsOneEffect() throws Exception {
        String root="/api/v1/reports/outpatient";
        String query="?fromDate=2026-10-01&toDate=2026-10-01";
        assertThat(call(HttpMethod.GET,root+"/daily"+query,"ADMIN",null).getStatusCode().value()).isEqualTo(404);
        assertThat(call(HttpMethod.GET,root+"/coverage/preview"+query,"MANAGER",null).getStatusCode().value()).isEqualTo(403);
        var response=call(HttpMethod.GET,root+"/coverage/preview"+query,"ADMIN",null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var snapshot=mapper.treeToValue(response.getBody().get("data"),Snapshot.class);
        var proofs=new TreeMap<String,SourceProof>();
        snapshot.sources().forEach((source,w)->proofs.put(source,new SourceProof("SYNTHETIC_REVIEW:"+source,"b".repeat(64),w.rowCount(),w.fingerprint())));
        var date=LocalDate.of(2026,10,1);
        var request=new AcceptOutpatientCoverageRequest(UUID.randomUUID(),date,date,null,"Asia/Bangkok","VND",proofs,
                Instant.parse("2026-10-02T00:00:00Z"),Instant.parse("2026-10-02T01:00:00Z"),"SYNTHETIC_APPROVAL_ONLY",snapshot.fingerprint());
        assertThat(call(HttpMethod.POST,root+"/coverage/acceptances","MANAGER",request).getStatusCode().value()).isEqualTo(403);
        var approved=call(HttpMethod.POST,root+"/coverage/acceptances","ADMIN",request);
        assertThat(approved.getStatusCode().value()).isEqualTo(200);
        assertThat(approved.getBody().at("/data/acceptedBy").asText()).isEqualTo(actor.toString());
        var retry=call(HttpMethod.POST,root+"/coverage/acceptances","ADMIN",request);
        assertThat(retry.getBody().get("data")).isEqualTo(approved.getBody().get("data"));
        for(String role:List.of("ADMIN","MANAGER")) {
            var report=call(HttpMethod.GET,root+"/daily"+query,role,null);
            assertThat(report.getStatusCode().value()).isEqualTo(200);
            assertThat(report.getBody().at("/data/covered").asBoolean()).isTrue();
        }
        assertThat(call(HttpMethod.GET,root+"/daily"+query,"DOCTOR",null).getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_outpatient_coverage_acceptance",Integer.class)).isOne();
    }
}
