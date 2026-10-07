package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.*;
import com.mediflow.organization.application.port.in.ManageSurgeryAuthorityUseCase;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SurgeryAuthorityController.class)
@Import({SecurityConfig.class,ThreadLocalCorrelationIdProvider.class,CorrelationIdFilter.class})
@TestPropertySource(properties="mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class SurgeryAuthorityControllerTest {
    private static final String SECRET="test-secret-must-have-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean ManageSurgeryAuthorityUseCase authority;

    @Test
    void lookup_serviceToken_returnsProjectionAndPreservesCorrelation() throws Exception {
        UUID id=UUID.randomUUID();
        String correlation=UUID.randomUUID().toString();
        when(authority.lookupRoom(id)).thenReturn(new OperatingRoomLookupDTO(false,false,id,null,null,Instant.now()));
        mvc.perform(get("/api/v1/org/operating-rooms/{id}/lookup",id)
                .header("Authorization",token("service","SYSTEM","surgery-service"))
                .header("X-Correlation-Id",correlation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.roomId").value(id.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlation))
                .andExpect(header().string("X-Correlation-Id",correlation));
    }

    @Test
    void lookup_humanRefreshAndWrongServiceCredentials_cannotReadAuthority() throws Exception {
        for(String path:new String[]{"/api/v1/org/operating-rooms/"+UUID.randomUUID()+"/lookup",
                "/api/v1/org/staff/"+UUID.randomUUID()+"/surgery-eligibility?teamRole=OR_NURSE&startsAt=2026-10-06T02:00:00Z&endsAt=2026-10-06T03:00:00Z"}) {
            mvc.perform(get(path).header("Authorization",token("access","ADMIN",UUID.randomUUID().toString())))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization",token("refresh","ADMIN",UUID.randomUUID().toString())))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",token("service","DOCTOR","surgery-service")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(authority);
    }

    @Test
    void decisions_onlyVerifiedAdminCanMutate_andActorCannotComeFromHeaders() throws Exception {
        UUID actor=UUID.randomUUID();
        UUID dept=UUID.randomUUID();
        String body="{\"expectedRevision\":0,\"roomCode\":\"OR-1\",\"roomName\":\"Room\",\"departmentId\":\""+dept+"\",\"active\":true,\"reason\":\"Reviewed\"}";
        for(String role:new String[]{"DOCTOR","NURSE","MANAGER","PATIENT"}) {
            mvc.perform(post("/api/v1/org/operating-rooms").contentType("application/json").content(body)
                    .header("Authorization", token("access", role, actor.toString(),
                            "PATIENT".equals(role) ? UUID.randomUUID().toString() : null)))
                    .andExpect(status().isForbidden());
        }
        when(authority.saveRoom(any(),any(),eq(actor))).thenAnswer(invocation ->
                new OperatingRoomDTO(invocation.getArgument(0),"OR-1","Room",dept,true,1,Instant.now()));
        mvc.perform(post("/api/v1/org/operating-rooms").contentType("application/json").content(body)
                .header("X-User-Id",UUID.randomUUID().toString())
                .header("Authorization",token("access","ADMIN",actor.toString())))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.revision").value(1));
        verify(authority).saveRoom(any(),any(),eq(actor));
    }

    @Test
    void lookup_failure_is503NotMissing_andMalformedRequestIs400() throws Exception {
        UUID id=UUID.randomUUID();
        String bearer=token("service","SYSTEM","surgery-service");
        when(authority.lookupRoom(id)).thenThrow(new DataAccessResourceFailureException("offline"));
        mvc.perform(get("/api/v1/org/operating-rooms/{id}/lookup",id).header("Authorization",bearer))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("ORG_LOOKUP_UNAVAILABLE"));
        mvc.perform(get("/api/v1/org/staff/{id}/surgery-eligibility",id).header("Authorization",bearer))
                .andExpect(status().isBadRequest());
    }

    private static String token(String type,String role,String subject) {
        return token(type, role, subject, null);
    }

    private static String token(String type,String role,String subject,String patientId) {
        Instant now=Instant.now();
        var builder=Jwts.builder().subject(subject).claim("type",type).claim("role",role);
        if (patientId != null) {
            builder.claim("patientId", patientId);
        }
        return "Bearer "+builder
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
