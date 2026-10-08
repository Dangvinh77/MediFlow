package com.mediflow.surgery.web;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.dto.SurgeryCreationOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.infrastructure.config.SecurityConfig;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the opt-in Surgery case creation boundary.
 *
 * <p>The use case is deliberately mocked here: clinical authority and the
 * creation transaction belong to the application tests. This suite proves
 * that the driving adapter validates the wire contract, authenticates a
 * trusted actor, applies the dual feature gate, and maps stable outcomes and
 * failures without leaking request or dependency details.</p>
 */
@WebMvcTest(controllers = SurgeryCreationController.class, properties = {
        "mediflow.features.surgery.enabled=true",
        "mediflow.surgery.creation.api.enabled=true",
        "mediflow.jwt.secret=creation-api-test-secret-with-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class SurgeryCreationApiTest {

    private static final String SECRET = "creation-api-test-secret-with-at-least-32-bytes";
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID CHECKLIST_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID EPISODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000105");
    private static final UUID DEPARTMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000106");
    private static final UUID REQUESTER_ID = UUID.fromString("00000000-0000-0000-0000-000000000107");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000108");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000109");
    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000110");
    private static final String PATH = "/api/v1/surgery/cases";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CreateSurgeryCaseUseCase creation;

    @Test
    void create_adminWithVerifiedStaff_validRequest_returnsCreatedOutcomeAndTrustedActor() throws Exception {
        when(creation.create(any())).thenReturn(freshOutcome(false));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PATH + "/" + CASE_ID))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.data.checklistSnapshotId").value(CHECKLIST_ID.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.data.actor").doesNotExist());

        verify(creation).create(argThat(command ->
                command.requestId().equals(REQUEST_ID)
                        && command.requestedBy().equals(REQUESTER_ID)
                        && command.actor().accountId().equals(ACCOUNT_ID)
                        && command.actor().verifiedStaffId().equals(STAFF_ID)
                        && command.correlationId().equals(CORRELATION_ID.toString())
                        && command.careEpisode().episodeId().equals(EPISODE_ID)));
    }

    @Test
    void create_doctorWithVerifiedStaff_validRequest_returnsCreatedOutcome() throws Exception {
        when(creation.create(any())).thenReturn(freshOutcome(false));

        perform(validBody(), "DOCTOR", canonicalKey())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PATH + "/" + CASE_ID));

        verify(creation).create(any());
    }

    @Test
    void create_replayedOutcome_returnsOkWithSameLocationAndReplayMarker() throws Exception {
        when(creation.create(any())).thenReturn(freshOutcome(true));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isOk())
                .andExpect(header().string("Location", PATH + "/" + CASE_ID))
                .andExpect(jsonPath("$.data.requestId").value(REQUEST_ID.toString()))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.data.replayed").value(true));

        verify(creation).create(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "MANAGER", "PATIENT"})
    void create_nonClinicalRole_isForbiddenBeforeUseCase(String role) throws Exception {
        perform(validBody(), role, canonicalKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_anonymousRequest_returnsUnauthorizedBeforeValidation() throws Exception {
        mvc.perform(post(PATH)
                        .header("Idempotency-Key", canonicalKey())
                        .header("X-Correlation-Id", CORRELATION_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_refreshToken_isRejectedAsUnauthorized() throws Exception {
        performWithToken(validBody(), canonicalKey(), token("ADMIN", STAFF_ID, "refresh"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(creation);
    }

    @Test
    void create_systemRoleToken_isRejectedAsUnauthorized() throws Exception {
        performWithToken(validBody(), canonicalKey(), token("SYSTEM", STAFF_ID, "access"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(creation);
    }

    @Test
    void create_forgedNonUuidSubject_isRejectedAsUnauthorized() throws Exception {
        performWithToken(validBody(), canonicalKey(), tokenWithSubject(
                        "ADMIN", STAFF_ID, "access", signingKey(SECRET), "not-an-account-id"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(creation);
    }

    @Test
    void create_invalidSignature_isRejectedAsUnauthorized() throws Exception {
        performWithToken(validBody(), canonicalKey(), token("ADMIN", STAFF_ID, "access", otherKey()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(creation);
    }

    @Test
    void create_missingStaffIdentity_isForbiddenEvenForAdmin() throws Exception {
        performWithToken(validBody(), canonicalKey(), token("ADMIN", null, "access"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_mismatchedAuthenticationDetails_isForbidden() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                ACCOUNT_ID, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails(new SurgeryAuthenticationDetails(
                UUID.fromString("00000000-0000-0000-0000-000000000199"), STAFF_ID, null, null));

        mvc.perform(post(PATH)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .authentication(authentication))
                        .header("Idempotency-Key", canonicalKey())
                        .header("X-Correlation-Id", CORRELATION_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_missingIdempotencyKey_returnsInvalidRequest() throws Exception {
        mvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + token("ADMIN", STAFF_ID, "access"))
                        .header("X-Correlation-Id", CORRELATION_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_mismatchedIdempotencyKey_returnsInvalidRequest() throws Exception {
        perform(validBody(), "ADMIN", UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_nonCanonicalIdempotencyKey_returnsInvalidRequest() throws Exception {
        perform(validBody(), "ADMIN", canonicalKey().toUpperCase())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_overlongIdempotencyKey_returnsValidationError() throws Exception {
        perform(validBody(), "ADMIN", "x".repeat(161))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_missingRequiredFields_returnsValidationError() throws Exception {
        perform("{}", "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_invalidPlannedQuantity_returnsValidationError() throws Exception {
        for (String invalidQuantity : new String[]{"0", "-1", "1.12345", "1000000000000000", "null"}) {
            perform(validBody().replace("1.5", invalidQuantity), "ADMIN", canonicalKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(creation);
    }

    @Test
    void create_duplicatePlannedItemCodes_returnsValidationError() throws Exception {
        perform(bodyWithItems("ITEM-A", "PRICE-A", "ITEM-A", "PRICE-B"), "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_moreThanOneHundredPlannedItems_returnsValidationError() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int index = 0; index < 101; index++) {
            if (index > 0) {
                items.append(',');
            }
            items.append("{\"itemCode\":\"ITEM-").append(index)
                    .append("\",\"priceCode\":\"PRICE-").append(index)
                    .append("\",\"quantity\":1.5}");
        }

        perform(bodyWithItemsJson(items.toString()), "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpectedField", "actorId", "accountId", "staffId", "role", "ready", "status",
            "emergencyOverride", "financialClearanceValid", "amount", "approved", "templateId", "systemProducer"})
    void create_unknownTopLevelField_returnsInvalidRequest(String field) throws Exception {
        perform(validBody().replaceFirst("\\{", "{\"" + field + "\":true,"), "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unexpectedField", "amount", "currency", "totalAmount"})
    void create_unknownNestedField_returnsInvalidRequest(String field) throws Exception {
        perform(validBody().replace("\"quantity\":1.5", "\"quantity\":1.5,\"" + field + "\":true"),
                        "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_outpatientWithAdmissionId_returnsValidationError() throws Exception {
        perform(validBody().replace("\"admissionId\":null", "\"admissionId\":\"" + EPISODE_ID + "\""),
                        "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_admissionWithMismatchedAdmissionId_returnsValidationError() throws Exception {
        String admission = "00000000-0000-0000-0000-000000000199";
        String body = validBody()
                .replace("OUTPATIENT_VISIT", "ADMISSION")
                .replace("\"admissionId\":null", "\"admissionId\":\"" + admission + "\"");

        perform(body, "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_malformedUuid_returnsInvalidRequest() throws Exception {
        perform(validBody().replace(REQUEST_ID.toString(), "not-a-uuid"), "ADMIN", canonicalKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(creation);
    }

    @Test
    void create_wrongPath_isNotMapped() throws Exception {
        mvc.perform(post(PATH + "/" + CASE_ID)
                        .header("Authorization", "Bearer " + token("ADMIN", STAFF_ID, "access"))
                        .header("Idempotency-Key", canonicalKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(creation);
    }

    @Test
    void create_revisionConflict_returnsConflictWithoutPrivateDetail() throws Exception {
        when(creation.create(any())).thenThrow(new SurgeryRevisionConflictException());

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SURGERY_REVISION_CONFLICT"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()));

        verify(creation).create(any());
    }

    @Test
    void create_businessRuleFailure_returnsUnprocessableEntityWithoutSensitiveDetail() throws Exception {
        when(creation.create(any())).thenThrow(new SurgeryRuleException(
                "SURGERY_CREATION_AUTHORITY_INVALID", "private clinical authority payload"));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SURGERY_CREATION_AUTHORITY_INVALID"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private clinical authority payload"))));

        verify(creation).create(any());
    }

    @Test
    void create_missingResource_returnsNotFoundWithoutSensitiveDetail() throws Exception {
        when(creation.create(any())).thenThrow(new ResourceNotFoundException(
                "SURGERY_TEMPLATE_NOT_FOUND", "private template storage path"));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SURGERY_TEMPLATE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private template storage path"))));

        verify(creation).create(any());
    }

    @Test
    void create_upstreamUnavailable_returnsServiceUnavailableWithoutSensitiveDetail() throws Exception {
        when(creation.create(any())).thenThrow(new UpstreamUnavailableException("private dependency payload"));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SURGERY_UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private dependency payload"))));

        verify(creation).create(any());
    }

    @Test
    void create_unexpectedFailure_returnsInternalErrorWithoutSensitiveDetail() throws Exception {
        when(creation.create(any())).thenThrow(new IllegalStateException("private sql and token payload"));

        perform(validBody(), "ADMIN", canonicalKey())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private sql and token payload"))));

        verify(creation).create(any());
    }

    private ResultActions perform(String body, String role, String key) throws Exception {
        return performWithToken(body, key, token(role, STAFF_ID, "access"));
    }

    private ResultActions performWithToken(String body, String key, String jwt) throws Exception {
        return mvc.perform(post(PATH)
                .header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .header("X-Correlation-Id", CORRELATION_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static SurgeryCreationOutcome freshOutcome(boolean replayed) {
        return new SurgeryCreationOutcome(
                REQUEST_ID, CASE_ID, CHECKLIST_ID, Instant.parse("2026-10-07T01:00:00Z"), replayed);
    }

    private static String canonicalKey() {
        return REQUEST_ID.toString();
    }

    private static String validBody() {
        return """
                {
                  "surgeryRequestId":"%s",
                  "careEpisodeType":"OUTPATIENT_VISIT",
                  "careEpisodeId":"%s",
                  "admissionId":null,
                  "recordId":null,
                  "patientId":"%s",
                  "departmentId":"%s",
                  "requestedBy":"%s",
                  "procedureCode":"APPENDECTOMY",
                  "indication":"Appendix inflammation",
                  "priority":"ROUTINE",
                  "requestedAt":"2026-10-07T00:59:30Z",
                  "templateRevision":1,
                  "plannedItems":[{"itemCode":"ITEM-A","priceCode":"PRICE-A","quantity":1.5}]
                }
                """.formatted(REQUEST_ID, EPISODE_ID, PATIENT_ID, DEPARTMENT_ID, REQUESTER_ID);
    }

    private static String bodyWithItems(String firstItemCode, String firstPriceCode,
                                        String secondItemCode, String secondPriceCode) {
        String items = "{\"itemCode\":\"" + firstItemCode + "\",\"priceCode\":\""
                + firstPriceCode + "\",\"quantity\":1.5},{\"itemCode\":\""
                + secondItemCode + "\",\"priceCode\":\"" + secondPriceCode
                + "\",\"quantity\":1.5}";
        return bodyWithItemsJson(items);
    }

    private static String bodyWithItemsJson(String items) {
        return """
                {
                  "surgeryRequestId":"%s",
                  "careEpisodeType":"OUTPATIENT_VISIT",
                  "careEpisodeId":"%s",
                  "admissionId":null,
                  "recordId":null,
                  "patientId":"%s",
                  "departmentId":"%s",
                  "requestedBy":"%s",
                  "procedureCode":"APPENDECTOMY",
                  "indication":"Appendix inflammation",
                  "priority":"ROUTINE",
                  "requestedAt":"2026-10-07T00:59:30Z",
                  "templateRevision":1,
                  "plannedItems":[%s]
                }
                """.formatted(REQUEST_ID, EPISODE_ID, PATIENT_ID, DEPARTMENT_ID, REQUESTER_ID, items);
    }

    private static String token(String role, UUID staffId, String type) {
        return token(role, staffId, type, signingKey(SECRET));
    }

    private static String token(String role, UUID staffId, String type, SecretKey key) {
        return tokenWithSubject(role, staffId, type, key, ACCOUNT_ID.toString());
    }

    private static String tokenWithSubject(String role, UUID staffId, String type, SecretKey key,
                                           String subject) {
        var builder = Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(60)));
        if (staffId != null) {
            builder.claim("staffId", staffId.toString());
        }
        return builder.signWith(key).compact();
    }

    private static SecretKey signingKey(String value) {
        return Keys.hmacShaKeyFor(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SecretKey otherKey() {
        return signingKey("different-signing-key-with-at-least-32-bytes");
    }
}

/** Verifies that both independent feature flags are required to expose creation. */
@WebMvcTest(controllers = SurgeryCreationController.class, properties = {
        "mediflow.features.surgery.enabled=true",
        "mediflow.surgery.creation.api.enabled=false",
        "mediflow.jwt.secret=creation-api-test-secret-with-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class SurgeryCreationApiDisabledTest {

    private static final String SECRET = "creation-api-test-secret-with-at-least-32-bytes";
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000108");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000109");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CreateSurgeryCaseUseCase creation;

    @Test
    void create_apiFlagDisabled_routeIsNotMapped() throws Exception {
        mvc.perform(post("/api/v1/surgery/cases")
                        .header("Authorization", "Bearer " + token())
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(creation);
    }

    private static String token() {
        return Jwts.builder()
                .subject(ACCOUNT_ID.toString())
                .claim("staffId", STAFF_ID.toString())
                .claim("type", "access")
                .claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
