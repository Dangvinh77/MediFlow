package com.mediflow.clinical.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.dto.request.UpdateRecordRequest;
import com.mediflow.clinical.application.dto.response.DiagnosisDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.in.ManageRecordUseCase;
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

@WebMvcTest(MedicalRecordController.class)
@Import(MedicalRecordControllerTest.MethodSecurityConfiguration.class)
class MedicalRecordControllerTest {

    private static final String BASE_PATH = "/api/v1/records";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManageRecordUseCase manageRecordUseCase;

    @Test
    @WithMockUser(roles = "NURSE")
    void getById_returnsMedicalRecordEnvelope() throws Exception {
        UUID recordId = UUID.randomUUID();
        when(manageRecordUseCase.getById(recordId)).thenReturn(record(recordId));

        mockMvc.perform(get(BASE_PATH + "/{id}", recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.data.diagnoses.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void getByPatient_returnsPatientRecords() throws Exception {
        UUID patientId = UUID.randomUUID();
        when(manageRecordUseCase.byPatient(patientId))
                .thenReturn(List.of(record(UUID.randomUUID())));

        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_returns201AndLocation() throws Exception {
        UUID recordId = UUID.randomUUID();
        when(manageRecordUseCase.create(any(CreateRecordRequest.class)))
                .thenReturn(record(recordId));

        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE_PATH + "/" + recordId))
                .andExpect(jsonPath("$.data.recordId").value(recordId.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returnsUpdatedRecord() throws Exception {
        UUID recordId = UUID.randomUUID();
        UpdateRecordRequest request = new UpdateRecordRequest("Đau đầu và sốt nhẹ");
        when(manageRecordUseCase.update(recordId, request)).thenReturn(record(recordId));

        mockMvc.perform(put(BASE_PATH + "/{id}", recordId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value(recordId.toString()));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void addDiagnosis_returns201AndLocation() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        AddDiagnosisRequest request = diagnosisRequest();
        when(manageRecordUseCase.addDiagnosis(recordId, request))
                .thenReturn(new DiagnosisDTO(
                        diagnosisId, "Viêm mũi họng cấp", "Niêm mạc họng đỏ", "J00"));

        mockMvc.perform(post(BASE_PATH + "/{id}/diagnoses", recordId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        BASE_PATH + "/" + recordId + "/diagnoses/" + diagnosisId))
                .andExpect(jsonPath("$.data.diagnosisId").value(diagnosisId.toString()));

        verify(manageRecordUseCase).addDiagnosis(recordId, request);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void create_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageRecordUseCase);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_withoutDiagnosis_returns400() throws Exception {
        CreateRecordRequest request = new CreateRecordRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "Đau đầu",
                null,
                List.of());

        mockMvc.perform(post(BASE_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(manageRecordUseCase);
    }

    private CreateRecordRequest createRequest() {
        return new CreateRecordRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "Sốt nhẹ, đau đầu",
                UUID.randomUUID(),
                List.of(diagnosisRequest()));
    }

    private AddDiagnosisRequest diagnosisRequest() {
        return new AddDiagnosisRequest(
                "Viêm mũi họng cấp", "Niêm mạc họng đỏ", "J00");
    }

    private MedicalRecordDTO record(UUID recordId) {
        Instant now = Instant.now();
        return new MedicalRecordDTO(
                recordId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                "Sốt nhẹ, đau đầu",
                UUID.randomUUID(),
                List.of(new DiagnosisDTO(
                        UUID.randomUUID(),
                        "Viêm mũi họng cấp",
                        "Niêm mạc họng đỏ",
                        "J00")),
                now,
                now);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
