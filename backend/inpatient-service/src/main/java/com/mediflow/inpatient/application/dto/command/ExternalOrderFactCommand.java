package com.mediflow.inpatient.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public sealed interface ExternalOrderFactCommand permits LabResultFactCommand,
        PrescriptionFilledFactCommand, SurgeryReadyFactCommand,
        SurgeryCompletedFactCommand, SurgeryCancelledFactCommand {

    UUID maSuKien();
    int phienBan();
    String maTuongQuan();
    UUID maYLenhBenNgoai();
    UUID maDotNoiTru();
    Instant xayRaLuc();
}
