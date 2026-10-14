package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.event.DepartmentCreatedEvent;
import com.mediflow.organization.application.event.StaffCreatedEvent;
import com.mediflow.organization.application.event.StaffDepartmentChangedEvent;

public interface EventPublisher {

    void publishDepartmentCreated(DepartmentCreatedEvent event);

    void publishStaffCreated(StaffCreatedEvent event);

    void publishStaffDepartmentChanged(StaffDepartmentChangedEvent event);
}
