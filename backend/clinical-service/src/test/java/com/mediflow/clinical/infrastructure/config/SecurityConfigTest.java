package com.mediflow.clinical.infrastructure.config;

import com.mediflow.clinical.application.port.in.ManageAppointmentUseCase;
import com.mediflow.clinical.web.AppointmentController;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageAppointmentUseCase manageAppointmentUseCase;

    @Test
    void search_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void search_withForbiddenRole_returns403Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void search_withAllowedRole_returns200() throws Exception {
        PageQuery defaultPage = PageQuery.of(null, null);
        when(manageAppointmentUseCase.search(
                isNull(), isNull(), eq(defaultPage)))
                .thenReturn(PageResult.empty(defaultPage));

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
