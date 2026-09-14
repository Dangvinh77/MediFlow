package com.mediflow.notification.infrastructure.config;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;
import com.mediflow.notification.web.NotificationController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SendNotificationUseCase sendNotificationUseCase;

    @MockBean
    private ReadNotificationUseCase readNotificationUseCase;

    @Test
    void getById_withoutAuthentication_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void getById_withForbiddenRole_returns403Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_withAllowedRole_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(readNotificationUseCase.getById(eq(id), any(), eq(true))).thenReturn(new NotificationDTO(
                id, UUID.randomUUID(), "Tiêu đề", "Nội dung", NotificationChannel.IN_APP,
                NotificationStatus.SENT, null, Instant.now(), Instant.now()));

        mockMvc.perform(get("/api/v1/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
