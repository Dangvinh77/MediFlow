package com.mediflow.patient.domain.exception;

public class PatientProfileAccessDeniedException extends RuntimeException {
    public PatientProfileAccessDeniedException() {
        super("Bạn chỉ được xem hồ sơ bệnh nhân của chính mình");
    }
}
