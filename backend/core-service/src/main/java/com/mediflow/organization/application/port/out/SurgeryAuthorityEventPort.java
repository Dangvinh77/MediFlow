package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.event.SurgeryAuthorityChangedEvent;

/** Append atomically with the authority mutation; transport is outside this transaction. */
public interface SurgeryAuthorityEventPort {
    void append(SurgeryAuthorityChangedEvent event);
}
