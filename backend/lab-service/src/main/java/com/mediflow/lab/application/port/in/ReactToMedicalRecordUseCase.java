package com.mediflow.lab.application.port.in;

import com.mediflow.lab.application.dto.command.MedicalRecordCreatedCommand;

public interface ReactToMedicalRecordUseCase {

    void onMedicalRecordCreated(MedicalRecordCreatedCommand command);
}
