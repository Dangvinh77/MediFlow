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
import com.mediflow.clinical.application.service.RecordHandoverApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;

class RecordHandoverControllerTest {
    static final UUID RECORD = UUID.randomUUID();
    static final String BASE = "/api/v2/records/" + RECORD + "/handovers";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Nested
    @WebMvcTest(value = RecordHandoverController.class, properties = "mediflow.v2.record-amendment=true")
    @Import({MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
    class Enabled {
        @Autowired MockMvc mvc;
        @MockBean RecordHandoverApplicationService service;

        @Test void anonymousIsUnauthorized() throws Exception {
            mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
        }

        @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
        void onlyDoctorsHandOver(String role) throws Exception {
            mvc.perform(post(BASE).with(user("u").roles(role)).with(csrf()).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
            mvc.perform(put(BASE + "/" + UUID.randomUUID() + "/acceptance").with(user("u").roles(role)).with(csrf())
                    .header("Idempotency-Key", "k")).andExpect(status().isForbidden());
            verifyNoInteractions(service);
        }

        @Test void doctorRequestsHandover() throws Exception {
            when(service.request(eq(RECORD), any(), eq("k"))).thenReturn(new IdempotentCommandPort.Result<>(null, 201, null, false));
            mvc.perform(post(BASE).with(user("d").roles("DOCTOR")).with(csrf()).header("Idempotency-Key", "k")
                    .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isCreated());
        }

        @Test void nurseReadsHandovers() throws Exception {
            when(service.list(RECORD)).thenReturn(List.of());
            mvc.perform(get(BASE).with(user("n").roles("NURSE"))).andExpect(status().isOk());
        }
    }

    @Nested
    @WebMvcTest(value = RecordHandoverController.class, properties = "mediflow.v2.record-amendment=false")
    @Import({MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
    class Disabled {
        @Autowired MockMvc mvc;

        @Test void featureOffIsNotFound() throws Exception {
            mvc.perform(get(BASE).with(user("d").roles("DOCTOR"))).andExpect(status().isNotFound());
        }
    }

    private static String body() {
        return "{\"toDoctorId\":\"" + UUID.randomUUID() + "\",\"reason\":\"Hết ca\",\"pendingItems\":\"Chờ kết quả\"}";
    }
}
