package com.mediflow.clinical.application.service;

import com.mediflow.clinical.application.dto.request.CreateClinicalLabOrderRequest;
import com.mediflow.clinical.application.dto.response.ClinicalLabOrderContextDTO;
import com.mediflow.clinical.application.dto.response.ClinicalLabOrderDTO;
import com.mediflow.clinical.application.port.in.CreateClinicalLabOrderUseCase;
import com.mediflow.clinical.application.port.in.GetClinicalLabOrderContextUseCase;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalLabOrderRepositoryPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.ClinicalAppointmentNotFoundException;
import com.mediflow.clinical.domain.exception.ClinicalRecordNotFoundException;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.ClinicalLabOrder;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

/** Creates immutable Lab authority from a live Clinical examination and serves persisted context. */
@Service
public class ClinicalLabOrderApplicationService implements CreateClinicalLabOrderUseCase,
        GetClinicalLabOrderContextUseCase {

    private final AppointmentRepositoryPort appointments;
    private final MedicalRecordRepositoryPort records;
    private final ClinicalLabOrderRepositoryPort orders;
    private final ClinicalStaffAuthority staffAuthority;
    private final boolean outpatientMvpEnabled;
    private final boolean careFinanceV2Enabled;
    private final Clock clock;

    public ClinicalLabOrderApplicationService(AppointmentRepositoryPort appointments,
                                              MedicalRecordRepositoryPort records,
                                              ClinicalLabOrderRepositoryPort orders,
                                              CurrentClinicalActorPort actors,
                                              StaffLookupPort staff,
                                              OutpatientMvpMode outpatientMvpMode,
                                              @Value("${mediflow.features.care-finance-v2.enabled:false}")
                                              boolean careFinanceV2Enabled,
                                              @Qualifier("clinicalLabOrderClock") Clock clock) {
        this.appointments = appointments;
        this.records = records;
        this.orders = orders;
        this.staffAuthority = new ClinicalStaffAuthority(actors, staff);
        this.outpatientMvpEnabled = outpatientMvpMode.enabled();
        this.careFinanceV2Enabled = careFinanceV2Enabled;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ClinicalLabOrderDTO create(UUID recordId, CreateClinicalLabOrderRequest request) {
        requireOutpatientMvp();
        if (recordId == null || request == null) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID", "A record and lab order are required");
        }
        if (request.requestedDate() == null) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID",
                    "A Lab order date is required");
        }

        MedicalRecord record = records.findByIdForUpdate(recordId)
                .orElseThrow(() -> new ClinicalRecordNotFoundException("Medical record not found: " + recordId));
        if (record.getAppointmentId() == null) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_CONTEXT_MISMATCH",
                    "An outpatient Lab order requires an appointment-backed examination");
        }
        Appointment appointment = appointments.findByIdForUpdate(record.getAppointmentId())
                .orElseThrow(() -> new ClinicalAppointmentNotFoundException(
                        "Appointment not found: " + record.getAppointmentId()));

        boolean recordMatchesAppointment = record.getPatientId().equals(appointment.getPatientId())
                && record.getDoctorId().equals(appointment.getDoctorId())
                && record.getDepartmentId().equals(appointment.getDepartmentId());
        if (!recordMatchesAppointment || record.getStatus() != MedicalRecordStatus.OPEN
                || (appointment.getCareContractVersion() != 1 && appointment.getCareContractVersion() != 2)
                || appointment.getStatus() != AppointmentStatus.IN_EXAM) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_CONTEXT_MISMATCH",
                    "Lab orders require the matching active outpatient examination");
        }

        staffAuthority.requireAssignedDoctor(appointment.getDoctorId(), appointment.getDepartmentId());
        ClinicalLabOrder order = ClinicalLabOrder.create(record.getRecordId(), record.getPatientId(),
                appointment.getDoctorId(), appointment.getDepartmentId(), appointment.getAppointmentId(),
                request.labType(), request.requestedDate(), request.priceCode(), clock.instant());
        return toDto(orders.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalLabOrderContextDTO get(UUID sourceOrderId) {
        if (sourceOrderId == null) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_INVALID", "A source order id is required");
        }
        Instant observedAt = clock.instant();
        return orders.findById(sourceOrderId)
                .map(order -> toContext(order, observedAt))
                .orElseGet(() -> ClinicalLabOrderContextDTO.absent(sourceOrderId, observedAt));
    }

    private void requireOutpatientMvp() {
        if (!outpatientMvpEnabled || !careFinanceV2Enabled) {
            throw new InvalidClinicalDataException("CLINICAL_LAB_ORDER_DISABLED",
                    "Clinical Lab order creation requires outpatient MVP and Care & Finance V2");
        }
    }

    private static ClinicalLabOrderDTO toDto(ClinicalLabOrder order) {
        return new ClinicalLabOrderDTO(order.sourceOrderId(), order.recordId(), order.patientId(),
                order.requestingDoctorId(), order.requestingDepartmentId(), order.careEpisodeType(),
                order.careEpisodeId(), order.labType(), order.requestedDate(), order.priceCode(), order.orderedAt());
    }

    private static ClinicalLabOrderContextDTO toContext(ClinicalLabOrder order, Instant observedAt) {
        return new ClinicalLabOrderContextDTO(true, order.sourceOrderId(), order.recordId(), order.patientId(),
                order.requestingDoctorId(), order.requestingDepartmentId(), order.careEpisodeType(),
                order.careEpisodeId(), order.labType(), order.requestedDate(), order.priceCode(),
                order.orderedAt(), observedAt);
    }
}
