package com.mediflow.notification.application.port.out;

import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;

public interface RegistrationNoticeProjectionPort {
    /** Atomically serializes revisions, rejects conflicts, and suppresses stale/terminal updates. */
    boolean advance(RegistrationNoticeCommand command);
}
