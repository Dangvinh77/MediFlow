package com.mediflow.inpatient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import com.mediflow.inpatient.application.port.out.SurgeryEventReceiptRepositoryPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.application.dto.request.CreateAdmissionRequest;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;
import com.mediflow.inpatient.application.dto.response.ClinicalOrderReferenceDTO;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InpatientServiceSmokeTest {

    private static final String SECRET = "inpatient-test-secret-at-least-32-bytes";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(
            SECRET.getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private AdmissionRepositoryPort admissions;
    @MockBean private com.mediflow.inpatient.application.port.out.AdmissionAuthorityRepositoryPort admissionAuthority;
    @MockBean private BedRepositoryPort beds;
    @MockBean private BedAssignmentRepositoryPort assignments;
    @MockBean private TreatmentEntryRepositoryPort treatments;
    @MockBean private ClinicalOrderReferenceRepositoryPort references;
    @MockBean private DischargeSummaryRepositoryPort discharges;
    @MockBean private ProcessedEventPort processedEvents;
    @MockBean private SurgeryEventReceiptRepositoryPort surgeryReceipts;
    @MockBean private InpatientEventStorePort eventStore;
    @MockBean private InpatientOutboxPort outbox;
    @MockBean private DepositSuggestionPolicyPort depositSuggestions;

    @Test
    void applicationContext_startsWithoutExternalInfrastructure() {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(ConnectionFactory.class)).isEmpty();
        assertThat(applicationContext.getEnvironment()
                .getProperty("eureka.client.enabled", Boolean.class)).isFalse();
    }

    @Test
    void rabbitOutboxUsesCorrelatedConfirmsAndReturnsForMandatoryMessages() {
        var environment = applicationContext.getEnvironment();

        assertThat(environment.getProperty("spring.rabbitmq.publisher-confirm-type"))
                .isEqualTo("correlated");
        assertThat(environment.getProperty("spring.rabbitmq.publisher-returns", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.rabbitmq.template.mandatory", Boolean.class))
                .isTrue();
    }

    @Test
    void healthAndInfo_arePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("MediFlow Inpatient Service"));
    }

    @Test
    void swaggerAndOpenApiAssets_arePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MediFlow Inpatient Service"));
    }

    @Test
    void plannedBusinessPath_withoutCorrelationId_generatesCorrelationIdAndRequiresAuthentication()
            throws Exception {

        MvcResult result = mockMvc.perform(get("/api/v1/inpatient/admissions"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String correlationId = result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID);
        assertThatCode(() -> UUID.fromString(correlationId)).doesNotThrowAnyException();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("correlationId").asText()).isEqualTo(correlationId);
    }

    @Test
    void unauthorizedRequest_validCorrelationId_preservesHeaderAndEnvelope() throws Exception {
        String correlationId = "c7146758-cfaa-4f6f-9615-4cf3b8044442";

        mockMvc.perform(get("/api/v1/inpatient/admissions")
                        .header(JwtClaims.HEADER_CORRELATION_ID, correlationId))
                .andExpect(status().isUnauthorized())
                .andExpect(header()
                        .string(JwtClaims.HEADER_CORRELATION_ID, correlationId))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }

    @Test
    void unauthorizedRequest_invalidCorrelationId_generatesCanonicalIdForHeaderAndEnvelope()
            throws Exception {

        MvcResult result = mockMvc.perform(get("/api/v1/inpatient/admissions")
                        .header(JwtClaims.HEADER_CORRELATION_ID, "not-a-uuid"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String correlationId = result.getResponse().getHeader(JwtClaims.HEADER_CORRELATION_ID);
        assertThat(correlationId).isNotEqualTo("not-a-uuid");
        assertThatCode(() -> UUID.fromString(correlationId)).doesNotThrowAnyException();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("correlationId").asText()).isEqualTo(correlationId);
    }

    @Test
    void validJwtReadsAdmissionAndReturnsEnvelope() throws Exception {
        UUID admissionId = UUID.randomUUID();
        Admission admission = Admission.create(admissionId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Pneumonia", Instant.parse("2026-09-27T04:00:00Z"),
                UUID.randomUUID(), AdmissionPriority.ROUTINE, false).markAwaitingBed();
        when(admissions.findById(admissionId)).thenReturn(Optional.of(admission));
        when(assignments.findActiveByAdmissionId(admissionId)).thenReturn(Optional.empty());
        when(references.findByAdmissionId(admissionId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/inpatient/admissions/{id}", admissionId)
                        .header("Authorization", "Bearer " + validToken("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maDotNoiTru").value(admissionId.toString()));
    }

    @Test
    void authenticatedRoleOutsideEndpointMatrix_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inpatient/admissions/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + validToken("PATIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void createAdmission_invalidBodyReturnsValidationEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/inpatient/admissions")
                        .header("Authorization", "Bearer " + validToken("DOCTOR"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(8));
    }

    @Test
    void createAdmission_cannotImpersonateAnotherStaffMember() throws Exception {
        UUID requestedActor = UUID.randomUUID();
        UUID authenticatedActor = UUID.randomUUID();
        CreateAdmissionRequest request = new CreateAdmissionRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), requestedActor, "Pneumonia", AdmissionPriority.ROUTINE,
                false, Instant.parse("2026-09-27T04:00:00Z"));

        mockMvc.perform(post("/api/v1/inpatient/admissions")
                        .header("Authorization", "Bearer " + validToken("DOCTOR", authenticatedActor))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(admissions, never()).findByAdmissionRequestId(any());
    }

    private String validToken(String role) {
        return validToken(role, null);
    }

    @Test
    void serviceToken_cannotReadOrMutateHumanAdmissionRoutes() throws Exception {
        Instant now=Instant.now();
        String token=Jwts.builder().subject("surgery-service").claim(JwtClaims.ROLE,"SYSTEM")
                .claim(JwtClaims.TYPE,JwtClaims.SERVICE_TOKEN_TYPE).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60))).signWith(SIGNING_KEY).compact();
        mockMvc.perform(get("/api/v1/inpatient/admissions/{id}",UUID.randomUUID())
                .header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        CreateAdmissionRequest request=new CreateAdmissionRequest(UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Test",AdmissionPriority.ROUTINE,false,now);
        mockMvc.perform(post("/api/v1/inpatient/admissions").contentType("application/json")
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        verify(admissions,never()).findById(any());
        verify(admissions,never()).findByAdmissionRequestId(any());
    }

    private String validToken(String role, UUID staffId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject("inpatient-smoke-test")
                .claim(JwtClaims.ROLE, role)
                .claim(JwtClaims.TYPE, JwtClaims.ACCESS_TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)));
        if (staffId != null) {
            builder.claim(JwtClaims.STAFF_ID, staffId.toString());
        }
        return builder.signWith(SIGNING_KEY).compact();
    }
}
