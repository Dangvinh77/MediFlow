package com.mediflow.pharmacy.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.model.enums.*;
import com.mediflow.pharmacy.infrastructure.config.PharmacyOutpatientApiConfiguration;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrescriptionController.class)
@ActiveProfiles("mvp-outpatient")
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class,
        PharmacyOutpatientApiConfiguration.class})
@TestPropertySource(properties = {
    "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
    "mediflow.features.care-finance-v2=true",
    "mediflow.pharmacy.identity.enabled=true",
    "mediflow.pharmacy.clinical-context.enabled=true",
    "mediflow.pharmacy.outpatient-api.enabled=true",
    "mediflow.pharmacy.clearance-consumer.enabled=true",
    "mediflow.pharmacy.clearance-authority.enabled=true"
})
class OutpatientPrescriptionControllerTest {
    private static final String PATH = "/api/v1/pharmacy/prescriptions";
    private final UUID doctorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private CreatePrescriptionUseCase legacy;
    @MockBean private CreateContextCheckedPrescriptionUseCase checkedWriter;
    @MockBean private GetPrescriptionUseCase reader;
    @MockBean private CancelPrescriptionUseCase canceller;
    @MockBean private DispensePrescriptionUseCase dispenser;
    @MockBean private OutpatientPrescriptionLifecycleUseCase lifecycle;

    @Test
    void createUsesCheckedWriterSignedActorAndStableCommandIdentity() throws Exception {
        UUID key = UUID.randomUUID();
        UUID prescription = UUID.randomUUID();
        when(checkedWriter.createWithContext(eq(key), any())).thenReturn(prescription);
        when(reader.getPrescriptionById(prescription)).thenReturn(dto(prescription));
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .header("Idempotency-Key", key).header("X-Correlation-Id", "mvp-create")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PATH + "/" + prescription))
                .andExpect(jsonPath("$.data.prescriptionId").value(prescription.toString()))
                .andExpect(jsonPath("$.correlationId").value("mvp-create"));
        var captured = ArgumentCaptor.forClass(CreatePrescriptionCommand.class);
        verify(checkedWriter).createWithContext(eq(key), captured.capture());
        assertThat(captured.getValue().actor()).isEqualTo(new ActorIdentity(accountId, doctorId, "DOCTOR"));
        assertThat(captured.getValue().request().careEpisodeId()).isNotEqualTo(captured.getValue().request().recordId());
        verifyNoInteractions(legacy);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1"})
    void invalidCommandKeyDoesNotEnterWriter(String key) throws Exception {
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_IDEMPOTENCY_KEY_REQUIRED"));
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void missingKeyDoesNotGenerateANewCommandId() throws Exception {
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_IDEMPOTENCY_KEY_REQUIRED"));
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void legacyIntentIsNotAnMvpFallback() throws Exception {
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_OUTPATIENT_V1_REQUIRED"));
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHARMACIST", "PATIENT", "SYSTEM"})
    void wrongRoleIsDeniedBeforeAnyCreation(String role) throws Exception {
        mvc.perform(post(PATH).with(authentication(actor(role, doctorId)))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void doctorCannotSubstituteAnotherPrescriber() throws Exception {
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", UUID.randomUUID())))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void providerUnavailableDoesNotFallBackToLegacy() throws Exception {
        when(checkedWriter.createWithContext(any(), any())).thenThrow(new PharmacyUpstreamUnavailableException());
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_UPSTREAM_UNAVAILABLE"));
        verifyNoInteractions(legacy, reader);
    }

    @Test
    void admissionIntentIsRejectedWithoutCallingUpstream() throws Exception {
        var base = request(1);
        UUID admission = UUID.randomUUID();
        var inpatient = new CreatePrescriptionRequest(null, base.patientId(), doctorId, base.departmentId(),
                base.prescribedDate(), base.lines(), 1, CareContext.ADMISSION, CareEpisodeType.ADMISSION,
                admission, admission, "RX");
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(inpatient)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CARE_CREATION_INVALID"));
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    private UsernamePasswordAuthenticationToken actor(String role, UUID staffId) {
        return new UsernamePasswordAuthenticationToken(new ActorIdentity(accountId, staffId, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void noRecordReferenceIsRejectedBeforeCallingUpstream() throws Exception {
        var base = request(1);
        var request = new CreatePrescriptionRequest(null, base.patientId(), doctorId, base.departmentId(),
                base.prescribedDate(), base.lines(), 1, CareContext.OUTPATIENT,
                CareEpisodeType.OUTPATIENT_VISIT, base.careEpisodeId(), null, "RX");
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", doctorId)))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CARE_CONTEXT_INVALID"));
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void doctorWithoutSignedStaffIdentityIsDeniedBeforeCallingUpstream() throws Exception {
        mvc.perform(post(PATH).with(authentication(actor("DOCTOR", null)))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    @Test
    void missingAuthenticationIsDeniedBeforeAnyCreation() throws Exception {
        mvc.perform(post(PATH).header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(1))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(checkedWriter, legacy, reader);
    }

    private CreatePrescriptionRequest request(int version) {
        var lines = List.of(new PrescriptionLineRequest(UUID.randomUUID(), 2, "Twice daily"));
        if (version == 0) return new CreatePrescriptionRequest(UUID.randomUUID(), UUID.randomUUID(), doctorId,
                UUID.randomUUID(), LocalDate.now(), lines);
        return new CreatePrescriptionRequest(UUID.randomUUID(), UUID.randomUUID(), doctorId,
                UUID.randomUUID(), LocalDate.now(), lines, 1, CareContext.OUTPATIENT,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, "RX");
    }

    private PrescriptionDTO dto(UUID id) {
        Instant now = Instant.now();
        return new PrescriptionDTO(id, UUID.randomUUID(), UUID.randomUUID(), doctorId, UUID.randomUUID(),
                LocalDate.now(), new BigDecimal("1000.00"), List.of(), PrescriptionStatus.ACTIVE,
                DispenseStatus.PENDING, false, null, null, null, now, now);
    }
}
