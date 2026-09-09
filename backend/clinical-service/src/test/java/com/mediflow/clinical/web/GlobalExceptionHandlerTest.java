package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.in.ManageAppointmentUseCase;
import com.mediflow.clinical.domain.exception.AppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.DuplicatePendingAppointmentException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.infrastructure.config.SecurityConfig;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class GlobalExceptionHandlerTest {

    private static final String BASE_PATH = "/api/v1/appointments";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageAppointmentUseCase manageAppointmentUseCase;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void missingAppointment_returns404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageAppointmentUseCase.getById(id))
                .thenThrow(new AppointmentNotFoundException("Không tìm thấy lịch hẹn"));

        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void duplicatePendingAppointment_returns409Envelope() throws Exception {
        when(manageAppointmentUseCase.create(any(CreateAppointmentRequest.class)))
                .thenThrow(new DuplicatePendingAppointmentException(
                        "Bệnh nhân đã có lịch chờ trong ngày"));

        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("APPOINTMENT_DUPLICATE_PENDING"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void invalidTransition_returns422Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageAppointmentUseCase.changeStatus(
                org.mockito.ArgumentMatchers.eq(id),
                org.mockito.ArgumentMatchers.eq(AppointmentStatus.ARRIVED)))
                .thenThrow(new InvalidClinicalDataException(
                        "APPOINTMENT_INVALID_TRANSITION",
                        "Chuyển trạng thái không hợp lệ"));

        mockMvc.perform(put(BASE_PATH + "/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARRIVED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("APPOINTMENT_INVALID_TRANSITION"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void upstreamFailure_returns503Envelope() throws Exception {
        when(manageAppointmentUseCase.search(any(), any(), any()))
                .thenThrow(new UpstreamUnavailableException(
                        "patient-service không phản hồi"));

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void invalidBody_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void malformedDate_returns400Envelope() throws Exception {
        mockMvc.perform(get(BASE_PATH).param("appointmentDate", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private String validCreateBody() {
        return """
                {
                  "patientId": "10000000-0000-0000-0000-000000000001",
                  "doctorId": "20000000-0000-0000-0000-000000000001",
                  "departmentId": "30000000-0000-0000-0000-000000000001",
                  "appointmentDate": "2099-01-01",
                  "appointmentTime": "08:30",
                  "reason": "Khám tổng quát"
                }
                """;
    }
}
