package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import com.mediflow.billing.infrastructure.pricing.CareFinancePriceProperties;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties={"eureka.client.enabled=false","mediflow.billing.ledger.enabled=true",
        "mediflow.billing.surgery-charge-consumer.enabled=true","mediflow.billing.surgery-completion-proof.enabled=true",
        "mediflow.billing.outbox.enabled=false","mediflow.billing.outbox.metrics-enabled=false","mediflow.billing.outbox.maintenance-enabled=false",
        "mediflow.billing.rabbit.retry.initial-interval-ms=100","mediflow.billing.rabbit.retry.max-interval-ms=100",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class SurgeryCompletionRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ=new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl);registry.add("spring.datasource.username",PG::getUsername);registry.add("spring.datasource.password",PG::getPassword);
        registry.add("spring.rabbitmq.host",MQ::getHost);registry.add("spring.rabbitmq.port",MQ::getAmqpPort);
        registry.add("spring.rabbitmq.username",MQ::getAdminUsername);registry.add("spring.rabbitmq.password",MQ::getAdminPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired ObjectMapper mapper;
    @Autowired IssueSurgeryChargeUseCase issue;
    @Autowired CareFinancePriceProperties prices;
    @BeforeEach void reset()throws Exception {
        listeners.getListenerContainer("billingEvents").stop();admin.purgeQueue("billing.q");admin.purgeQueue("billing.dlq");
        jdbc.execute("TRUNCATE billing_account,billing_event_outbox,processed_event CASCADE");
        prices.setPrices(Map.of("PRICE",new CareFinancePriceProperties.Price("Procedure",new BigDecimal("100"))));
        issue.issue(new SurgeryChargeDecoder(mapper).decode("surgery.case.created",Files.readAllBytes(Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created.admission.v1.json"))));
    }
    @AfterEach void stop(){listeners.getListenerContainer("billingEvents").stop();}
    private void send(byte[] bytes){var props=new MessageProperties();props.setContentType("application/json");rabbit.send("mediflow.events","surgery.completed",new Message(bytes,props));}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private void accepted(int deliveries){await().atMost(Duration.ofSeconds(15)).untilAsserted(()->{
        assertThat(count("surgery_completion_delivery")).isEqualTo(deliveries);assertThat(admin.getQueueInfo("billing.q").getMessageCount()).isZero();});}
    private void rejected(byte[] bytes){await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(admin.getQueueInfo("billing.dlq").getMessageCount()).isOne());
        var message=rabbit.receive("billing.dlq",5000);assertThat(message).isNotNull();assertThat(message.getBody()).isEqualTo(bytes);}
    @Test void singleDispatcherAcceptsActualProofAndNewDeliveryAcrossListenerRestart()throws Exception {
        var bytes=SurgeryCompletionDecoderTest.fixture("admission");listeners.getListenerContainer("billingEvents").start();send(bytes);accepted(1);
        listeners.getListenerContainer("billingEvents").stop();listeners.getListenerContainer("billingEvents").start();send(bytes);
        var root=(ObjectNode)mapper.readTree(bytes);root.put("eventId",UUID.randomUUID().toString());send(mapper.writeValueAsBytes(root));accepted(2);
        assertThat(count("surgery_completion_source")).isOne();assertThat(count("processed_event")).isOne();
        assertThat(count("billing_event_outbox")).isOne();assertThat(count("settlement")).isZero();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM billing_event_outbox",Boolean.class)).isFalse();
    }
    @Test void unsupportedSourceRevisionRetriesToRetainedByteDlqWithoutCompatibilityEffect()throws Exception {
        var root=(ObjectNode)mapper.readTree(SurgeryCompletionDecoderTest.fixture("admission"));((ObjectNode)root.path("payload")).put("sourceRevision",2);
        byte[] invalid=mapper.writeValueAsBytes(root);listeners.getListenerContainer("billingEvents").start();send(invalid);rejected(invalid);
        assertThat(count("surgery_completion_source")).isZero();assertThat(count("processed_event")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE reconciled_result_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test void proofStorageFailureRollsBackChargesAndReceiptThenOriginalDlqBytesRecover()throws Exception {
        byte[] bytes=SurgeryCompletionDecoderTest.fixture("admission");
        jdbc.execute("ALTER TABLE surgery_completion_source ADD CONSTRAINT test_fault CHECK (case_revision<0)");
        listeners.getListenerContainer("billingEvents").start();
        try{send(bytes);rejected(bytes);assertThat(count("surgery_completion_source")).isZero();assertThat(count("processed_event")).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE reconciled_result_id IS NOT NULL",Integer.class)).isZero();}
        finally{jdbc.execute("ALTER TABLE surgery_completion_source DROP CONSTRAINT test_fault");}
        send(bytes);accepted(1);assertThat(count("surgery_completion_source")).isOne();
    }
}
