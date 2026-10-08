package com.mediflow.notification.application.port.in;

import com.mediflow.notification.application.dto.command.AdmissionClosedCommand;
import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.dto.command.SurgeryReadyCommand;

/**
 * In-port cho các projection ngoài payment receipt (HANDOFF-NOTIFICATION-CARE-PROJECTIONS,
 * CONTRACT-CARE-PROJECTIONS-01 "Notification subscriptions"). Notification chỉ tạo thông báo —
 * không bao giờ cấp quyền cho một chuyển trạng thái điều trị/tài chính.
 */
public interface ReactToCareProjectionUseCase {
    void onAdmissionDepositRequested(AdmissionDepositRequestedCommand command);
    void onAdmissionStarted(AdmissionStartedCommand command);
    void onAdmissionClosed(AdmissionClosedCommand command);
    void onSurgeryReady(SurgeryReadyCommand command);
    void onSurgeryCancelled(SurgeryCancelledNoticeCommand command);
}
