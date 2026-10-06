package com.mediflow.inpatient.infrastructure.web;

import com.mediflow.inpatient.application.dto.response.AdmissionLookupDTO;
import com.mediflow.inpatient.application.port.in.LookupAdmissionAuthorityUseCase;
import com.mediflow.inpatient.infrastructure.config.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdmissionAuthorityController.class)
@Import({SecurityConfig.class,CorrelationIdFilter.class})
@TestPropertySource(properties="mediflow.jwt.secret=test-inpatient-lookup-secret-at-least-32-bytes")
class AdmissionAuthorityControllerTest {
    private static final String SECRET="test-inpatient-lookup-secret-at-least-32-bytes";
    @Autowired MockMvc mvc;
    @MockBean LookupAdmissionAuthorityUseCase admissions;

    @Test void lookup_serviceJwt_preservesCorrelation_andOutageIsNotAbsence() throws Exception {
        UUID id=UUID.randomUUID();
        String correlation=UUID.randomUUID().toString();
        when(admissions.lookup(id)).thenReturn(new AdmissionLookupDTO(false,id,null,null,null,null,false,null,Instant.now()));
        mvc.perform(get("/api/v1/inpatient/admissions/{id}/lookup",id)
                .header("Authorization",token("service","SYSTEM","surgery-service",60))
                .header("X-Correlation-Id",correlation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.admissionId").value(id.toString()))
                .andExpect(jsonPath("$.correlationId").value(correlation))
                .andExpect(header().string("X-Correlation-Id",correlation));
        when(admissions.lookup(id)).thenThrow(new DataAccessResourceFailureException("offline"));
        mvc.perform(get("/api/v1/inpatient/admissions/{id}/lookup",id)
                .header("Authorization",token("service","SYSTEM","surgery-service",60)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("INPATIENT_LOOKUP_UNAVAILABLE"));
    }

    @Test void lookup_humanRefreshWrongOrLongLivedServiceToken_cannotRead() throws Exception {
        String path="/api/v1/inpatient/admissions/"+UUID.randomUUID()+"/lookup";
        mvc.perform(get(path).header("Authorization",token("access","ADMIN",UUID.randomUUID().toString(),60)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization",token("refresh","SYSTEM","surgery-service",60)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization",token("service","ADMIN","surgery-service",60)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization",token("service","SYSTEM","surgery-service",300)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(admissions);
    }

    private static String token(String type,String role,String subject,int ttl) {
        Instant now=Instant.now();
        return "Bearer "+Jwts.builder().subject(subject).claim("type",type).claim("role",role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
