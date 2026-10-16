package com.mediflow.pharmacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Packaged Pharmacy/Billing/Report plus Notification, private databases and a real broker. All business commands
 * and foreign observations use HTTP. The isolated test-only bootstrap observes/injects faults into
 * Pharmacy's own store; foreign databases are never seeded or queried. Broker probes capture
 * actual committed producer bytes, never consumer-shaped replacement fixtures.
 * This proves the CURRENT V0 saga, not V1 clearance/admission activation or upstream identity lookup.
 */
@Testcontainers
class PharmacyDistributedRuntimeAcceptanceIT {
    private static final String SECRET = "pharmacy-distributed-test-only-secret-at-least-32-bytes";
    private static final String EXCHANGE = "mediflow.events";
    private static final Network NETWORK = Network.newNetwork();

    @Container static final PostgreSQLContainer<?> PHARMACY_DB = database("pharmacy-db");
    @Container static final PostgreSQLContainer<?> BILLING_DB = database("billing-db");
    @Container static final PostgreSQLContainer<?> REPORT_DB = database("report-db");
    @Container static final PostgreSQLContainer<?> NOTIFICATION_DB = database("notification-db");
    @Container static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final GenericContainer<?> REPORT = service("report", 8088, REPORT_DB, "report-db");
    @Container static final GenericContainer<?> NOTIFICATION = service("notification", 8087, NOTIFICATION_DB, "notification-db");
    @Container static final GenericContainer<?> BILLING = service("billing", 8086, BILLING_DB, "billing-db");
    @Container static final GenericContainer<?> PHARMACY = service("pharmacy", 8085, PHARMACY_DB, "pharmacy-db");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private UUID patientId;
    private UUID departmentId;
    private UUID doctorId;
    private UUID accountId;
    private String correlationId;
    private LocalDate date;

    @RegisterExtension
    final TestWatcher diagnostics = new TestWatcher() {
        @Override public void testFailed(ExtensionContext context, Throwable cause) {
            // These are isolated synthetic test apps. Persist diagnostics before Testcontainers removes them.
            for (var app : List.of(PHARMACY, BILLING, REPORT, NOTIFICATION)) {
                System.err.println("Runtime diagnostics: " + app.getDockerImageName());
                var lines=app.getLogs().replaceAll("(?m)^Using generated security password:.*$", "[redacted development password]").split("\\R");
                System.err.println(String.join("\n",java.util.Arrays.copyOfRange(lines,Math.max(0,lines.length-80),lines.length)));
            }
        }
    };

    @BeforeEach
    void isolatedBusinessScope() {
        patientId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();
        date = LocalDate.now(ZoneId.of("Asia/Bangkok"));
    }

    @Test
    void paidPrescription_actualFourServices_duplicateDeliveryHasOneStockAndReportEffect() throws Exception {
        try (Connection connection = brokerConnection(); Channel channel = connection.createChannel()) {
            String paymentProbe = probe(channel, "payment.completed");
            String fillProbe = probe(channel, "prescription.filled");
            UUID drugId = createDrug();
            UUID prescriptionId = createPrescription(drugId);
            JsonNode invoice = awaitInvoice(prescriptionId);
            assertThat(invoice.path("totalAmount").decimalValue()).isEqualByComparingTo("30.00");
            assertThat(drug(drugId).path("stockQuantity").asInt()).isEqualTo(20);
            awaitInvoiceNotice(invoice.path("invoiceId").asText());

            pay(invoice.path("invoiceId").asText());
            awaitCompletion(prescriptionId, invoice.path("invoiceId").asText());
            awaitReport(drugId, 1, "30.00");
            GetResponse payment = captured(channel, paymentProbe);
            GetResponse filled = captured(channel, fillProbe);
            // CURRENT Billing deliberately starts the payment leg's correlation at the invoice UUID.
            // Verify propagation within that leg; this is not an assertion of an uninterrupted root trace.
            assertCorrelation(payment, invoice.path("invoiceId").asText());
            assertCorrelation(filled, invoice.path("invoiceId").asText());

            // At-least-once transport may repeat both original delivery IDs.
            publish(channel, "payment.completed", payment.getBody(), payment.getProps());
            publish(channel, "prescription.filled", filled.getBody(), filled.getProps());
            // A new delivery ID for the same immutable source must also be effect-idempotent.
            publishNewDelivery(channel, "payment.completed", payment);
            publishNewDelivery(channel, "prescription.filled", filled);
            assertStableResult(drugId, prescriptionId, invoice.path("invoiceId").asText(), 1, "30.00");
        }
    }

