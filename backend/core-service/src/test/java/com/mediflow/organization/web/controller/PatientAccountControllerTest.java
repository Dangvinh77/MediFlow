package com.mediflow.organization.web.controller;
import com.mediflow.organization.application.service.ProvisionPatientAccountService;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
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
    @MockBean CorrelationIdProvider correlation;
    final UUID actor = UUID.fromString("00000000-0000-4000-8000-000000000002");
    final UUID patient = UUID.fromString("00000000-0000-4000-8000-000000000001");
    @org.junit.jupiter.api.BeforeEach void setUp() {
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
    }

    @Test @WithMockUser(username="00000000-0000-4000-8000-000000000002", roles="NURSE")
    void nurseIssuesOnlyPatientAndReceivesNoPassword() throws Exception {
        var account = Account.create(UUID.randomUUID(), "012345678901", "hash", null, patient, Role.PATIENT);
        account.requirePasswordChange();
        when(service.provision(actor, "key", patient)).thenReturn(new CreateAccountUseCase.CreationResult(account, false));
        when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID());
        mvc.perform(post("/api/v1/org/patient-accounts").header("Idempotency-Key", "key")
                .contentType("application/json").content("{\"patientId\":\"" + patient + "\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.username").value("012345678901"))
            .andExpect(jsonPath("$.data.mustChangePassword").value(true))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
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
}
