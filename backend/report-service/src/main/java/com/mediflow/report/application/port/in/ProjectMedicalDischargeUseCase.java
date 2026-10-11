package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import java.util.UUID;

public interface ProjectMedicalDischargeUseCase {
    void receive(DecodedCareFinanceEvent event);
    void recover(UUID admissionId);
}
