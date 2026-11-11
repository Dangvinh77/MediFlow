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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
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

import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.TreatmentCourseApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;

class TreatmentCourseControllerTest {
    static final String OPEN = "/api/v2/treatment-courses";
    static final UUID COURSE = UUID.randomUUID();

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Nested
    @WebMvcTest(value = TreatmentCourseController.class, properties = "mediflow.v2.treatment-course=true")
    @Import({MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
    class Enabled {
        @Autowired MockMvc mvc;
        @MockBean TreatmentCourseApplicationService service;

        @Test void anonymousIsUnauthorized() throws Exception {
            mvc.perform(get("/api/v2/patients/" + UUID.randomUUID() + "/treatment-courses")).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
        }

        @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
        void onlyDoctorsWrite(String role) throws Exception {
            mvc.perform(post(OPEN).with(user("u").roles(role)).with(csrf()).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content(openBody())).andExpect(status().isForbidden());
            mvc.perform(put(OPEN + "/" + COURSE + "/closure").with(user("u").roles(role)).with(csrf())
                    .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"decision\":\"RESOLVED\",\"note\":\"Ổn\"}")).andExpect(status().isForbidden());
            verifyNoInteractions(service);
        }

        @ParameterizedTest @ValueSource(strings = {"CASHIER", "PATIENT", "PHARMACIST"})
        void careStaffRead(String role) throws Exception {
            mvc.perform(get("/api/v2/patients/" + UUID.randomUUID() + "/treatment-courses").with(user("u").roles(role)))
                    .andExpect(status().isForbidden());
        }

        @Test void doctorOpensWithLocation() throws Exception {
            when(service.open(any(), eq("k"))).thenReturn(new IdempotentCommandPort.Result<>(null, 201,
                    "/api/v2/treatment-courses/" + COURSE, false));
            mvc.perform(post(OPEN).with(user("d").roles("DOCTOR")).with(csrf()).header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(openBody()))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v2/treatment-courses/" + COURSE))
                    .andExpect(header().string("Idempotency-Replayed", "false"));
        }

        @Test void missingKeyIsBadRequest() throws Exception {
            mvc.perform(post(OPEN).with(user("d").roles("DOCTOR")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(openBody())).andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }

        @Test void nurseReadsCourses() throws Exception {
            when(service.forPatient(any())).thenReturn(List.of());
            mvc.perform(get("/api/v2/patients/" + UUID.randomUUID() + "/treatment-courses").with(user("n").roles("NURSE")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @WebMvcTest(value = TreatmentCourseController.class, properties = "mediflow.v2.treatment-course=false")
    @Import({MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
    class Disabled {
        @Autowired MockMvc mvc;

        @Test void featureOffIsNotFound() throws Exception {
            mvc.perform(get("/api/v2/patients/" + UUID.randomUUID() + "/treatment-courses").with(user("d").roles("DOCTOR")))
                    .andExpect(status().isNotFound());
        }
    }

    private static String openBody() {
        return "{\"patientId\":\"" + UUID.randomUUID() + "\",\"problem\":\"Tăng huyết áp\",\"openingReason\":\"Theo dõi\","
                + "\"recordId\":\"" + UUID.randomUUID() + "\"}";
    }
}