    @Test
    void reportJvmDown_paidSagaCompletes_andRestartCatchesUpWithoutRepeatingStock() throws Exception {
        UUID drugId = createDrug();
        UUID prescriptionId = createPrescription(drugId);
        JsonNode invoice = awaitInvoice(prescriptionId);
        REPORT.getDockerClient().stopContainerCmd(REPORT.getContainerId()).withTimeout(0).exec();
        try {
            pay(invoice.path("invoiceId").asText());
            awaitCompletion(prescriptionId, invoice.path("invoiceId").asText());
            assertThat(drug(drugId).path("stockQuantity").asInt()).isEqualTo(17);
            try (Connection connection = brokerConnection(); Channel channel = connection.createChannel()) {
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                        assertThat(channel.queueDeclarePassive("report.q").getMessageCount()).isGreaterThanOrEqualTo(2));
            }
        } finally {
            REPORT.getDockerClient().startContainerCmd(REPORT.getContainerId()).exec();
            awaitHealthy(REPORT, 8088);
        }
        awaitReport(drugId, 1, "30.00");
        assertStableResult(drugId, prescriptionId, invoice.path("invoiceId").asText(), 1, "30.00");
    }

    @Test
    void brokerUnavailable_prescriptionCommits_andProducerJvmRestartRecoversItsOutbox() throws Exception {
        UUID drugId = createDrug();
        UUID prescriptionId;
        assertThat(BROKER.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        try {
            prescriptionId = createPrescription(drugId);
            assertThat(prescription(prescriptionId).path("dispenseStatus").asText()).isEqualTo("PENDING");
            assertThat(drug(drugId).path("stockQuantity").asInt()).isEqualTo(20);
            PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
        } finally {
            assertThat(BROKER.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
            if (!Boolean.TRUE.equals(PHARMACY.getDockerClient().inspectContainerCmd(PHARMACY.getContainerId())
                    .exec().getState().getRunning())) {
                PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();
            }
            awaitHealthy(PHARMACY, 8085);
        }
        JsonNode invoice = awaitInvoice(prescriptionId);
        pay(invoice.path("invoiceId").asText());
        awaitCompletion(prescriptionId, invoice.path("invoiceId").asText());
        awaitReport(drugId, 1, "30.00");
        assertStableResult(drugId, prescriptionId, invoice.path("invoiceId").asText(), 1, "30.00");
    }

    @Test
    void pharmacyJvmDownPaymentWaitsDurablyAndRestartCompletesV0SagaOnce() throws Exception {
        UUID drugId=createDrug();UUID prescriptionId=createPrescription(drugId);
        String invoiceId=awaitInvoice(prescriptionId).path("invoiceId").asText();
        assertThat(drug(drugId).path("stockQuantity").asInt()).isEqualTo(20);
        PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
        try {
            pay(invoiceId);
            try(Connection connection=brokerConnection();Channel channel=connection.createChannel()) {
                await().atMost(Duration.ofSeconds(20)).untilAsserted(()->
                        assertThat(channel.queueDeclarePassive("pharmacy.q").getMessageCount()).isGreaterThanOrEqualTo(1));
            }
            assertThat(get(BILLING,8086,"/api/v1/billing/invoices/"+invoiceId).path("sagaStatus").asText())
                    .isNotEqualTo("COMPLETED");
        } finally {
            PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();
            awaitHealthy(PHARMACY,8085);
        }
        awaitCompletion(prescriptionId,invoiceId);awaitReport(drugId,1,"30.00");
        assertStableResult(drugId,prescriptionId,invoiceId,1,"30.00");
    }

    @ParameterizedTest @ValueSource(strings={"BEFORE_FINALIZE","BEFORE_ACK","BEFORE_PUBLISH_MARK"})
    void actualJvmCrashAtCommittedEffectBoundaryNeverRepeatsStockOrFilledProof(String point)throws Exception {
        UUID drugId=createDrug(),prescriptionId=createPrescription(drugId);
        String invoiceId=awaitInvoice(prescriptionId).path("invoiceId").asText();
        acceptance("/faults/"+point+"/"+prescriptionId,"POST",null);
        try {
            pay(invoiceId);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(state(prescriptionId).path("paused").asBoolean()).isTrue());
            JsonNode before=state(prescriptionId);
            assertThat(before.path("stock").get(0).asInt()).isEqualTo(17);
            assertThat(before.path("slip").path("status").asText()).isEqualTo("DISPENSED");
            assertThat(before.path("receipts").get(0).asText()).isEqualTo(point.equals("BEFORE_FINALIZE")?"RECEIVED":"DISPENSED");
            JsonNode proof=filledProof(before);
            if(point.equals("BEFORE_PUBLISH_MARK"))assertThat(proof.path("published").asBoolean()).isFalse();
            recordSnapshot(point+"_BEFORE_CRASH",prescriptionId,invoiceId,before);
            PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
            PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();awaitHealthy(PHARMACY,8085);
            awaitCompletion(prescriptionId,invoiceId);awaitReport(drugId,1,"30.00");
            await().atMost(Duration.ofSeconds(30)).untilAsserted(()->{
                JsonNode after=state(prescriptionId),same=filledProof(after);
                assertThat(after.path("receipts").get(0).asText()).isEqualTo("DISPENSED");
                assertThat(same.path("event_id")).isEqualTo(proof.path("event_id"));
                assertThat(same.path("payload")).isEqualTo(proof.path("payload"));
                assertThat(same.path("published").asBoolean()).isTrue();
            });
            assertStableResult(drugId,prescriptionId,invoiceId,1,"30.00");
            recordSnapshot(point+"_RECOVERED",prescriptionId,invoiceId,state(prescriptionId));
        } finally {
            if(!Boolean.TRUE.equals(PHARMACY.getDockerClient().inspectContainerCmd(PHARMACY.getContainerId()).exec().getState().getRunning())) {
                PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();awaitHealthy(PHARMACY,8085);
            }
            acceptance("/faults/NONE/"+prescriptionId,"POST",null);
        }
    }

    @ParameterizedTest @ValueSource(strings={"cancel","expire","shortage"})
    void realBillingCompensatesTerminalOrStockFailureAndLateDuplicatePaymentOnce(String scenario)throws Exception {
        try(Connection connection=brokerConnection();Channel channel=connection.createChannel()) {
            String payments=probe(channel,"payment.completed"),failures=probe(channel,"prescription.dispense.failed");
            String reversals=probe(channel,"payment.failed"),completedRefunds=probe(channel,"payment.refunded");
            UUID drugId=createDrug(),prescriptionId=createPrescription(drugId);
            String invoiceId=awaitInvoice(prescriptionId).path("invoiceId").asText();
            if(scenario.equals("cancel"))command(PHARMACY,8085,"/api/v1/pharmacy/prescriptions/"+prescriptionId+"/cancel",
                    "PUT","{\"reason\":\"Isolated late payment acceptance\"}",200);
            else acceptance("/"+scenario+"/"+prescriptionId,"POST",null);
            pay(invoiceId);awaitCompensated(invoiceId);
            GetResponse payment=captured(channel,payments),failure=captured(channel,failures);
            GetResponse reversal=captured(channel,reversals);
            assertThat(mapper.readTree(reversal.getBody()).path("invoiceId").asText()).isEqualTo(invoiceId);
            assertThat(mapper.readTree(reversal.getBody()).path("patientId").asText()).isEqualTo(patientId.toString());
            JsonNode terminal=state(prescriptionId);
            assertThat(terminal.path("receipts").get(0).asText()).isEqualTo("COMPENSATED");
            assertThat(terminal.path("slip").path("status").asText()).isIn("CANCELLED","EXPIRED","FAILED");
            assertThat(terminal.path("stock").get(0).asInt()).isEqualTo(scenario.equals("shortage")?0:20);
            publish(channel,"payment.completed",payment.getBody(),payment.getProps());
            publish(channel,"prescription.dispense.failed",failure.getBody(),failure.getProps());
            publishNewDelivery(channel,"payment.completed",payment);publishNewDelivery(channel,"prescription.dispense.failed",failure);
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(()->{
                assertThat(state(prescriptionId).path("stock")).isEqualTo(terminal.path("stock"));
                assertThat(state(prescriptionId).path("slip")).isEqualTo(terminal.path("slip"));
                JsonNode invoice=get(BILLING,8086,"/api/v1/billing/invoices/"+invoiceId);
                assertThat(invoice.path("sagaStatus").asText()).isEqualTo("REFUNDED");
                assertThat(invoice.path("isPaid").asBoolean()).isFalse();
                for(var fee:invoice.path("fees"))assertThat(fee.path("isPaid").asBoolean()).isFalse();
                assertThat(channel.queueDeclarePassive(reversals).getMessageCount()).isZero();
                assertThat(channel.queueDeclarePassive(completedRefunds).getMessageCount()).isZero();
            });
            assertThat(state(prescriptionId).path("outbox")).noneSatisfy(e->assertThat(e.path("routing_key").asText()).isEqualTo("prescription.filled"));
            assertTransportDrained();
            assertThat(channel.queueDeclarePassive(reversals).getMessageCount()).isZero();
            assertThat(channel.queueDeclarePassive(completedRefunds).getMessageCount()).isZero();
            recordSnapshot("COMPENSATION_"+scenario,prescriptionId,invoiceId,state(prescriptionId));
        }
    }

    @Test void realListenerConflictingPaymentRetainsOriginalBytesInDlqWithoutChangingCommittedEffects()throws Exception {
        try(Connection connection=brokerConnection();Channel channel=connection.createChannel()) {
            String payments=probe(channel,"payment.completed");UUID drugId=createDrug(),prescriptionId=createPrescription(drugId);
            String invoiceId=awaitInvoice(prescriptionId).path("invoiceId").asText();pay(invoiceId);
            awaitCompletion(prescriptionId,invoiceId);awaitReport(drugId,1,"30.00");
            GetResponse payment=captured(channel,payments);
            var conflicting=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(payment.getBody());conflicting.put("totalAmount",31);
            byte[] bytes=mapper.writeValueAsBytes(conflicting);
            // Direct to Pharmacy's test-owned input queue: foreign consumers must not receive poison test data.
            channel.basicPublish("","pharmacy.q",payment.getProps(),bytes);
            GetResponse dead=captured(channel,"pharmacy.dlq");assertThat(dead.getBody()).isEqualTo(bytes);
            assertThat(state(prescriptionId).path("receipts").get(0).asText()).isEqualTo("DISPENSED");
            assertStableResult(drugId,prescriptionId,invoiceId,1,"30.00");
            recordSnapshot("CONFLICT_RETAINED_DLQ",prescriptionId,invoiceId,state(prescriptionId));
            recordMessageProof("CONFLICT_RETAINED_DLQ",dead);
        }
    }

    @Test void realListenerDatabaseRollbackRetainsReceiptAndDlqThenRedriveCommitsOnce()throws Exception {
        UUID drugId=createDrug(),prescriptionId=createPrescription(drugId);
        String invoiceId=awaitInvoice(prescriptionId).path("invoiceId").asText();
        acceptance("/outbox-rejection/true","POST",null);
        try(Connection connection=brokerConnection();Channel channel=connection.createChannel()) {
            pay(invoiceId);GetResponse failed=captured(channel,"pharmacy.dlq");
            JsonNode pending=state(prescriptionId);
            assertThat(pending.path("stock").get(0).asInt()).isEqualTo(20);
            assertThat(pending.path("slip").path("status").asText()).isEqualTo("PENDING");
            assertThat(pending.path("receipts").get(0).asText()).isEqualTo("RECEIVED");
            assertThat(pending.path("reservations").get(0).asText()).isEqualTo("RESERVED");
            assertThat(pending.path("outbox")).noneSatisfy(e->assertThat(e.path("routing_key").asText()).isEqualTo("prescription.filled"));
            recordSnapshot("DB_ROLLBACK_RETAINED_DLQ",prescriptionId,invoiceId,pending);
            recordMessageProof("DB_ROLLBACK_RETAINED_DLQ",failed);
            acceptance("/outbox-rejection/false","POST",null);
            channel.basicPublish("","pharmacy.q",failed.getProps(),failed.getBody());
            awaitCompletion(prescriptionId,invoiceId);awaitReport(drugId,1,"30.00");
            assertStableResult(drugId,prescriptionId,invoiceId,1,"30.00");
            recordSnapshot("DB_ROLLBACK_REDRIVEN",prescriptionId,invoiceId,state(prescriptionId));
        } finally {acceptance("/outbox-rejection/false","POST",null);}
    }

    /** Synthetic acceptance evidence only: allowlisted IDs/states/hashes, never JWTs or raw payloads. */
    private void recordSnapshot(String scenario, UUID prescriptionId, String invoiceId, JsonNode state)
            throws Exception {
        var evidence=mapper.createObjectNode();
        evidence.put("scenario",scenario).put("prescriptionId",prescriptionId.toString()).put("invoiceId",invoiceId);
        for(String field:List.of("stock","slip","receipts","reservations"))evidence.set(field,state.path(field));
        evidence.put("outboxCount",state.path("outbox").size());
        var outbox=evidence.putArray("outbox");
        for(var row:state.path("outbox"))outbox.addObject()
                .put("eventId",row.path("event_id").asText()).put("routingKey",row.path("routing_key").asText())
                .put("published",row.path("published").asBoolean())
                .put("payloadSha256",sha256(row.path("payload").asText().getBytes(StandardCharsets.UTF_8)));
        var invoice=get(BILLING,8086,"/api/v1/billing/invoices/"+invoiceId);
        evidence.put("billingSagaStatus",invoice.path("sagaStatus").asText())
                .put("billingIsPaid",invoice.path("isPaid").asBoolean());
        evidence.set("billingTotalAmount",invoice.path("totalAmount"));
        System.out.println("ACCEPTANCE_SNAPSHOT "+mapper.writeValueAsString(evidence));
    }

    private void recordMessageProof(String scenario, GetResponse message) throws Exception {
        System.out.println("ACCEPTANCE_MESSAGE "+mapper.writeValueAsString(Map.of(
                "scenario",scenario,"eventId",mapper.readTree(message.getBody()).path("eventId").asText(),
                "bodySha256",sha256(message.getBody()),"bodyLength",message.getBody().length)));
    }

    private static String sha256(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private JsonNode filledProof(JsonNode state) {
        var rows=new java.util.ArrayList<JsonNode>();state.path("outbox").forEach(e->{if(e.path("routing_key").asText().equals("prescription.filled"))rows.add(e);});
        assertThat(rows).hasSize(1);return rows.getFirst();
    }
    private JsonNode state(UUID prescription){try{return acceptance("/state/"+prescription,"GET",null);}catch(Exception failure){throw new AssertionError(failure);}}
    private JsonNode acceptance(String path,String method,String body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base(PHARMACY,8085)+"/api/v1/pharmacy/admin/acceptance"+path))
                .timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    private void awaitCompensated(String invoice) {await().atMost(Duration.ofSeconds(45)).untilAsserted(()->
            assertThat(get(BILLING,8086,"/api/v1/billing/invoices/"+invoice).path("sagaStatus").asText()).isEqualTo("REFUNDED"));}

    private UUID createDrug() throws Exception {
        String body = """
                {"drugName":"Distributed runtime test drug","unit":"tablet","price":10.00,
                 "stockQuantity":20,"expiryDate":"%s","lowStockThreshold":0}
                """.formatted(date.plusYears(1));
        return UUID.fromString(command(PHARMACY, 8085, "/api/v1/pharmacy/drugs", "POST", body, 201)
                .path("drugId").asText());
    }

    private UUID createPrescription(UUID drugId) throws Exception {
        String body = """
                {"recordId":"%s","patientId":"%s","doctorId":"%s","departmentId":"%s",
                 "prescribedDate":"%s","lines":[{"drugId":"%s","quantity":3,"dosage":"test-only"}]}
                """.formatted(UUID.randomUUID(), patientId, doctorId, departmentId, date, drugId);
        return UUID.fromString(command(PHARMACY, 8085, "/api/v1/pharmacy/prescriptions", "POST", body, 201)
                .path("prescriptionId").asText());
    }

    private JsonNode awaitInvoice(UUID prescriptionId) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            JsonNode invoices = get(BILLING, 8086, "/api/v1/billing/patient/" + patientId + "?size=100").path("content");
            assertThat(invoices.isArray()).isTrue();
            assertThat(invoices.size()).isEqualTo(1);
            assertThat(invoices.get(0).path("prescriptionId").asText()).isEqualTo(prescriptionId.toString());
        });
        return get(BILLING, 8086, "/api/v1/billing/patient/" + patientId + "?size=100").path("content").get(0);
    }

    private void pay(String invoiceId) throws Exception {
        command(BILLING, 8086, "/api/v1/billing/invoices/" + invoiceId + "/pay", "PUT",
                "{\"paymentMethod\":\"CASH\"}", 200);
    }

    private void awaitInvoiceNotice(String invoiceId) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var notices = get(NOTIFICATION, 8087, "/api/v1/notifications/patient/" + patientId + "?size=100")
                    .path("content");
            assertThat(notices.isArray()).isTrue();
            assertThat(notices).anySatisfy(notice -> {
                assertThat(notice.path("title").asText()).isEqualTo("Yêu cầu thanh toán");
                assertThat(notice.path("content").asText()).contains(invoiceId, "không phải biên nhận");
                assertThat(notice.path("channel").asText()).isEqualTo("IN_APP");
                assertThat(notice.path("status").asText()).isEqualTo("SENT");
            });
        });
    }

    private void awaitCompletion(UUID prescriptionId, String invoiceId) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(prescription(prescriptionId).path("dispenseStatus").asText()).isEqualTo("DISPENSED");
            assertThat(get(BILLING, 8086, "/api/v1/billing/invoices/" + invoiceId).path("sagaStatus").asText())
                    .isEqualTo("COMPLETED");
        });
    }

    private void awaitReport(UUID drugId, int fills, String revenue) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertReport(drugId, fills, revenue));
    }

    private void assertReport(UUID drugId, int fills, String revenue) {
        JsonNode daily = get(REPORT, 8088, "/api/v1/reports/daily?date=" + date + "&departmentId=" + departmentId);
        assertThat(daily.path("prescriptionCount").asInt()).isEqualTo(fills);
        assertThat(daily.path("revenue").decimalValue()).isEqualByComparingTo(new BigDecimal(revenue));
        JsonNode top = get(REPORT, 8088, "/api/v1/reports/top-medicines?fromDate=" + date
                + "&toDate=" + date + "&departmentId=" + departmentId);
        assertThat(top.isArray()).isTrue();
        assertThat(top.size()).isEqualTo(1);
        assertThat(top.get(0).path("drugId").asText()).isEqualTo(drugId.toString());
        assertThat(top.get(0).path("totalQuantity").asInt()).isEqualTo(3);
    }

    private void assertStableResult(UUID drugId, UUID prescriptionId, String invoiceId, int fills, String revenue) {
        // Observe convergence, then keep asserting through asynchronous duplicate processing.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(drug(drugId).path("stockQuantity").asInt()).isEqualTo(17);
            assertThat(prescription(prescriptionId).path("dispenseStatus").asText()).isEqualTo("DISPENSED");
            assertThat(get(BILLING, 8086, "/api/v1/billing/invoices/" + invoiceId).path("sagaStatus").asText())
                    .isEqualTo("COMPLETED");
            assertReport(drugId, fills, revenue);
        });
        assertTransportDrained();
    }

    private void assertTransportDrained() {
        try (Connection connection = brokerConnection(); Channel channel = connection.createChannel()) {
            // Effects alone are insufficient: a valid duplicate must not be quietly poisoned into a DLQ.
            await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                for (String queue : List.of("billing.q", "report.q", "notification.q", "pharmacy.q")) {
                    assertThat(channel.queueDeclarePassive(queue).getMessageCount()).as(queue).isZero();
                }
                for (String queue : List.of("billing.dlq", "report.dlq", "notification.dlq", "pharmacy.dlq")) {
                    assertThat(channel.queueDeclarePassive(queue).getMessageCount()).as(queue).isZero();
                }
            });
        } catch (Exception failure) {
            throw new AssertionError("Runtime transport did not drain cleanly", failure);
        }
    }

    private JsonNode drug(UUID id) { return get(PHARMACY, 8085, "/api/v1/pharmacy/drugs/" + id); }
    private JsonNode prescription(UUID id) { return get(PHARMACY, 8085, "/api/v1/pharmacy/prescriptions/" + id); }

    private JsonNode get(GenericContainer<?> app, int port, String path) {
        try { return command(app, port, path, "GET", null, 200); }
        catch (Exception failure) { throw new AssertionError("Runtime HTTP query unavailable: " + path, failure); }
    }

    private JsonNode command(GenericContainer<?> app, int port, String path, String method, String body, int status)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base(app, port) + path)).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + token()).header("Content-Type", "application/json")
                .header("X-Correlation-Id", correlationId)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        JsonNode envelope = mapper.readTree(response.body());
        assertThat(envelope.path("success").asBoolean()).isTrue();
        return envelope.path("data");
    }

    private String token() {
        return Jwts.builder().subject(accountId.toString()).claim("staffId", doctorId.toString())
                .claim("role", "ADMIN").claim("type", "access").issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private Connection brokerConnection() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(BROKER.getHost());
        factory.setPort(BROKER.getAmqpPort());
        factory.setUsername(BROKER.getAdminUsername());
        factory.setPassword(BROKER.getAdminPassword());
        return factory.newConnection();
    }

    private String probe(Channel channel, String routingKey) throws Exception {
        String queue = channel.queueDeclare("", false, true, true, Map.of()).getQueue();
        channel.queueBind(queue, EXCHANGE, routingKey);
        return queue;
    }

    private GetResponse captured(Channel channel, String queue) throws Exception {
        GetResponse[] result = new GetResponse[1];
        await().atMost(Duration.ofSeconds(20)).until(() -> (result[0] = channel.basicGet(queue, true)) != null);
        return result[0];
    }

    private void assertCorrelation(GetResponse message, String expected) throws Exception {
        assertThat(mapper.readTree(message.getBody()).path("correlationId").asText()).isEqualTo(expected);
    }

    private void publishNewDelivery(Channel channel, String routingKey, GetResponse original) throws Exception {
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(original.getBody());
        UUID deliveryId = UUID.randomUUID();
        payload.put("eventId", deliveryId.toString());
        publish(channel, routingKey, mapper.writeValueAsBytes(payload), original.getProps().builder()
                .messageId(deliveryId.toString()).build());
    }

    private void publish(Channel channel, String routingKey, byte[] payload, AMQP.BasicProperties properties)
            throws Exception {
        channel.confirmSelect();
        channel.basicPublish(EXCHANGE, routingKey, true, properties, payload);
        channel.waitForConfirmsOrDie(5000);
    }

    private static PostgreSQLContainer<?> database(String alias) {
        return new PostgreSQLContainer<>("postgres:16-alpine").withNetwork(NETWORK).withNetworkAliases(alias);
    }

    private static GenericContainer<?> service(String name, int port, PostgreSQLContainer<?> database, String alias) {
        Path jar = Path.of("../" + name + "-service/target/" + name + "-service-0.0.1-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Package current " + name + " before distributed acceptance");
        var image = new ImageFromDockerfile().withFileFromPath("app.jar", jar);
        if(name.equals("pharmacy")) image.withFileFromPath("support",Path.of("target/test-classes/com/mediflow/acceptancefixture"));
        image.withDockerfileFromBuilder(builder->{
            builder.from("eclipse-temurin:21-jre-alpine").copy("app.jar","/app/app.jar");
            if(name.equals("pharmacy"))builder.copy("support/","/test-support/com/mediflow/acceptancefixture/");
            builder.user("10001");
            if(name.equals("pharmacy"))builder.entryPoint("java","-Dloader.path=/test-support",
                    "-Dloader.main=com.mediflow.acceptancefixture.PharmacyFaultBootApplication","-cp","/app/app.jar",
                    "org.springframework.boot.loader.launch.PropertiesLauncher");
            else builder.entryPoint("java","-jar","/app/app.jar");
            builder.build();
        });
        return new GenericContainer<>(image).dependsOn(database, BROKER).withNetwork(NETWORK).withExposedPorts(port)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://" + alias + ":5432/" + database.getDatabaseName())
                .withEnv("MEDIFLOW_DB_USER", database.getUsername()).withEnv("MEDIFLOW_DB_PASSWORD", database.getPassword())
                .withEnv("MEDIFLOW_RABBIT_HOST", "rabbit").withEnv("MEDIFLOW_RABBIT_USER", BROKER.getAdminUsername())
                .withEnv("MEDIFLOW_RABBIT_PASSWORD", BROKER.getAdminPassword()).withEnv("MEDIFLOW_JWT_SECRET", SECRET)
                .withEnv("EUREKA_CLIENT_ENABLED", "false").withEnv("MEDIFLOW_CARE_FINANCE_V2", "false")
                .withEnv("MEDIFLOW_PHARMACY_OUTBOX_LEASE_SECONDS","5")
                .withEnv("JAVA_TOOL_OPTIONS", "-Xms64m -Xmx256m")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(180)));
    }

    private void awaitHealthy(GenericContainer<?> app, int port) {
        await().atMost(Duration.ofSeconds(120)).ignoreExceptions().until(() ->
                http.send(HttpRequest.newBuilder(URI.create(base(app, port) + "/actuator/health"))
                        .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200);
    }

    private static String base(GenericContainer<?> app, int port) {
        // Host ports may change when the same container restarts; inspect rather than reuse a cache.
        int mapped = Integer.parseInt(app.getDockerClient().inspectContainerCmd(app.getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(port))[0]
                .getHostPortSpec());
        return "http://" + app.getHost() + ":" + mapped;
    }

    @AfterAll
    static void shutdown() {
        PHARMACY.stop(); BILLING.stop(); REPORT.stop(); NOTIFICATION.stop();
        PHARMACY_DB.stop(); BILLING_DB.stop(); REPORT_DB.stop(); NOTIFICATION_DB.stop(); BROKER.stop(); NETWORK.close();
    }
}
