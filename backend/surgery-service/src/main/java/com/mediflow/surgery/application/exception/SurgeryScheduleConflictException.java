package com.mediflow.surgery.application.exception;

/** Another finalized or still-active case owns the requested room/staff resource. */
public final class SurgeryScheduleConflictException extends RuntimeException {

    public SurgeryScheduleConflictException() {
        super("Phòng mổ hoặc nhân viên đã được giữ cho ca phẫu thuật khác");
    }
}
