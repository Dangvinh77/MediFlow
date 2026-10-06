package com.mediflow.organization.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.StaffLookupDTO;
import com.mediflow.organization.application.dto.response.StaffIdentityLookupDTO;
import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.application.port.in.LookupStaffIdentityUseCase;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.infrastructure.config.SecurityConfig;
import com.mediflow.organization.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.organization.infrastructure.web.CorrelationIdFilter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(StaffController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class StaffControllerContractTest {

    private static final String BASE_PATH = "/api/v1/org/staff";
    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreateStaffUseCase createStaffUseCase;

    @MockBean
    private ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase;

    @MockBean
    private GetStaffUseCase getStaffUseCase;

    @MockBean
    private LookupStaffIdentityUseCase lookupStaffIdentityUseCase;

    @MockBean
    private UpdateStaffUseCase updateStaffUseCase;

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_eligibleDoctor_returnsEnvelopeAndDepartment() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(getStaffUseCase.lookup(staffId))
                .thenReturn(StaffLookupDTO.eligible(departmentId));

        mockMvc.perform(get(BASE_PATH + "/{id}/exists", staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.eligibleDoctor").value(true))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(header().exists(JwtClaims.HEADER_CORRELATION_ID));

        verify(getStaffUseCase).lookup(staffId);
    }

    @Test
    void lookup_serviceJwtWithSystemRole_isAuthorized() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(getStaffUseCase.lookup(staffId))
                .thenReturn(StaffLookupDTO.eligible(departmentId));

        mockMvc.perform(get(BASE_PATH + "/{id}/exists", staffId)
                .header("Authorization", "Bearer " + systemToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibleDoctor").value(true))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_missingStaff_returnsConfirmedNegativeEnvelope() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(getStaffUseCase.lookup(staffId)).thenReturn(StaffLookupDTO.missing());

        mockMvc.perform(get(BASE_PATH + "/{id}/exists", staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.eligibleDoctor").value(false))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void lookup_nonDoctor_returnsIneligibleEnvelope() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(getStaffUseCase.lookup(staffId)).thenReturn(StaffLookupDTO.ineligible());

        mockMvc.perform(get(BASE_PATH + "/{id}/exists", staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.eligibleDoctor").value(false))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void identityLookup_existingStaff_returnsActiveJobAndDepartment() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(lookupStaffIdentityUseCase.lookup(staffId))
                .thenReturn(new StaffIdentityLookupDTO(true, true, "NURSE", departmentId,
                        List.of("OR_NURSE")));

        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", staffId)
                        .header(JwtClaims.HEADER_CORRELATION_ID,
                                "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.jobTitle").value("NURSE"))
                .andExpect(jsonPath("$.data.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.data.eligibleTeamRoles[0]").value("OR_NURSE"))
                .andExpect(jsonPath("$.correlationId")
                        .value("11111111-1111-1111-1111-111111111111"))
                .andExpect(header().string(JwtClaims.HEADER_CORRELATION_ID,
                        "11111111-1111-1111-1111-111111111111"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void identityLookup_missingStaff_returnsConfirmedAbsence() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(lookupStaffIdentityUseCase.lookup(staffId))
                .thenReturn(StaffIdentityLookupDTO.missing());

        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", staffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void identityLookup_malformedUuid_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/not-a-uuid/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_UUID"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void identityLookup_humanTokenIsForbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void identityLookup_nonSystemServiceTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", UUID.randomUUID())
                        .header("Authorization", "Bearer "
                                + token("clinical-service", "DOCTOR", JwtClaims.SERVICE_TOKEN_TYPE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void identityLookup_refreshTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", UUID.randomUUID())
                        .header("Authorization", "Bearer "
                                + token("account-1", "ADMIN", JwtClaims.REFRESH_TOKEN_TYPE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_SYSTEM_SERVICE")
    void identityLookup_persistenceFailure_returnsUnavailable() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(lookupStaffIdentityUseCase.lookup(staffId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        mockMvc.perform(get(BASE_PATH + "/{id}/lookup", staffId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ORG_LOOKUP_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void lookup_humanRole_returnsForbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}/exists", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void lookup_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/{id}/exists", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void staffList_clinicalRole_isAllowed() throws Exception {
        when(getStaffUseCase.search(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.empty(new com.mediflow.common.api.PageQuery(0, 20)));

        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void staffList_patientRole_isForbidden() throws Exception {
        mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isForbidden());
    }

    private String systemToken() {
        return token("clinical-service", "SYSTEM", JwtClaims.SERVICE_TOKEN_TYPE);
    }

    private String token(String subject, String role, String type) {
        return Jwts.builder()
                .subject(subject)
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, type)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(SIGNING_KEY)
                .compact();
    }
}
