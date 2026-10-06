package com.mediflow.surgery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.support.SurgeryScheduledFixture;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Explicit Failsafe profile: packaged current apps, separate JVMs, real Eureka, PG and Rabbit. */
@Testcontainers
class SurgeryGatewayRuntimeAcceptanceIT {
    private static final String SECRET="surgery-runtime-acceptance-only-secret-at-least-32-bytes";
    private static final Network NETWORK=Network.newNetwork();
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("surgery_runtime_test").withNetwork(NETWORK).withNetworkAliases("postgres");
    @Container static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final PostgreSQLContainer<?> ORGANIZATION_PG=new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("organization_runtime_test").withNetwork(NETWORK).withNetworkAliases("organization-db");
    @Container static final GenericContainer<?> EUREKA=app("eureka-server",8761,"eureka")
            .withEnv("SERVER_PORT","8761");
    @Container static final GenericContainer<?> ORGANIZATION=app("organization-service",8089,"organization")
            .dependsOn(ORGANIZATION_PG,RABBIT,EUREKA)
            .withEnv("SPRING_DATASOURCE_URL","jdbc:postgresql://organization-db:5432/organization_runtime_test")
            .withEnv("MEDIFLOW_DB_USER",ORGANIZATION_PG.getUsername())
            .withEnv("MEDIFLOW_DB_PASSWORD",ORGANIZATION_PG.getPassword())
            .withEnv("MEDIFLOW_RABBIT_HOST","rabbit").withEnv("MEDIFLOW_RABBIT_USER",RABBIT.getAdminUsername())
            .withEnv("MEDIFLOW_RABBIT_PASSWORD",RABBIT.getAdminPassword())
            .withEnv("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE","http://eureka:8761/eureka/")
            .withEnv("EUREKA_INSTANCE_HOSTNAME","organization")
            .withEnv("EUREKA_INSTANCE_INITIALINFOREPLICATIONINTERVALSECONDS","1")
            .withEnv("MEDIFLOW_ORGANIZATION_SURGERY_AUTHORITY_OUTBOX_ENABLED","true")
            .withEnv("MEDIFLOW_ORGANIZATION_SURGERYAUTHORITY_OUTBOXDELAYMS","200");
    @Container static final GenericContainer<?> SURGERY=app("surgery-service",8091,"surgery")
            .dependsOn(PG,RABBIT,EUREKA,ORGANIZATION)
            .withEnv("JAVA_TOOL_OPTIONS","-Xms64m -Xmx256m")
            .withEnv("SPRING_DATASOURCE_URL","jdbc:postgresql://postgres:5432/surgery_runtime_test")
            .withEnv("MEDIFLOW_DB_USER",PG.getUsername()).withEnv("MEDIFLOW_DB_PASSWORD",PG.getPassword())
            .withEnv("MEDIFLOW_RABBIT_HOST","rabbit")
            .withEnv("MEDIFLOW_RABBIT_USER",RABBIT.getAdminUsername())
            .withEnv("MEDIFLOW_RABBIT_PASSWORD",RABBIT.getAdminPassword())
            .withEnv("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE","http://eureka:8761/eureka/")
            .withEnv("EUREKA_INSTANCE_HOSTNAME","surgery")
            .withEnv("EUREKA_INSTANCE_INITIALINFOREPLICATIONINTERVALSECONDS","1")
            .withEnv("EUREKA_CLIENT_REGISTRYFETCHINTERVALSECONDS","1")
            .withEnv("SPRING_CLOUD_LOADBALANCER_CACHE_ENABLED","false")
            .withEnv("MEDIFLOW_FEATURES_SURGERY_ENABLED","true")
            .withEnv("MEDIFLOW_SURGERY_MESSAGING_CONSUMERS_ENABLED","true")
            .withEnv("MEDIFLOW_SURGERY_MESSAGING_ORGANIZATIONAUTHORITY_ENABLED","true")
            .withEnv("MEDIFLOW_SURGERY_MESSAGING_ORGANIZATIONAUTHORITY_POLLINTERVALMS","200")
            .withEnv("MEDIFLOW_SURGERY_MESSAGING_ORGANIZATIONAUTHORITY_INITIALDELAYMS","500");
    @Container static final GenericContainer<?> GATEWAY=app("gateway",8080,"gateway")
            .dependsOn(EUREKA,SURGERY)
            .withEnv("SERVER_PORT","8080")
            .withEnv("EUREKA_CLIENT_SERVICEURL_DEFAULTZONE","http://eureka:8761/eureka/")
            .withEnv("EUREKA_CLIENT_REGISTRYFETCHINTERVALSECONDS","1")
            .withEnv("EUREKA_INSTANCE_HOSTNAME","gateway")
            .withEnv("SPRING_CLOUD_LOADBALANCER_CACHE_ENABLED","false")
            .withEnv("MEDIFLOW_GATEWAY_SURGERY_ENABLED","true");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper=new ObjectMapper();

