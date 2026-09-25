package com.mediflow.notification.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.Roles;
import com.mediflow.notification.application.dto.command.CallerIdentity;
import com.mediflow.notification.application.dto.request.SendNotificationRequest;
import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;

/**
 * REST controller cho thông báo (backend-spec/07-notification.md §9). Chỉ đảm nhiệm HTTP,
 * validation, phân quyền theo role, và trích {@code callerPatientId} từ JWT cho kiểm tra quyền
 * sở hữu BR-N6 — bản thân việc so sánh nằm trong {@link ReadNotificationUseCase}
 * ({@code NotificationApplicationService}), không phải ở đây.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final ReadNotificationUseCase readNotificationUseCase;

    public NotificationController(SendNotificationUseCase sendNotificationUseCase,
                                  ReadNotificationUseCase readNotificationUseCase) {
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.readNotificationUseCase = readNotificationUseCase;
    }

    /** Danh sách thông báo của một bệnh nhân. {@code PATIENT} chỉ xem được của chính mình (BR-N6). */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE', 'PATIENT')")
    public ResponseEntity<ApiResponse<PageResult<NotificationDTO>>> byPatient(
            @PathVariable UUID patientId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        PageResult<NotificationDTO> result = readNotificationUseCase.byPatient(
                patientId, PageQuery.of(page, size), callerPatientId(authentication), isStaff(authentication));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** Xem một thông báo theo id. {@code PATIENT} chỉ xem được thông báo của chính mình (BR-N6). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PATIENT')")
    public ResponseEntity<ApiResponse<NotificationDTO>> getById(
            @PathVariable UUID id,
            Authentication authentication) {

        NotificationDTO dto = readNotificationUseCase.getById(
                id, callerPatientId(authentication), isStaff(authentication));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** Gửi thông báo thủ công — dùng bởi vận hành viên hoặc các service khác ({@code SYSTEM}). */
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<ApiResponse<NotificationDTO>> send(@Valid @RequestBody SendNotificationRequest request) {
        NotificationDTO created = sendNotificationUseCase.send(request);
        URI location = URI.create("/api/v1/notifications/" + created.notificationId());
        return ResponseEntity.created(location).body(ApiResponse.ok(created));
    }

    /**
     * {@code sub} của JWT luôn là accountId, không bao giờ là patientId
     * (docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md). Với vai trò {@code PATIENT},
     * danh tính bệnh nhân đến từ claim {@code patientId} ký riêng, do {@link
     * com.mediflow.notification.infrastructure.security.JwtAuthFilter} đặt vào principal dạng
     * {@link CallerIdentity}; claim thiếu/hỏng cho {@code null}, không bao giờ rơi về accountId.
     * Nhân viên (không phải PATIENT) không có "patientId của chính mình" nên trả về {@code null};
     * {@code isStaff() == true} khiến giá trị này không được dùng tới trong quyết định BR-N6.
     */
    private UUID callerPatientId(Authentication authentication) {
        if (isStaff(authentication) || authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof CallerIdentity identity) {
            return identity.patientId();
        }
        return null;
    }

    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_" + Roles.PATIENT)) {
                return false;
            }
        }
        return true;
    }
}
