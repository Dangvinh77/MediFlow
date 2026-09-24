package com.mediflow.inpatient;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

import javax.crypto.SecretKey;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InpatientServiceSmokeTest {

    private static final String SECRET = "inpatient-test-secret-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContext_startsWithoutExternalInfrastructure() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ConnectionFactory.class)).isEmpty();
        assertThat(applicationContext.getEnvironment()
                .getProperty("eureka.client.enabled", Boolean.class)).isFalse();
    }

    @Test
    void healthAndInfo_arePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("MediFlow Inpatient Service"));
    }

    @Test
    void swaggerAndOpenApiAssets_arePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MediFlow Inpatient Service"));
    }

    @Test
    void plannedBusinessPath_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/inpatient/admissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtPassesSecurity_butNoBusinessEndpointExists() throws Exception {
        mockMvc.perform(get("/api/v1/inpatient/admissions")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isNotFound());
    }

    private String validToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("inpatient-smoke-test")
                .claim(JwtClaims.ROLE, "DOCTOR")
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
