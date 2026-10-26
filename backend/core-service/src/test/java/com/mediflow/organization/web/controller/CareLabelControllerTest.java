package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.CareLabelDTO;
import com.mediflow.organization.application.port.in.GetCareLabelUseCase;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CareLabelController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class CareLabelControllerTest {
    @Autowired MockMvc mvc;
    @MockBean GetCareLabelUseCase labels;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"})
    void returnsOnlyDisplayIdentityForHumanRoles(String role) throws Exception {
        UUID id = UUID.randomUUID();
        when(labels.read("doctor", id)).thenReturn(java.util.Optional.of(new CareLabelDTO(id, "Bác sĩ Nguyễn An")));
        mvc.perform(get("/api/v1/org/care-labels/doctor/{id}", id).header("Authorization", "Bearer " + token(role, id, "access")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.id").value(id.toString()))
            .andExpect(jsonPath("$.data.label").value("Bác sĩ Nguyễn An"))
            .andExpect(jsonPath("$.data", org.hamcrest.Matchers.aMapWithSize(2)))
            .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "REFRESH", "ANONYMOUS"})
    void rejectsNonHumanAccess(String scenario) throws Exception {
        var request = get("/api/v1/org/care-labels/doctor/{id}", UUID.randomUUID());
        if (!scenario.equals("ANONYMOUS")) request.header("Authorization", "Bearer " + token(
            scenario.equals("REFRESH") ? "PATIENT" : "SYSTEM", UUID.randomUUID(), scenario.equals("REFRESH") ? "refresh" : "service"));
        mvc.perform(request).andExpect(status().is4xxClientError());
        verifyNoInteractions(labels);
    }
    private String token(String role, UUID staff, String type) {
        var builder = Jwts.builder().subject(UUID.randomUUID().toString()).claim("role", role).claim("type", type)
                .expiration(Date.from(Instant.now().plusSeconds(120)));
        if (staff != null) builder.claim(role.equals("PATIENT") ? "patientId" : "staffId", staff.toString());
        return builder.signWith(Keys.hmacShaKeyFor("test-secret-must-have-at-least-32-bytes".getBytes(StandardCharsets.UTF_8))).compact();
    }
}
