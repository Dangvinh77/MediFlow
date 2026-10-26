package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.event.ScheduleManagementScopeChangedEvent;

public interface ScheduleManagementScopeEventPublisher {
    void publish(ScheduleManagementScopeChangedEvent event);
}
