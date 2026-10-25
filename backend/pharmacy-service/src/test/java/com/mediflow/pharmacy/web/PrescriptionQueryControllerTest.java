package com.mediflow.pharmacy.web;

import com.mediflow.common.api.*;
import com.mediflow.pharmacy.application.port.in.*;
import com.mediflow.pharmacy.infrastructure.config.SecurityConfig;
import com.mediflow.pharmacy.infrastructure.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PrescriptionQueryController.class)
@Import({SecurityConfig.class,JwtAuthFilter.class,GlobalExceptionHandler.class})
@TestPropertySource(properties="mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class PrescriptionQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SearchPrescriptionsUseCase search;
    @MockBean GetPrescribingContextUseCase context;
    @ParameterizedTest @ValueSource(strings={"ADMIN","DOCTOR","PHARMACIST"})
    void listsWithoutKnowingAnyId(String role) throws Exception {
        when(search.search(null,null,null,PageQuery.of(null,null))).thenReturn(PageResult.empty(PageQuery.of(null,null)));
        mvc.perform(get("/api/v1/pharmacy/prescriptions").with(user("reader").roles(role)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }
    @ParameterizedTest @ValueSource(strings={"PATIENT","NURSE","CASHIER"})
    void deniesOtherRoles(String role) throws Exception {
        mvc.perform(get("/api/v1/pharmacy/prescriptions").with(user("reader").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(search);
    }
    @Test void pharmacistCannotGetPrescribingContext() throws Exception {
        mvc.perform(get("/api/v1/pharmacy/prescriptions/prescribing/context").param("recordId",java.util.UUID.randomUUID().toString())
            .with(user("reader").roles("PHARMACIST"))).andExpect(status().isForbidden());
        verifyNoInteractions(context);
    }
}
