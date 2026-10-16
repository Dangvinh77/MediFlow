package com.mediflow.pharmacy;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Packaged MVP guards and original producer-fixture recovery. The live issuer chain is explicitly
 * opt-in until Billing publishes its new V1 cohort; a trusted kernel seed is not Core/Clinical proof.
 */
@Testcontainers
class PharmacyOutpatientRuntimeAcceptanceIT {
    private static final String SECRET="isolated-outpatient-runtime-test-secret-at-least-32-bytes";
    private static final Network NETWORK=Network.newNetwork();
    @Container static final PostgreSQLContainer<?> PHARMACY_DB=database("pharmacy-db");
    @Container static final PostgreSQLContainer<?> BILLING_DB=database("billing-db");
    @Container static final PostgreSQLContainer<?> REPORT_DB=database("report-db");
    @Container static final RabbitMQContainer BROKER=new RabbitMQContainer("rabbitmq:3.13-alpine").withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final GenericContainer<?> BILLING=service("billing",8086,BILLING_DB,"billing-db")
            .withEnv("MEDIFLOW_BILLING_LEDGER_ENABLED","true")
            .withEnv("MEDIFLOW_BILLING_PRESCRIPTION_CHARGE_CONSUMER_ENABLED","true")
            .withEnv("MEDIFLOW_BILLING_PRESCRIPTION_PRICE_CODES","DRUG")
            .withEnv("MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED","true");
    @Container static final GenericContainer<?> PHARMACY=service("pharmacy",8085,PHARMACY_DB,"pharmacy-db")
            .withEnv("SPRING_PROFILES_ACTIVE","outpatient-mvp")
            .withEnv("MEDIFLOW_CARE_FINANCE_V2","true")
            .withEnv("MEDIFLOW_PHARMACY_IDENTITY_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_CLINICAL_CONTEXT_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_CLEARANCE_CONSUMER_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_CLEARANCE_AUTHORITY_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_OUTPATIENT_API_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_OUTPATIENT_EVENTS_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_OUTPATIENT_EVENTS_ACCEPTANCE_REFERENCE","ISOLATED_ACCEPTANCE_ONLY")
            .withEnv("MEDIFLOW_PHARMACY_ADMISSION_CONSUMER_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_ADMISSION_AUTHORITY_ENABLED","true")
            .withEnv("MEDIFLOW_PHARMACY_BILLING_BASE_URL","http://billing:8086");
    @Container static final GenericContainer<?> REPORT=service("report",8088,REPORT_DB,"report-db")
            .withEnv("SPRING_PROFILES_ACTIVE","outpatient-mvp").withEnv("MEDIFLOW_CARE_FINANCE_V2","true")
            .withEnv("MEDIFLOW_REPORT_FINANCIAL_READ_ENABLED","true").withEnv("MEDIFLOW_REPORT_OPERATIONAL_CONSUMER_ENABLED","true")
            .withEnv("MEDIFLOW_REPORT_FINANCIAL_PUBLICATION_ENABLED","true").withEnv("MEDIFLOW_REPORT_SETTLEMENT_CONSUMER_ENABLED","true")
            .withEnv("MEDIFLOW_REPORT_OPERATIONAL_PUBLICATION_ENABLED","true");
    private final ObjectMapper mapper=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final UUID account=UUID.randomUUID();

    @Test void packagedMvpExcludesDeferredAndLegacyConsumersDespiteGlobalAndOldGateOverrides()throws Exception {
        var mode=raw(PHARMACY,8085,"/api/v1/pharmacy/admin/acceptance/mode","GET",null,200);
        assertThat(mode.path("application").asText()).isEqualTo("pharmacy-service");
        for(String field:List.of("admissionPorts","admissionConsumers","inpatientClients","legacyPaymentConsumers"))assertThat(mode.path(field).asInt()).as(field).isZero();
        assertThat(mode.path("patientDestination").asText()).isEqualTo("core-service");
        assertThat(mode.path("organizationDestination").asText()).isEqualTo("core-service");
        assertThat(mode.path("openInView").asBoolean()).isFalse();
        try(var jar=new java.util.zip.ZipFile(Path.of("target/pharmacy-service-0.0.1-SNAPSHOT.jar").toFile())) {
            assertThat(jar.stream().map(java.util.zip.ZipEntry::getName)).noneMatch(name->name.contains("acceptancefixture"));
        }
        for(String path:List.of("/api/v1/reports/financial/daily?from=2026-10-01&to=2026-10-02",
                "/api/v1/reports/operations/daily?from=2026-10-01&to=2026-10-02",
                "/api/v1/reports/operations/surgery?from=2026-10-01&to=2026-10-02"))
            raw(REPORT,8088,path,"GET",null,404);
    }