    @BeforeEach void waitForCurrentDiscoveryRegistration() {
        surgeryDb().execute("TRUNCATE surgery_case CASCADE"); // Only the isolated runtime test DB.
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(request(GATEWAY,8080,"/api/v1/surgery/cases","ADMIN",null,null).statusCode())
                        .isEqualTo(200));
    }

    @Test void actualDiscoveryRouteReadsOwnedDbAndPreservesCorrelation() throws Exception {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            var response=request(GATEWAY,8080,"/api/v1/surgery/cases?page=0&size=20","ADMIN",null,null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(response.body()).path("data").path("content").isEmpty()).isTrue();
        });
        String correlation=UUID.randomUUID().toString();
        var response=request(GATEWAY,8080,"/api/v1/surgery/cases","ADMIN",correlation,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-Id")).contains(correlation);
        assertThat(mapper.readTree(response.body()).path("correlationId").asText()).isEqualTo(correlation);
        var registry=http.send(HttpRequest.newBuilder(URI.create(base(EUREKA,8761)+"/eureka/apps/SURGERY-SERVICE"))
                .header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(registry.statusCode()).isEqualTo(200);
        assertThat(registry.body()).contains("UP","surgery");
        assertThat(SURGERY.execInContainer("sh","-c","id -u").getStdout().trim()).isEqualTo("10001");
    }

    @Test void bothBoundariesDenyWrongRoleAndReturnRealNotFoundNotStubSuccess() throws Exception {
        for (var container : new GenericContainer<?>[]{GATEWAY,SURGERY}) {
            int port=container==GATEWAY ? 8080 : 8091;
            assertThat(request(container,port,"/api/v1/surgery/cases",null,null,null).statusCode()).isEqualTo(401);
            assertThat(request(container,port,"/api/v1/surgery/cases","PATIENT",null,null).statusCode()).isEqualTo(403);
            var absent=request(container,port,"/api/v1/surgery/cases/"+UUID.randomUUID(),"ADMIN",null,null);
            assertThat(absent.statusCode()).isEqualTo(404);
            assertThat(mapper.readTree(absent.body()).path("error").path("code").asText()).isEqualTo("SURGERY_CASE_NOT_FOUND");
        }
        var internal=request(GATEWAY,8080,"/api/v1/inpatient/admissions/"+UUID.randomUUID()+"/lookup","ADMIN",null,null);
        assertThat(internal.statusCode()).isEqualTo(403);
        var financialInternal=request(GATEWAY,8080,"/api/v1/billing/financial-clearances/"+UUID.randomUUID()+"/lookup","ADMIN",null,null);
        assertThat(financialInternal.statusCode()).isEqualTo(403);
        assertThat(RABBIT.execInContainer("rabbitmqctl","list_queues","name").getStdout())
                .contains("surgery.financial-clearance.q", "surgery.organization-authority.q");
    }

    @Test void actualOrganizationAuthorityProtectsDraftAndReadScopeWithoutCreatingBookings() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        UUID department = UUID.fromString(createOrganization("/departments", """
                {"departmentName":"Runtime test department","abbreviation":"RT-%s","departmentType":"CLINICAL"}
                """.formatted(suffix)).path("departmentId").asText());
        UUID staff = UUID.fromString(createOrganization("/staff", """
                {"fullName":"Runtime test surgeon","departmentId":"%s","jobTitle":"DOCTOR","licenseNumber":"TEST-%s"}
                """.formatted(department, suffix)).path("staffId").asText());
        UUID room = UUID.fromString(createOrganization("/operating-rooms", """
                {"expectedRevision":0,"roomCode":"RT-%s","roomName":"Runtime room","departmentId":"%s","active":true,"reason":"Test fixture"}
                """.formatted(suffix.toUpperCase(), department)).path("roomId").asText());
        Instant starts = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant ends = starts.plusSeconds(3600);
        String grant = """
                {"expectedRevision":0,"active":true,"validFrom":"%s","validUntil":"%s","reason":"Explicit runtime test qualification"}
                """.formatted(starts.minusSeconds(300), ends.plusSeconds(300));
        var granted = send(ORGANIZATION,8089,"/api/v1/org/staff/"+staff+"/surgery-roles/PRIMARY_SURGEON",
                "ADMIN",null,"PUT",grant,null);
        assertThat(granted.statusCode()).as(granted.body()).isEqualTo(200);
        UUID caseId = seedOwnedCase(department, staff);
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            var board = send(GATEWAY,8080,"/api/v1/surgery/cases","DOCTOR",staff,"GET",null,null);
            assertThat(board.statusCode()).as(board.body()).isEqualTo(200);
            assertThat(mapper.readTree(board.body()).path("data").path("totalElements").asInt()).isEqualTo(1);
        });
        var preop = send(GATEWAY,8080,"/api/v1/surgery/cases/"+caseId+"/preop","ADMIN",null,"POST",
                "{\"expectedCaseRevision\":0}","runtime-preop");
        assertThat(preop.statusCode()).as(preop.body()).isEqualTo(200);
        String draft = """
                {"expectedCaseRevision":1,"expectedScheduleRevision":0,"roomId":"%s","startsAt":"%s","endsAt":"%s",
                 "team":[{"staffId":"%s","role":"PRIMARY_SURGEON"}]}
                """.formatted(room,starts,ends,staff);
        var prepared = send(GATEWAY,8080,"/api/v1/surgery/cases/"+caseId+"/schedule","ADMIN",null,"PUT",draft,"runtime-draft");
        assertThat(prepared.statusCode()).as(prepared.body()).isEqualTo(200);
        assertThat(mapper.readTree(prepared.body()).path("data").path("scheduleStatus").asText()).isEqualTo("DRAFT");
        assertThat(surgeryDb().queryForObject("SELECT count(*) FROM surgery_resource_reservation",Integer.class)).isZero();
        var revoked = send(ORGANIZATION,8089,"/api/v1/org/staff/"+staff+"/surgery-roles/PRIMARY_SURGEON",
                "ADMIN",null,"PUT",grant.replace("\"expectedRevision\":0","\"expectedRevision\":1")
                        .replace("\"active\":true","\"active\":false"),null);
        assertThat(revoked.statusCode()).as(revoked.body()).isEqualTo(200);
        var denied = send(GATEWAY,8080,"/api/v1/surgery/cases/"+caseId+"/schedule","ADMIN",null,"PUT",
                draft.replace("\"expectedCaseRevision\":1","\"expectedCaseRevision\":2")
                        .replace("\"expectedScheduleRevision\":0","\"expectedScheduleRevision\":1"),"runtime-revoked");
        assertThat(denied.statusCode()).as(denied.body()).isEqualTo(422);
        assertThat(mapper.readTree(denied.body()).path("error").path("code").asText()).isEqualTo("SURGERY_STAFF_INELIGIBLE");
        var detail = send(GATEWAY,8080,"/api/v1/surgery/cases/"+caseId,"DOCTOR",staff,"GET",null,null);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat(mapper.readTree(detail.body()).path("data").path("caseDetails").path("revision").asInt()).isEqualTo(2);
        assertThat(mapper.readTree(detail.body()).path("data").path("plannedSchedule").path("revision").asInt()).isEqualTo(1);
    }

    @Test void roomAuthorityChange_realProducerInvalidatesOnlyItsExactScheduledCase() throws Exception {
        var authority = authorityFixture();
        var unrelated = authorityFixture();
        var scheduled = SurgeryScheduledFixture.seed(surgeryDb(), authority.department(), authority.room(), authority.staff(), authority.starts(), authority.ends());
        var untouched = SurgeryScheduledFixture.seed(surgeryDb(), unrelated.department(), unrelated.room(), unrelated.staff(), unrelated.starts(), unrelated.ends());
        String correlation = UUID.randomUUID().toString();
        var response = send(ORGANIZATION,8089,"/api/v1/org/operating-rooms/" + authority.room(), "ADMIN",null,"PUT",
                """
                {"expectedRevision":1,"roomCode":"%s","roomName":"Runtime room","departmentId":"%s","active":false,"reason":"Runtime room withdrawn"}
                """.formatted(authority.roomCode(),authority.department()),null,correlation);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertInvalidated(scheduled, authority.room(), "ROOM", correlation);
        assertThat(surgeryDb().queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",String.class,untouched.caseId())).isEqualTo("SCHEDULED");
        assertThat(surgeryDb().queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",String.class,untouched.caseId()))
                .containsExactly("RESERVED","RESERVED");
    }

    @Test void staffCapabilityRevocation_realProducerAndConsumerReplayDoNotDuplicateInvalidation() throws Exception {
        var authority = authorityFixture();
        var scheduled = SurgeryScheduledFixture.seed(surgeryDb(),authority.department(),authority.room(),authority.staff(),authority.starts(),authority.ends());
        String correlation = UUID.randomUUID().toString();
        revokeCapability(authority,correlation);
        assertInvalidated(scheduled,authority.staff(),"STAFF_CAPABILITY",correlation);
        byte[] delivered = surgeryDb().queryForObject("""
                SELECT i.payload FROM surgery_inbox i JOIN surgery_authority_change a USING(event_id)
                WHERE a.reference_id=? AND a.reference_kind='STAFF_CAPABILITY' AND a.source_revision=2
                """,byte[].class,authority.staff());
        // The semantic replay gets its own inbox marker. Also await zero unacknowledged
        // deliveries: a later marker alone would not prove the exact duplicate was ACKed.
        var event = mapper.readTree(delivered);
        UUID barrier = UUID.randomUUID();
        ((com.fasterxml.jackson.databind.node.ObjectNode)event).put("eventId",barrier.toString());
        try (var connection = rabbitConnection(); var channel = connection.createChannel()) {
            channel.confirmSelect();
            var properties = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                    .contentType("application/json").deliveryMode(2).build();
            channel.basicPublish("mediflow.events","organization.surgery.authority.changed",true,properties,delivered);
            channel.basicPublish("mediflow.events","organization.surgery.authority.changed",true,properties,mapper.writeValueAsBytes(event));
            channel.waitForConfirmsOrDie(5000);
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(surgeryDb()
                .queryForObject("SELECT status FROM surgery_inbox WHERE event_id=?",String.class,barrier)).isEqualTo("APPLIED"));
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            var queues = RABBIT.execInContainer("rabbitmqctl","list_queues","name","messages_ready","messages_unacknowledged");
            assertThat(queues.getExitCode()).isZero();
            var replayQueue = queues.getStdout().lines().filter(line -> line.startsWith("surgery.organization-authority.q\t"))
                    .findFirst().orElseThrow();
            assertThat(replayQueue.trim().split("\\s+")).containsExactly("surgery.organization-authority.q","0","0");
        });
        try (var connection = rabbitConnection(); var channel = connection.createChannel()) {
            assertThat(channel.queueDeclarePassive("surgery.organization-authority.dlq").getMessageCount()).isZero();
        }
        assertThat(surgeryDb().queryForObject("SELECT count(*) FROM surgery_inbox_conflict",Integer.class)).isZero();
        assertThat(surgeryDb().queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",Long.class,scheduled.caseId())).isEqualTo(4);
        assertThat(surgeryDb().queryForObject("SELECT count(*) FROM surgery_authority_invalidation WHERE surgery_case_id=?",Integer.class,scheduled.caseId())).isEqualTo(1);
        assertThat(surgeryDb().queryForObject("SELECT count(*) FROM surgery_revision_history WHERE surgery_case_id=? AND change_code='ORGANIZATION_AUTHORITY_CHANGED'",Integer.class,scheduled.caseId())).isEqualTo(1);
    }

    @Test void consumerJvmRestart_recoversDurableProducerMessageWithoutLosingBookingsOrHistory() throws Exception {
        var authority = authorityFixture();
        var scheduled = SurgeryScheduledFixture.seed(surgeryDb(),authority.department(),authority.room(),authority.staff(),authority.starts(),authority.ends());
        String correlation = UUID.randomUUID().toString();
        SURGERY.getDockerClient().stopContainerCmd(SURGERY.getContainerId()).withTimeout(10).exec();
        try {
            revokeCapability(authority,correlation);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                try (var connection = rabbitConnection(); var channel = connection.createChannel()) {
                    var queue = channel.queueDeclarePassive("surgery.organization-authority.q");
                    assertThat(queue.getConsumerCount()).isZero(); assertThat(queue.getMessageCount()).isGreaterThan(0);
                }
            });
            assertThat(surgeryDb().queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",String.class,scheduled.caseId())).isEqualTo("SCHEDULED");
            assertThat(surgeryDb().queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?",String.class,scheduled.caseId())).containsExactly("RESERVED","RESERVED");
        } finally {
            SURGERY.getDockerClient().startContainerCmd(SURGERY.getContainerId()).exec();
            // The restarted JVM closes connections until HTTP is actually listening. Retry only
            // transport failures during this bounded health wait, never business assertions.
            await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                    .ignoreExceptionsMatching(failure -> failure instanceof java.io.IOException).untilAsserted(() -> {
                var health = http.send(HttpRequest.newBuilder(URI.create(base(SURGERY,8091)+"/actuator/health"))
                        .timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(health.statusCode()).isEqualTo(200);
            });
        }
        assertInvalidated(scheduled,authority.staff(),"STAFF_CAPABILITY",correlation);
    }

    private AuthorityFixture authorityFixture() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0,8).toUpperCase(java.util.Locale.ROOT);
        UUID department = UUID.fromString(createOrganization("/departments","""
                {"departmentName":"Event runtime department","abbreviation":"EV-%s","departmentType":"CLINICAL"}
                """.formatted(suffix)).path("departmentId").asText());
        UUID staff = UUID.fromString(createOrganization("/staff","""
                {"fullName":"Event runtime surgeon","departmentId":"%s","jobTitle":"DOCTOR","licenseNumber":"EVENT-%s"}
                """.formatted(department,suffix)).path("staffId").asText());
        String roomCode = "EV-"+suffix;
        UUID room = UUID.fromString(createOrganization("/operating-rooms","""
                {"expectedRevision":0,"roomCode":"%s","roomName":"Runtime room","departmentId":"%s","active":true,"reason":"Runtime reference fixture"}
                """.formatted(roomCode,department)).path("roomId").asText());
        Instant starts = Instant.now().plusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS), ends=starts.plusSeconds(3600);
        String decision = """
                {"expectedRevision":0,"active":true,"validFrom":"%s","validUntil":"%s","reason":"Explicit runtime qualification"}
                """.formatted(starts.minusSeconds(300),ends.plusSeconds(300));
        var response = send(ORGANIZATION,8089,"/api/v1/org/staff/"+staff+"/surgery-roles/PRIMARY_SURGEON","ADMIN",null,"PUT",decision,null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        // Initial revisions must be consumed before seeding a pre-start snapshot; otherwise the
        // create/grant hints themselves could invalidate it before the mutation under test.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(surgeryDb().queryForObject(
                "SELECT count(*) FROM surgery_authority_change WHERE reference_id IN (?,?) AND source_revision=1",Integer.class,room,staff)).isEqualTo(2));
        return new AuthorityFixture(department,staff,room,roomCode,starts,ends,decision);
    }
    private void revokeCapability(AuthorityFixture authority,String correlation) throws Exception {
        var response = send(ORGANIZATION,8089,"/api/v1/org/staff/"+authority.staff()+"/surgery-roles/PRIMARY_SURGEON","ADMIN",null,"PUT",
                authority.decision().replace("\"expectedRevision\":0","\"expectedRevision\":1").replace("\"active\":true","\"active\":false"),null,correlation);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
    private void assertInvalidated(SurgeryScheduledFixture.Fixture scheduled,UUID reference,String kind,String correlation) {
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            assertThat(surgeryDb().queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?",String.class,scheduled.caseId())).isEqualTo("PREOP_IN_PROGRESS");
            assertThat(surgeryDb().queryForObject("SELECT count(*) FROM surgery_authority_invalidation WHERE surgery_case_id=? AND status='APPLIED'",Integer.class,scheduled.caseId())).isEqualTo(1);
        });
        var db = surgeryDb();
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?",Long.class,scheduled.caseId())).isEqualTo(4);
        assertThat(db.queryForObject("SELECT readiness_snapshot_id FROM surgery_case WHERE surgery_case_id=?",UUID.class,scheduled.caseId())).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_readiness_snapshot WHERE readiness_snapshot_id=?",Integer.class,scheduled.snapshotId())).isEqualTo(1);
        assertThat(db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=? AND schedule_revision=1",String.class,scheduled.caseId())).containsExactly("RELEASED","RELEASED");
        assertThat(db.queryForObject("SELECT status FROM surgery_schedule WHERE schedule_id=?",String.class,scheduled.scheduleId())).isEqualTo("RELEASED");
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_schedule_history WHERE schedule_id=?",Integer.class,scheduled.scheduleId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT correlation_id FROM surgery_revision_history WHERE surgery_case_id=? AND revision=4 AND actor_type='SYSTEM' AND system_producer='organization-service'",String.class,scheduled.caseId())).isEqualTo(correlation);
        assertThat(db.queryForObject("SELECT count(*) FROM surgery_authority_change WHERE reference_id=? AND reference_kind=? AND source_revision=2 AND correlation_id=?",Integer.class,reference,kind,correlation)).isEqualTo(1);
    }
    private com.rabbitmq.client.Connection rabbitConnection() throws Exception {
        var factory = new com.rabbitmq.client.ConnectionFactory();
        factory.setHost(RABBIT.getHost()); factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername()); factory.setPassword(RABBIT.getAdminPassword());
        return factory.newConnection();
    }
    private record AuthorityFixture(UUID department,UUID staff,UUID room,String roomCode,Instant starts,Instant ends,String decision) {}

    private com.fasterxml.jackson.databind.JsonNode createOrganization(String path,String body) throws Exception {
        var response=send(ORGANIZATION,8089,"/api/v1/org"+path,"ADMIN",null,"POST",body,null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return mapper.readTree(response.body()).path("data");
    }
    private HttpResponse<String> send(GenericContainer<?> container,int port,String path,String role,
            UUID staff,String method,String body,String key) throws Exception {
        return send(container,port,path,role,staff,method,body,key,null);
    }
    private HttpResponse<String> send(GenericContainer<?> container,int port,String path,String role,
            UUID staff,String method,String body,String key,String correlation) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base(container,port)+path)).timeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+token(role,staff));
        if (key!=null) builder.header("Idempotency-Key",key);
        if (correlation!=null) builder.header("X-Correlation-Id",correlation);
        if (body!=null) builder.header("Content-Type","application/json");
        return http.send(builder.method(method,body==null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private org.springframework.jdbc.core.JdbcTemplate surgeryDb() {
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
    }
    /** Test-only existing aggregate prerequisite, not evidence of referral/create implementation. */
    private UUID seedOwnedCase(UUID department,UUID staff) {
        UUID caseId=UUID.randomUUID(), account=UUID.fromString("00000000-0000-0000-0000-000000000001");
        var db=surgeryDb();
        db.update("""
                INSERT INTO surgery_case(surgery_case_id,surgery_request_id,episode_type,episode_id,medical_record_id,
                    patient_id,department_id,requested_by,procedure_code,indication,priority,status,requested_at)
                VALUES (?,?,'OUTPATIENT_VISIT',?,?,?, ?,?,'TEST-PROC','Test-only indication','ROUTINE','REQUESTED',now())
                """,caseId,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),department,staff);
        db.update("""
                INSERT INTO surgery_status_history(surgery_case_id,sequence_no,new_status,actor_type,account_id,
                    staff_id,reason,occurred_at,correlation_id)
                SELECT surgery_case_id,0,'REQUESTED','HUMAN',?,?, 'CASE_CREATED',requested_at,'runtime-fixture'
                FROM surgery_case WHERE surgery_case_id=?
                """,account,staff,caseId);
        db.update("""
                INSERT INTO surgery_revision_history(surgery_case_id,revision,change_code,new_status,actor_type,
                    account_id,staff_id,occurred_at,correlation_id)
                SELECT surgery_case_id,0,'CASE_CREATED','REQUESTED','HUMAN',?,?,requested_at,'runtime-fixture'
                FROM surgery_case WHERE surgery_case_id=?
                """,account,staff,caseId);
        return caseId;
    }

    private HttpResponse<String> request(GenericContainer<?> container,int port,String path,
            String role,String correlation,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base(container,port)+path)).timeout(Duration.ofSeconds(5));
        if (role!=null) builder.header("Authorization","Bearer "+token(role));
        if (correlation!=null) builder.header("X-Correlation-Id",correlation);
        builder.header("X-User-Id",UUID.randomUUID().toString()); // Must not change the signed actor.
        return http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private String token(String role) {
        return token(role,null);
    }
    private String token(String role,UUID staff) {
        var builder = Jwts.builder().subject("00000000-0000-0000-0000-000000000001").claim("type","access").claim("role",role);
        if ("PATIENT".equals(role)) builder.claim("patientId", "00000000-0000-0000-0000-000000000002");
        if (staff!=null) builder.claim("staffId",staff.toString());
        return builder
                .expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private static GenericContainer<?> app(String service,int port,String alias) {
        Path jar=Path.of("..",service,"target",service+"-0.0.1-SNAPSHOT.jar");
        if (!Files.isRegularFile(jar)) throw new IllegalStateException("Package current "+service+" before runtime acceptance");
        var image=new ImageFromDockerfile().withFileFromPath("app.jar",jar);
        if (service.equals("surgery-service")) image.withFileFromPath("Dockerfile",Path.of("Dockerfile"));
        else image.withDockerfileFromBuilder(builder -> builder.from("eclipse-temurin:21-jre-alpine")
                .copy("app.jar","/app/app.jar").user("10001").entryPoint("java","-jar","/app/app.jar").build());
        return new GenericContainer<>(image).withNetwork(NETWORK).withNetworkAliases(alias)
                .withExposedPorts(port).withEnv("MEDIFLOW_JWT_SECRET",SECRET)
                .withEnv("JAVA_TOOL_OPTIONS","-Xms64m -Xmx256m")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(120)));
    }
    private static String base(GenericContainer<?> container,int port) {
        // Docker may reallocate a dynamic host port when the owned app JVM is restarted.
        var bindings = container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec()
                .getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(port));
        if (bindings == null || bindings.length == 0) throw new IllegalStateException("Runtime app has no current binding");
        return "http://"+container.getHost()+":"+bindings[0].getHostPortSpec();
    }
    @AfterAll static void closeNetworkAfterOwnedContainers() {
        GATEWAY.stop(); SURGERY.stop(); ORGANIZATION.stop(); EUREKA.stop(); RABBIT.stop(); PG.stop(); ORGANIZATION_PG.stop(); NETWORK.close();
    }
}
