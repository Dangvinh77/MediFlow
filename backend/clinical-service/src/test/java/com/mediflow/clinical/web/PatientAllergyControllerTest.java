package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.clinical.application.dto.response.PatientAllergiesDTO;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.PatientAllergyApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;

@WebMvcTest(PatientAllergyController.class)
@Import({PatientAllergyControllerTest.MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class PatientAllergyControllerTest {
    static final UUID PATIENT = UUID.randomUUID();
    static final String BASE = "/api/v2/patients/" + PATIENT + "/allergies";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean PatientAllergyApplicationService service;

    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "CASHIER", "PATIENT", "PHARMACIST"})
    void onlyCareStaffAdd(String role) throws Exception {
        mvc.perform(post(BASE).with(user("u").roles(role)).with(csrf()).header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content("{\"substance\":\"Penicillin\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
    void onlyDoctorsChangeStatus(String role) throws Exception {
        mvc.perform(put(BASE + "/" + UUID.randomUUID() + "/status").with(user("u").roles(role)).with(csrf())
                .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"reason\":\"x\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "PATIENT", "PHARMACIST"})
    void careStaffRead(String role) throws Exception {
        mvc.perform(get(BASE).with(user("u").roles(role))).andExpect(status().isForbidden());
    }

    @Test void nurseAddsAndReads() throws Exception {
        when(service.add(eq(PATIENT), any(), eq("k"))).thenReturn(new IdempotentCommandPort.Result<>(null, 201, null, false));
        mvc.perform(post(BASE).with(user("n").roles("NURSE")).with(csrf()).header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON).content("{\"substance\":\"Penicillin\"}"))
                .andExpect(status().isCreated());
        when(service.list(PATIENT)).thenReturn(new PatientAllergiesDTO(List.of(), List.of()));
        mvc.perform(get(BASE).with(user("n").roles("NURSE"))).andExpect(status().isOk());
    }
}
