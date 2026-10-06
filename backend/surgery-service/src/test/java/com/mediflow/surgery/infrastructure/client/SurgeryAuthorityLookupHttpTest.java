package com.mediflow.surgery.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Exercises real Feign serialization on the producer's exact checked-in fixture bytes. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SurgeryAuthorityLookupHttpTest {
    private static final UUID ROOM=UUID.fromString("01000000-0000-0000-0000-000000000001");
    private static final UUID STAFF=UUID.fromString("02000000-0000-0000-0000-000000000001");
    private static final String CORRELATION="04000000-0000-0000-0000-000000000001";
    private static final Instant NOW=Instant.parse("2026-10-05T02:00:00Z");
    private static final Instant START=Instant.parse("2026-10-06T02:00:00Z");
    private static final Instant END=START.plusSeconds(3600);
    private static final AtomicReference<Reply> REPLY=new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_PATH=new AtomicReference<>();
    private static final AtomicReference<String> REQUEST_QUERY=new AtomicReference<>();
    private static final AtomicReference<String> AUTH=new AtomicReference<>();
    private static HttpServer server;
    @Autowired OrganizationLookupPort lookups;
    @Autowired com.mediflow.surgery.application.port.out.AdmissionLookupPort admissions;
    @Autowired com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort financial;
    @MockBean SurgeryClockPort clock;

    @DynamicPropertySource static void producer(DynamicPropertyRegistry registry) throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        com.sun.net.httpserver.HttpHandler handler=exchange->{
            REQUEST_PATH.set(exchange.getRequestURI().getPath());
            REQUEST_QUERY.set(exchange.getRequestURI().getRawQuery());
            AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
            Reply reply=REPLY.get();
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id",CORRELATION);
            exchange.sendResponseHeaders(reply.status(),reply.body().length);
            try(var output=exchange.getResponseBody()) { output.write(reply.body()); }
        };
        server.createContext("/api/v1/org/",handler);
        server.createContext("/api/v1/inpatient/",handler);
        server.createContext("/api/v1/billing/",handler);
        server.start();
        registry.add("mediflow.surgery.organization.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
        registry.add("mediflow.surgery.inpatient.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
        registry.add("mediflow.surgery.billing.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
    }

    @BeforeEach void clock() { when(clock.now()).thenReturn(NOW); }
    @AfterAll static void stop() { if(server!=null) server.stop(0); }

    @ParameterizedTest
    @CsvSource({"room.active.json,ACTIVE","room.inactive.json,INACTIVE","room.missing.json,NOT_FOUND"})
    void room_sameProducerFixture_projectsRealStatesAndUsesServiceCredential(String fixture,String state) throws Exception {
        REPLY.set(new Reply(200,fixture(fixture)));
        var result=lookups.findRoom(ROOM,CORRELATION);
        assertThat(result.state().name()).isEqualTo(state);
        assertThat(result.observedAt()).isEqualTo(NOW);
        var authority = new ObjectMapper().readTree(fixture(fixture)).path("data").path("departmentId");
        assertThat(result.roomDepartmentId()).isEqualTo(authority.isNull() || authority.isMissingNode()
                ? null : UUID.fromString(authority.asText()));
        assertThat(REQUEST_PATH.get()).isEqualTo("/api/v1/org/operating-rooms/"+ROOM+"/lookup");
        assertServiceToken();
    }

    @ParameterizedTest
    @CsvSource({"eligibility.granted.json,ACTIVE","eligibility.denied.json,INACTIVE","eligibility.missing.json,NOT_FOUND"})
    void eligibility_sameProducerFixture_preservesRoleAndEntireInterval(String fixture,String state) throws Exception {
        REPLY.set(new Reply(200,fixture(fixture)));
        var result=eligibility();
        assertThat(result.state().name()).isEqualTo(state);
        assertThat(result.staffId()).isEqualTo(STAFF);
        assertThat(result.teamRole()).isEqualTo(SurgeryTeamRole.PRIMARY_SURGEON);
        assertThat(result.startsAt()).isEqualTo(START);
        assertThat(result.endsAt()).isEqualTo(END);
        assertThat(REQUEST_PATH.get()).isEqualTo("/api/v1/org/staff/"+STAFF+"/surgery-eligibility");
        String query=java.net.URLDecoder.decode(REQUEST_QUERY.get(),StandardCharsets.UTF_8);
        assertThat(query).contains("teamRole=PRIMARY_SURGEON","startsAt="+START,"endsAt="+END);
        assertServiceToken();
    }

    @Test void lookup_unavailableOrOlderProducer404_isNotConfirmedAbsence() {
        for(int status:new int[]{401,403,404,500,503}) {
            REPLY.set(new Reply(status,"{}".getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(()->lookups.findRoom(ROOM,CORRELATION)).isInstanceOf(UpstreamUnavailableException.class);
            assertThatThrownBy(this::eligibility).isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @Test void eligibility_wrongIdentityRoleIntervalRevisionFreshnessOrEnvelope_failsClosed() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        for(String field:new String[]{"staffId","teamRole","startsAt","sourceRevision","observedAt","departmentId","correlationId"}) {
            ObjectNode root=(ObjectNode)mapper.readTree(fixture("eligibility.granted.json"));
            ObjectNode data=(ObjectNode)root.get("data");
            switch(field) {
                case "staffId" -> data.put(field,UUID.randomUUID().toString());
                case "teamRole" -> data.put(field,"OR_NURSE");
                case "startsAt" -> data.put(field,START.plusSeconds(1).toString());
                case "sourceRevision" -> data.put(field,"0");
                case "observedAt" -> data.put(field,NOW.minusSeconds(31).toString());
                case "departmentId" -> data.putNull(field);
                case "correlationId" -> root.put(field,UUID.randomUUID().toString());
            }
            REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
            assertThatThrownBy(this::eligibility).as(field).isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @Test void room_malformedOrFutureObservation_failsClosed() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        ObjectNode root=(ObjectNode)mapper.readTree(fixture("room.active.json"));
        ((ObjectNode)root.get("data")).put("observedAt",NOW.plusSeconds(6).toString());
        REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
        assertThatThrownBy(()->lookups.findRoom(ROOM,CORRELATION)).isInstanceOf(UpstreamUnavailableException.class);
        ((ObjectNode)root.get("data")).put("observedAt",NOW.toString()).putNull("active");
        REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
        assertThatThrownBy(()->lookups.findRoom(ROOM,CORRELATION)).isInstanceOf(UpstreamUnavailableException.class);
    }

    private OrganizationLookupPort.SurgicalEligibilitySnapshot eligibility() {
        return lookups.findSurgicalEligibility(STAFF,SurgeryTeamRole.PRIMARY_SURGEON,START,END,CORRELATION);
    }

    @ParameterizedTest
    @CsvSource({"admission.active.json,ACTIVE","admission.discharged.json,INACTIVE","admission.missing.json,NOT_FOUND"})
    void admission_sameProducerBytes_distinguishesMedicalWindowAndAbsence(String name,String state) throws Exception {
        REPLY.set(new Reply(200,Files.readAllBytes(Path.of(
                "../inpatient-service/src/test/resources/contracts/admission-authority-v1",name))));
        UUID id=UUID.fromString("05000000-0000-0000-0000-000000000001");
        var result=admissions.findAdmission(id,CORRELATION);
        assertThat(result.state().name()).isEqualTo(state);
        assertThat(result.admissionId()).isEqualTo(id);
        assertThat(REQUEST_PATH.get()).isEqualTo("/api/v1/inpatient/admissions/"+id+"/lookup");
        assertServiceToken();
    }

    @Test void admission_wrongIdUnknownStatusOrStaleObservation_failsClosed() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        byte[] fixture=Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/admission-authority-v1/admission.active.json"));
        for(String field:new String[]{"admissionId","patientId","status","sourceRevision","observedAt"}) {
            ObjectNode root=(ObjectNode)mapper.readTree(fixture);
            ObjectNode data=(ObjectNode)root.get("data");
            switch(field) {
                case "admissionId" -> data.put(field,UUID.randomUUID().toString());
                case "patientId" -> data.putNull(field);
                case "status" -> data.put(field,"READY");
                case "sourceRevision" -> data.put(field,"-1");
                case "observedAt" -> data.put(field,NOW.minusSeconds(31).toString());
            }
            REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
            assertThatThrownBy(()->admissions.findAdmission(UUID.fromString("05000000-0000-0000-0000-000000000001"),CORRELATION))
                    .as(field).isInstanceOf(UpstreamUnavailableException.class);
        }
        for(int status:new int[]{404,503}) {
            REPLY.set(new Reply(status,"{}".getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(()->admissions.findAdmission(UUID.randomUUID(),CORRELATION))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"active","inactive","missing"})
    void financial_sameBillingProducerFixtures_useExactGrantAndCurrentObservation(String state) throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-10-06T08:00:00Z"));
        REPLY.set(new Reply(200,financialFixture(state)));
        var result=financial.observe(financialGrant(),CORRELATION);
        assertThat(result.eligible()).isEqualTo(state.equals("active"));
        assertThat(result.observedAt()).isEqualTo(Instant.parse("2026-10-06T08:00:00Z"));
        assertThat(result.validUntil()).isNull(); // Producer has no business expiry; freshness is separate.
        assertThat(REQUEST_PATH.get()).isEqualTo("/api/v1/billing/financial-clearances/"+financialGrant().clearanceId()+"/lookup");
        assertServiceToken();
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"clearanceId","invoiceId","accountId","patientId","surgeryCaseId",
            "careEpisodeId","admissionId","purpose","careEpisodeType","observedAt","future","eligible","exists","grantedAt","expired","envelope"})
    void financial_mismatchedMissingStaleOrMalformedAuthority_failsClosed(String field) throws Exception {
        Instant financialNow=Instant.parse("2026-10-06T08:00:00Z");
        when(clock.now()).thenReturn(financialNow);
        var mapper=new ObjectMapper(); var root=(ObjectNode)mapper.readTree(financialFixture("active")); var data=(ObjectNode)root.path("data");
        switch(field) {
            case "clearanceId","invoiceId","accountId","patientId","surgeryCaseId","careEpisodeId","admissionId" -> data.put(field,UUID.randomUUID().toString());
            case "purpose" -> data.put(field,"PRESCRIPTION");
            case "careEpisodeType" -> data.put(field,"OUTPATIENT_VISIT");
            case "observedAt" -> data.put(field,financialNow.minusSeconds(31).toString());
            case "future" -> data.put("observedAt",financialNow.plusSeconds(6).toString());
            case "eligible","exists","grantedAt" -> data.putNull(field);
            case "expired" -> data.put("expiresAt",financialNow.toString());
            case "envelope" -> root.put("correlationId","wrong");
            default -> throw new AssertionError(field);
        }
        REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
        assertThatThrownBy(()->financial.observe(financialGrant(),CORRELATION)).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test void financial_businessExpiry_isNotReplacedWithObservationTtl() throws Exception {
        Instant at=Instant.parse("2026-10-06T08:00:00Z");
        when(clock.now()).thenReturn(at);
        var mapper=new ObjectMapper(); var root=(ObjectNode)mapper.readTree(financialFixture("active"));
        ((ObjectNode)root.path("data")).put("expiresAt",at.plusSeconds(300).toString());
        REPLY.set(new Reply(200,mapper.writeValueAsBytes(root)));
        assertThat(financial.observe(financialGrant(),CORRELATION).validUntil()).isEqualTo(at.plusSeconds(300));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={401,403,404,500,503})
    void financial_httpFailure_isNotConfirmedAbsence(int status) {
        REPLY.set(new Reply(status,"{}".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(()->financial.observe(financialGrant(),CORRELATION)).isInstanceOf(UpstreamUnavailableException.class);
    }
    private static byte[] financialFixture(String state) throws IOException {
        ObjectMapper mapper=new ObjectMapper();
        // Only the per-request correlation changes; all business bytes come from Billing's fixtures.
        ObjectNode root=(ObjectNode)mapper.readTree(Files.readAllBytes(
                Path.of("../billing-service/src/test/resources/contracts/clearance-authority-v1",state+".json")));
        root.put("correlationId",CORRELATION);
        return mapper.writeValueAsBytes(root);
    }
    private static com.mediflow.surgery.domain.model.SurgeryFinancialClearance financialGrant() {
        return new com.mediflow.surgery.domain.model.SurgeryFinancialClearance(financialId(46),financialId(5),financialId(1),financialId(2),financialId(4),
                com.mediflow.surgery.domain.model.CareEpisodeType.ADMISSION,financialId(3),financialId(3),new java.math.BigDecimal("100"),
                "VND","CASH",Instant.parse("2026-10-05T08:00:00Z"),null,"a".repeat(64));
    }
    private static UUID financialId(int value) { return UUID.fromString("00000000-0000-0000-0000-"+String.format("%012d",value)); }

    private static byte[] fixture(String name) throws IOException {
        return Files.readAllBytes(Path.of("../organization-service/src/test/resources/contracts/surgery-authority-v1",name));
    }

    private static void assertServiceToken() {
        var claims=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(
                "surgery-test-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(AUTH.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("surgery-service");
        assertThat(claims.get("type")).isEqualTo("service");
        assertThat(claims.get("role")).isEqualTo("SYSTEM");
    }

    private record Reply(int status,byte[] body) {}
}
