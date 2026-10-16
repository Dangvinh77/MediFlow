package com.mediflow.pharmacy.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.enums.*;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrescriptionController.class)
@ActiveProfiles("mvp-outpatient")
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class OutpatientPrescriptionLifecycleControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private CreatePrescriptionUseCase legacyCreator;
    @MockBean private GetPrescriptionUseCase reader;
    @MockBean private CancelPrescriptionUseCase legacyCancel;
    @MockBean private DispensePrescriptionUseCase legacyDispense;
    @MockBean private OutpatientPrescriptionLifecycleUseCase lifecycle;
    private final UUID id = UUID.randomUUID();
    private final ActorIdentity pharmacist = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "PHARMACIST");

    @Test
    void dispenseRoutesToV1PortWithSignedActorAndCorrelation() throws Exception {
        var dto = new DispenseDTO(UUID.randomUUID(), id, DispenseStatus.DISPENSED, Instant.now(),
                pharmacist.staffId(), DispenseActorType.STAFF, null);
        when(lifecycle.dispense(id, pharmacist, "trace")).thenReturn(dto);
        mvc.perform(put(path("dispense")).with(authentication(auth(pharmacist)))
                        .header("X-Correlation-Id", "trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPENSED"))
                .andExpect(jsonPath("$.data.dispensedBy").value(pharmacist.staffId().toString()));
        verify(lifecycle).dispense(id, pharmacist, "trace");
        verifyNoInteractions(legacyDispense, legacyCancel);
    }

    @Test
    void clearanceDenialCannotCallLegacyDispense() throws Exception {
        when(lifecycle.dispense(id, pharmacist, "trace"))
                .thenThrow(new DispenseAuthorizationException("PHARMACY_CLEARANCE_REQUIRED", "Missing clearance"));
        mvc.perform(put(path("dispense")).with(authentication(auth(pharmacist)))
                        .header("X-Correlation-Id", "trace"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CLEARANCE_REQUIRED"));
        verifyNoInteractions(legacyDispense);
    }

    @Test
    void persistedFailureOutcomeIsReturnedWithoutClaimingRefundCompleted() throws Exception {
        var dto = new DispenseDTO(UUID.randomUUID(), id, DispenseStatus.FAILED, null, null, null, "DRUG_EXPIRED");
        when(lifecycle.dispense(id, pharmacist, "trace")).thenReturn(dto);
        mvc.perform(put(path("dispense")).with(authentication(auth(pharmacist)))
                        .header("X-Correlation-Id", "trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("DRUG_EXPIRED"))
                .andExpect(jsonPath("$.data.refunded").doesNotExist());
        verifyNoInteractions(legacyDispense);
    }

    @Test
    void cancelRoutesToV1PortInsteadOfLegacyWriter() throws Exception {
        ActorIdentity doctor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR");
        var command = new CancelPrescriptionCommand(id, doctor, "Changed treatment", "trace");
        when(lifecycle.cancel(command)).thenReturn(new CancelPrescriptionResult(id,
                PrescriptionStatus.CANCELLED, 2, Instant.now()));
        mvc.perform(put(path("cancel")).with(authentication(auth(doctor)))
                        .header("X-Correlation-Id", "trace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed treatment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(lifecycle).cancel(command);
        verifyNoInteractions(legacyCancel, legacyDispense);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYSTEM", "DOCTOR", "PATIENT"})
    void dispenseRejectsWrongRoleBeforeAnyPort(String role) throws Exception {
        var actor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), role);
        mvc.perform(put(path("dispense")).with(authentication(auth(actor))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lifecycle, legacyDispense);
    }

    private String path(String action) {
        return "/api/v1/pharmacy/prescriptions/" + id + "/" + action;
    }

    private UsernamePasswordAuthenticationToken auth(ActorIdentity actor) {
        return new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + actor.role())));
    }
}
