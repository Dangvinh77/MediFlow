package com.mediflow.clinical.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.mediflow.clinical.application.service.RecordLabOrderApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecordLabOrderController.class)
@Import({RecordLabOrderControllerTest.MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class RecordLabOrderControllerTest {
    private static final String PATH = "/api/v2/records/" + UUID.randomUUID() + "/lab-orders";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean RecordLabOrderApplicationService orders;

    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "PATIENT", "PHARMACIST", "LAB_TECH"})
    void otherRolesAreForbidden(String role) throws Exception {
        mvc.perform(get(PATH).with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(orders);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE"})
    void careStaffMayRead(String role) throws Exception {
        mvc.perform(get(PATH).with(user("u").roles(role))).andExpect(status().isOk());
    }
}
