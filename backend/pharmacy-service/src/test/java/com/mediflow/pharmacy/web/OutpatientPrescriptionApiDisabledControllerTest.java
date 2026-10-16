package com.mediflow.pharmacy.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrescriptionController.class)
@ActiveProfiles("mvp-outpatient")
@Import({SecurityConfig.class, JwtAuthFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class OutpatientPrescriptionApiDisabledControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockBean private CreatePrescriptionUseCase legacy;
    @MockBean private GetPrescriptionUseCase reader;
    @MockBean private CancelPrescriptionUseCase canceller;
    @MockBean private DispensePrescriptionUseCase dispenser;

    @Test
    void defaultMvpProfileKeepsV1UnavailableWithoutCallingLegacyWriter() throws Exception {
        var request = new CreatePrescriptionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.now(), List.of(new PrescriptionLineRequest(UUID.randomUUID(), 1, "Daily")),
                1, CareContext.OUTPATIENT, CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, "RX");
        mvc.perform(post("/api/v1/pharmacy/prescriptions")
                        .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CARE_FINANCE_V2_UNAVAILABLE"));
        verifyNoInteractions(legacy, reader);
    }

    @Test
    void disabledMvpLifecycleDoesNotFallBackToLegacyDispenseOrCancel() throws Exception {
        String base = "/api/v1/pharmacy/prescriptions/" + UUID.randomUUID();
        mvc.perform(put(base + "/dispense").with(user(UUID.randomUUID().toString()).roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CARE_FINANCE_V2_UNAVAILABLE"));
        mvc.perform(put(base + "/cancel").with(user(UUID.randomUUID().toString()).roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reason\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("PHARMACY_CARE_FINANCE_V2_UNAVAILABLE"));
        verifyNoInteractions(dispenser, canceller);
    }
}
