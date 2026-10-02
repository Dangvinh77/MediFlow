package com.mediflow.patient.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.event.PatientCreatedEvent;
import com.mediflow.patient.application.event.PatientUpdatedEvent;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.DeletePatientUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PatientWriteApplicationService
        implements CreatePatientUseCase, UpdatePatientUseCase, DeletePatientUseCase {

    private final PatientRepositoryPort patients;
    private final PatientDtoMapper mapper;
    private final PatientEventPublisherPort events;
    private final CorrelationIdProvider correlationIds;

    public PatientWriteApplicationService(
            PatientRepositoryPort patients,
            PatientDtoMapper mapper,
            PatientEventPublisherPort events,
            CorrelationIdProvider correlationIds) {
        this.patients = patients;
        this.mapper = mapper;
        this.events = events;
        this.correlationIds = correlationIds;
    }

    @Override
    @Transactional
    public PatientDTO create(CreatePatientRequest request) {
        String identityNumber = request.soCmnd();
        if (identityNumber != null && patients.existsByIdentityNumber(identityNumber.trim())) {
            throw new DuplicateResourceException(
                    "PATIENT_CMND_DUPLICATE", "Số CMND/CCCD đã tồn tại");
        }

        Patient saved = patients.save(Patient.create(
                request.hoTen(), request.ngaySinh(), request.gioiTinh(), request.soCmnd(),
                request.diaChi(), request.soDienThoai(), request.email(), request.bhytSo()));
        events.publishCreated(new PatientCreatedEvent(
                UUID.randomUUID(), saved.createdAt(), correlationIds.currentOrCreate().toString(),
                saved.patientId(), saved.fullName(), saved.email(), saved.phoneNumber()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public PatientDTO update(UUID patientId, UpdatePatientRequest request) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        patient.update(request.hoTen(), request.ngaySinh(), request.gioiTinh(), request.diaChi(),
                request.soDienThoai(), request.email(), request.bhytSo());
        Patient saved = patients.save(patient);
        events.publishUpdated(new PatientUpdatedEvent(
                UUID.randomUUID(), saved.updatedAt(), correlationIds.currentOrCreate().toString(),
                saved.patientId(), saved.fullName(), saved.email(), saved.phoneNumber(), saved.address()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID patientId) {
        patients.findById(patientId).orElseThrow(() -> new PatientNotFoundException(patientId));
        patients.deleteById(patientId);
    }
}
