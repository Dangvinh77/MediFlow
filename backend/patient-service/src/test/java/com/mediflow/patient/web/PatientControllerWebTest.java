package com.mediflow.patient.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.LookupPatientUseCase;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.infrastructure.config.SecurityConfig;
import com.mediflow.patient.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.patient.infrastructure.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class,
        CorrelationIdFilter.class, ThreadLocalCorrelationIdProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "mediflow.jwt.secret=01234567890123456789012345678901")
class PatientControllerWebTest {
    private static final String SECRET = "01234567890123456789012345678901";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean GetPatientUseCase patients;
    @MockBean LookupPatientUseCase lookup;

    @Test
    void humanAccessCanReadPatientAndCorrelationIsPreserved() throws Exception {
        UUID id = UUID.randomUUID();
        when(patients.getById(id)).thenReturn(dto(id));
        String correlation = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/patients/{id}", id)
                        .header("Authorization", bearer("access", "DOCTOR", UUID.randomUUID().toString()))
                        .header("X-Correlation-Id", correlation))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", correlation))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.maBenhNhan", is(id.toString())))
                .andExpect(jsonPath("$.data.gioiTinh", is("M")));
    }

    @Test
    void humanAccessCanSearchWithVietnameseDtoPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(patients.search(any(), any())).thenReturn(PageResult.of(List.of(dto(id)), 1, 0, 20));
        mvc.perform(get("/api/v1/patients?keyword=nguyen&page=0&size=20")
                        .header("Authorization", bearer("access", "NURSE", UUID.randomUUID().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].maBenhNhan", is(id.toString())))
                .andExpect(jsonPath("$.data.number", is(0)));
    }

    @Test
    void serviceTokenCanUseExistsButCannotReadHumanEndpoint() throws Exception {
        UUID id = UUID.randomUUID();
        when(lookup.exists(id)).thenReturn(new PatientLookupDTO(false, id));
        mvc.perform(get("/api/v1/patients/{id}/exists", id)
                        .header("Authorization", bearer("service", "SYSTEM", "patient-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists", is(false)))
                .andExpect(jsonPath("$.data.patientId", is(id.toString())));
        mvc.perform(get("/api/v1/patients/{id}", id)
                        .header("Authorization", bearer("service", "SYSTEM", "patient-service")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void humanTokenCannotUseServiceOnlyExistsAndTamperedTokenIsUnauthorized() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/patients/{id}/exists", id)
                        .header("Authorization", bearer("access", "DOCTOR", UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/{id}", id)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        mvc.perform(get("/api/v1/patients/{id}", id)
                        .header("Authorization", bearer("refresh", "DOCTOR", UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void missingPatientIs404AndStoreFailureIs503() throws Exception {
        UUID id = UUID.randomUUID();
        when(patients.getById(id)).thenThrow(new com.mediflow.patient.domain.exception.PatientNotFoundException(id));
        mvc.perform(get("/api/v1/patients/{id}", id)
                        .header("Authorization", bearer("access", "DOCTOR", UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PATIENT_NOT_FOUND")));
        when(lookup.exists(id)).thenThrow(new DataAccessResourceFailureException("db down"));
        mvc.perform(get("/api/v1/patients/{id}/exists", id)
                        .header("Authorization", bearer("service", "SYSTEM", "patient-service")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code", is("PATIENT_STORE_UNAVAILABLE")));
    }

    private String bearer(String type, String role, String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject(subject).claim("type", type).claim("role", role)
                .signWith(key).compact();
    }

    private PatientDTO dto(UUID id) {
        return new PatientDTO(id, "Nguyen Van A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", "Hanoi", "0900000000", "a@example.com", null, null, null);
    }
}
