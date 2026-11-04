package com.mediflow.organization.web.controller;
import com.mediflow.organization.application.service.ProvisionPatientAccountService;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.organization.application.service.SecurityAccountOperationsService;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientAccountController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties="mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class PatientAccountControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ProvisionPatientAccountService service;
    @MockBean SecurityAccountOperationsService securityOperations;
    @MockBean CorrelationIdProvider correlation;
    final UUID actor = UUID.fromString("00000000-0000-4000-8000-000000000002");
    final UUID patient = UUID.fromString("00000000-0000-4000-8000-000000000001");
    final UUID correlationId = UUID.fromString("00000000-0000-4000-8000-000000000003");
    @org.junit.jupiter.api.BeforeEach void setUp() {
        when(correlation.currentOrCreate()).thenReturn(correlationId);
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void nurseIssuesOnlyPatientAndReceivesNoPassword() throws Exception {
        var account = Account.create(UUID.randomUUID(), "patient@example.test", "hash", null, patient, Role.PATIENT);
        account.requirePasswordChange();
        var creation = new CreateAccountUseCase.CreationResult(account, false, "Temporary-Only-123!");
        var delivery = new MailDeliveryDTO(UUID.randomUUID(), MailDeliveryDTO.Status.PENDING);
        when(service.provisionWithMail(actor, "key", patient))
                .thenReturn(new ProvisionPatientAccountService.ProvisionedAccount(creation, delivery));
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
        mvc.perform(post("/api/v1/org/patient-accounts").header("Idempotency-Key", "key")
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.username").value("patient@example.test"))
            .andExpect(jsonPath("$.data.mustChangePassword").value(true))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.data.mailDelivery.messageId").value(delivery.messageId().toString()))
            .andExpect(jsonPath("$.data.mailDelivery.status").value("PENDING"));
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void rejectsRoleInjectionAndMissingKey() throws Exception {
        mvc.perform(post("/api/v1/org/patient-accounts").header("Idempotency-Key", "key")
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/org/patient-accounts")
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="PATIENT")
    void patientCannotProvisionAccounts() throws Exception {
        mvc.perform(post("/api/v1/org/patient-accounts").header("Idempotency-Key", "key")
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void nurseCanResetPatientPasswordByPatientIdWithoutReturningSecrets() throws Exception {
        UUID messageId = UUID.fromString("00000000-0000-4000-8000-000000000004");
        when(securityOperations.resetPatientPassword(actor, patient, "reset-key", correlationId))
                .thenReturn(new MailDeliveryDTO(messageId, MailDeliveryDTO.Status.PENDING));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/org/patient-accounts/{patientId}/password", patient)
                        .header("Idempotency-Key", "reset-key")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.textBody").doesNotExist())
                .andExpect(jsonPath("$.data.htmlBody").doesNotExist());

        verify(securityOperations).resetPatientPassword(actor, patient, "reset-key", correlationId);
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void nurseCanReadAndRetryPatientMailMetadataByPatientId() throws Exception {
        UUID messageId = UUID.fromString("00000000-0000-4000-8000-000000000004");
        var delivery = new MailDeliveryDTO(messageId, MailDeliveryDTO.Status.FAILED);
        when(securityOperations.latestPatientDelivery(patient)).thenReturn(delivery);
        when(securityOperations.retryPatientDelivery(actor, patient, messageId, correlationId))
                .thenReturn(new MailDeliveryDTO(messageId, MailDeliveryDTO.Status.PENDING));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/org/patient-accounts/{patientId}/mail-delivery", patient))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.textBody").doesNotExist());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/org/patient-accounts/{patientId}/mail-delivery/{messageId}/retry", patient, messageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.htmlBody").doesNotExist());

        verify(securityOperations).latestPatientDelivery(patient);
        verify(securityOperations).retryPatientDelivery(actor, patient, messageId, correlationId);
    }

    @Test @WithMockUser(roles="DOCTOR")
    void nonNurseCannotResetOrInspectPatientMailThroughScopedRoutes() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/org/patient-accounts/{patientId}/password", patient)
                        .header("Idempotency-Key", "reset-key")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/org/patient-accounts/{patientId}/mail-delivery", patient))
                .andExpect(status().isForbidden());
        verifyNoInteractions(securityOperations);
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void resetRejectsAnyCallerSuppliedCredentialData() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/org/patient-accounts/{patientId}/password", patient)
                        .header("Idempotency-Key", "reset-key")
                        .contentType("application/json").content("{\"newPassword\":\"guessable\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(securityOperations);
    }
}
