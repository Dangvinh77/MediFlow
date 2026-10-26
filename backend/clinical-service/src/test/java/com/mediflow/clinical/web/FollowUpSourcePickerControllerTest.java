package com.mediflow.clinical.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.clinical.application.dto.response.FollowUpSourceSummary;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

@WebMvcTest(value = OutpatientRegistrationController.class, properties = "mediflow.clinical.scheduling.enabled=true")
@Import({FollowUpSourcePickerControllerTest.MethodSecurityConfiguration.class,
        ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class FollowUpSourcePickerControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OutpatientRegistrationApplicationService registrations;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void followUpSourcePickerRejectsDoctorRoleAtTheHttpBoundary() throws Exception {
        UUID patientId = UUID.randomUUID();

        mockMvc.perform(get("/api/v2/outpatient-registrations/follow-up-sources")
                        .param("patientId", patientId.toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrations);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void followUpSourcePickerReturnsPagedSourceRowsForNurse() throws Exception {
        UUID patientId = UUID.randomUUID();
        var source = new FollowUpSourceSummary(UUID.randomUUID(), UUID.randomUUID(), patientId,
                UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.parse("2026-10-20T11:00:00Z"), null);
        when(registrations.followUpSources(patientId, PageQuery.of(0, 20)))
                .thenReturn(PageResult.of(List.of(source), 1, 0, 20));

        mockMvc.perform(get("/api/v2/outpatient-registrations/follow-up-sources")
                        .param("patientId", patientId.toString()))
                .andExpect(status().isOk());

        verify(registrations).followUpSources(patientId, PageQuery.of(0, 20));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {}
}
