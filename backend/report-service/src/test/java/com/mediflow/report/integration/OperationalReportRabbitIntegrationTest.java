package com.mediflow.report.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.port.in.ReplayOperationalProjectionUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Approved producer bytes, real transactional kernels, private Rabbit intake and finite replay. */
@Testcontainers
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=report-operational-integration-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.report.operational-consumer.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple"
})
class OperationalReportRabbitIntegrationTest {
    private static final String QUEUE = "report.operational-v2.q", DLQ = "report.operational-v2.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ReplayOperationalProjectionUseCase replay;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void cleanAndStartOwnListener() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE operational_source_snapshot, operational_delivery, operational_event_journal, operational_contribution, daily_operational_report, report_admission_target, report_admission_delivery CASCADE");
        listener().start();
    }
    @AfterEach void stopOwnListener() { listener().stop(); }

    @ParameterizedTest
    @CsvSource({
            "medicalrecord.completed,clinical-service,contracts/medicalrecord.completed.v1.json,1,completed_visits,1",
            "lab.result.created,lab-service,contracts/lab.result.created.v1.json,1,lab_tests,1",
            "lab.result.created,lab-service,contracts/lab.result.created.admission.v1.json,1,lab_tests,1",
            "prescription.filled,pharmacy-service,contracts/care-finance-v1/prescription.filled.v1.json,2,dispensed_prescriptions,1",
            "surgery.completed,surgery-service,contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json,2,surgeries_completed,1",
            "surgery.cancelled,surgery-service,contracts/surgery-outcomes-v1/surgery.cancelled.admission.v1.json,1,surgeries_cancelled,1"
    })
    void actualProducerBytes_commitBothScopesAndMinimalReplayJournal(String key, String service, String file,
            int metrics, String column, long total) throws Exception {
        byte[] body = fixture(service, file); send(key, body); drain("operational_event_journal", 1);
        assertThat(count("operational_contribution")).isEqualTo(metrics);
        assertThat(jdbc.queryForList("SELECT " + column + " FROM daily_operational_report", Long.class))
                .hasSize(2).containsOnly(total);
        assertThat(jdbc.queryForList("SELECT event_snapshot::text || contribution_snapshot::text FROM operational_event_journal", String.class))
                .allSatisfy(snapshot -> assertThat(snapshot).doesNotContain("patientId", "complicationsSummary", "dosage", "drugName", "performedItems"));
        assertNoLegacyOrPublicationEffects();
    }

    @Test void duplicateAndNewDelivery_sameBusinessResultCountOnceAndCanRebuild() throws Exception {
        byte[] body = surgery(); send("surgery.completed", body); send("surgery.completed", body);
        drain("operational_event_journal", 1);
        var root = (ObjectNode) mapper.readTree(body); root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("surgery.completed", mapper.writeValueAsBytes(root)); drain("operational_event_journal", 2);
        assertThat(count("operational_contribution")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT surgeries_completed FROM daily_operational_report", Long.class)).containsOnly(1L);
        var progress = replay.start();
        for (int attempt = 0; attempt < 3 && progress.status() == com.mediflow.report.application.dto.response.OperationalReplayProgress.Status.BUILDING; attempt++)
            progress = replay.advance(progress.generationId(), 1);
        assertThat(progress.status()).isEqualTo(com.mediflow.report.application.dto.response.OperationalReplayProgress.Status.VERIFIED);
        assertNoLegacyOrPublicationEffects();
    }

    @Test void changedNonMetricEvidence_conflictsAndRollsBackNewClaim() throws Exception {
        byte[] body = surgery(); send("surgery.completed", body); drain("operational_event_journal", 1);
        var root = (ObjectNode) mapper.readTree(body); root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.get("payload")).put("complicationsSummary", "changed unretained clinical evidence");
        byte[] conflicting = mapper.writeValueAsBytes(root);
        listener().start(); send("surgery.completed", conflicting); retainedDlq(conflicting);
        assertThat(count("operational_event_journal")).isOne();
        assertThat(count("operational_delivery")).isEqualTo(2); // Original result has two metrics.
        assertThat(count("operational_contribution")).isEqualTo(2);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"version", "source", "revision"})
    void unsupportedContract_rejectsBeforeAnyEffect(String defect) throws Exception {
        var root = (ObjectNode) mapper.readTree(surgery());
        if (defect.equals("version")) root.put("version", 2);
        else if (defect.equals("source")) ((ObjectNode) root.get("payload")).remove("resultId");
        else ((ObjectNode) root.get("payload")).put("sourceRevision", 2);
        byte[] invalid = mapper.writeValueAsBytes(root); send("surgery.completed", invalid); retainedDlq(invalid);
        assertThat(count("operational_event_journal")).isZero();
        assertThat(count("operational_delivery")).isZero();
        assertThat(count("operational_contribution")).isZero();
        assertThat(count("daily_operational_report")).isZero();
    }

    @Test void secondScopeSqlFailure_rollsBackThenRetainedBytesReplayOnce() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_hospital_scope() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.department_id IS NULL THEN RAISE EXCEPTION 'injected second-scope failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER report_scope_failure BEFORE INSERT OR UPDATE ON daily_operational_report FOR EACH ROW EXECUTE FUNCTION reject_hospital_scope()");
        byte[] body = surgery(), retained;
        try {
            send("surgery.completed", body); retained = retainedDlq(body);
            assertThat(count("operational_event_journal")).isZero();
            assertThat(count("operational_source_snapshot")).isZero();
            assertThat(count("operational_delivery")).isZero();
            assertThat(count("operational_contribution")).isZero();
            assertThat(count("daily_operational_report")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER report_scope_failure ON daily_operational_report");
            jdbc.execute("DROP FUNCTION reject_hospital_scope()");
        }
        listener().start(); send("surgery.completed", retained); drain("operational_event_journal", 1);
        assertThat(jdbc.queryForList("SELECT surgeries_completed FROM daily_operational_report", Long.class)).hasSize(2).containsOnly(1L);
        assertNoLegacyOrPublicationEffects();
    }

    @Test void admissionCloseBeforeStart_restartPairsEvidenceAndCountsOnlyAdmissionNotDischarge() throws Exception {
        send("admission.closed", admission("admission.closed")); drain("report_admission_delivery", 1);
        assertThat(jdbc.queryForList("SELECT fact_type FROM report_admission_fact", String.class)).containsExactly("CLOSED");
        listener().start(); send("admission.started", admission("admission.started")); drain("report_admission_delivery", 2);
        assertThat(jdbc.queryForList("SELECT fact_type FROM report_admission_fact", String.class)).containsExactlyInAnyOrder("STARTED", "CLOSED");
        assertThat(count("operational_contribution")).isOne();
        assertThat(jdbc.queryForList("SELECT admissions FROM daily_operational_report", Long.class)).hasSize(2).containsOnly(1L);
        assertThat(jdbc.queryForList("SELECT discharges, inpatient_days FROM daily_operational_report"))
                .allSatisfy(row -> { assertThat(row.get("discharges")).isEqualTo(0L); assertThat(row.get("inpatient_days")).isEqualTo(0L); });
        assertNoLegacyOrPublicationEffects();
    }

    @Test void wrongAdmissionPatient_rollsBackClaimAndPreservesPendingClose() throws Exception {
        send("admission.closed", admission("admission.closed")); drain("report_admission_delivery", 1);
        var root = (ObjectNode) mapper.readTree(admission("admission.started"));
        ((ObjectNode) root.get("payload")).put("patientId", UUID.randomUUID().toString());
        byte[] invalid = mapper.writeValueAsBytes(root);
        listener().start(); send("admission.started", invalid); retainedDlq(invalid);
        assertThat(count("report_admission_delivery")).isOne();
        assertThat(jdbc.queryForList("SELECT fact_type FROM report_admission_fact", String.class)).containsExactly("CLOSED");
    }

    private void assertNoLegacyOrPublicationEffects() {
        for (String table : new String[]{"processed_event", "daily_visit_report", "monthly_revenue_report", "drug_statistic", "operational_report_publication", "financial_contribution", "daily_financial_report"})
            assertThat(count(table)).as(table).isZero();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("reportOperationalFacts"); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private void drain(String table, long expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(table)).isEqualTo(expected));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private byte[] retainedDlq(byte[] expected) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());
        listener().stop(); var retained = rabbit.receive(DLQ, 5000); assertThat(retained).isNotNull();
        assertThat(retained.getBody()).isEqualTo(expected); return retained.getBody();
    }
    private void send(String key, byte[] body) throws Exception {
        rabbit.invoke(operations -> { operations.send("mediflow.events", key, new Message(body, new MessageProperties()));
            operations.waitForConfirmsOrDie(10000); return null; });
    }
    private byte[] surgery() throws Exception { return fixture("surgery-service", "contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json"); }
    private byte[] admission(String key) throws Exception { return fixture("inpatient-service", "contracts/" + key + ".v1.json"); }
    private byte[] fixture(String service, String file) throws Exception { return Files.readAllBytes(Path.of("../" + service + "/src/test/resources/" + file)); }
}
