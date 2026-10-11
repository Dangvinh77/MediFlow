package com.mediflow.report.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.application.port.in.RebuildFinancialSourcesUseCase;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties={"eureka.client.enabled=false","mediflow.jwt.secret=final-report-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true","mediflow.report.cash-receipt-consumer.enabled=true","mediflow.report.cash-refund-consumer.enabled=true",
        "mediflow.report.final-recognition-consumer.enabled=true","mediflow.report.final-recognition-consumer.recovery-initial-delay-ms=3600000",
        "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000","spring.rabbitmq.listener.simple.auto-startup=false"})
class FinalSettlementRecognitionRabbitIntegrationTest {
    private static final String QUEUE="report.final-recognition-v2.q",DLQ="report.final-recognition-v2.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;@Autowired RabbitTemplate rabbit;@Autowired RabbitAdmin admin;@Autowired RabbitListenerEndpointRegistry registry;
    @Autowired ApplyFinalSettlementRecognitionUseCase finals;
    @Autowired RebuildFinancialSourcesUseCase rebuild;
    @BeforeEach void clean() {
        registry.stop();for(String queue:new String[]{QUEUE,DLQ,"report.cash-receipts-v2.q","report.cash-refunds-v2.q"})admin.purgeQueue(queue);
        jdbc.execute("TRUNCATE financial_source_rebuild,report_final_recognition,report_final_recognition_daily,report_cash_receipt,report_gross_cash_daily,report_cash_refund,report_refund_cash_daily CASCADE");registry.start();
    }
    @AfterEach void stop(){registry.stop();}
    @Test void delayedOriginalBrokerDeliveryAndListenerRestartRecoverDurablePendingOnlyOnce()throws Exception {
        var bytes=fixture("settlement-recognition-completed");send(bytes);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(state()).isEqualTo("PENDING"));
        send(fixture("refund-final-recognition-deposit"));send(fixture("payment-final-recognition-deposit"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'",Integer.class)).isOne());
        registry.stop();finals.recover(UUID.fromString("00000000-0000-4000-8000-000000000015"));registry.start();send(bytes);
        var duplicate=(ObjectNode)new ObjectMapper().readTree(bytes);duplicate.put("eventId",UUID.randomUUID().toString());send(new ObjectMapper().writeValueAsBytes(duplicate));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(count("report_final_recognition_delivery")).isEqualTo(2));registry.stop();
        assertThat(earned()).isEqualByComparingTo("100");assertThat(count("report_final_recognition")).isOne();assertThat(count("report_final_deposit_release")).isOne();
        assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isZero();assertThat(count("financial_report_publication")).isZero();
    }
    @Test void databaseFailureDeadLettersOriginalBytesAndReplaysWithAtomicEarnedRelease()throws Exception {
        cash();var bytes=fixture("settlement-recognition-completed");jdbc.execute("ALTER TABLE report_final_recognition_daily ADD CONSTRAINT test_final_broker_failure CHECK(deposit_released=0)");
        try {
            send(bytes);dead();for(String table:new String[]{"report_final_recognition","report_final_recognition_delivery","report_final_recognition_contribution","report_final_deposit_release","report_final_recognition_daily"})assertThat(count(table)).isZero();
        }finally{jdbc.execute("ALTER TABLE report_final_recognition_daily DROP CONSTRAINT test_final_broker_failure");}
        Message retained=rabbit.receive(DLQ,5000);assertThat(retained).isNotNull();assertThat(retained.getBody()).isEqualTo(bytes);
        registry.start();send(retained.getBody());await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(state()).isEqualTo("APPLIED"));registry.stop();assertThat(earned()).isEqualByComparingTo("100");
    }
    @Test void nonFinalClaimReachesDeadLetterWithoutAnyFinancialContribution()throws Exception {
        var root=(ObjectNode)new ObjectMapper().readTree(fixture("settlement-recognition-completed"));((ObjectNode)root.get("payload")).put("finalSettlement",false);
        send(new ObjectMapper().writeValueAsBytes(root));dead();assertThat(count("report_final_recognition")).isZero();assertThat(count("report_final_recognition_daily")).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"PRF","PFR","RPF","RFP","FPR","FRP"})
    void originalFinancialFacts_brokerArrivalPermutations_reconcileFiveMetricsWithoutDoubleEffect(String order) throws Exception {
        for (char kind : order.toCharArray()) {
            String fixtureName = switch (kind) {
                case 'P' -> "payment-final-recognition-deposit";
                case 'R' -> "refund-final-recognition-deposit";
                case 'F' -> "settlement-recognition-completed";
                default -> throw new IllegalArgumentException("Unknown arrival kind");
            };
            String evidenceTable = switch (kind) {
                case 'P' -> "report_cash_receipt";
                case 'R' -> "report_cash_refund";
                case 'F' -> "report_final_recognition";
                default -> throw new IllegalArgumentException("Unknown arrival kind");
            };
            send(fixture(fixtureName));
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(evidenceTable)).isOne());
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT state FROM report_cash_refund",String.class)).isEqualTo("APPLIED"));
        finals.recover(UUID.fromString("00000000-0000-4000-8000-000000000015"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(state()).isEqualTo("APPLIED"));
        // Re-deliver after listener restart; neither claim is erased and no producer is republished.
        registry.stop(); registry.start();
        send(fixture("payment-final-recognition-deposit")); send(fixture("refund-final-recognition-deposit"));
        var newDelivery=(ObjectNode)new ObjectMapper().readTree(fixture("settlement-recognition-completed"));
        newDelivery.put("eventId",UUID.randomUUID().toString()); send(new ObjectMapper().writeValueAsBytes(newDelivery));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("report_final_recognition_delivery")).isEqualTo(2));
        registry.stop();
        var generation = rebuild.start(java.time.LocalDate.of(2026,10,10),java.time.LocalDate.of(2026,10,10),"Asia/Bangkok");
        assertThat(rebuild.advance(generation.generationId(),1).status().name()).isEqualTo("VERIFIED");
        var row = jdbc.queryForMap("SELECT * FROM financial_source_rebuild_day WHERE generation_id=? AND department_id IS NULL",generation.generationId());
        assertThat((BigDecimal)row.get("cash_received")).isEqualByComparingTo("120");
        assertThat((BigDecimal)row.get("refunds")).isEqualByComparingTo("20");
        assertThat((BigDecimal)row.get("earned_revenue")).isEqualByComparingTo("100");
        assertThat((BigDecimal)row.get("deposit_liability")).isZero(); assertThat((BigDecimal)row.get("outstanding_receivable")).isZero();
        assertThat(count("report_final_deposit_release")).isOne(); assertThat(count("report_final_recognition_contribution")).isEqualTo(4);
        assertThat(count("financial_report_publication")).isZero(); assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isZero();
    }
    private void cash()throws Exception {send(fixture("payment-final-recognition-deposit"));send(fixture("refund-final-recognition-deposit"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_refund WHERE state='APPLIED'",Integer.class)).isOne());}
    private static byte[] fixture(String name)throws Exception{return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/"+name+".json"));}
    private void send(byte[] bytes)throws Exception {String routing=new ObjectMapper().readTree(bytes).get("eventType").asText();rabbit.send("mediflow.events",routing,new Message(bytes,new MessageProperties()));}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private String state(){return jdbc.queryForList("SELECT state FROM report_final_recognition",String.class).stream().findFirst().orElse("ABSENT");}
    private BigDecimal earned(){return jdbc.queryForObject("SELECT earned_revenue FROM report_final_recognition_daily WHERE department_id IS NULL",BigDecimal.class);}
    private void dead(){await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne());registry.stop();}
}
