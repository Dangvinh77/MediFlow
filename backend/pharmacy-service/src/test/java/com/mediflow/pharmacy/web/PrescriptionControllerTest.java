package com.mediflow.pharmacy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionLineDTO;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrescriptionController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class PrescriptionControllerTest {

    private static final String BASE_PATH = "/api/v1/pharmacy/prescriptions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreatePrescriptionUseCase useCase;

    @MockBean
    private CancelPrescriptionUseCase cancelPrescriptionUseCase;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR"})
    void create_allowedRole_returns201LocationAndPendingPrescription(String role) throws Exception {
        UUID prescriptionId = UUID.randomUUID();
        when(useCase.create(any(CreatePrescriptionRequest.class)))
                .thenReturn(prescriptionDto(prescriptionId));

        mockMvc.perform(post(BASE_PATH)
                        .with(user("creator").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE_PATH + "/" + prescriptionId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.prescriptionId").value(prescriptionId.toString()))
                .andExpect(jsonPath("$.data.totalAmount").value(2000.00))
                .andExpect(jsonPath("$.data.dispenseStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.lines[0].drugName").value("Paracetamol"));
    }

    @Test
    @WithMockUser(roles = "PHARMACIST")
    void create_pharmacistRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(useCase);
    }

    @Test
    void create_missingAuthentication_returns401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(useCase);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_emptyLines_returns400() throws Exception {
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                List.of());

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(useCase);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_duplicateDrug_returns422() throws Exception {
        UUID drugId = UUID.randomUUID();
        CreatePrescriptionRequest request = requestWithLines(List.of(
                new PrescriptionLineRequest(drugId, 1, "Buổi sáng"),
                new PrescriptionLineRequest(drugId, 1, "Buổi tối")));
        when(useCase.create(any(CreatePrescriptionRequest.class)))
                .thenThrow(new PrescriptionRuleException(
                        "PRESCRIPTION_DUPLICATE_DRUG",
                        "Một thuốc chỉ được xuất hiện một lần trong đơn"));

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PRESCRIPTION_DUPLICATE_DRUG"));
    }

    private CreatePrescriptionRequest validRequest() {
        return requestWithLines(List.of(new PrescriptionLineRequest(
                UUID.randomUUID(), 2, "Ngày 2 lần")));
    }

    private CreatePrescriptionRequest requestWithLines(List<PrescriptionLineRequest> lines) {
        return new CreatePrescriptionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                lines);
    }

    private PrescriptionDTO prescriptionDto(UUID prescriptionId) {
        UUID drugId = UUID.randomUUID();
        return new PrescriptionDTO(
                prescriptionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                new BigDecimal("2000.00"),
                List.of(new PrescriptionLineDTO(
                        UUID.randomUUID(),
                        drugId,
                        "Paracetamol",
                        2,
                        new BigDecimal("1000.00"),
                        "Ngày 2 lần",
                        new BigDecimal("2000.00"))),
                PrescriptionStatus.ACTIVE,
                DispenseStatus.PENDING,
                null,
                null,
                null,
                Instant.now(),
                null);
    }
}
