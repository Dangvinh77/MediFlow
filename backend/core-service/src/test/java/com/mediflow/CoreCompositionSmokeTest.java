package com.mediflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.mediflow.core.CoreServiceApplication;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.domain.model.Role;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(classes = CoreServiceApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "mediflow.datasource.organization.url=jdbc:h2:mem:core_org;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "mediflow.datasource.organization.username=sa",
        "mediflow.datasource.organization.password=",
        "mediflow.datasource.organization.driver-class-name=org.h2.Driver",
        "mediflow.datasource.patient.url=jdbc:h2:mem:core_patient;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "mediflow.datasource.patient.username=sa",
        "mediflow.datasource.patient.password=",
        "mediflow.datasource.patient.driver-class-name=org.h2.Driver",
        "mediflow.database.migrate=false",
        "mediflow.patient.outbox.scheduling-enabled=false",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
        "mediflow.jwt.secret=01234567890123456789012345678901",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class CoreCompositionSmokeTest {

    private static final String JWT_SECRET = "01234567890123456789012345678901";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    VerifyCredentialsUseCase verifyCredentialsUseCase;

    @Autowired
    @Qualifier("organizationEntityManagerFactory")
    EntityManagerFactory organizationEntityManagerFactory;

    @Autowired
    @Qualifier("patientEntityManagerFactory")
    EntityManagerFactory patientEntityManagerFactory;

    @Test
    void organizationAndPatientStartWithSeparatePersistenceContexts() {
        assertThat(organizationEntityManagerFactory).isNotSameAs(patientEntityManagerFactory);
        assertThat(organizationEntityManagerFactory.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getPackageName())
                .anyMatch("com.mediflow.organization.infrastructure.persistence.entity"::equals);
        assertThat(patientEntityManagerFactory.getMetamodel().getEntities())
                .extracting(entity -> entity.getJavaType().getPackageName())
                .anyMatch("com.mediflow.patient.infrastructure.persistence"::equals);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void errorsReturnTheSameCorrelationIdInHeaderAndBody() throws Exception {
        for (String path : new String[] {"/api/v1/org/departments/not-a-uuid", "/api/v1/patients/not-a-uuid"}) {
            MvcResult result = mockMvc.perform(get(path))
                    .andExpect(status().isBadRequest())
                    .andReturn();
            String header = result.getResponse().getHeader("X-Correlation-Id");
            String body = objectMapper.readTree(result.getResponse().getContentAsString())
                    .path("correlationId").asText();
            assertThat(header).isEqualTo(body);
        }
    }

    @Test
    void serviceLookupTokensWorkInBothContextsAndHumanTokensAreRejected() throws Exception {
        String service = bearer("service", "SYSTEM", "clinical-service");
        String human = bearer("access", "DOCTOR", UUID.randomUUID().toString());
        for (String path : new String[] {
                "/api/v1/org/departments/" + UUID.randomUUID() + "/lookup",
                "/api/v1/patients/" + UUID.randomUUID() + "/exists"}) {
            mockMvc.perform(get(path).header("Authorization", service))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path).header("Authorization", human))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void pharmacyServiceTokenCannotVerifyAccountCredentials() throws Exception {
        when(verifyCredentialsUseCase.execute("admin", "password"))
                .thenReturn(new VerifyCredentialsUseCase.VerifiedAccount(
                        UUID.randomUUID(), null, null, null, Role.ADMIN));
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String pharmacyService = "Bearer " + Jwts.builder()
                .subject("pharmacy-service")
                .claim("type", "service")
                .claim("role", "SYSTEM")
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(post("/api/v1/org/accounts/verify")
                        .header("Authorization", pharmacyService)
                        .header("X-Service-Id", "gateway")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isForbidden());
        verify(verifyCredentialsUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    void accessTokensRequireFutureExpirationInBothContexts() throws Exception {
        for (String path : new String[] {"/api/v1/org/departments", "/api/v1/patients"}) {
            for (Instant expiration : new Instant[] {null, Instant.now().minusSeconds(60)}) {
                var builder = Jwts.builder().subject(UUID.randomUUID().toString())
                        .claim("type", "access").claim("role", "ADMIN");
                if (expiration != null) builder.expiration(Date.from(expiration));
                String token = builder.signWith(Keys.hmacShaKeyFor(
                        JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
                mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                .jsonPath("$.error.code").value("UNAUTHORIZED"));
            }
            mockMvc.perform(get(path).header("Authorization",
                            bearer("access", "ADMIN", UUID.randomUUID().toString())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void gatewayServiceCanVerifyButGatewayWithoutServiceScopeCannot() throws Exception {
        when(verifyCredentialsUseCase.execute("admin", "password"))
                .thenReturn(new VerifyCredentialsUseCase.VerifiedAccount(
                        UUID.randomUUID(), null, null, null, Role.ADMIN));
        for (String type : new String[] {"access", "refresh", "service"}) {
            mockMvc.perform(post("/api/v1/org/accounts/verify")
                            .header("Authorization", bearer(type, "SYSTEM", "gateway"))
                            .header("X-Service-Id", "gateway")
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                    .andExpect(type.equals("service") ? status().isOk() : status().isUnauthorized());
        }
        verify(verifyCredentialsUseCase).execute("admin", "password");
    }

    private String bearer(String type, String role, String subject) {
        return "Bearer " + Jwts.builder().subject(subject)
                .claim("type", type).claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
