package com.mediflow.lab.infrastructure.config;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.lab.web.LabController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabController.class)
@Import({SecurityConfig.class, ThreadLocalCorrelationIdProvider.class,
        SecurityConfigTest.ActuatorEndpointProbe.class})
@TestPropertySource(properties =
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManageLabTestUseCase manageLabTestUseCase;

    @Test
    void search_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/lab"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void search_withForbiddenRole_returns403Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/lab"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void search_withAllowedRole_returns200() throws Exception {
        PageQuery defaultPage = PageQuery.of(null, null);
        when(manageLabTestUseCase.search(isNull(), isNull(), eq(defaultPage)))
                .thenReturn(PageResult.empty(defaultPage));

        mockMvc.perform(get("/api/v1/lab"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void health_withoutAuthentication_isPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void info_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    void info_withAuthentication_isPermitted() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void sensitiveActuatorEndpoint_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @RestController
    static class ActuatorEndpointProbe {

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/actuator/info")
        Map<String, String> info() {
            return Map.of("status", "AVAILABLE");
        }

        @GetMapping("/actuator/env")
        Map<String, String> env() {
            return Map.of("status", "SENSITIVE");
        }
    }
}
