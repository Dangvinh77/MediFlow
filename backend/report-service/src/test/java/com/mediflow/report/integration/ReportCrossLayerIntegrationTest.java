package com.mediflow.report.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.report.infrastructure.config.RabbitConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Cross-layer acceptance tests for the report read model (T10).
 *
 * <p>The test drives the real RabbitMQ consumer and PostgreSQL projections. Awaitility is used
 * for every asynchronous assertion so the tests do not rely on scheduling sleeps. Testcontainers
 * disables this class when Docker is unavailable on a developer machine.</p>
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReportCrossLayerIntegrationTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 9, 15);
    private static final UUID DEPARTMENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @BeforeEach
    void cleanProjectionAndQueues() {
        listenerRegistry.stop();
        rabbitAdmin.purgeQueue(RabbitConfig.QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitConfig.DLQ, false);
        jdbcTemplate.update("DELETE FROM payment_contribution");
        jdbcTemplate.update("DELETE FROM drug_statistic");
        jdbcTemplate.update("DELETE FROM monthly_revenue_report");
        jdbcTemplate.update("DELETE FROM daily_visit_report");
        jdbcTemplate.update("DELETE FROM processed_event");
        listenerRegistry.start();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void operationalEvents_buildDailyAndDrugProjections_andApiReadsEnvelope() throws Exception {
        UUID drugId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T01:00:00Z",
                "correlationId", "cross-layer-medical", "recordId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID,
                "examinationDate", REPORT_DATE));
        publish(RabbitConfig.RK_LAB_RESULT_CREATED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T02:00:00Z",
                "correlationId", "cross-layer-lab", "labId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID,
                "performedDate", REPORT_DATE));
        publish(RabbitConfig.RK_PRESCRIPTION_FILLED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T03:00:00Z",
                "correlationId", "cross-layer-prescription", "prescriptionId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID, "dispensedItems", List.of(
                        event("drugId", drugId, "drugName", "Paracetamol", "quantity", 2),
                        event("drugId", drugId, "drugName", "Paracetamol", "quantity", 3))));

        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                REPORT_DATE, 1);
        awaitProjection("SELECT lab_count FROM daily_visit_report WHERE report_date = ? AND department_id = ?",
                REPORT_DATE, DEPARTMENT_ID, 1);
        awaitProjection("SELECT prescription_count FROM daily_visit_report WHERE report_date = ? AND department_id = ?",
                REPORT_DATE, DEPARTMENT_ID, 1);
        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? AND drug_id = ? AND department_id IS NULL",
                REPORT_DATE, drugId, 5);

        mockMvc.perform(get("/api/v1/reports/daily").param("date", REPORT_DATE.toString())
                        .param("departmentId", DEPARTMENT_ID.toString())
                        .header("X-Correlation-Id", "api-correlation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.visitCount").value(1))
                .andExpect(jsonPath("$.data.labCount").value(1))
                .andExpect(jsonPath("$.data.prescriptionCount").value(1))
                .andExpect(jsonPath("$.correlationId").value("api-correlation"));
    }

    @Test
    void legacyVisitRedelivery_countsOnceAcrossTwoDepartments_andHospitalScope() {
        UUID otherDepartmentId = UUID.randomUUID();
        Map<String, Object> firstVisit = event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T01:00:00Z",
                "correlationId", "visit-department-a", "recordId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID, "examinationDate", REPORT_DATE);
        publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, firstVisit);
        publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, firstVisit);
        publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T02:00:00Z",
                "correlationId", "visit-department-b", "recordId", UUID.randomUUID(),
                "departmentId", otherDepartmentId, "examinationDate", REPORT_DATE));

        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id IS NULL", REPORT_DATE, 2);
        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id = ?", REPORT_DATE, DEPARTMENT_ID, 1);
        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id = ?", REPORT_DATE, otherDepartmentId, 1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void legacyVisitWithNewEventIdForSameRecord_isStillDoubleCounted_characterization() {
        UUID recordId = UUID.randomUUID();
        for (int index = 1; index <= 2; index++) {
            publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event(
                    "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T01:00:00Z",
                    "correlationId", "same-record-new-event-" + index, "recordId", recordId,
                    "departmentId", DEPARTMENT_ID, "examinationDate", REPORT_DATE));
        }

        // The legacy inbox keys only by eventId. Source-ID contributions are a D11/R-01 redesign,
        // so this documents the existing gap rather than treating the value 2 as the target rule.
        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id IS NULL", REPORT_DATE, 2);
        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id = ?", REPORT_DATE, DEPARTMENT_ID, 2);
    }

    @Test
    void prescriptionsAcrossBangkokMidnight_areBucketedIntoSeparateDays() {
        UUID drugId = UUID.randomUUID();
        publish(RabbitConfig.RK_PRESCRIPTION_FILLED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-30T16:30:00Z",
                "correlationId", "before-midnight", "prescriptionId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID, "dispensedItems", List.of(
                        event("drugId", drugId, "drugName", "Boundary medicine", "quantity", 1))));
        publish(RabbitConfig.RK_PRESCRIPTION_FILLED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-30T17:30:00Z",
                "correlationId", "after-midnight", "prescriptionId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID, "dispensedItems", List.of(
                        event("drugId", drugId, "drugName", "Boundary medicine", "quantity", 2))));

        LocalDate septemberEnd = LocalDate.of(2026, 9, 30);
        LocalDate octoberStart = LocalDate.of(2026, 10, 1);
        awaitProjection("SELECT prescription_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id IS NULL", septemberEnd, 1);
        awaitProjection("SELECT prescription_count FROM daily_visit_report WHERE report_date = ? "
                + "AND department_id = ?", octoberStart, DEPARTMENT_ID, 1);
        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? "
                + "AND drug_id = ? AND department_id IS NULL", septemberEnd, drugId, 1);
        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? "
                + "AND drug_id = ? AND department_id IS NULL", octoberStart, drugId, 2);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void topMedicines_queryIsInclusiveScopedDeterministicAndUsesLatestName() throws Exception {
        UUID firstDrugId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondDrugId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        publishPrescription(LocalDate.of(2026, 9, 14), DEPARTMENT_ID, firstDrugId, "Old name", 2);
        publishPrescription(REPORT_DATE, DEPARTMENT_ID, firstDrugId, "Latest name", 3);
        publishPrescription(REPORT_DATE, DEPARTMENT_ID, secondDrugId, "Second name", 5);
        publishPrescription(LocalDate.of(2026, 9, 16), DEPARTMENT_ID, firstDrugId,
                "Outside range", 100);
        publishPrescription(REPORT_DATE, null, firstDrugId, "Hospital scope", 100);

        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? "
                        + "AND drug_id = ? AND department_id = ?",
                LocalDate.of(2026, 9, 14), firstDrugId, DEPARTMENT_ID, 2);
        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? "
                        + "AND drug_id = ? AND department_id = ?",
                REPORT_DATE, firstDrugId, DEPARTMENT_ID, 3);
        awaitProjection("SELECT dispensed_quantity FROM drug_statistic WHERE report_date = ? "
                        + "AND drug_id = ? AND department_id = ?",
                REPORT_DATE, secondDrugId, DEPARTMENT_ID, 5);

        mockMvc.perform(get("/api/v1/reports/top-medicines")
                        .param("fromDate", "2026-09-14")
                        .param("toDate", "2026-09-15")
                        .param("departmentId", DEPARTMENT_ID.toString())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].drugId").value(firstDrugId.toString()))
                .andExpect(jsonPath("$.data[0].drugName").value("Latest name"))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(5))
                .andExpect(jsonPath("$.data[1].drugId").value(secondDrugId.toString()))
                .andExpect(jsonPath("$.data[1].totalQuantity").value(5));
    }

    @Test
    void paymentEvents_areIdempotent_andCompensatingFailureIsReversible() {
        UUID invoiceId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        BigDecimal amount = new BigDecimal("125.50");

        Map<String, Object> completed = event(
                "eventId", UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                "occurredAt", "2026-09-15T04:00:00Z", "correlationId", "payment-completed",
                "invoiceId", invoiceId, "departmentId", DEPARTMENT_ID, "totalAmount", amount);
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, completed);
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, completed); // same event delivery
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, event(
                "eventId", UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "occurredAt", "2026-09-15T04:00:00Z", "correlationId", "payment-replayed",
                "invoiceId", invoiceId, "departmentId", DEPARTMENT_ID, "totalAmount", amount));

        awaitStatus(invoiceId, "APPLIED");
        awaitProjection("SELECT total_revenue FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id IS NULL",
                2026, 9, amount);
        awaitProjection("SELECT invoice_count FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id = ?",
                2026, 9, DEPARTMENT_ID, 1);

        publish(RabbitConfig.RK_PAYMENT_FAILED, event(
                "eventId", UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                "occurredAt", "2026-09-15T05:00:00Z", "correlationId", "payment-failed",
                "invoiceId", invoiceId));

        awaitStatus(invoiceId, "REVERSED");
        awaitProjection("SELECT total_revenue FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id IS NULL",
                2026, 9, BigDecimal.ZERO.setScale(2));
        awaitProjection("SELECT invoice_count FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id = ?",
                2026, 9, DEPARTMENT_ID, 0);
    }

    @Test
    void failureBeforeCompletion_keepsRevenueAtZeroAfterSafeReprocessing() {
        UUID invoiceId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        publish(RabbitConfig.RK_PAYMENT_FAILED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T06:00:00Z",
                "correlationId", "failed-first", "invoiceId", invoiceId));
        awaitStatus(invoiceId, "PENDING_REVERSAL");

        publish(RabbitConfig.RK_PAYMENT_COMPLETED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T06:00:00Z",
                "correlationId", "completed-after-failure", "invoiceId", invoiceId,
                "departmentId", DEPARTMENT_ID, "totalAmount", new BigDecimal("99.00")));
        awaitStatus(invoiceId, "REVERSED");

        awaitProjection("SELECT COALESCE((SELECT total_revenue FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id IS NULL), CAST(0 AS DECIMAL(15,2)))",
                2026, 9, BigDecimal.ZERO.setScale(2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_contribution WHERE invoice_id = ?",
                Integer.class, invoiceId)).isEqualTo(1);
    }

    @Test
    void shuffledLegacyPaymentDeliveries_andRedelivery_leaveBothInvoicesReversed() {
        UUID failedFirstInvoice = UUID.randomUUID();
        UUID completedFirstInvoice = UUID.randomUUID();
        Map<String, Object> failedFirst = event("eventId", UUID.randomUUID(),
                "occurredAt", "2026-09-16T02:00:00Z", "correlationId", "shuffled-failed-first",
                "invoiceId", failedFirstInvoice);
        Map<String, Object> completedFirst = event("eventId", UUID.randomUUID(),
                "occurredAt", "2026-09-15T02:00:00Z", "correlationId", "shuffled-completed-first",
                "invoiceId", completedFirstInvoice, "departmentId", DEPARTMENT_ID,
                "totalAmount", new BigDecimal("70.00"));
        Map<String, Object> lateCompletion = event("eventId", UUID.randomUUID(),
                "occurredAt", "2026-09-15T03:00:00Z", "correlationId", "shuffled-late-completion",
                "invoiceId", failedFirstInvoice, "departmentId", DEPARTMENT_ID,
                "totalAmount", new BigDecimal("40.00"));
        Map<String, Object> lateFailure = event("eventId", UUID.randomUUID(),
                "occurredAt", "2026-09-16T03:00:00Z", "correlationId", "shuffled-late-failure",
                "invoiceId", completedFirstInvoice);

        publish(RabbitConfig.RK_PAYMENT_FAILED, failedFirst);
        awaitStatus(failedFirstInvoice, "PENDING_REVERSAL");
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, completedFirst);
        awaitStatus(completedFirstInvoice, "APPLIED");
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, lateCompletion);
        awaitStatus(failedFirstInvoice, "REVERSED");
        publish(RabbitConfig.RK_PAYMENT_FAILED, lateFailure);
        awaitStatus(completedFirstInvoice, "REVERSED");

        // A replay of the same legacy deliveries must not re-apply either contribution.
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, lateCompletion);
        publish(RabbitConfig.RK_PAYMENT_FAILED, lateFailure);
        UUID barrierEventId = UUID.randomUUID();
        publish(RabbitConfig.RK_LAB_RESULT_CREATED, event(
                "eventId", barrierEventId, "occurredAt", "2026-09-17T01:00:00Z",
                "correlationId", "shuffled-barrier", "labId", UUID.randomUUID(),
                "departmentId", DEPARTMENT_ID, "performedDate", LocalDate.of(2026, 9, 17)));
        // The Rabbit listener processes this queue serially; the barrier proves both redeliveries
        // were consumed before checking that the processed ledger still has one row per event ID.
        awaitProjection("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", barrierEventId, 1L);
        awaitProjection("SELECT total_revenue FROM monthly_revenue_report WHERE year = ? AND month = ? "
                + "AND department_id IS NULL", 2026, 9, BigDecimal.ZERO.setScale(2));
        awaitProjection("SELECT invoice_count FROM monthly_revenue_report WHERE year = ? AND month = ? "
                + "AND department_id = ?", 2026, 9, DEPARTMENT_ID, 0);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_contribution",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event",
                Integer.class)).isEqualTo(5);
    }

    @Test
    void concurrentFirstEvents_areSerializedWithoutLostProjectionUpdates() throws Exception {
        int eventCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<? extends Future<?>> publishes = IntStream.range(0, eventCount)
                    .mapToObj(index -> executor.submit(() -> publish(
                            RabbitConfig.RK_MEDICAL_RECORD_CREATED,
                            event("eventId", UUID.randomUUID(),
                                    "occurredAt", "2026-09-15T07:00:00Z",
                                    "correlationId", "concurrent-" + index,
                                    "recordId", UUID.randomUUID(),
                                    "departmentId", DEPARTMENT_ID,
                                    "examinationDate", REPORT_DATE))))
                    .toList();
            for (Future<?> publish : publishes) {
                publish.get();
            }
        } finally {
            executor.shutdownNow();
        }

        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                REPORT_DATE, eventCount);
        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? AND department_id = ?",
                REPORT_DATE, DEPARTMENT_ID, eventCount);
    }

    @Test
    void conflictingPayment_isRetriedToDlq_withoutMutatingCommittedProjection() {
        UUID invoiceId = UUID.fromString("22222222-3333-4444-5555-666666666666");
        UUID completedEventId = UUID.randomUUID();
        publish(RabbitConfig.RK_PAYMENT_COMPLETED, event(
                "eventId", completedEventId, "occurredAt", "2026-09-15T08:00:00Z",
                "correlationId", "original-payment", "invoiceId", invoiceId,
                "departmentId", DEPARTMENT_ID, "totalAmount", new BigDecimal("50.00")));
        awaitStatus(invoiceId, "APPLIED");

        publish(RabbitConfig.RK_PAYMENT_COMPLETED, event(
                "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T08:00:00Z",
                "correlationId", "conflicting-payment", "invoiceId", invoiceId,
                "departmentId", DEPARTMENT_ID, "totalAmount", new BigDecimal("60.00")));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var info = rabbitAdmin.getQueueInfo(RabbitConfig.DLQ);
            assertThat(info).isNotNull();
            assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT amount FROM payment_contribution WHERE invoice_id = ?", BigDecimal.class, invoiceId))
                .isEqualByComparingTo("50.00");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class, completedEventId))
                .isEqualTo(1);
        awaitProjection("SELECT total_revenue FROM monthly_revenue_report WHERE year = ? AND month = ? AND department_id IS NULL",
                2026, 9, new BigDecimal("50.00"));
    }

    @Test
    void restartingConsumer_reprocessesMessageLeftOnDurableQueue() {
        listenerRegistry.stop();
        try {
            publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event(
                    "eventId", UUID.randomUUID(), "occurredAt", "2026-09-15T09:00:00Z",
                    "correlationId", "restart-recovery", "recordId", UUID.randomUUID(),
                    "departmentId", DEPARTMENT_ID,
                    "examinationDate", REPORT_DATE));

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                var info = rabbitAdmin.getQueueInfo(RabbitConfig.QUEUE);
                assertThat(info).isNotNull();
                assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
            });
        } finally {
            listenerRegistry.start();
        }

        awaitProjection("SELECT visit_count FROM daily_visit_report WHERE report_date = ? AND department_id IS NULL",
                REPORT_DATE, 1);
    }

    @Test
    void malformedMessage_isDeadLettered_withoutProcessedEventOrProjectionEffect() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        Message malformed = new Message("{\"eventId\":\"not-a-uuid\"}".getBytes(StandardCharsets.UTF_8),
                properties);
        rabbitTemplate.send(RabbitConfig.EVENTS_EXCHANGE, RabbitConfig.RK_PAYMENT_COMPLETED, malformed);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var info = rabbitAdmin.getQueueInfo(RabbitConfig.DLQ);
            assertThat(info).isNotNull();
            assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_contribution", Integer.class))
                .isZero();
    }

    @Test
    void missingLegacySourceId_isDeadLettered_withoutClaimingEvent() {
        UUID eventId = UUID.randomUUID();
        publish(RabbitConfig.RK_MEDICAL_RECORD_CREATED, event(
                "eventId", eventId, "occurredAt", "2026-09-15T01:00:00Z",
                "correlationId", "missing-source-id", "departmentId", DEPARTMENT_ID,
                "examinationDate", REPORT_DATE, "diagnosis", "private-clinical-detail"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var info = rabbitAdmin.getQueueInfo(RabbitConfig.DLQ);
            assertThat(info).isNotNull();
            assertThat(info.getMessageCount()).isEqualTo(1);
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = ?",
                Integer.class, eventId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM daily_visit_report",
                Integer.class)).isZero();
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, routingKey, payload);
    }

    private void publishPrescription(LocalDate date, UUID departmentId, UUID drugId,
                                     String drugName, int quantity) {
        publish(RabbitConfig.RK_PRESCRIPTION_FILLED, event(
                "eventId", UUID.randomUUID(),
                "occurredAt", date + "T02:00:00Z",
                "correlationId", "top-medicine-" + UUID.randomUUID(),
                "prescriptionId", UUID.randomUUID(),
                "departmentId", departmentId,
                "dispensedItems", List.of(event("drugId", drugId,
                        "drugName", drugName, "quantity", quantity))));
    }

    private static Map<String, Object> event(Object... fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            payload.put(String.valueOf(fields[index]), fields[index + 1]);
        }
        return payload;
    }

    private void awaitStatus(UUID invoiceId, String status) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbcTemplate.queryForObject("SELECT status FROM payment_contribution WHERE invoice_id = ?",
                        String.class, invoiceId)).isEqualTo(status));
    }

    private void awaitProjection(String sql, Object first, Object... rest) {
        Object expected = rest[rest.length - 1];
        Object[] queryArgs = new Object[rest.length - 1];
        System.arraycopy(rest, 0, queryArgs, 0, rest.length - 1);
        final Object[] finalQueryArgs = prepend(first, queryArgs);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbcTemplate.queryForObject(sql, Object.class, finalQueryArgs)).isEqualTo(expected));
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private static org.awaitility.core.ConditionFactory await() {
        return org.awaitility.Awaitility.await().pollInterval(Duration.ofMillis(100)).ignoreExceptions();
    }
}
