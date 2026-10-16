package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CreateContextCheckedPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreateOutpatientPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import java.util.UUID;

/** Uses the proof-checked writer, never its trusted low-level overloads or the legacy writer. */
public class PublicOutpatientPrescriptionCreationService implements CreateOutpatientPrescriptionUseCase {
    private final CreateContextCheckedPrescriptionUseCase writer;
    private final GetPrescriptionUseCase reader;

    public PublicOutpatientPrescriptionCreationService(
            CreateContextCheckedPrescriptionUseCase writer, GetPrescriptionUseCase reader) {
        this.writer = writer;
        this.reader = reader;
    }

    @Override
    public PrescriptionDTO create(UUID commandId, CreatePrescriptionCommand command) {
        CarePrescriptionCreationService.validate(commandId, command);
        if (command.request().recordId() == null) {
            throw new PrescriptionRuleException("PHARMACY_CARE_CONTEXT_INVALID",
                    "An exact outpatient medical record is required");
        }
        UUID prescriptionId = writer.createWithContext(commandId, command);
        return reader.getPrescriptionById(prescriptionId);
    }
}
