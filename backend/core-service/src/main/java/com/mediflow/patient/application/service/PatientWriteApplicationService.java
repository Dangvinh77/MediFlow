package com.mediflow.patient.application.service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Locale;
import com.mediflow.patient.domain.exception.InvalidPatientDataException;
import com.mediflow.patient.application.event.PatientArchivedEvent;
import com.mediflow.patient.application.port.out.PatientIdentityAuditPort;
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
    private final PatientIdentityAuditPort identityAudit;

    public PatientWriteApplicationService(
            PatientRepositoryPort patients,
            PatientDtoMapper mapper,
            PatientEventPublisherPort events,
            CorrelationIdProvider correlationIds,
            PatientIdentityAuditPort identityAudit) {
        this.patients = patients;
        this.mapper = mapper;
        this.events = events;
        this.correlationIds = correlationIds;
        this.identityAudit = identityAudit;
    }

    @Override
    @Transactional("patientTransactionManager")
    public PatientDTO create(CreatePatientRequest request) {
        String normalizedEmail = normalizeAndRequireEmail(request.email());
        if (patients.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException(
                    "PATIENT_EMAIL_DUPLICATE", "Email này đã được sử dụng cho một hồ sơ bệnh nhân khác");
        }
        String identityNumber = request.soCmnd();
        if (identityNumber != null && patients.existsByIdentityNumber(identityNumber.trim())) {
            throw new DuplicateResourceException(
                    "PATIENT_CMND_DUPLICATE", "Số CMND/CCCD đã tồn tại");
        }

        Patient saved = patients.save(Patient.create(
                request.hoTen(), request.ngaySinh(), request.gioiTinh(), request.soCmnd(),
                request.diaChi(), request.soDienThoai(), normalizedEmail, request.bhytSo()));
        events.publishCreated(new PatientCreatedEvent(
                UUID.randomUUID(), saved.createdAt(), correlationIds.currentOrCreate().toString(),
                saved.patientId(), saved.fullName(), saved.email(), saved.phoneNumber()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional("patientTransactionManager")
    public PatientDTO update(UUID patientId, UpdatePatientRequest request) {
        Patient patient = patients.findByIdForUpdate(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        if (patient.archived()) {
            throw new InvalidPatientDataException(
                    "PATIENT_ARCHIVED", "Hồ sơ đã lưu trữ, không thể chỉnh sửa");
        }
        String requestedEmail = request.email() == null ? patient.email() : normalizeAndRequireEmail(request.email());
        if (!Objects.equals(normalizeEmail(patient.email()), normalizeEmail(requestedEmail))
                && patients.existsByEmailAndPatientIdNot(requestedEmail, patientId)) {
            throw new DuplicateResourceException(
                    "PATIENT_EMAIL_DUPLICATE", "Email này đã được sử dụng cho một hồ sơ bệnh nhân khác");
        }
        String identityNumber = request.soCmnd() == null ? patient.identityNumber() : request.soCmnd().trim();
        boolean identityChanged = !Objects.equals(identityNumber, patient.identityNumber())
                || !Objects.equals(request.ngaySinh(), patient.dateOfBirth());
        if (identityChanged && (request.lyDoThayDoiDinhDanh() == null
                || request.lyDoThayDoiDinhDanh().isBlank() || request.lyDoThayDoiDinhDanh().trim().length() > 500)) {
            throw new InvalidPatientDataException(
                    "PATIENT_IDENTITY_REASON_REQUIRED", "Vui lòng nhập lý do thay đổi CCCD hoặc ngày sinh (tối đa 500 ký tự)");
        }
        String oldIdentity = patient.identityNumber();
        LocalDate oldBirth = patient.dateOfBirth();
        if (patients.existsByIdentityNumberAndPatientIdNot(identityNumber, patientId)) {
            throw new DuplicateResourceException("PATIENT_CMND_DUPLICATE", "Số CMND/CCCD đã tồn tại");
        }
        patient.update(request.hoTen(), request.ngaySinh(), request.gioiTinh(), request.diaChi(),
                request.soDienThoai(), requestedEmail,
                request.bhytSo(), identityNumber);
        Patient saved = patients.save(patient);
        if (identityChanged) {
            identityAudit.record(patientId, request.lyDoThayDoiDinhDanh().trim(), oldIdentity,
                    saved.identityNumber(), oldBirth, saved.dateOfBirth(), saved.updatedAt(), correlationIds.currentOrCreate());
        }
        events.publishUpdated(new PatientUpdatedEvent(
                UUID.randomUUID(), saved.updatedAt(), correlationIds.currentOrCreate().toString(),
                saved.patientId(), saved.fullName(), saved.email(), saved.phoneNumber(), saved.address()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional("patientTransactionManager")
    public PatientDTO synchronizeAccountEmail(UUID patientId, String email) {
        Patient patient = patients.findByIdForUpdate(patientId)
                .orElseThrow(() -> new PatientNotFoundException(patientId));
        if (patient.archived()) {
            throw new InvalidPatientDataException("PATIENT_ARCHIVED", "Hồ sơ đã lưu trữ, không thể đồng bộ email");
        }
        String normalizedEmail = normalizeAndRequireEmail(email);
        if (Objects.equals(normalizeEmail(patient.email()), normalizedEmail)) return mapper.toDto(patient);
        if (patients.existsByEmailAndPatientIdNot(normalizedEmail, patientId)) {
            throw new DuplicateResourceException(
                    "PATIENT_EMAIL_DUPLICATE", "Email này đã được sử dụng cho một hồ sơ bệnh nhân khác");
        }
        patient.update(patient.fullName(), patient.dateOfBirth(), patient.gender(), patient.address(),
                patient.phoneNumber(), normalizedEmail, patient.healthInsuranceNumber(), patient.identityNumber());
        Patient saved = patients.save(patient);
        events.publishUpdated(new PatientUpdatedEvent(UUID.randomUUID(), saved.updatedAt(),
                correlationIds.currentOrCreate().toString(), saved.patientId(), saved.fullName(),
                saved.email(), saved.phoneNumber(), saved.address()));
        return mapper.toDto(saved);
    }

    @Override
    @Transactional("patientTransactionManager")
    public void delete(UUID patientId) {
        Patient patient = patients.findByIdForUpdate(patientId).orElseThrow(() -> new PatientNotFoundException(patientId));
        if (patient.archived()) return;
        patient.archive();
        Patient saved = patients.save(patient);
        events.publishArchived(new PatientArchivedEvent(
                UUID.randomUUID(), saved.updatedAt(), correlationIds.currentOrCreate().toString(), patientId));
    }

    private static String normalizeAndRequireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidPatientDataException("PATIENT_EMAIL_REQUIRED", "Email không được để trống");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new InvalidPatientDataException("PATIENT_EMAIL_INVALID", "Email không đúng định dạng");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
