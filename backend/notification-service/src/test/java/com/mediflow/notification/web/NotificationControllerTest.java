package com.mediflow.notification.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.notification.application.dto.command.CallerIdentity;
import com.mediflow.notification.application.dto.request.SendNotificationRequest;
import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.domain.exception.NotificationAccessDeniedException;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-slice test cho {@link NotificationController} — HTTP shape, phân quyền và BR-N6. */
@WebMvcTest(NotificationController.class)
@Import(NotificationControllerTest.MethodSecurityConfiguration.class)
class NotificationControllerTest {

    private static final String BASE_PATH = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SendNotificationUseCase sendNotificationUseCase;

    @MockBean
    private ReadNotificationUseCase readNotificationUseCase;

    @Test
    void getById_patientOwnNotification_returns200WithCallerPatientId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID patientId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(readNotificationUseCase.getById(id, patientId, false)).thenReturn(dto(id, patientId));

        mockMvc.perform(get(BASE_PATH + "/{id}", id)
                        .with(authentication(patientAuthentication(accountId, patientId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(id.toString()));

        verify(readNotificationUseCase).getById(id, patientId, false);
    }

    @Test
    void getById_otherPatientNotification_returns403Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID patientId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(readNotificationUseCase.getById(id, patientId, false))
                .thenThrow(new NotificationAccessDeniedException("Không có quyền xem thông báo này"));

        mockMvc.perform(get(BASE_PATH + "/{id}", id)
                        .with(authentication(patientAuthentication(accountId, patientId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"));
    }

    /** Token PATIENT thiếu claim patientId (HANDOFF-NOTIFICATION-PATIENT-JWT-CLAIM.md): không được
     * rơi về accountId — callerPatientId phải là null nên tầng application luôn từ chối BR-N6. */
    @Test
    void getById_patientTokenWithoutPatientIdClaim_neverFallsBackToAccountId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(readNotificationUseCase.getById(id, null, false))
                .thenThrow(new NotificationAccessDeniedException("Không có quyền xem thông báo này"));

        mockMvc.perform(get(BASE_PATH + "/{id}", id)
                        .with(authentication(patientAuthentication(accountId, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"));

        verify(readNotificationUseCase).getById(id, null, false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void byPatient_staffCaller_isStaffTrueAndCallerPatientIdNull() throws Exception {
        UUID patientId = UUID.randomUUID();
        PageQuery defaultPage = PageQuery.of(null, null);
        when(readNotificationUseCase.byPatient(eq(patientId), eq(defaultPage), eq(null), eq(true)))
                .thenReturn(PageResult.of(List.of(dto(UUID.randomUUID(), patientId)), 1, 0, 20));

        mockMvc.perform(get(BASE_PATH + "/patient/{patientId}", patientId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_returns201AndLocation() throws Exception {
        UUID notificationId = UUID.randomUUID();
        SendNotificationRequest request = new SendNotificationRequest(
                UUID.randomUUID(), "Nhắc lịch", "Nội dung", NotificationChannel.IN_APP, null);
        when(sendNotificationUseCase.send(any(SendNotificationRequest.class)))
                .thenReturn(dto(notificationId, request.patientId()));

        mockMvc.perform(post(BASE_PATH + "/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/notifications/" + notificationId))
                .andExpect(jsonPath("$.data.notificationId").value(notificationId.toString()));
    }

    @Test
    @WithMockUser(roles = "NURSE")
    void send_forbiddenRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendNotificationRequest(
                                UUID.randomUUID(), "T", "C", NotificationChannel.IN_APP, null))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(sendNotificationUseCase);
    }

    private UsernamePasswordAuthenticationToken patientAuthentication(UUID accountId, UUID patientId) {
        CallerIdentity identity = new CallerIdentity(accountId, patientId, "PATIENT");
        return new UsernamePasswordAuthenticationToken(
                identity, null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT")));
    }

    private NotificationDTO dto(UUID id, UUID patientId) {
        return new NotificationDTO(id, patientId, "Tiêu đề", "Nội dung", NotificationChannel.IN_APP,
                NotificationStatus.SENT, null, Instant.now(), Instant.now());
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
