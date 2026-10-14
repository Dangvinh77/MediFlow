package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.domain.model.AdmissionPriority;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;

@WebMvcTest(value = ClinicalAdmissionReferralController.class, properties = {
        "mediflow.features.care-finance-v2.enabled=true",
        "mediflow.features.admission-referral.enabled=true"
})
@Import({ClinicalAdmissionReferralControllerTest.MethodSecurityConfiguration.class,
        ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class ClinicalAdmissionReferralControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompleteMedicalRecordUseCase records;

    @Test
    @WithMockUser(roles = "DOCTOR")
    void admissionReferral_remainsAvailableWhenLegacyV2AndItsGateAreExplicitlyEnabled() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID referralId = UUID.randomUUID();
        when(records.requestAdmission(any(), any())).thenReturn(new AdmissionReferralDTO(
                referralId, recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Needs admission", AdmissionPriority.URGENT, false, Instant.now()));

        mockMvc.perform(post("/api/v1/records/" + recordId + "/admission-referrals")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diagnosisSummary\":\"Needs admission\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isCreated());

        verify(records).requestAdmission(any(), any(CreateAdmissionReferralRequest.class));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
