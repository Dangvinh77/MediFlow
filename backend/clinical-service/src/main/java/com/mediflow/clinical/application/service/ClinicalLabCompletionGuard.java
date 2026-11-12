package com.mediflow.clinical.application.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Objects;

import com.mediflow.clinical.application.dto.authority.LabOrderLifecycleSnapshot;
import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.LabOrderLifecyclePort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.CareEpisodeType;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;

/** Validates live Lab authority before a scheduled outpatient record is completed. */
public final class ClinicalLabCompletionGuard {
    private final ClinicalLabOrderRepositoryPort orders;
    private final LabOrderLifecyclePort lifecycle;

    public ClinicalLabCompletionGuard(ClinicalLabOrderRepositoryPort orders, LabOrderLifecyclePort lifecycle) {
        this.orders = Objects.requireNonNull(orders);
        this.lifecycle = Objects.requireNonNull(lifecycle);
    }

    public void requireAllClosed(MedicalRecord record, Appointment appointment, String correlationId) {
        if (record == null || appointment == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("record, appointment, and correlation are required");
        }
        if (record.getAppointmentId() == null || !record.getAppointmentId().equals(appointment.getAppointmentId())
                || !record.getPatientId().equals(appointment.getPatientId())
                || !record.getDoctorId().equals(appointment.getDoctorId())
                || !record.getDepartmentId().equals(appointment.getDepartmentId())) {
            throw bindingMismatch();
        }

        List<ClinicalLabOrder> recordedOrders = orders.findByRecordId(record.getRecordId());
        if (recordedOrders == null) {
            throw unavailable("Clinical Lab order lookup returned no result");
        }
        for (ClinicalLabOrder recorded : recordedOrders) {
            if (recorded == null || !record.getRecordId().equals(recorded.recordId())
                    || !record.getPatientId().equals(recorded.patientId())
                    || !record.getDepartmentId().equals(recorded.requestingDepartmentId())
                    || !record.getDoctorId().equals(recorded.requestingDoctorId())
                    || recorded.careEpisodeType() != CareEpisodeType.OUTPATIENT_VISIT
                    || !appointment.getAppointmentId().equals(recorded.careEpisodeId())) {
                throw bindingMismatch();
            }
            LabOrderLifecycleSnapshot current = lifecycle.lifecycle(recorded.sourceOrderId(), correlationId);
            requireWellFormed(recorded, current);
            if (!current.exists()) {
                requireAbsentSnapshot(current);
                throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_NOT_FOUND",
                        "The recorded Lab order is not present in Lab");
            }
            requireSameBinding(recorded, current);
            if ("COMPLETED".equals(current.status()) || "CANCELLED".equals(current.status())) {
                continue;
            }
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_PENDING",
                    "Every recorded Lab order must be completed or cancelled before record completion");
        }
    }

    /** Withdraws an order of this visit that Lab never received; an order Lab holds is cancelled in Lab instead. */
    public ClinicalLabOrder withdrawUnreceived(MedicalRecord record, Appointment appointment, UUID sourceOrderId,
            UUID staffId, String reason, Instant at, String correlationId) {
        ClinicalLabOrder recorded = orders.findById(sourceOrderId)
                .filter(order -> record.getRecordId().equals(order.recordId()))
                .orElseThrow(() -> new InvalidClinicalDataException("CLINICAL_LAB_ORDER_NOT_FOUND",
                        "The Lab order does not belong to this visit"));
        if (!record.getPatientId().equals(recorded.patientId())
                || !appointment.getAppointmentId().equals(recorded.careEpisodeId())) {
            throw bindingMismatch();
        }
        LabOrderLifecycleSnapshot current = lifecycle.lifecycle(sourceOrderId, correlationId);
        requireWellFormed(recorded, current);
        if (current.exists()) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_RECEIVED",
                    "Lab already holds this order; cancel it through Lab instead");
        }
        requireAbsentSnapshot(current);
        orders.withdraw(sourceOrderId, staffId, reason, at);
        return recorded;
    }

    static void requireWellFormed(ClinicalLabOrder recorded, LabOrderLifecycleSnapshot current) {
        if (current == null || current.exists() == null
                || !recorded.sourceOrderId().equals(current.sourceOrderId()) || current.observedAt() == null) {
            throw unavailable("Lab returned an invalid source-order lifecycle response");
        }
    }

    static void requireSameBinding(ClinicalLabOrder recorded, LabOrderLifecycleSnapshot current) {
        if (!recorded.recordId().equals(current.recordId())
                || !recorded.patientId().equals(current.patientId())
                || !recorded.requestingDepartmentId().equals(current.departmentId())
                || !recorded.careEpisodeType().name().equals(current.careEpisodeType())
                || !recorded.careEpisodeId().equals(current.careEpisodeId())) {
            throw bindingMismatch();
        }
    }

    private static void requireAbsentSnapshot(LabOrderLifecycleSnapshot current) {
        if (current.testId() != null || current.recordId() != null || current.patientId() != null
                || current.departmentId() != null || current.careEpisodeType() != null
                || current.careEpisodeId() != null || current.status() != null) {
            throw unavailable("Lab returned a malformed missing-order response");
        }
    }

    private static InvalidClinicalDataException bindingMismatch() {
        return new InvalidClinicalDataException("CLINICAL_LAB_ORDER_BINDING_MISMATCH",
                "Lab lifecycle does not match the exact Clinical order and outpatient record");
    }

    private static UpstreamUnavailableException unavailable(String message) {
        return new UpstreamUnavailableException("LAB_LIFECYCLE_UNAVAILABLE", message);
    }
}
