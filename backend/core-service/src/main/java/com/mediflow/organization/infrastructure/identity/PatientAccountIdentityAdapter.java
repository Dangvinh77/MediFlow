package com.mediflow.organization.infrastructure.identity;

import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Core co-host composition through the Patient public use case; stores remain separate. */
@Component
public class PatientAccountIdentityAdapter implements PatientAccountIdentityPort {
    private final GetPatientUseCase patients;
    public PatientAccountIdentityAdapter(GetPatientUseCase patients) { this.patients = patients; }

    @Override public String identityNumber(UUID patientId) {
        var patient = patients.getById(patientId);
        if (patient.daLuuTru() || patient.soCmnd() == null
                || !patient.soCmnd().trim().matches("[a-zA-Z0-9._-]{3,50}")) {
            throw new InvalidAccountException("Hồ sơ đã lưu trữ hoặc chưa có định danh hợp lệ để cấp tài khoản.");
        }
        return patient.soCmnd().trim();
    }
}
