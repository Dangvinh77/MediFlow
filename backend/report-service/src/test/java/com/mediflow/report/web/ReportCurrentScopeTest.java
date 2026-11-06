package com.mediflow.report.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.report.infrastructure.config.SecurityConfig;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties="mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class ReportCurrentScopeTest {
    static final UUID ACCOUNT=UUID.randomUUID(), DEPARTMENT=UUID.randomUUID();
    static HttpServer core;
    static volatile String body;
    static volatile int responseStatus=200;
    static volatile String forwardedBearer, forwardedCorrelation;
    static {
        try {
            core=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            core.createContext("/api/v1/org/me/capabilities", exchange -> {
                forwardedBearer=exchange.getRequestHeaders().getFirst("Authorization");
                forwardedCorrelation=exchange.getRequestHeaders().getFirst("X-Correlation-Id");
                byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(responseStatus,bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            }); core.start();
        } catch(Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("mediflow.report.core-base-url",()->"http://127.0.0.1:"+core.getAddress().getPort());
    }
    @AfterAll static void stop(){core.stop(0);}
    @BeforeEach void resetCore(){responseStatus=200;body=authority(ACCOUNT,"DOCTOR",true);forwardedBearer=null;}
    @Autowired MockMvc mvc;
    @MockBean ReadReportUseCase reader;
    static String authority(UUID account,String role,boolean grant) {
        return "{\"success\":true,\"error\":null,\"timestamp\":\""+Instant.now()+"\",\"data\":{\"accountId\":\""+account+"\",\"staffId\":\""+UUID.randomUUID()+"\",\"role\":\""+role+"\",\"observedAt\":\""+Instant.now()+"\",\"managementDepartments\":"+(grant?"[{\"departmentId\":\""+DEPARTMENT+"\",\"departmentName\":\"Cardiology\",\"appointmentId\":\"11111111-1111-4111-8111-111111111111\",\"revision\":1}]":"[]")+"}}";
    }
    static String token(String role){return io.jsonwebtoken.Jwts.builder().subject(ACCOUNT.toString())
            .claim("type","access").claim("role",role).expiration(Date.from(Instant.now().plusSeconds(300)))
            .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();}
    @Test void managerHospitalScopeIsDeniedBeforeRead() throws Exception {
        for(String query:new String[]{"daily?date=2026-10-01","monthly?month=10&year=2026","top-medicines?fromDate=2026-10-01&toDate=2026-10-01"}) {
            mvc.perform(get("/api/v1/reports/"+query).header("Authorization","Bearer "+token("DOCTOR")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        } verifyNoInteractions(reader);
    }
    @Test void assignedDepartmentAllowedAndSameTokenRevocationTakesEffect() throws Exception {
        String bearer="Bearer "+token("DOCTOR");
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+DEPARTMENT).header("Authorization",bearer).header("X-Correlation-Id","scope-test")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(forwardedBearer).isEqualTo(bearer);
        org.assertj.core.api.Assertions.assertThat(forwardedCorrelation).isEqualTo("scope-test");
        clearInvocations(reader);body=authority(ACCOUNT,"DOCTOR",false);
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+DEPARTMENT).header("Authorization",bearer)).andExpect(status().isForbidden());
        verifyNoInteractions(reader);
    }
    @Test void otherDepartmentDenied() throws Exception {
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+UUID.randomUUID()).header("Authorization","Bearer "+token("DOCTOR"))).andExpect(status().isForbidden());verifyNoInteractions(reader);
    }
    @Test void invalidMismatchedAndUnavailableAuthorityFailClosed() throws Exception {
        String valid=authority(ACCOUNT,"DOCTOR",true);
        for(String invalid:new String[]{"{}",valid.replace("\"success\":true","\"success\":false"),authority(UUID.randomUUID(),"DOCTOR",true),authority(ACCOUNT,"ADMIN",true),valid.replace("\"staffId\":\"","\"staffId\":\"bad"),valid.replace("\"managementDepartments\":[", "\"managementDepartments\":null,\"ignored\":[")}) {
            body=invalid;
            mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+DEPARTMENT).header("Authorization","Bearer "+token("DOCTOR"))).andExpect(status().isForbidden());
        }
        body=valid;responseStatus=503;
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+DEPARTMENT).header("Authorization","Bearer "+token("DOCTOR"))).andExpect(status().isForbidden());verifyNoInteractions(reader);
    }
    @Test void adminHospitalScopeDoesNotDependOnCore() throws Exception {
        responseStatus=503;
        mvc.perform(get("/api/v1/reports/daily?date=2026-10-01").header("Authorization","Bearer "+token("ADMIN"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(forwardedBearer).isNull();
    }
    @Test void departmentHeadWorksForEveryLegacyQuery() throws Exception {
        String bearer="Bearer "+token("DOCTOR");
        for (String query:new String[]{"daily?date=2026-10-01", "monthly?month=10&year=2026", "top-medicines?fromDate=2026-10-01&toDate=2026-10-01"}) {
            mvc.perform(get("/api/v1/reports/"+query+"&departmentId="+DEPARTMENT)
                    .header("Authorization",bearer)).andExpect(status().isOk());
        }
    }
    @Test void malformedAndStaleScopeEvidenceNeverGrantsAccess() throws Exception {
        String valid=authority(ACCOUNT,"DOCTOR",true);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for (String field:new String[]{"observedAt","managementDepartments","revision","departmentId"}) {
            var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(valid);
            var data=(com.fasterxml.jackson.databind.node.ObjectNode)envelope.get("data");
            switch(field) {
                case "observedAt" -> data.put(field,Instant.now().minusSeconds(120).toString());
                case "managementDepartments" -> data.putNull(field);
                case "revision" -> ((com.fasterxml.jackson.databind.node.ObjectNode)data.get("managementDepartments").get(0)).put(field,-1);
                default -> ((com.fasterxml.jackson.databind.node.ObjectNode)data.get("managementDepartments").get(0)).putNull(field);
            }
            body=envelope.toString();
            mvc.perform(get("/api/v1/reports/daily?date=2026-10-01&departmentId="+DEPARTMENT)
                    .header("Authorization","Bearer "+token("DOCTOR"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reader);
    }

}
