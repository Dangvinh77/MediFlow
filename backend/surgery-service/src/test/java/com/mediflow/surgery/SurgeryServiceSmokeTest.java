package com.mediflow.surgery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurgeryServiceSmokeTest {

    private static final String SECRET = "surgery-test-secret-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void health_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownSurgeryApi_withoutToken_isUnauthorizedWithCorrelation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/surgery/cases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andReturn();

        assertThat(UUID.fromString(result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID)))
                .isNotNull();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("correlationId").asText())
                .isEqualTo(result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }

    @Test
    void noBusinessPlaceholder_returnsNotFoundEvenForAuthenticatedCaller() throws Exception {
        mockMvc.perform(get("/api/v1/surgery/cases")
                        .header("Authorization", "Bearer " + validAccessToken()))
                .andExpect(status().isNotFound());
    }

    private String validAccessToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("9d2c0477-91e7-4e92-82ae-e18c82f11375")
                .claim(JwtClaims.ROLE, "DOCTOR")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