    @Test void originalBillingGrantAndCashFixturesSurviveJvmRestartWithoutAutomaticDispensing()throws Exception {
        byte[] grant=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-prescription.json"));
        byte[] cash=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json"));
        JsonNode source=mapper.readTree(grant).path("payload");
        var drug=api(PHARMACY,8085,"/api/v1/pharmacy/drugs","POST",mapper.writeValueAsString(Map.of(
                "drugName","Original Billing fixture target","unit","tablet","price",100,"stockQuantity",20,
                "expiryDate",LocalDate.now().plusYears(1).toString(),"lowStockThreshold",0)),201);
        raw(PHARMACY,8085,"/api/v1/pharmacy/admin/acceptance/clearance-fixture-target","POST",mapper.writeValueAsString(Map.of(
                "prescriptionId",source.path("prescriptionId").asText(),"patientId",source.path("patientId").asText(),
                "episodeId",source.path("careEpisodeId").asText(),"drugId",drug.path("drugId").asText())),200);
        String path="/api/v1/pharmacy/admin/acceptance/state/"+source.path("prescriptionId").asText();
        PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
        try(Connection connection=connection();Channel channel=connection.createChannel()) {
            channel.confirmSelect();
            for(int copy=0;copy<2;copy++) {
                channel.basicPublish("mediflow.events","financial.clearance.granted",true,new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).build(),grant);
                channel.basicPublish("mediflow.events","payment.completed",true,new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(2).build(),cash);
            }
            channel.waitForConfirmsOrDie(5000);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(()->
                    assertThat(channel.queueDeclarePassive("pharmacy.financial-clearance.q").getMessageCount()).isEqualTo(2));
        } finally {PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();healthy(PHARMACY,8085);}
        await().atMost(Duration.ofSeconds(25)).untilAsserted(()->assertThat(raw(PHARMACY,8085,path,"GET",null,200).path("grants").asInt()).isOne());
        PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
        PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();healthy(PHARMACY,8085);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20)).untilAsserted(()->{
            var state=raw(PHARMACY,8085,path,"GET",null,200);
            assertThat(state.path("grants").asInt()).isOne();assertThat(state.path("receipts")).isEmpty();
            assertThat(state.path("stock").get(0).asInt()).isEqualTo(20);
            assertThat(state.path("slip").path("status").asText()).isEqualTo("PENDING");
            assertThat(state.path("outbox")).isEmpty();
        });
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="mediflow.acceptance.billing-v1-published",matches="true",
            disabledReason="Billing V1 source publication is HELD; run explicitly after producer owner acceptance. Never release foreign rows here.")
    void actualV1IssuerGrantSurvivesPharmacyJvmRestartAndDuplicatesNeverAutoDispense()throws Exception {
        try(Connection connection=connection();Channel channel=connection.createChannel()) {
            String invoices=probe(channel,"invoice.created"),payments=probe(channel,"payment.completed"),grants=probe(channel,"financial.clearance.granted"),filled=probe(channel,"prescription.filled");
            var drug=api(PHARMACY,8085,"/api/v1/pharmacy/drugs","POST",mapper.writeValueAsString(Map.of(
                    "drugName","Isolated V1 kernel seed","unit","tablet","price",10,"stockQuantity",20,
                    "expiryDate",LocalDate.now().plusYears(1).toString(),"lowStockThreshold",0)),201);
            UUID drugId=UUID.fromString(drug.path("drugId").asText());
            UUID patient=UUID.randomUUID(),episode=UUID.randomUUID();
            String body=mapper.writeValueAsString(Map.ofEntries(Map.entry("recordId",UUID.randomUUID()),Map.entry("patientId",patient),
                    Map.entry("doctorId",UUID.randomUUID()),Map.entry("departmentId",UUID.randomUUID()),
                    Map.entry("prescribedDate",LocalDate.now().toString()),Map.entry("lines",List.of(Map.of("drugId",drugId,"quantity",3,"dosage","isolated test"))),
                    Map.entry("careContractVersion",1),Map.entry("careContext","OUTPATIENT"),Map.entry("careEpisodeType","OUTPATIENT_VISIT"),
                    Map.entry("careEpisodeId",episode),Map.entry("priceCode","DRUG")));
            var created=raw(PHARMACY,8085,"/api/v1/pharmacy/admin/acceptance/outpatient-prescriptions","POST",body,200);
            UUID prescription=UUID.fromString(created.path("prescriptionId").asText());
            JsonNode invoice=mapper.readTree(captured(channel,invoices).getBody()).path("payload");
            assertThat(invoice.path("prescriptionId").asText()).isEqualTo(prescription.toString());
            assertThat(invoice.path("careEpisodeId").asText()).isEqualTo(episode.toString());
            assertThat(invoice.path("totalAmount").decimalValue()).isEqualByComparingTo("30.00");
            PHARMACY.getDockerClient().stopContainerCmd(PHARMACY.getContainerId()).withTimeout(0).exec();
            GetResponse cash,grant;
            try {
                api(BILLING,8086,"/api/v1/billing/payment-requests/"+invoice.path("paymentRequestId").asText()+"/payments","POST",
                        "{\"idempotencyKey\":\""+UUID.randomUUID()+"\",\"amount\":30.00,\"currency\":\"VND\",\"paymentMethod\":\"CASH\"}",200);
                cash=captured(channel,payments);grant=captured(channel,grants);
                assertThat(mapper.readTree(grant.getBody()).path("payload").path("prescriptionId").asText()).isEqualTo(prescription.toString());
                await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(channel.queueDeclarePassive("pharmacy.financial-clearance.q").getMessageCount()).isGreaterThanOrEqualTo(1));
            } finally {PHARMACY.getDockerClient().startContainerCmd(PHARMACY.getContainerId()).exec();healthy(PHARMACY,8085);}
            String statePath="/api/v1/pharmacy/admin/acceptance/state/"+prescription;
            await().atMost(Duration.ofSeconds(25)).untilAsserted(()->assertThat(raw(PHARMACY,8085,statePath,"GET",null,200).path("grants").asInt()).isOne());
            for(int i=0;i<2;i++) {
                channel.basicPublish("mediflow.events","financial.clearance.granted",true,grant.getProps(),grant.getBody());
                channel.basicPublish("mediflow.events","payment.completed",true,cash.getProps(),cash.getBody());
            }
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20)).untilAsserted(()->{
                JsonNode state=raw(PHARMACY,8085,statePath,"GET",null,200);
                assertThat(state.path("stock").get(0).asInt()).isEqualTo(20);
                assertThat(state.path("slip").path("status").asText()).isEqualTo("PENDING");
                assertThat(state.path("receipts")).isEmpty();assertThat(state.path("grants").asInt()).isOne();
            });
            assertThat(channel.basicGet(filled,true)).isNull();
            var result=api(PHARMACY,8085,"/api/v1/pharmacy/prescriptions/"+prescription+"/dispense","PUT",null,200);
            assertThat(result.path("status").asText()).isEqualTo("DISPENSED");
            var proof=captured(channel,filled);assertThat(mapper.readTree(proof.getBody()).path("payload").path("prescriptionId").asText()).isEqualTo(prescription.toString());
            var retry=api(PHARMACY,8085,"/api/v1/pharmacy/prescriptions/"+prescription+"/dispense","PUT",null,200);
            assertThat(retry.path("dispenseId")).isEqualTo(result.path("dispenseId"));
            assertThat(raw(PHARMACY,8085,statePath,"GET",null,200).path("stock").get(0).asInt()).isEqualTo(17);
            assertThat(channel.queueDeclarePassive("pharmacy.financial-clearance.dlq").getMessageCount()).isZero();
        }
    }

    private JsonNode api(GenericContainer<?> app,int port,String path,String method,String body,int status)throws Exception {
        var root=raw(app,port,path,method,body,status);assertThat(root.path("success").asBoolean()).as(root.toString()).isTrue();return root.path("data");
    }
    private JsonNode raw(GenericContainer<?> app,int port,String path,String method,String body,int status)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base(app,port)+path)).timeout(Duration.ofSeconds(8))
                .header("Authorization","Bearer "+token()).header("Content-Type","application/json").header("X-Correlation-Id","isolated-v1-runtime")
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(path+": "+response.body()).isEqualTo(status);
        return mapper.readTree(response.body());
    }
    private String token(){return Jwts.builder().subject(account.toString()).claim("role","ADMIN").claim("type","access")
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
    private Connection connection()throws Exception {var f=new ConnectionFactory();f.setHost(BROKER.getHost());f.setPort(BROKER.getAmqpPort());
        f.setUsername(BROKER.getAdminUsername());f.setPassword(BROKER.getAdminPassword());return f.newConnection();}
    private String probe(Channel c,String key)throws Exception {String q=c.queueDeclare("",false,true,true,Map.of()).getQueue();c.queueBind(q,"mediflow.events",key);return q;}
    private GetResponse captured(Channel c,String queue)throws Exception {var result=new GetResponse[1];await().atMost(Duration.ofSeconds(35)).until(()->(result[0]=c.basicGet(queue,true))!=null);return result[0];}
    private static PostgreSQLContainer<?> database(String alias){return new PostgreSQLContainer<>("postgres:16-alpine").withNetwork(NETWORK).withNetworkAliases(alias);}
    private static GenericContainer<?> service(String name,int port,PostgreSQLContainer<?> db,String alias) {
        Path jar=Path.of("../"+name+"-service/target/"+name+"-service-0.0.1-SNAPSHOT.jar");
        if(!Files.isRegularFile(jar))throw new IllegalStateException("Package current "+name+" first");
        var image=new ImageFromDockerfile().withFileFromPath("app.jar",jar);
        if(name.equals("pharmacy"))image.withFileFromPath("support",Path.of("target/test-classes/com/mediflow/acceptancefixture"));
        image.withDockerfileFromBuilder(b->{b.from("eclipse-temurin:21-jre-alpine").copy("app.jar","/app/app.jar");
            if(name.equals("pharmacy"))b.copy("support/","/test-support/com/mediflow/acceptancefixture/");b.user("10001");
            if(name.equals("pharmacy"))b.entryPoint("java","-Dloader.path=/test-support","-Dloader.main=com.mediflow.acceptancefixture.PharmacyFaultBootApplication","-cp","/app/app.jar","org.springframework.boot.loader.launch.PropertiesLauncher");
            else b.entryPoint("java","-jar","/app/app.jar");b.build();});
        return new GenericContainer<>(image).dependsOn(db,BROKER).withNetwork(NETWORK).withNetworkAliases(name).withExposedPorts(port)
                .withEnv("SPRING_DATASOURCE_URL","jdbc:postgresql://"+alias+":5432/"+db.getDatabaseName())
                .withEnv("MEDIFLOW_DB_USER",db.getUsername()).withEnv("MEDIFLOW_DB_PASSWORD",db.getPassword())
                .withEnv("MEDIFLOW_RABBIT_HOST","rabbit").withEnv("MEDIFLOW_RABBIT_USER",BROKER.getAdminUsername())
                .withEnv("MEDIFLOW_RABBIT_PASSWORD",BROKER.getAdminPassword()).withEnv("MEDIFLOW_JWT_SECRET",SECRET)
                .withEnv("EUREKA_CLIENT_ENABLED","false").withEnv("JAVA_TOOL_OPTIONS","-Xms64m -Xmx256m")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200).withStartupTimeout(Duration.ofSeconds(180)));
    }
    private void healthy(GenericContainer<?> app,int port){await().atMost(Duration.ofSeconds(100)).ignoreExceptions().until(()->
            http.send(HttpRequest.newBuilder(URI.create(base(app,port)+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200);}
    private static String base(GenericContainer<?> app,int port){var binding=app.getDockerClient().inspectContainerCmd(app.getContainerId()).exec().getNetworkSettings().getPorts()
            .getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(port))[0];return "http://"+app.getHost()+":"+binding.getHostPortSpec();}
    @AfterAll static void close(){PHARMACY.stop();BILLING.stop();REPORT.stop();PHARMACY_DB.stop();BILLING_DB.stop();REPORT_DB.stop();BROKER.stop();NETWORK.close();}
}
