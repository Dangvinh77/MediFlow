package com.mediflow.lab.web;

import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.domain.exception.LabRuleException;
import com.mediflow.lab.domain.exception.LabTestNotFoundException;
import com.mediflow.lab.domain.model.LabTestStatus;
import com.mediflow.lab.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class GlobalExceptionHandlerTest {

    private static final String BASE_PATH = "/api/v1/lab";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageLabTestUseCase manageLabTestUseCase;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void missingLabTest_returns404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageLabTestUseCase.getById(id))
                .thenThrow(new LabTestNotFoundException(id));

        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LAB_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void invalidTransition_returns422Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageLabTestUseCase.changeStatus(
                eq(id), eq(LabTestStatus.IN_PROGRESS)))
                .thenThrow(new LabRuleException(
                        "LAB_INVALID_TRANSITION",
                        "Chuyển trạng thái không hợp lệ"));

        mockMvc.perform(put(BASE_PATH + "/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LAB_INVALID_TRANSITION"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void invalidBody_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void malformedStatus_returns400Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
