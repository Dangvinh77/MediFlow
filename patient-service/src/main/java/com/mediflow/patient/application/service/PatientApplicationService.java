package com.mediflow.patient.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.mapper.PatientDtoMapper;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.DeletePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import com.mediflow.patient.application.port.out.PatientEventPublisherPort;
import com.mediflow.patient.application.port.out.PatientRepositoryPort;
import com.mediflow.patient.domain.exception.PatientNotFoundException;
import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the patient use cases.
 *
 * <p>What lives here versus in {@code Patient}: rules an object can check on its own (a birth date
 * cannot be in the future) belong to the model. Rules that need to ask the outside world (is this
 * CMND already taken?) belong here, because a single {@code Patient} instance cannot know.
 *
 * <p>Depends only on out-ports. It has no idea whether storage is Postgres or whether events go
 * to RabbitMQ — that is the whole point of the layering.
 */
@Service
public class PatientApplicationService
        implements CreatePatientUseCase, UpdatePatientUseCase, DeletePatientUseCase, GetPatientUseCase {

    private final PatientRepositoryPort repository;
    private final PatientEventPublisherPort events;
    private final PatientDtoMapper mapper;

    public PatientApplicationService(PatientRepositoryPort repository,
                                     PatientEventPublisherPort events,
                                     PatientDtoMapper mapper) {
        this.repository = repository;
        this.events = events;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PatientDTO create(CreatePatientRequest request) {
        // BR-P1: số CMND/CCCD là duy nhất. Needs the repository, so it cannot live in the model.
        if (repository.existsBySoCmnd(request.soCmnd())) {
            throw new DuplicateResourceException("PATIENT_CMND_DUPLICATE",
                    "Số CMND/CCCD đã tồn tại: " + request.soCmnd());
        }

        Patient patient = Patient.taoMoi(
                request.hoTen(), request.ngaySinh(), request.gioiTinh(), request.soCmnd(),
                request.diaChi(), request.soDienThoai(), request.email(), request.bhytSo());

        Patient saved = repository.save(patient);

        // The adapter defers the actual send until after this transaction commits (BR-P8):
        // a rolled-back create must not announce a patient that does not exist.
        events.publishCreated(saved);

        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    public PatientDTO update(UUID maBenhNhan, UpdatePatientRequest request) {
        Patient patient = timHoacBao(maBenhNhan);

        patient.capNhat(request.hoTen(), request.ngaySinh(), request.gioiTinh(),
                request.diaChi(), request.soDienThoai(), request.email(), request.bhytSo());

        Patient saved = repository.save(patient);
        events.publishUpdated(saved);

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PatientDTO getById(UUID maBenhNhan) {
        return mapper.toDto(timHoacBao(maBenhNhan));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PatientDTO> search(String keyword, PageQuery pageQuery) {
        // A blank keyword means "no filter", not "match the empty string".
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return repository.search(kw, pageQuery).map(mapper::toDto);
    }

    @Override
    @Transactional
    public void delete(UUID maBenhNhan) {
        timHoacBao(maBenhNhan);
        repository.deleteById(maBenhNhan);
    }

    private Patient timHoacBao(UUID maBenhNhan) {
        return repository.findById(maBenhNhan)
                .orElseThrow(() -> new PatientNotFoundException(maBenhNhan));
    }
}
