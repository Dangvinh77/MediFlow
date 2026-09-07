package com.mediflow.notification.application.port.in;

import java.util.UUID;

import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * In-port — đọc thông báo (backend-spec/07-notification.md §5, §9).
 * {@code NotificationApplicationService} (Phần 3/5) hiện thực.
 */
public interface ReadNotificationUseCase {

    /**
     * Xem một thông báo theo id.
     *
     * <p><b>Kiểm tra quyền sở hữu (BR-N6) — bắt buộc, không phải tùy chọn:</b> nếu
     * {@code !isStaff} và {@code notification.patientId != callerPatientId} thì ném
     * {@code NotificationAccessDeniedException} (403). Id bệnh nhân người gọi lấy từ JWT, không
     * bao giờ từ tham số request (§9).
     *
     * @param id              thông báo cần đọc
     * @param callerPatientId id bệnh nhân của người gọi (từ claim JWT); có thể null nếu là nhân viên
     * @param isStaff         true nếu người gọi là ADMIN/NURSE... (được xem của mọi bệnh nhân)
     */
    NotificationDTO getById(UUID id, UUID callerPatientId, boolean isStaff);

    /** Danh sách thông báo của một bệnh nhân, mới nhất trước, phân trang. */
    PageResult<NotificationDTO> byPatient(UUID patientId, PageQuery page);
}
