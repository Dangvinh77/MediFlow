package com.mediflow.notification.application.port.out;

import com.mediflow.notification.domain.model.StaffCriticalNotice;

public interface StaffCriticalNoticePort {
    /** False when this notice was already recorded. */
    boolean record(StaffCriticalNotice notice);
}
