package com.mediflow.lab.web;

import com.mediflow.lab.application.service.PatientResultsService;
import com.mediflow.lab.application.port.out.CorrelationIdProvider;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientResultsController.class)
@Import(PatientResultsControllerTest.MethodSecurity.class)
class PatientResultsControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockBean PatientResultsService service;
    @MockBean com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider correlation;
    @BeforeEach void setup() { when(correlation.currentOrCreate()).thenReturn(UUID.randomUUID()); }

    @Test @WithMockUser(roles="PATIENT")
    void returnsBoundedPageWithoutCaching() throws Exception {
        when(service.mine(PageQuery.of(1, 5))).thenReturn(PageResult.empty(PageQuery.of(1, 5)));
        mvc.perform(get("/api/v1/lab/mine?page=1&size=5"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.number").value(1)).andExpect(jsonPath("$.data.size").value(5));
        verify(service).mine(PageQuery.of(1, 5));
    }
    @Test @WithMockUser(roles="PATIENT")
    void rejectsPatientScopeInjection() throws Exception {
        mvc.perform(get("/api/v1/lab/mine?patientId=" + UUID.randomUUID()))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="NURSE")
    void staffCannotUsePatientPortalEndpoint() throws Exception {
        mvc.perform(get("/api/v1/lab/mine")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
