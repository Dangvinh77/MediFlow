package com.mediflow.surgery.web;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryPreopUseCase;
import com.mediflow.surgery.application.port.in.UpdateChecklistItemUseCase;
import com.mediflow.surgery.domain.model.SurgeryActorType;
import com.mediflow.surgery.infrastructure.config.SecurityConfig;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the opt-in checklist and consent mutation boundary.
 *
 * <p>The application port is the only mock. The slice imports the production
 * security configuration and therefore exercises the real JWT filter, role
 * checks, verified-staff extraction and controller validation.</p>
 */
@WebMvcTest(controllers = SurgeryPreopMutationController.class, properties = {
        "mediflow.features.surgery.enabled=true",
        "mediflow.surgery.preop.api.enabled=true",
        "mediflow.jwt.secret=preop-mutation-api-test-secret-with-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class SurgeryPreopMutationApiTest {

    private static final String SECRET = "preop-mutation-api-test-secret-with-at-least-32-bytes";
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CHECKLIST_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID EVIDENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID CONSENT_EVIDENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000204");
    private static final UUID SIGNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000205");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000206");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final UUID OTHER_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000208");
    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000209");
    private static final String CHECKLIST_PATH = "/api/v1/surgery/cases/" + CASE_ID + "/checklist";
    private static final String CONSENT_PATH = "/api/v1/surgery/cases/" + CASE_ID + "/consents";
    private static final String IDEMPOTENCY_KEY = "preop-command-201";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RecordSurgeryPreopUseCase preop;

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE"})
    void checklist_allowedRoleWithVerifiedStaff_returnsReceiptOutcome(String role) throws Exception {
        when(preop.updateChecklist(any())).thenReturn(outcome(false));

        performChecklist(checklistBody(), role, IDEMPOTENCY_KEY)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.data.commandCode").value("UPDATE_CHECKLIST_ITEM"))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false));

        verify(preop).updateChecklist(argThat(command ->
                command.surgeryCaseId().equals(CASE_ID)
                        && command.checklistItemId().equals(CHECKLIST_ITEM_ID)
                        && command.expectedCaseRevision() == 3
                        && command.expectedSnapshotRevision() == 2
                        && command.expectedItemRevision() == 1
                        && command.status().name().equals("SATISFIED")
                        && command.evidenceReferenceId().equals(EVIDENCE_ID)
                        && command.evidenceRevision() == 4
                        && command.idempotencyKey().equals(IDEMPOTENCY_KEY)
                        && command.correlationId().equals(CORRELATION_ID.toString())
                        && command.actor().actorType() == SurgeryActorType.HUMAN
                        && command.actor().accountId().equals(ACCOUNT_ID)
                        && command.actor().verifiedStaffId().equals(STAFF_ID)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE"})
    void consent_allowedRoleWithVerifiedStaff_returnsReceiptOutcome(String role) throws Exception {
        when(preop.recordConsent(any())).thenReturn(consentOutcome(false));

        performConsent(consentBody(), role, IDEMPOTENCY_KEY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", CONSENT_PATH + "/" + CONSENT_EVIDENCE_ID))
                .andExpect(header().string("X-Correlation-Id", CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()))
                .andExpect(jsonPath("$.data.commandCode").value("SIGN_CONSENT"))
                .andExpect(jsonPath("$.data.surgeryCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.data.replayed").value(false));

        verify(preop).recordConsent(argThat(command ->
                command.surgeryCaseId().equals(CASE_ID)
                        && command.expectedCaseRevision() == 3
                        && command.consentType().name().equals("SURGERY")
                        && command.signerId().equals(SIGNER_ID)
                        && command.signerType().name().equals("PATIENT")
                        && command.evidenceDocumentId().equals(CONSENT_EVIDENCE_ID)
                        && command.idempotencyKey().equals(IDEMPOTENCY_KEY)
                        && command.correlationId().equals(CORRELATION_ID.toString())
                        && command.recordedBy().actorType() == SurgeryActorType.HUMAN
                        && command.recordedBy().accountId().equals(ACCOUNT_ID)
                        && command.recordedBy().verifiedStaffId().equals(STAFF_ID)));
    }

    @Test
    void checklist_authorizedReplay_returnsOkWithReplayMarker() throws Exception {
        when(preop.updateChecklist(any())).thenReturn(outcome(true));

        performChecklist(checklistBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true));

        verify(preop).updateChecklist(any(UpdateChecklistItemUseCase.Command.class));
    }

    @Test
    void consent_authorizedReplay_returnsOkWithReplayMarker() throws Exception {
        when(preop.recordConsent(any())).thenReturn(consentOutcome(true));

        performConsent(consentBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isOk())
                .andExpect(header().string("Location", CONSENT_PATH + "/" + CONSENT_EVIDENCE_ID))
                .andExpect(jsonPath("$.data.replayed").value(true));

        verify(preop).recordConsent(any(ManageSurgeryConsentUseCase.SignCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SURGERY", "ANESTHESIA"})
    void consent_supportedType_isAccepted(String consentType) throws Exception {
        when(preop.recordConsent(any())).thenReturn(consentOutcome(false));

        performConsent(consentBody().replace("\"consentType\":\"SURGERY\"",
                        "\"consentType\":\"" + consentType + "\""), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isCreated());

        verify(preop).recordConsent(argThat(command -> command.consentType().name().equals(consentType)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT", "GUARDIAN", "AUTHORIZED_REPRESENTATIVE"})
    void consent_supportedSignerType_isAccepted(String signerType) throws Exception {
        when(preop.recordConsent(any())).thenReturn(consentOutcome(false));

        performConsent(consentBody().replace("\"signerType\":\"PATIENT\"",
                        "\"signerType\":\"" + signerType + "\""), "NURSE", IDEMPOTENCY_KEY)
                .andExpect(status().isCreated());

        verify(preop).recordConsent(argThat(command -> command.signerType().name().equals(signerType)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "PATIENT", "PHARMACIST", "CASHIER", "LAB_TECH"})
    void mutation_forbiddenRole_neverReachesUseCase(String role) throws Exception {
        performChecklist(checklistBody(), role, IDEMPOTENCY_KEY)
                .andExpect(status().isForbidden());
        performConsent(consentBody(), role, IDEMPOTENCY_KEY)
                .andExpect(status().isForbidden());

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_anonymousRequest_returnsUnauthorizedBeforeValidation() throws Exception {
        mvc.perform(put(CHECKLIST_PATH)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Correlation-Id", CORRELATION_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post(CONSENT_PATH)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header("X-Correlation-Id", CORRELATION_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_refreshToken_isRejectedAsUnauthorized() throws Exception {
        performChecklistWithToken(checklistBody(), IDEMPOTENCY_KEY, token("ADMIN", STAFF_ID, "refresh"))
                .andExpect(status().isUnauthorized());
        performConsentWithToken(consentBody(), IDEMPOTENCY_KEY, token("ADMIN", STAFF_ID, "refresh"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_serviceToken_isRejectedAsUnauthorized() throws Exception {
        performChecklistWithToken(checklistBody(), IDEMPOTENCY_KEY, token("ADMIN", STAFF_ID, "service"))
                .andExpect(status().isUnauthorized());
        performConsentWithToken(consentBody(), IDEMPOTENCY_KEY, token("ADMIN", STAFF_ID, "service"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_systemRoleToken_isRejectedAsUnauthorized() throws Exception {
        performChecklistWithToken(checklistBody(), IDEMPOTENCY_KEY, token("SYSTEM", STAFF_ID, "access"))
                .andExpect(status().isUnauthorized());
        performConsentWithToken(consentBody(), IDEMPOTENCY_KEY, token("SYSTEM", STAFF_ID, "access"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_adminWithoutSignedStaffIdentity_isForbidden() throws Exception {
        performChecklistWithToken(checklistBody(), IDEMPOTENCY_KEY, token("ADMIN", null, "access"))
                .andExpect(status().isForbidden());
        performConsentWithToken(consentBody(), IDEMPOTENCY_KEY, token("ADMIN", null, "access"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preop);
    }

    @Test
    void mutation_mismatchedVerifiedAccountAndDetails_isForbidden() throws Exception {
        var mismatched = new UsernamePasswordAuthenticationToken(
                ACCOUNT_ID, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mismatched.setDetails(new SurgeryAuthenticationDetails(
                OTHER_ACCOUNT_ID, STAFF_ID, null, null));

        mvc.perform(put(CHECKLIST_PATH)
                        .with(authentication(mismatched))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checklistBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_missingIdempotencyKey_returnsInvalidRequest() throws Exception {
        mvc.perform(put(CHECKLIST_PATH)
                        .header("Authorization", "Bearer " + token("ADMIN", STAFF_ID, "access"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checklistBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @Test
    void consent_missingIdempotencyKey_returnsInvalidRequest() throws Exception {
        mvc.perform(post(CONSENT_PATH)
                        .header("Authorization", "Bearer " + token("ADMIN", STAFF_ID, "access"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(consentBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @Test
    void consent_blankIdempotencyKey_returnsValidationError() throws Exception {
        performConsent(consentBody(), "ADMIN", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_overlongIdempotencyKey_returnsValidationError() throws Exception {
        performChecklist(checklistBody(), "ADMIN", "x".repeat(161))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expectedCaseRevision", "expectedSnapshotRevision", "expectedItemRevision",
            "evidenceRevision"})
    void checklist_negativeRevision_returnsValidationError(String field) throws Exception {
        String invalid = checklistBody().replace(revisionJson(field), revisionJson(field, -1));

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void consent_negativeCaseRevision_returnsValidationError() throws Exception {
        performConsent(consentBody().replace("\"expectedCaseRevision\":3", "\"expectedCaseRevision\":-1"),
                        "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_satisfiedWithoutEvidenceReference_returnsValidationError() throws Exception {
        String invalid = checklistBody().replace(
                "\"evidenceReferenceId\":\"" + EVIDENCE_ID + "\"", "\"evidenceReferenceId\":null");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_satisfiedWithoutEvidenceRevision_returnsValidationError() throws Exception {
        String invalid = checklistBody().replace("\"evidenceRevision\":4", "\"evidenceRevision\":null");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_pendingWithEvidence_returnsValidationError() throws Exception {
        String invalid = checklistBody().replace("\"status\":\"SATISFIED\"", "\"status\":\"PENDING\"");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_notApplicableStatus_isRejected() throws Exception {
        String invalid = checklistBody().replace("\"status\":\"SATISFIED\"", "\"status\":\"NOT_APPLICABLE\"")
                .replace("\"evidenceReferenceId\":\"" + EVIDENCE_ID + "\"", "\"evidenceReferenceId\":null")
                .replace("\"evidenceRevision\":4", "\"evidenceRevision\":null");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_failedEvidenceRevisionWithoutReference_returnsValidationError() throws Exception {
        String invalid = checklistBody().replace("\"status\":\"SATISFIED\"", "\"status\":\"FAILED\"")
                .replace("\"evidenceReferenceId\":\"" + EVIDENCE_ID + "\"", "\"evidenceReferenceId\":null");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_unknownStatus_returnsInvalidRequest() throws Exception {
        performChecklist(checklistBody().replace("\"status\":\"SATISFIED\"", "\"status\":\"UNKNOWN\""),
                        "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @ParameterizedTest
    @ValueSource(strings = {"actorId", "accountId", "staffId", "role", "ready", "approved",
            "financialClearanceValid", "emergencyOverride"})
    void checklist_unknownAuthorityField_returnsInvalidRequest(String field) throws Exception {
        String invalid = checklistBody().replaceFirst("\\{", "{\"" + field + "\":true,");

        performChecklist(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @ParameterizedTest
    @ValueSource(strings = {"actorId", "accountId", "staffId", "role", "ready", "approved",
            "financialClearanceValid", "emergencyOverride"})
    void consent_unknownAuthorityField_returnsInvalidRequest(String field) throws Exception {
        String invalid = consentBody().replaceFirst("\\{", "{\"" + field + "\":true,");

        performConsent(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @Test
    void consent_unknownConsentType_returnsInvalidRequest() throws Exception {
        performConsent(consentBody().replace("\"consentType\":\"SURGERY\"", "\"consentType\":\"UNKNOWN\""),
                        "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @Test
    void consent_unknownSignerType_returnsInvalidRequest() throws Exception {
        performConsent(consentBody().replace("\"signerType\":\"PATIENT\"", "\"signerType\":\"UNKNOWN\""),
                        "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(preop);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expectedCaseRevision", "consentType", "signerId", "signerType", "evidenceDocumentId"})
    void consent_missingRequiredField_returnsValidationError(String field) throws Exception {
        String invalid = consentBody().replace(consentJson(field), consentJson(field, "null"));

        performConsent(invalid, "ADMIN", IDEMPOTENCY_KEY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(preop);
    }

    @Test
    void checklist_revisionConflict_returnsConflictWithoutPrivateDetail() throws Exception {
        when(preop.updateChecklist(any())).thenThrow(new SurgeryRevisionConflictException());

        performChecklist(checklistBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SURGERY_REVISION_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("private"))))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()));
    }

    @Test
    void consent_businessRuleFailure_returnsUnprocessableWithoutPrivateDetail() throws Exception {
        when(preop.recordConsent(any())).thenThrow(new BusinessRuleException(
                "SURGERY_CONSENT_POLICY_INVALID", "private signer policy and document payload"));

        performConsent(consentBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SURGERY_CONSENT_POLICY_INVALID"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("private signer policy"))));
    }

    @Test
    void checklist_missingCase_returnsNotFoundWithoutPrivateDetail() throws Exception {
        when(preop.updateChecklist(any())).thenThrow(new ResourceNotFoundException(
                "SURGERY_CASE_NOT_FOUND", "private case and patient data"));

        performChecklist(checklistBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SURGERY_CASE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("private case"))));
    }

    @Test
    void consent_upstreamFailure_returnsServiceUnavailableWithoutPrivateDetail() throws Exception {
        when(preop.recordConsent(any())).thenThrow(new UpstreamUnavailableException(
                "private organization authority response"));

        performConsent(consentBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SURGERY_UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("private organization"))));
    }

    @Test
    void checklist_unexpectedFailure_returnsInternalErrorWithoutPrivateDetail() throws Exception {
        doThrow(new IllegalStateException("private SQL, JWT claims and clinical payload"))
                .when(preop).updateChecklist(any());

        performChecklist(checklistBody(), "DOCTOR", IDEMPOTENCY_KEY)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(not(containsString("private SQL"))))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID.toString()));
    }

    @Test
    void consent_revokePath_isNotMapped() throws Exception {
        mvc.perform(post(CONSENT_PATH + "/00000000-0000-0000-0000-000000000210/revoke")
                        .header("Authorization", "Bearer " + token("ADMIN", STAFF_ID, "access"))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(preop);
    }

    private ResultActions performChecklist(String body, String role, String key) throws Exception {
        return performChecklistWithToken(body, key, token(role, STAFF_ID, "access"));
    }

    private ResultActions performChecklistWithToken(String body, String key, String jwt) throws Exception {
        return mvc.perform(put(CHECKLIST_PATH)
                .header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .header("X-Correlation-Id", CORRELATION_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions performConsent(String body, String role, String key) throws Exception {
        return performConsentWithToken(body, key, token(role, STAFF_ID, "access"));
    }

    private ResultActions performConsentWithToken(String body, String key, String jwt) throws Exception {
        return mvc.perform(post(CONSENT_PATH)
                .header("Authorization", "Bearer " + jwt)
                .header("Idempotency-Key", key)
                .header("X-Correlation-Id", CORRELATION_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static SurgeryCommandOutcome outcome(boolean replayed) {
        return new SurgeryCommandOutcome("UPDATE_CHECKLIST_ITEM", CASE_ID, 4, CHECKLIST_ITEM_ID,
                2, "APPLIED", Instant.parse("2026-10-07T01:00:00Z"), replayed);
    }

    private static SurgeryCommandOutcome consentOutcome(boolean replayed) {
        return new SurgeryCommandOutcome("SIGN_CONSENT", CASE_ID, 4, CONSENT_EVIDENCE_ID,
                2, "APPLIED", Instant.parse("2026-10-07T01:00:00Z"), replayed);
    }

    private static String checklistBody() {
        return """
                {
                  "checklistItemId":"%s",
                  "expectedCaseRevision":3,
                  "expectedSnapshotRevision":2,
                  "expectedItemRevision":1,
                  "status":"SATISFIED",
                  "evidenceReferenceId":"%s",
                  "evidenceRevision":4
                }
                """.formatted(CHECKLIST_ITEM_ID, EVIDENCE_ID);
    }

    private static String consentBody() {
        return """
                {
                  "expectedCaseRevision":3,
                  "consentType":"SURGERY",
                  "signerId":"%s",
                  "signerType":"PATIENT",
                  "evidenceDocumentId":"%s"
                }
                """.formatted(SIGNER_ID, CONSENT_EVIDENCE_ID);
    }

    private static String revisionJson(String field) {
        return switch (field) {
            case "expectedCaseRevision" -> "\"expectedCaseRevision\":3";
            case "expectedSnapshotRevision" -> "\"expectedSnapshotRevision\":2";
            case "expectedItemRevision" -> "\"expectedItemRevision\":1";
            case "evidenceRevision" -> "\"evidenceRevision\":4";
            default -> throw new IllegalArgumentException("Unknown revision field: " + field);
        };
    }

    private static String revisionJson(String field, long value) {
        return switch (field) {
            case "expectedCaseRevision" -> "\"expectedCaseRevision\":" + value;
            case "expectedSnapshotRevision" -> "\"expectedSnapshotRevision\":" + value;
            case "expectedItemRevision" -> "\"expectedItemRevision\":" + value;
            case "evidenceRevision" -> "\"evidenceRevision\":" + value;
            default -> throw new IllegalArgumentException("Unknown revision field: " + field);
        };
    }

    private static String consentJson(String field) {
        return switch (field) {
            case "expectedCaseRevision" -> "\"expectedCaseRevision\":3";
            case "consentType" -> "\"consentType\":\"SURGERY\"";
            case "signerId" -> "\"signerId\":\"" + SIGNER_ID + "\"";
            case "signerType" -> "\"signerType\":\"PATIENT\"";
            case "evidenceDocumentId" -> "\"evidenceDocumentId\":\"" + CONSENT_EVIDENCE_ID + "\"";
            default -> throw new IllegalArgumentException("Unknown consent field: " + field);
        };
    }

    private static String consentJson(String field, String value) {
        return (switch (field) {
            case "expectedCaseRevision" -> "\"expectedCaseRevision\":3";
            case "consentType" -> "\"consentType\":\"SURGERY\"";
            case "signerId" -> "\"signerId\":\"" + SIGNER_ID + "\"";
            case "signerType" -> "\"signerType\":\"PATIENT\"";
            case "evidenceDocumentId" -> "\"evidenceDocumentId\":\"" + CONSENT_EVIDENCE_ID + "\"";
            default -> throw new IllegalArgumentException("Unknown consent field: " + field);
        }).replaceFirst(":(?:3|\"[A-Z_]+\"|\"[0-9a-f-]+\")", ":" + value);
    }

    private static String token(String role, UUID staffId, String type) {
        var builder = Jwts.builder()
                .subject(ACCOUNT_ID.toString())
                .claim("type", type)
                .claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(60)));
        if (staffId != null) {
            builder.claim("staffId", staffId.toString());
        }
        return builder.signWith(signingKey(SECRET)).compact();
    }

    private static SecretKey signingKey(String value) {
        return Keys.hmacShaKeyFor(value.getBytes(StandardCharsets.UTF_8));
    }
}

/** Verifies that the Surgery business flag is independently required. */
@WebMvcTest(controllers = SurgeryPreopMutationController.class, properties = {
        "mediflow.features.surgery.enabled=false",
        "mediflow.surgery.preop.api.enabled=true",
        "mediflow.jwt.secret=preop-mutation-disabled-test-secret-with-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class SurgeryPreopMutationApiBusinessFlagDisabledTest {

    private static final String SECRET = "preop-mutation-disabled-test-secret-with-at-least-32-bytes";
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000206");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RecordSurgeryPreopUseCase preop;

    @Test
    void mutation_businessFlagDisabled_routeIsNotMapped() throws Exception {
        mvc.perform(put("/api/v1/surgery/cases/" + CASE_ID + "/checklist")
                        .header("Authorization", "Bearer " + token())
                        .header("Idempotency-Key", "disabled-preop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(preop);
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

/** Verifies that the pre-op API flag is independently required. */
@WebMvcTest(controllers = SurgeryPreopMutationController.class, properties = {
        "mediflow.features.surgery.enabled=true",
        "mediflow.surgery.preop.api.enabled=false",
        "mediflow.jwt.secret=preop-mutation-disabled-test-secret-with-at-least-32-bytes"
})
@Import(SecurityConfig.class)
class SurgeryPreopMutationApiFeatureFlagDisabledTest {

    private static final String SECRET = "preop-mutation-disabled-test-secret-with-at-least-32-bytes";
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000206");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000207");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RecordSurgeryPreopUseCase preop;

    @Test
    void mutation_preopApiFlagDisabled_routeIsNotMapped() throws Exception {
        mvc.perform(post("/api/v1/surgery/cases/" + CASE_ID + "/consents")
                        .header("Authorization", "Bearer " + token())
                        .header("Idempotency-Key", "disabled-preop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(preop);
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
