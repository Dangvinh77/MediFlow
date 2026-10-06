package com.mediflow.surgery.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.port.in.*;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.messaging.consumer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

/** Actual Rabbit listener, real transaction/inbox and persisted Billing bytes. Not multi-service E2E. */
@Testcontainers
@SpringBootTest(properties={"mediflow.jwt.secret=surgery-live-consumer-test-secret-at-least-32-bytes",
        "mediflow.features.surgery.enabled=true","mediflow.surgery.messaging.consumers.enabled=true",
        "mediflow.surgery.messaging.producer.enabled=false","eureka.client.enabled=false",
        "spring.rabbitmq.publisher-confirm-type=simple",
        "spring.cloud.discovery.enabled=false","mediflow.surgery.messaging.consumers.pending-poll-ms=600000"})
class SurgeryClearanceRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl);
        registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
        registry.add("spring.rabbitmq.host",RABBIT::getHost);
        registry.add("spring.rabbitmq.port",RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username",RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password",RABBIT::getAdminPassword);
    }
    private static final Instant NOW=Instant.parse("2026-10-05T08:01:00Z");
    private static final String DLQ="surgery.financial-clearance.dlq";
    @Autowired RabbitTemplate rabbit;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired PlatformTransactionManager transactions;
    @Autowired QueryPendingSurgeryClearancesUseCase pending;
    @Autowired ReactToSurgeryClearanceUseCase receive;
    @Autowired RecordSurgeryClearanceRetryUseCase retry;
    @Autowired SurgeryClearanceDecoder decoder;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listeners;
    @MockBean SurgeryClockPort clock;

    @BeforeEach void reset() {
        // Previous messages are observed applied/dead-lettered before each test returns.
        jdbc.execute("TRUNCATE surgery_case,surgery_inbox,surgery_inbox_semantic_mutex CASCADE");
        when(clock.now()).thenReturn(NOW);
        new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit).purgeQueue(DLQ,false);
        listeners.start();
    }
    @org.junit.jupiter.api.AfterEach void stopListener() { listeners.stop(); }
    @Test void actualListenerDuplicateIsOneFinancialProofAndNeverReadiesOrStartsCase() throws Exception {
        createCase(); byte[] bytes=fixture("surgery");
        send(bytes); send(bytes);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(count("surgery_financial_clearance")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM surgery_inbox",String.class)).isEqualTo("APPLIED");
        });
        awaitQueueDrained();
        assertThat(count("surgery_inbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_case",String.class)).isEqualTo("REQUESTED");
        assertThat(count("surgery_outbox")).isZero();
    }
    @Test void pendingIsAcknowledgedDurablyAndFreshWorkerRecoversAfterCaseAppears() throws Exception {
        send(fixture("surgery"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_inbox WHERE status='PENDING'",Integer.class)).isEqualTo(1));
        awaitQueueDrained();
        assertThat(pending.due(20)).isEmpty();
        createCase(); when(clock.now()).thenReturn(NOW.plusSeconds(60));
        assertThat(pending.due(20)).hasSize(1);
        new PendingSurgeryClearanceWorker(pending,receive,decoder,retry).poll();
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_inbox",String.class)).isEqualTo("APPLIED");
        new PendingSurgeryClearanceWorker(pending,receive,decoder,retry).poll();
        assertThat(count("surgery_financial_clearance")).isEqualTo(1);
    }
    @Test void validPrescriptionGrantIsNotApplicableNotPoisonOrSurgeryPermission() throws Exception {
        send(fixture("prescription")); awaitQueueDrained();
        assertThat(count("surgery_inbox")).isZero();
        assertThat(count("surgery_financial_clearance")).isZero();
        assertThat(rabbit.receive(DLQ,500)).isNull();
    }
    @Test void unsupportedEnvelopeAndWrongTargetReachDlqWithoutPermission() throws Exception {
        createCase();
        byte[] invalid="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        send(invalid);
        var dead=rabbit.receive(DLQ,10000);
        assertThat(dead).isNotNull(); assertThat(dead.getBody()).containsExactly(invalid);
        assertThat(count("surgery_inbox")).isZero();
        var root=(ObjectNode)mapper.readTree(fixture("surgery"));
        ((ObjectNode)root.path("payload")).put("patientId",UUID.randomUUID().toString());
        send(mapper.writeValueAsBytes(root));
        assertThat(rabbit.receive(DLQ,10000)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_inbox",String.class)).isEqualTo("QUARANTINED");
        assertThat(count("surgery_financial_clearance")).isZero();
    }
    private void createCase() {
        var value=SurgeryCase.create(id(4),id(12),new CareEpisode(CareEpisodeType.ADMISSION,id(3),id(3),null),
                id(2),id(8),id(9),"TEST_PROC","Test-only indication",SurgeryPriority.ROUTINE,
                NOW.minusSeconds(120),SurgeryAuditActor.human(id(9),id(9)),"rabbit-test");
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> cases.save(value,-1));
    }
    private void send(byte[] body) {
        var properties=new MessageProperties(); properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.invoke(operations -> {
            operations.send("mediflow.events","financial.clearance.granted",new Message(body,properties));
            operations.waitForConfirmsOrDie(5000);
            return null;
        });
    }
    private void awaitQueueDrained() {
        // Send was confirmed. Zero ready messages plus graceful stop waits for in-flight processing/ack.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit).getQueueInfo(SurgeryClearanceConsumer.QUEUE)
                        .getMessageCount()).isZero());
        listeners.stop();
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table,Integer.class); }
    private byte[] fixture(String purpose) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-" + purpose + ".json"));
    }
    private UUID id(int suffix) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix)); }
}
