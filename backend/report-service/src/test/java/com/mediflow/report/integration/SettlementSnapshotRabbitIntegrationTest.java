package com.mediflow.report.integration;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties={"eureka.client.enabled=false","mediflow.jwt.secret=settlement-report-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true","mediflow.report.settlement-consumer.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class SettlementSnapshotRabbitIntegrationTest {
    private static final String QUEUE="report.settlement-snapshots-v2.q", DLQ="report.settlement-snapshots-v2.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc; @Autowired RabbitTemplate rabbit; @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    private final ObjectMapper mapper=new ObjectMapper();
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() { return registry.getListenerContainer("reportSettlementSnapshots"); }
    @BeforeEach void clean() {
        listener().stop(); admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE report_settlement_snapshot,report_receivable_stock CASCADE"); listener().start();
    }
    @AfterEach void stop() { listener().stop(); }
    private byte[] fixture(int version) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-reconciled-"+version+".json"));
    }
    private void send(byte[] bytes) { rabbit.send("mediflow.events","settlement.reconciled",new Message(bytes,new MessageProperties())); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    private int queueSize(String queue) { return admin.getQueueInfo(queue).getMessageCount(); }
    private BigDecimal stock() { return jdbc.queryForObject("SELECT outstanding_receivable FROM report_receivable_stock WHERE department_id IS NULL",BigDecimal.class); }
    private void applied(int deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(count("report_settlement_delivery")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertThat(queueSize(QUEUE)).isZero());
        listener().stop();
    }
    private void dead() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(queueSize(DLQ)).isOne()); listener().stop();
    }
    @Test void consume_actualBillingBytesOutOfOrderAndDuplicate_latest15NoCashOrFinalEffects() throws Exception {
        send(fixture(3)); send(fixture(1)); send(fixture(2)); send(fixture(3)); applied(3);
        assertThat(stock()).isEqualByComparingTo("15"); assertThat(count("report_settlement_snapshot")).isEqualTo(3);
        assertThat(count("report_cash_receipt")).isZero(); assertThat(count("financial_contribution")).isZero();
        assertThat(count("daily_financial_report")).isZero(); assertThat(queueSize(DLQ)).isZero();
    }
    @Test void consume_sameSourceNewDelivery_oneStockContribution() throws Exception {
        send(fixture(1)); applied(1); var duplicate=(ObjectNode)mapper.readTree(fixture(1)); duplicate.put("eventId",UUID.randomUUID().toString());
        listener().start(); send(mapper.writeValueAsBytes(duplicate)); applied(2);
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(stock()).isEqualByComparingTo("40");
    }
    @Test void consume_conflictingContext_deadLettersWithoutNewSourceClaim() throws Exception {
        send(fixture(1)); applied(1); var bad=(ObjectNode)mapper.readTree(fixture(2));
        ((ObjectNode)bad.get("payload")).put("patientId",UUID.randomUUID().toString());
        listener().start(); send(mapper.writeValueAsBytes(bad)); dead();
        assertThat(count("report_settlement_snapshot")).isOne(); assertThat(count("report_settlement_delivery")).isOne();
        assertThat(stock()).isEqualByComparingTo("40");
    }
    @Test void consume_databaseFailure_boundedRetriesRollbackThenRetainedByteReplay() throws Exception {
        byte[] bytes=fixture(1); jdbc.execute("ALTER TABLE report_receivable_stock ADD CONSTRAINT test_broker_failure CHECK(department_id IS NULL)");
        try {
            send(bytes); dead(); assertThat(count("report_settlement_snapshot")).isZero(); assertThat(count("report_settlement_delivery")).isZero();
            assertThat(count("report_receivable_stock")).isZero();
        } finally { jdbc.execute("ALTER TABLE report_receivable_stock DROP CONSTRAINT test_broker_failure"); }
        Message retained=rabbit.receive(DLQ,5000); assertThat(retained).isNotNull(); assertThat(retained.getBody()).isEqualTo(bytes);
        listener().start(); send(retained.getBody()); applied(1); assertThat(stock()).isEqualByComparingTo("40");
    }
}
