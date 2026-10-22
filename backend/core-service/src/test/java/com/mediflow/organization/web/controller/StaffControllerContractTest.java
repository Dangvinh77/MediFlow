package com.mediflow.organization.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static final String ACTOR_ID = "22222222-2222-2222-2222-222222222222";
    private static final String IDEMPOTENCY_KEY = "staff-create-contract-1";
    private static final UUID DEPARTMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "\"\"", "\"12345678901\"", "\"1234567890123\"", "\"01234567890A\""})
    @WithMockUser(roles = "ADMIN")
    void createStaff_requiresValidIdentity(String identityJson) throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Staff\",\"departmentId\":\"" + DEPARTMENT_ID
                                + "\",\"jobTitle\":\"NURSE\",\"identityNumber\":" + identityJson + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("identityNumber"));
        org.mockito.Mockito.verifyNoInteractions(createStaffUseCase);
    }

    @Test
    @WithMockUser(username = ACTOR_ID, roles = "ADMIN")
    void createStaff_keyedReplayReturnsSameLocationWithExplicitReplayFlag() throws Exception {
        var staff = com.mediflow.organization.domain.model.Staff.create(
                "Alex Morgan", DEPARTMENT_ID,
                com.mediflow.organization.domain.model.JobTitle.NURSE,
                null, null, null, null, "012345678901");
        when(createStaffUseCase.executeWithIdempotency(
                UUID.fromString(ACTOR_ID), IDEMPOTENCY_KEY, "Alex Morgan", DEPARTMENT_ID,
                com.mediflow.organization.domain.model.JobTitle.NURSE, null, null, null, null, "012345678901"))
                .thenReturn(new CreateStaffUseCase.CreationResult(staff, false),
                        new CreateStaffUseCase.CreationResult(staff, true));
        String body = "{\"fullName\":\"Alex Morgan\",\"departmentId\":\"" + DEPARTMENT_ID
                + "\",\"jobTitle\":\"NURSE\",\"identityNumber\":\"012345678901\"}";

        mockMvc.perform(post(BASE_PATH).header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(header().string("Location", "/api/v1/org/staff/" + staff.getStaffId()))
                .andExpect(jsonPath("$.data.staffId").value(staff.getStaffId().toString()))
                .andExpect(jsonPath("$.data.identityNumber").value("012345678901"));
        mockMvc.perform(post(BASE_PATH).header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(header().string("Location", "/api/v1/org/staff/" + staff.getStaffId()))
                .andExpect(jsonPath("$.data.staffId").value(staff.getStaffId().toString()))
                .andExpect(jsonPath("$.data.identityNumber").value("012345678901"));
    }

    @Test
    @WithMockUser(username = ACTOR_ID, roles = "ADMIN")
    void createStaff_keyedDifferentPayloadReturnsStableConflict() throws Exception {
        when(createStaffUseCase.executeWithIdempotency(
                UUID.fromString(ACTOR_ID), IDEMPOTENCY_KEY, "Alex Morgan", DEPARTMENT_ID,
                com.mediflow.organization.domain.model.JobTitle.NURSE, null, null, null, null, "012345678901"))
                .thenThrow(new com.mediflow.organization.domain.exception.StaffCreateIdempotencyConflictException());

        mockMvc.perform(post(BASE_PATH).header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Alex Morgan\",\"departmentId\":\"" + DEPARTMENT_ID
                                + "\",\"jobTitle\":\"NURSE\",\"identityNumber\":\"012345678901\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STAFF_IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void changingDepartmentHeadTitleReturnsExistingBusinessErrorContract() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(updateStaffUseCase.execute(staffId, "Audit Nurse",
                com.mediflow.organization.domain.model.JobTitle.NURSE, null, null, null, null))
                .thenThrow(new com.mediflow.organization.domain.exception.InvalidDepartmentHeadException(
                        "Remove or reassign the department head before changing their professional title"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE_PATH + "/{id}", staffId)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Audit Nurse\",\"jobTitle\":\"NURSE\",\"identityNumber\":\"012345678901\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DEPARTMENT_HEAD_INVALID"))
                .andExpect(header().exists(JwtClaims.HEADER_CORRELATION_ID));
    }

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

    @Test
    @WithMockUser(roles = "DOCTOR")
    void staffList_invalidJobTitle_returnsInvalidRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH + "?jobTitle=UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void staffList_invalidPage_returnsInvalidRequest() throws Exception {
        mockMvc.perform(get(BASE_PATH + "?page=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE"})
    void staffReads_onlyAdminReceivesPersonalInformation(String role) throws Exception {
        var staff = com.mediflow.organization.domain.model.Staff.create(
                "Directory Person", DEPARTMENT_ID,
                com.mediflow.organization.domain.model.JobTitle.DOCTOR,
                "Cardiology", "LICENSE-PRIVATE", "0901234567", "private@example.test", "012345678901");
        when(getStaffUseCase.getStaffById(staff.getStaffId())).thenReturn(staff);
        when(getStaffUseCase.search(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(List.of(staff), 1, 0, 20));
        for (String suffix : List.of("", "/" + staff.getStaffId())) {
            String root = suffix.isEmpty() ? "$.data.content[0]" : "$.data";
            var result = mockMvc.perform(get(BASE_PATH + suffix)
                            .header("Authorization", "Bearer " + token(ACTOR_ID, role, JwtClaims.ACCESS_TOKEN_TYPE)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath(root + ".fullName").value("Directory Person"))
                    .andExpect(jsonPath(root + ".departmentId").value(DEPARTMENT_ID.toString()));
            if ("ADMIN".equals(role)) {
                result.andExpect(jsonPath(root + ".identityNumber").value("012345678901"))
                        .andExpect(jsonPath(root + ".licenseNumber").value("LICENSE-PRIVATE"))
                        .andExpect(jsonPath(root + ".phoneNumber").value("0901234567"))
                        .andExpect(jsonPath(root + ".email").value("private@example.test"))
                        .andExpect(jsonPath(root + ".createdAt").isNotEmpty());
            } else {
                for (String field : List.of("identityNumber", "licenseNumber", "phoneNumber", "email", "createdAt", "updatedAt")) {
                    result.andExpect(jsonPath(root + "." + field).doesNotExist());
                }
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH"})
    void signedStaffIdentityCanReadOwnPrivateProfile(String role) throws Exception {
        var staff = com.mediflow.organization.domain.model.Staff.create("My Profile", DEPARTMENT_ID,
                com.mediflow.organization.domain.model.JobTitle.DOCTOR,
                "Cardiology", "MY-LICENSE", "0901234567", "mine@example.test", "012345678901");
        when(getStaffUseCase.getStaffById(staff.getStaffId())).thenReturn(staff);
        String access = staffAccessToken(role, staff.getStaffId());
        mockMvc.perform(get(BASE_PATH + "/{id}", staff.getStaffId())
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.identityNumber").value("012345678901"))
                .andExpect(jsonPath("$.data.email").value("mine@example.test"));
        if (List.of("MANAGER", "DOCTOR", "NURSE").contains(role)) {
            when(getStaffUseCase.search(org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(PageResult.of(List.of(staff), 1, 0, 20));
            mockMvc.perform(get(BASE_PATH).header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].identityNumber").doesNotExist())
                    .andExpect(jsonPath("$.data.content[0].email").doesNotExist());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PHARMACIST", "CASHIER", "LAB_TECH"})
    void personalAccessDoesNotGrantDirectoryOrAnotherStaffProfile(String role) throws Exception {
        UUID ownId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        for (String access : List.of(staffAccessToken(role, ownId), token(ACTOR_ID, role, JwtClaims.ACCESS_TOKEN_TYPE))) {
            mockMvc.perform(get(BASE_PATH + "/{id}", otherId)
                            .header("Authorization", "Bearer " + access)
                            .header("X-Staff-Id", otherId.toString()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(BASE_PATH).header("Authorization", "Bearer " + access))
                    .andExpect(status().isForbidden());
        }
        org.mockito.Mockito.verifyNoInteractions(getStaffUseCase);
    }

    private String staffAccessToken(String role, UUID staffId) {
        return Jwts.builder().subject(ACTOR_ID).claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE).claim(JwtClaims.STAFF_ID, staffId.toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(SIGNING_KEY).compact();
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
