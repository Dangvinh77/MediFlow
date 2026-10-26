package com.mediflow.patient.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.infrastructure.config.SecurityConfig;
import com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.patient.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientDirectoryController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, ThreadLocalCorrelationIdProvider.class,
        PatientGlobalExceptionHandler.class})
@TestPropertySource(properties = "mediflow.jwt.secret=01234567890123456789012345678901")
class PatientDirectoryControllerWebTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean GetPatientUseCase patients;

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "PHARMACIST", "CASHIER", "LAB_TECH"})
    void returnsOnlyTheThreeDirectoryFieldsWithPaginationAndCorrelation(String role) throws Exception {
        UUID id = UUID.randomUUID();
        var dto = new PatientDTO(id, "Nguyễn An", LocalDate.of(1990, 1, 1), Gender.M,
                "private-id", "private-address", "private-phone", "private-email", "private-insurance", null, null);
        when(patients.search("An", PageQuery.of(1, 5))).thenReturn(PageResult.of(List.of(dto), 6, 1, 5));
        when(patients.getById(id)).thenReturn(dto);
        String trace = UUID.randomUUID().toString();
        var result = mvc.perform(get("/api/v1/patients/directory/search")
                        .param("keyword", "An").param("page", "1").param("size", "5")
                        .header("Authorization", bearer(role)).header("X-Correlation-Id", trace))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.correlationId").value(trace))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.number").value(1))
                .andExpect(jsonPath("$.data.content[0].hoTen").value("Nguyễn An")).andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsString()).at("/data/content/0").size()).isEqualTo(3);
        var detail = mvc.perform(get("/api/v1/patients/directory/{id}", id).header("Authorization", bearer(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maBenhNhan").value(id.toString()))
                .andExpect(jsonPath("$.data.ngaySinh").value("1990-01-01")).andReturn();
        assertThat(json.readTree(detail.getResponse().getContentAsString()).at("/data").size()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"PATIENT", "NURSE", "MANAGER", "SYSTEM"})
    void deniesRolesOutsideTheCareStaffDirectory(String role) throws Exception {
        mvc.perform(get("/api/v1/patients/directory/search").header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/directory/{id}", UUID.randomUUID()).header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(patients);
    }

    private String bearer(String role) {
        var token = Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("type", "SYSTEM".equals(role) ? "service" : "access").claim("role", role);
        if ("PATIENT".equals(role)) token.claim("patientId", UUID.randomUUID().toString());
        return "Bearer " + token.expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))).compact();
    }
}
