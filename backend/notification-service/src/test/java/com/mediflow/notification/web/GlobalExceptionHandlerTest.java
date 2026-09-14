package com.mediflow.notification.web;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.mediflow.notification.application.dto.request.SendNotificationRequest;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.domain.exception.NotificationAccessDeniedException;
import com.mediflow.notification.domain.exception.NotificationAddressInvalidException;
import com.mediflow.notification.domain.exception.NotificationNotFoundException;
import com.mediflow.notification.infrastructure.config.SecurityConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xác nhận {@link GlobalExceptionHandler} map đúng mã lỗi notification sang HTTP status. */
@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes")
class GlobalExceptionHandlerTest {

    private static final String BASE_PATH = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SendNotificationUseCase sendNotificationUseCase;

    @MockBean
    private ReadNotificationUseCase readNotificationUseCase;

    @Test
    @WithMockUser(roles = "ADMIN")
    void missingNotification_returns404Envelope() throws Exception {
        UUID id = UUID.randomUUID();
        when(readNotificationUseCase.getById(eq(id), any(), eq(true)))
                .thenThrow(new NotificationNotFoundException("Không tìm thấy thông báo id=" + id));

        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void accessDenied_returns403WithNotificationCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(readNotificationUseCase.getById(eq(id), any(), eq(false)))
                .thenThrow(new NotificationAccessDeniedException("Không có quyền xem thông báo này"));

        mockMvc.perform(get(BASE_PATH + "/{id}", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidAddress_returns422Envelope() throws Exception {
        when(sendNotificationUseCase.send(any(SendNotificationRequest.class)))
                .thenThrow(new NotificationAddressInvalidException("Địa chỉ email không hợp lệ: sai-dia-chi"));

        mockMvc.perform(post(BASE_PATH + "/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"11111111-1111-1111-1111-111111111111","title":"T",
                                 "content":"C","channel":"EMAIL","recipientAddress":"sai-dia-chi"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_ADDRESS_INVALID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_missingRequiredFields_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_invalidChannelEnum_returns400Envelope() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"11111111-1111-1111-1111-111111111111","title":"T",
                                 "content":"C","channel":"FAX"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
