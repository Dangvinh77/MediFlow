package com.mediflow.report.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.report.application.dto.command.DispensedItem;

/** Driving use case for applying validated domain events to report projections. */
public interface UpdateAggregateUseCase {

    void onMedicalRecordCreated(UUID eventId, LocalDate reportDate, UUID departmentId);

    void onLabResultCreated(UUID eventId, LocalDate reportDate, UUID departmentId);

    void onPrescriptionFilled(UUID eventId, Instant occurredAt, UUID departmentId,
                              UUID prescriptionId, List<DispensedItem> items);

    void onPaymentCompleted(UUID eventId, Instant occurredAt, UUID invoiceId,
                            UUID departmentId, BigDecimal amount);

    void onPaymentFailed(UUID eventId, Instant occurredAt, UUID invoiceId);
}
