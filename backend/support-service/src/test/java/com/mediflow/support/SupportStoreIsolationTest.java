package com.mediflow.support;

import com.mediflow.report.messaging.consumer.OutpatientReportFactConsumer;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Disposable real stores/broker; not executed by the explicit non-container selector. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.jwt.secret=" + SupportSecurityTest.SECRET,
        "mediflow.features.care-finance-v2=true", "mediflow.report.outpatient-consumer.enabled=true",
        "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000",
        "mediflow.notification.care-v1.enabled=true", "mediflow.notification.outpatient-prescription-consumer.enabled=true"})
class SupportStoreIsolationTest {
    @Container static final PostgreSQLContainer<?> REPORT = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final PostgreSQLContainer<?> NOTIFICATION = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("mediflow.report.datasource.url", REPORT::getJdbcUrl);
        r.add("mediflow.report.datasource.username", REPORT::getUsername);
        r.add("mediflow.report.datasource.password", REPORT::getPassword);
        r.add("mediflow.notification.datasource.url", NOTIFICATION::getJdbcUrl);
        r.add("mediflow.notification.datasource.username", NOTIFICATION::getUsername);
        r.add("mediflow.notification.datasource.password", NOTIFICATION::getPassword);
        r.add("spring.rabbitmq.host", MQ::getHost); r.add("spring.rabbitmq.port", MQ::getAmqpPort);
        r.add("spring.rabbitmq.username", MQ::getAdminUsername); r.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }
    @Autowired @Qualifier("reportJdbcTemplate") JdbcTemplate report;
    @Autowired @Qualifier("notificationJdbcTemplate") JdbcTemplate notification;
    @Autowired @Qualifier("reportDataSource") HikariDataSource reportPool;
    @Autowired @Qualifier("notificationDataSource") HikariDataSource notificationPool;
    @Autowired @Qualifier("notificationRabbitTemplate") RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired OutpatientReportFactConsumer reportConsumer;
    @Autowired TestRestTemplate http;

    @BeforeEach void resetDisposableStores() {
        listeners.getListenerContainers().forEach(c -> c.stop());
        for (String queue : new String[]{"report.outpatient-v1.q", "report.outpatient-v1.dlq", "notification.q", "notification.dlq"})
            admin.purgeQueue(queue);
        report.execute("TRUNCATE operational_contribution, operational_delivery, operational_event_journal, operational_source_snapshot, daily_operational_report CASCADE");
        notification.execute("TRUNCATE notification, processed_event, care_notification_source CASCADE");
    }
    @AfterEach void stopConsumers() { listeners.getListenerContainers().forEach(c -> c.stop()); }

    @Test void eachMigrationHistoryAndPoolBelongsToExactlyOneContext() {
        assertThat(reportPool).isNotSameAs(notificationPool);
        assertThat(reportPool.getPoolName()).isEqualTo("report-store");
        assertThat(notificationPool.getPoolName()).isEqualTo("notification-store");
        assertThat(report.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isPositive();
        assertThat(notification.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(4);
        assertThat(report.queryForObject("SELECT to_regclass('notification')", String.class)).isNull();
        assertThat(notification.queryForObject("SELECT to_regclass('operational_contribution')", String.class)).isNull();
    }

    @Test void sameProducerBytesReachBothContextsAndReportReplayCannotResendNotice() throws Exception {
        byte[] body = fixture(); start(); publish(body);
        awaitEffects();
        Map<String, Object> notice = notification.queryForMap("SELECT * FROM notification");
        long held = notification.queryForObject("SELECT count(*) FROM notification_event_outbox", Long.class);
        MessageProperties properties = new MessageProperties(); properties.setReceivedRoutingKey("prescription.filled");
        for (int i = 0; i < 20; i++) reportConsumer.consume(new Message(body, properties));
        assertThat(notification.queryForMap("SELECT * FROM notification")).isEqualTo(notice);
        assertThat(notification.queryForObject("SELECT count(*) FROM notification_event_outbox", Long.class)).isEqualTo(held);
        assertThat(report.queryForObject("SELECT sum(numeric_value) FROM operational_contribution WHERE metric_type='DISPENSED_PRESCRIPTIONS'", Long.class)).isOne();
        UUID id = (UUID) notice.get("notification_id");
        assertThat(get("/api/v1/notifications/" + id, SupportSecurityTest.token("PATIENT", "access")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v1/notifications/" + id, SupportSecurityTest.token("PATIENT", "access", UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/v1/notifications/" + id, SupportSecurityTest.token("MANAGER", "access")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void notificationFailureDeadLettersWithoutRollingBackReportAndOriginalBytesRecover() throws Exception {
        notification.execute("CREATE FUNCTION fail_support_notice() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated test fault'; END $$");
        notification.execute("CREATE TRIGGER fail_support_notice BEFORE INSERT ON notification_event_outbox FOR EACH ROW EXECUTE FUNCTION fail_support_notice()");
        byte[] body = fixture();
        try {
            start(); publish(body);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(report.queryForObject("SELECT count(*) FROM operational_event_journal", Integer.class)).isOne();
                assertThat(admin.getQueueInfo("notification.dlq").getMessageCount()).isEqualTo(1);
            });
            assertThat(notification.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
            assertThat(notification.queryForObject("SELECT count(*) FROM processed_event", Integer.class)).isZero();
        } finally {
            notification.execute("DROP TRIGGER fail_support_notice ON notification_event_outbox");
            notification.execute("DROP FUNCTION fail_support_notice()");
        }
        Message retained = rabbit.receive("notification.dlq", 5000);
        assertThat(retained).isNotNull(); assertThat(retained.getBody()).isEqualTo(body);
        publish(retained.getBody()); awaitEffects();
        assertThat(report.queryForObject("SELECT count(*) FROM operational_event_journal", Integer.class)).isOne();
        assertThat(notification.queryForObject("SELECT count(*) FROM notification", Integer.class)).isOne();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void exhaustedContextPoolCannotStarveTheOtherContext(boolean exhaustReport) throws Exception {
        HikariDataSource blocked = exhaustReport ? reportPool : notificationPool;
        String deadLetters = exhaustReport ? "report.outpatient-v1.dlq" : "notification.dlq";
        var held = new java.util.ArrayList<java.sql.Connection>();
        byte[] body = fixture();
        try {
            for (int i = 0; i < blocked.getMaximumPoolSize(); i++) held.add(blocked.getConnection());
            start(); publish(body);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                if (exhaustReport)
                    assertThat(notification.queryForObject("SELECT count(*) FROM notification", Integer.class)).isOne();
                else
                    assertThat(report.queryForObject("SELECT count(*) FROM operational_event_journal", Integer.class)).isOne();
                assertThat(admin.getQueueInfo(deadLetters).getMessageCount()).isEqualTo(1);
            });
        } finally {
            for (var connection : held) connection.close();
        }
        Message retained = rabbit.receive(deadLetters, 5000);
        assertThat(retained).isNotNull();
        assertThat(retained.getBody()).isEqualTo(body);
        publish(retained.getBody()); awaitEffects();
        assertThat(notification.queryForObject("SELECT count(*) FROM notification_event_outbox", Integer.class)).isOne();
        assertThat(report.queryForObject("SELECT count(*) FROM operational_event_journal", Integer.class)).isOne();
    }

    private void awaitEffects() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(report.queryForObject("SELECT count(*) FROM operational_event_journal", Integer.class)).isOne();
            assertThat(notification.queryForObject("SELECT count(*) FROM notification", Integer.class)).isOne();
        });
    }
    private void start() { listeners.getListenerContainers().forEach(c -> c.start()); }
    private void publish(byte[] body) { rabbit.send("mediflow.events", "prescription.filled", new Message(body, new MessageProperties())); }
    private static byte[] fixture() throws Exception {
        return Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json"));
    }
    private ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders(); headers.set("Authorization", token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
