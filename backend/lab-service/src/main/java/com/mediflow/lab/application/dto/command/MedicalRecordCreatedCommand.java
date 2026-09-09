package com.mediflow.lab.application.dto.command;

import java.util.UUID;

/** Fields Lab needs to identify a medicalrecord.created delivery. */
public record MedicalRecordCreatedCommand(
        UUID eventId,
        UUID recordId,
        UUID patientId,
        UUID departmentId
) {
}
