package com.mediflow.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.report.infrastructure.config.RabbitConfig;
import com.mediflow.report.web.OperationalSnapshotReportController;

import io.micrometer.core.instrument.MeterRegistry;

/** Actual scheduled DB capture and Actuator wiring, without enabling any V2 writer or report. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "mediflow.features.care-finance-v2=false",
        "mediflow.report.telemetry.enabled=true", "mediflow.report.telemetry.sample-interval-ms=5000",
        "mediflow.report.telemetry.stale-after-seconds=10", "management.endpoints.web.exposure.include=health,info,metrics",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
class ReportTelemetryRuntimeTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Autowired MeterRegistry meters;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    @Autowired @Qualifier("reportQueueBindings") Declarables bindings;

    @Test
    void scheduler_readsRealDatabaseAndActualMetricsEndpointReturnsAggregate() throws Exception {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(meters.get("report.telemetry.available").gauge().value()).isOne());
        mvc.perform(get("/actuator/metrics/report.admission.pending").with(user("staff").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("report.admission.pending"))
                .andExpect(jsonPath("$.measurements[0].value").value(0));
        assertThat(meters.get("report.telemetry.sample.failures").counter().count()).isZero();
    }

    @Test
    void actualMetricsEndpoint_deniesPatientAndAnonymousAndAllowsManager() throws Exception {
        mvc.perform(get("/actuator/metrics").with(user("patient").roles("PATIENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").with(user("manager").roles("MANAGER"))).andExpect(status().isOk());
    }

    @Test
    void telemetryOptIn_neverEnablesV2RoutesBindingsOrPublication() {
        assertThat(context.getBeansOfType(OperationalSnapshotReportController.class)).isEmpty();
        assertThat(bindings.getDeclarablesByType(Binding.class)).extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrderElementsOf(RabbitConfig.SUBSCRIBED_ROUTING_KEYS).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_report_publication", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operational_event_journal", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_cash_receipt", Long.class)).isZero();
    }
}
