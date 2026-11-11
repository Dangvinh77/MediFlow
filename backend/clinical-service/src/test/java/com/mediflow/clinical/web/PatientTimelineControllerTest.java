package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.clinical.application.dto.response.RecordDocumentDTO;
import com.mediflow.clinical.application.service.PatientTimelineApplicationService;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.api.PageResult;

@WebMvcTest(PatientTimelineController.class)
@Import({PatientTimelineControllerTest.MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class PatientTimelineControllerTest {
    static final UUID PATIENT = UUID.randomUUID();
    static final UUID RECORD = UUID.randomUUID();
    static final String TIMELINE = "/api/v2/patients/" + PATIENT + "/timeline";
    static final String DOCUMENTS = "/api/v2/records/" + RECORD + "/documents";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean PatientTimelineApplicationService service;

    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(TIMELINE)).andExpect(status().isUnauthorized());
        mvc.perform(get(DOCUMENTS)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "PATIENT", "PHARMACIST", "LAB_TECH"})
    void onlyCareStaffRead(String role) throws Exception {
        mvc.perform(get(TIMELINE).with(user("u").roles(role))).andExpect(status().isForbidden());
        mvc.perform(get(DOCUMENTS).with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void timelinePassesFiltersAndDefaults() throws Exception {
        UUID department = UUID.randomUUID();
        when(service.timeline(eq(PATIENT), anyInt(), anyInt(), any(), any()))
                .thenReturn(PageResult.of(List.of(), 0, 0, 20));
        mvc.perform(get(TIMELINE).param("departmentId", department.toString()).param("status", "COMPLETED")
                        .with(user("d").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        verify(service).timeline(PATIENT, 0, 20, department, MedicalRecordStatus.COMPLETED);
    }

    @Test void unknownStatusIsBadRequest() throws Exception {
        mvc.perform(get(TIMELINE).param("status", "LOST").with(user("d").roles("DOCTOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void nurseReadsDocuments() throws Exception {
        when(service.documents(RECORD)).thenReturn(List.of(
                new RecordDocumentDTO("LAB", UUID.randomUUID(), "Bình thường", java.time.Instant.now(), true)));
        mvc.perform(get(DOCUMENTS).with(user("n").roles("NURSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].afterConclusion").value(true));
    }
}
