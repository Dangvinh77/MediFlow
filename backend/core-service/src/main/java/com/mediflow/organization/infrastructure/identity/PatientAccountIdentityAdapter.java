package com.mediflow.organization.infrastructure.identity;

import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Core co-host composition through the Patient public use case; stores remain separate. */
@Component
public class PatientAccountIdentityAdapter implements PatientAccountIdentityPort {
    private final GetPatientUseCase patients;
    private final UpdatePatientUseCase patientUpdates;
    public PatientAccountIdentityAdapter(GetPatientUseCase patients, UpdatePatientUseCase patientUpdates) {
        this.patients = patients;
        this.patientUpdates = patientUpdates;
    }

    @Override public String identityNumber(UUID patientId) {
        var patient = patients.getById(patientId);
        if (patient.daLuuTru() || patient.soCmnd() == null
                || !patient.soCmnd().trim().matches("[a-zA-Z0-9._-]{3,50}")) {
            throw new InvalidAccountException("Hồ sơ đã lưu trữ hoặc chưa có định danh hợp lệ để cấp tài khoản.");
        }
        return patient.soCmnd().trim();
    }

    @Override public String email(UUID patientId) {
        var patient = patients.getById(patientId);
        String email = patient.email();
        if (patient.daLuuTru() || email == null || email.length() > 254
                || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidAccountException("Hồ sơ bệnh nhân chưa có email hợp lệ để cấp tài khoản.");
        }
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override public String profileEmail(UUID patientId) {
        return patients.getById(patientId).email();
    }

    @Override public String displayName(UUID patientId) {
        var patient = patients.getById(patientId);
        return patient.daLuuTru() ? "" : patient.hoTen();
    }

    @Override public void requireActivePatient(UUID patientId) {
        if (patients.getById(patientId).daLuuTru()) {
            throw new InvalidAccountException("An archived patient profile cannot change account email");
        }
    }

    @Override public void updateEmail(UUID patientId, String email) {
        patientUpdates.synchronizeAccountEmail(patientId, email);
    }
}
