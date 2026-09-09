package com.mediflow.clinical.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.request.CreateAppointmentRequest;
import com.mediflow.clinical.application.dto.request.UpdateAppointmentRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.port.in.ManageAppointmentUseCase;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import(AppointmentControllerTest.MethodSecurityConfiguration.class)
class AppointmentControllerTest {

    private static final String BASE_PATH = "/api/v1/appointments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageAppointmentUseCase manageAppointmentUseCase;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getById_returnsAppointmentEnvelope() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(manageAppointmentUseCase.getById(appointmentId))
                .thenReturn(appointment(appointmentId));

        mockMvc.perform(get(BASE_PATH + "/{id}", appointmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.appointmentTime").value("08:30"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void getByPatient_returnsPatientAppointments() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(manageAppointmentUseCase.byPatient(patientId))
                .thenReturn(List.of(appointment(UUID.randomUUID())));

        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void search_passesFiltersAndPageQuery() throws Exception {
        UUID departmentId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        PageQuery pageQuery = new PageQuery(2, 5);
        when(manageAppointmentUseCase.search(departmentId, date, pageQuery))
                .thenReturn(PageResult.of(
                        List.of(appointment(UUID.randomUUID())), 11, 2, 5));

        mockMvc.perform(get(BASE_PATH)
                        .param("departmentId", departmentId.toString())
                        .param("appointmentDate", date.toString())
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        verify(manageAppointmentUseCase).search(departmentId, date, pageQuery);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void create_returns201AndLocation() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(manageAppointmentUseCase.create(any(CreateAppointmentRequest.class)))
                .thenReturn(appointment(appointmentId));

        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location", BASE_PATH + "/" + appointmentId))
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(appointmentId.toString()));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void update_returnsUpdatedAppointment() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UpdateAppointmentRequest request = new UpdateAppointmentRequest(
                LocalDate.now().plusDays(2), LocalTime.of(9, 0), "Tái khám");
        when(manageAppointmentUseCase.update(appointmentId, request))
                .thenReturn(appointment(appointmentId));

        mockMvc.perform(put(BASE_PATH + "/{id}", appointmentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentId")
                        .value(appointmentId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changeStatus_delegatesRequestedStatus() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(manageAppointmentUseCase.changeStatus(
                appointmentId, AppointmentStatus.ARRIVED))
                .thenReturn(appointment(appointmentId));

        mockMvc.perform(put(BASE_PATH + "/{id}/status", appointmentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARRIVED\"}"))
                .andExpect(status().isOk());

        verify(manageAppointmentUseCase)
                .changeStatus(appointmentId, AppointmentStatus.ARRIVED);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageAppointmentUseCase);
    }

    private CreateAppointmentRequest createRequest() {
        return new CreateAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 30),
                "Khám tổng quát");
    }

    private AppointmentDTO appointment(UUID appointmentId) {
        Instant now = Instant.now();
        return new AppointmentDTO(
                appointmentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 30),
                AppointmentStatus.PENDING,
                "Khám tổng quát",
                now,
                now);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
