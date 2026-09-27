package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

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

import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.in.ManageExamGateUseCase;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.clinical.domain.model.AdmissionPriority;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordDisposition;

@WebMvcTest(value = ClinicalCareFinanceController.class, properties = "mediflow.features.care-finance-v2.enabled=true")
@Import({ClinicalCareFinanceControllerTest.MethodSecurityConfiguration.class,
        ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class ClinicalCareFinanceControllerTest {

    private static final String APPOINTMENTS = "/api/v1/appointments/";
    private static final String RECORDS = "/api/v1/records/";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageExamGateUseCase examGate;

    @MockBean
    private CompleteMedicalRecordUseCase records;

    @Test
    @WithMockUser(roles = "NURSE")
    void checkIn_nurseAllowedAndDoctorDenied() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        when(examGate.checkIn(appointmentId)).thenReturn(appointment(appointmentId));

        mockMvc.perform(put(APPOINTMENTS + appointmentId + "/check-in").with(csrf()))
                .andExpect(status().isOk());
        verify(examGate).checkIn(appointmentId);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void checkIn_doctorDenied() throws Exception {
        mockMvc.perform(put(APPOINTMENTS + UUID.randomUUID() + "/check-in").with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(examGate);
    }
    @Test
    @WithMockUser(roles = "NURSE")
    void startExamAndRecordCommands_nurseDenied() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

        mockMvc.perform(put(APPOINTMENTS + appointmentId + "/start-exam")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(RECORDS + recordId + "/complete")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"OUTPATIENT_FOLLOW_UP\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RECORDS + recordId + "/admission-referrals")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosisSummary\":\"Needs admission\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examGate, records);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorCanStartAndCompleteExamAndRequestAdmission() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID referralId = UUID.randomUUID();
        when(examGate.startExam(any(), any())).thenReturn(appointment(appointmentId));
        when(records.complete(any(), any())).thenReturn(record(recordId));
        when(records.requestAdmission(any(), any())).thenReturn(new AdmissionReferralDTO(
                referralId, recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Needs inpatient observation", AdmissionPriority.URGENT, false, Instant.now()));

        mockMvc.perform(put(APPOINTMENTS + appointmentId + "/start-exam")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(RECORDS + recordId + "/complete")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disposition\":\"OUTPATIENT_FOLLOW_UP\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(RECORDS + recordId + "/admission-referrals")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosisSummary\":\"Needs admission\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isCreated());

        verify(examGate).startExam(any(), any(StartExamRequest.class));
        verify(records).complete(any(), any(CompleteRecordRequest.class));
        verify(records).requestAdmission(any(), any(CreateAdmissionReferralRequest.class));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void startExam_nestedEmergencyOverrideMissingApprovedAt_returnsBadRequest() throws Exception {
        mockMvc.perform(put(APPOINTMENTS + UUID.randomUUID() + "/start-exam")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergencyOverride\":{\"overrideId\":\""
                                + UUID.randomUUID() + "\",\"approvedBy\":\"" + UUID.randomUUID()
                                + "\",\"approverRole\":\"DOCTOR\",\"reason\":\"Critical condition\"}}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(examGate);
    }

    private AppointmentDTO appointment(UUID id) {
        Instant now = Instant.now();
        return new AppointmentDTO(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), LocalTime.NOON, AppointmentStatus.IN_EXAM, null, now, now,
                (short) 1, UUID.randomUUID(), now, null, "OUTPATIENT_EXAM", now, now, null);
    }

    private MedicalRecordDTO record(UUID id) {
        Instant now = Instant.now();
        return new MedicalRecordDTO(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, UUID.randomUUID(), java.util.List.of(), now, now,
                MedicalRecordStatus.COMPLETED, RecordDisposition.OUTPATIENT_FOLLOW_UP, null, now);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
