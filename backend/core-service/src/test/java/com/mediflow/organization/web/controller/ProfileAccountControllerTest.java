package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.dto.response.ProfileAccountLookupDTO;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.application.port.in.LookupProfileAccountUseCase;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileAccountController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class ProfileAccountControllerTest {
    private static final UUID STAFF_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PATIENT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired MockMvc mvc;
    @MockBean LookupProfileAccountUseCase lookup;
    @MockBean CorrelationIdProvider correlation;

    @BeforeEach
    void setUp() {
        when(correlation.currentOrCreate()).thenReturn(UUID.fromString("44444444-4444-4444-8444-444444444444"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanLookupStaffAccountMetadataWithoutSecrets() throws Exception {
        var account = Account.create(ACCOUNT_ID, "staff@example.test", "sensitive-hash", STAFF_ID,
                null, Role.DOCTOR);
        when(lookup.staff(STAFF_ID)).thenReturn(new ProfileAccountLookupDTO(account,
                "staff@example.test", false));

        mvc.perform(get("/api/v1/org/profile-accounts/staff/{staffId}", STAFF_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.account.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.data.account.username").value("staff@example.test"))
                .andExpect(jsonPath("$.data.profileEmail").value("staff@example.test"))
                .andExpect(jsonPath("$.data.emailSyncPending").value(false))
                .andExpect(jsonPath("$.data.account.password").doesNotExist())
                .andExpect(jsonPath("$.data.account.passwordHash").doesNotExist());

        verify(lookup).staff(STAFF_ID);
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void nurseCanLookupPatientAccountButCannotLookupStaffAccount() throws Exception {
        when(lookup.patient(PATIENT_ID)).thenReturn(new ProfileAccountLookupDTO(null,
                "patient@example.test", false));

        mvc.perform(get("/api/v1/org/profile-accounts/patient/{patientId}", PATIENT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.account").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.profileEmail").value("patient@example.test"))
                .andExpect(jsonPath("$.data.emailSyncPending").value(false));

        mvc.perform(get("/api/v1/org/profile-accounts/staff/{staffId}", STAFF_ID))
                .andExpect(status().isForbidden());
        verify(lookup).patient(PATIENT_ID);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void otherRolesCannotReadProfileAccountMetadata() throws Exception {
        mvc.perform(get("/api/v1/org/profile-accounts/patient/{patientId}", PATIENT_ID))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/org/profile-accounts/staff/{staffId}", STAFF_ID))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lookup);
    }
}
