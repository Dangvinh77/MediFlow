package com.mediflow.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.dto.request.OperatingRoomRequest;
import com.mediflow.organization.application.dto.request.SurgicalCapabilityRequest;
import com.mediflow.organization.application.port.in.ManageSurgeryAuthorityUseCase;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.domain.model.SurgicalTeamRole;
import com.mediflow.organization.infrastructure.messaging.SurgeryAuthorityOutboxDispatcher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SurgeryAuthorityIntegrationTest {
    private static final String SECRET="integration-surgery-authority-secret-at-least-32-bytes";
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",PG::getJdbcUrl);
        registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
        registry.add("spring.rabbitmq.host",RABBIT::getHost);
        registry.add("spring.rabbitmq.port",RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username",RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password",RABBIT::getAdminPassword);
        registry.add("mediflow.jwt.secret",()->SECRET);
        registry.add("eureka.client.enabled",()->false);
    }
    @Autowired ManageSurgeryAuthorityUseCase authority;
    @Autowired DepartmentRepository departments;
    @Autowired StaffRepository staff;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ConnectionFactory connection;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    private Department department;
    private Staff doctor;
    private final UUID actor=UUID.randomUUID();
    private final Instant start=Instant.parse("2026-10-06T02:00:00Z");
    private final Instant end=start.plusSeconds(3600);

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM surgery_authority_outbox");
        jdbc.update("DELETE FROM surgical_capability");
        jdbc.update("DELETE FROM operating_room");
        department=departments.save(Department.create(UUID.randomUUID(),"Surgery", "S"+UUID.randomUUID().toString().substring(0,8).toUpperCase(java.util.Locale.ROOT),DepartmentType.CLINICAL,"A"));
        doctor=staff.save(Staff.create("Doctor",department.getDepartmentId(),JobTitle.DOCTOR,null,"LIC-001",null,null));
    }

    @Test void http_adminDecisionThenServiceLookup_preservesRealIdentityAndAudit() throws Exception {
        String correlation=UUID.randomUUID().toString();
        var request=new OperatingRoomRequest(0L,"or-01","Room",department.getDepartmentId(),true,"Decision reference AUTH-001");
        var created=http.exchange("/api/v1/org/operating-rooms",HttpMethod.POST,
                entity(request,"access","ADMIN",actor.toString(),correlation),String.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode createdBody=mapper.readTree(created.getBody());
        UUID roomId=UUID.fromString(createdBody.at("/data/roomId").asText());
        assertThat(createdBody.at("/data/roomCode").asText()).isEqualTo("OR-01");
        var lookedUp=http.exchange("/api/v1/org/operating-rooms/"+roomId+"/lookup",HttpMethod.GET,
                entity(null,"service","SYSTEM","surgery-service",correlation),String.class);
        assertThat(lookedUp.getStatusCode().value()).isEqualTo(200);
        var body=mapper.readTree(lookedUp.getBody());
        assertThat(body.at("/data/active").asBoolean()).isTrue();
        assertThat(body.at("/data/departmentId").asText()).isEqualTo(department.getDepartmentId().toString());
        assertThat(body.at("/data/sourceRevision").asText()).isEqualTo("1");
        assertThat(body.at("/correlationId").asText()).isEqualTo(correlation);
        assertThat(lookedUp.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlation);
        var event=mapper.readTree(jdbc.queryForObject("SELECT payload::text FROM surgery_authority_outbox",String.class));
        assertThat(event.at("/payload/actorAccountId").asText()).isEqualTo(actor.toString());
        assertThat(event.at("/correlationId").asText()).isEqualTo(correlation);
        assertThat(event.at("/version").asInt()).isEqualTo(1);
    }

    @Test void eligibility_transferExpiryRevocationAndDepartmentDeactivation_failClosed() {
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isFalse();
        authority.decideCapability(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(0L,true,start,end,"Explicit qualification"),actor);
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isTrue();
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.ANESTHESIOLOGIST,start,end).eligible()).isFalse();
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end.plusSeconds(1)).eligible()).isFalse();
        jdbc.update("UPDATE department SET is_active=false WHERE department_id=?",department.getDepartmentId());
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isFalse();
        jdbc.update("UPDATE department SET is_active=true WHERE department_id=?",department.getDepartmentId());
        Department other=departments.save(Department.create(UUID.randomUUID(),"Other", "X"+UUID.randomUUID().toString().substring(0,8).toUpperCase(java.util.Locale.ROOT),DepartmentType.CLINICAL,"B"));
        doctor.changeDepartment(other.getDepartmentId());
        staff.save(doctor);
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isFalse();
        authority.decideCapability(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(1L,true,start,end,"Reapproved for new department"),actor);
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isTrue();
        doctor.deactivate();
        staff.save(doctor);
        authority.decideCapability(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,
                new SurgicalCapabilityRequest(2L,false,start,end,"Revoked after departure"),actor);
        assertThat(authority.lookupEligibility(doctor.getStaffId(),SurgicalTeamRole.PRIMARY_SURGEON,start,end).eligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_authority_outbox",Integer.class)).isEqualTo(3);
    }

    @Test void concurrentRevision_sameExpectedRevision_commitsOneDecisionAndOneAudit() throws Exception {
        UUID roomId=UUID.randomUUID();
        authority.saveRoom(roomId,roomRequest(0,true),actor);
        jdbc.update("DELETE FROM surgery_authority_outbox");
        var ready=new CountDownLatch(2);
        var go=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> operation=()->{
                ready.countDown();
                if(!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
                try { authority.saveRoom(roomId,roomRequest(1,false),actor); return true; }
                catch(DuplicateResourceException conflict) { return false; }
            };
            var one=workers.submit(operation);
            var two=workers.submit(operation);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(java.util.List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("SELECT revision FROM operating_room WHERE room_id=?",Long.class,roomId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_authority_outbox",Integer.class)).isEqualTo(1);
    }

    @Test void outboxInsertFailure_rollsBackAuthorityMutation() {
        jdbc.execute("""
                CREATE FUNCTION reject_authority_event_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'injected audit failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("CREATE TRIGGER reject_authority_event_test BEFORE INSERT ON surgery_authority_outbox FOR EACH ROW EXECUTE FUNCTION reject_authority_event_test()");
        UUID roomId=UUID.randomUUID();
        try {
            assertThatThrownBy(()->authority.saveRoom(roomId,roomRequest(0,true),actor))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            jdbc.execute("DROP TRIGGER reject_authority_event_test ON surgery_authority_outbox");
            jdbc.execute("DROP FUNCTION reject_authority_event_test()");
        }
        assertThat(authority.lookupRoom(roomId).exists()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_authority_outbox",Integer.class)).isZero();
    }

    @Test void outbox_mandatoryReturnRetainsPending_thenRetryPublishesSameEventId() throws Exception {
        authority.saveRoom(UUID.randomUUID(),roomRequest(0,true),actor);
        UUID eventId=jdbc.queryForObject("SELECT event_id FROM surgery_authority_outbox",UUID.class);
        RabbitTemplate rabbit=new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        var dispatcher=new SurgeryAuthorityOutboxDispatcher(jdbc,rabbit,new TransactionTemplate(manager));
        dispatcher.dispatch();
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM surgery_authority_outbox",Boolean.class)).isTrue();
        RabbitAdmin admin=new RabbitAdmin(connection);
        Queue queue=new Queue("authority-test-"+UUID.randomUUID(),false,false,false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange("mediflow.events"))
                .with("organization.surgery.authority.changed"));
        try {
            dispatcher.dispatch();
            var message=rabbit.receive(queue.getName(),5000);
            assertThat(message).isNotNull();
            assertThat(mapper.readTree(message.getBody()).at("/eventId").asText()).isEqualTo(eventId.toString());
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
            assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM surgery_authority_outbox",Boolean.class)).isTrue();
            dispatcher.dispatch();
            assertThat(rabbit.receive(queue.getName(),200)).isNull();
        } finally { admin.deleteQueue(queue.getName()); }
    }

    @Test void migration_v2Upgrade_preservesOldDepartmentAndSeedsNoAuthority() {
        String schema="authority_upgrade_test";
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .schemas(schema).locations("classpath:db/migration").target("2").load().migrate();
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO "+schema+".department(department_id,department_name,abbreviation,department_type) VALUES(?,?,?,?)",
                id,"Existing","OLD","CLINICAL");
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword())
                .schemas(schema).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".department WHERE department_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".operating_room",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+schema+".surgical_capability",Integer.class)).isZero();
    }

    private OperatingRoomRequest roomRequest(long revision,boolean active) {
        return new OperatingRoomRequest(revision,"OR-1","Room",department.getDepartmentId(),active,"Audited decision");
    }

    private static HttpEntity<Object> entity(Object body,String type,String role,String subject,String correlation) {
        Instant now=Instant.now();
        String token=Jwts.builder().subject(subject).claim("type",type).claim("role",role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        HttpHeaders headers=new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Correlation-Id",correlation);
        return new HttpEntity<>(body,headers);
    }
}
