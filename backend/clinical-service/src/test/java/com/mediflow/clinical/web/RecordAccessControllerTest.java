package com.mediflow.clinical.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.service.RecordAccessApplicationService;
import com.mediflow.clinical.application.service.RecordReadPolicy;
import com.mediflow.clinical.domain.model.RecordAccessDeclaration;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;

@WebMvcTest(RecordAccessController.class)
@Import({RecordAccessControllerTest.Config.class, RecordAccessApplicationService.class,
        ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class RecordAccessControllerTest {
    private static final String PATH = "/api/v2/records/access-declarations";
    private static final String GOOD_REASON = "Ra soat chat luong ho so";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private MedicalRecordRepositoryPort records;
    @MockBean
    private AppointmentRepositoryPort appointments;
    @MockBean
    private RecordAccessDeclarationPort declarations;
    @MockBean
    private RecordAccessLogPort log;
    @MockBean
    private RecordReadPolicy readPolicy;
    @MockBean
    private CurrentClinicalActorPort actors;

    @Test
    @WithMockUser(roles = "NURSE")
    void nurseMayDeclareEmergencyOnly() throws Exception {
        actor("NURSE");

        declare("CONSULTATION", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(declarations, never()).save(any());

        declare("EMERGENCY", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purpose").value("EMERGENCY"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotUseConsultationPurpose() throws Exception {
        actor("ADMIN");

        declare("CONSULTATION", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(declarations, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reasonRequiredForQualityReviewAndRecordManagement() throws Exception {
        actor("ADMIN");

        declare("QUALITY_REVIEW", null).andExpect(status().isBadRequest());
        declare("RECORD_MANAGEMENT", "qua ngan").andExpect(status().isBadRequest());
        declare("RECORD_MANAGEMENT", "x".repeat(501)).andExpect(status().isBadRequest());
        verify(declarations, never()).save(any());

        declare("RECORD_MANAGEMENT", GOOD_REASON).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorEmergencyNeedsNoReason() throws Exception {
        actor("DOCTOR");

        declare("EMERGENCY", null).andExpect(status().isCreated());

        assertThat(saved().reason()).isNull();
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctorDeclarationLastsNinetySixHours() throws Exception {
        actor("DOCTOR");

        declare("CONSULTATION", null).andExpect(status().isCreated());

        RecordAccessDeclaration declaration = saved();
        assertThat(Duration.between(declaration.declaredAt(), declaration.expiresAt())).isEqualTo(Duration.ofHours(96));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAndNurseDeclarationsLastSixtyMinutes() throws Exception {
        actor("ADMIN");

        declare("QUALITY_REVIEW", GOOD_REASON).andExpect(status().isCreated());

        RecordAccessDeclaration declaration = saved();
        assertThat(Duration.between(declaration.declaredAt(), declaration.expiresAt())).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void declarationIsLoggedWithPurpose() throws Exception {
        ActorIdentity actor = actor("DOCTOR");

        declare("CARE_CONTINUITY", null).andExpect(status().isCreated());

        ArgumentCaptor<RecordAccessLogEntry> entry = ArgumentCaptor.forClass(RecordAccessLogEntry.class);
        verify(log).append(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo("DECLARE");
        assertThat(entry.getValue().outcome()).isEqualTo("ALLOWED");
        assertThat(entry.getValue().purpose()).isEqualTo("CARE_CONTINUITY");
        assertThat(entry.getValue().actorAccountId()).isEqualTo(actor.accountId());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void patientCannotDeclare() throws Exception {
        declare("EMERGENCY", null).andExpect(status().isForbidden());
        verify(declarations, never()).save(any());
    }

    private ActorIdentity actor(String role) {
        ActorIdentity actor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), null, role);
        when(actors.current()).thenReturn(actor);
        return actor;
    }

    private ResultActions declare(String purpose, String reason) throws Exception {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return mockMvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(
                "{\"patientId\":\"" + UUID.randomUUID() + "\",\"purpose\":\"" + purpose + "\",\"reason\":" + reasonJson + "}"));
    }

    private RecordAccessDeclaration saved() {
        ArgumentCaptor<RecordAccessDeclaration> declaration = ArgumentCaptor.forClass(RecordAccessDeclaration.class);
        verify(declarations).save(declaration.capture());
        return declaration.getValue();
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean
        OutpatientMvpMode outpatientMvpMode() {
            return new OutpatientMvpMode(true);
        }

        @Bean
        RecordAccessPolicyMode recordAccessPolicyMode() {
            return new RecordAccessPolicyMode(true);
        }
    }
}
