package com.mediflow.surgery.application.exception;

/** An accepted command was based on a superseded case revision. */
public final class SurgeryRevisionConflictException extends RuntimeException {

    public SurgeryRevisionConflictException() {
        super("Phiên bản ca phẫu thuật đã thay đổi");
    }
}
