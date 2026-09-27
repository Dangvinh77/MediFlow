package com.mediflow.inpatient.infrastructure.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.AdmissionSearchQuery;
import com.mediflow.inpatient.application.dto.query.BedSearchQuery;
import com.mediflow.inpatient.application.dto.request.AdmitRequest;
import com.mediflow.inpatient.application.dto.request.AssignBedRequest;
import com.mediflow.inpatient.application.dto.request.CancelAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CloseAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CorrectTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.CreateAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CreateBedRequest;
import com.mediflow.inpatient.application.dto.request.CreateTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.MedicalDischargeRequest;
import com.mediflow.inpatient.application.dto.request.RegisterOrderReferenceRequest;
import com.mediflow.inpatient.application.dto.request.ReleaseBedRequest;
import com.mediflow.inpatient.application.dto.request.TransferBedRequest;
import com.mediflow.inpatient.application.dto.request.UpdateBedRequest;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;
import com.mediflow.inpatient.application.dto.response.BedDTO;
import com.mediflow.inpatient.application.dto.response.ClinicalOrderReferenceDTO;
import com.mediflow.inpatient.application.dto.response.TreatmentEntryDTO;
import com.mediflow.inpatient.application.port.in.ManageAdmissionUseCase;
import com.mediflow.inpatient.application.port.in.ManageBedUseCase;
import com.mediflow.inpatient.application.port.in.ManageDischargeUseCase;
import com.mediflow.inpatient.application.port.in.ManageTreatmentUseCase;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import com.mediflow.inpatient.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.inpatient.infrastructure.security.ActorIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter for admission, bed, treatment and discharge use cases. */
@RestController
@RequestMapping("/api/v1/inpatient")
@Transactional
public class InpatientController {
    private final ManageAdmissionUseCase admissions;
    private final ManageBedUseCase beds;
    private final ManageTreatmentUseCase treatments;
    private final ManageDischargeUseCase discharges;
    private final ActorIdentity actorIdentity;
    private final HttpServletRequest servletRequest;

    public InpatientController(ManageAdmissionUseCase admissions,
                               ManageBedUseCase beds,
                               ManageTreatmentUseCase treatments,
                               ManageDischargeUseCase discharges,
                               ActorIdentity actorIdentity,
                               HttpServletRequest servletRequest) {
        this.admissions = admissions;
        this.beds = beds;
        this.treatments = treatments;
        this.discharges = discharges;
        this.actorIdentity = actorIdentity;
        this.servletRequest = servletRequest;
    }

    @PostMapping("/admissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> createAdmission(
            @Valid @RequestBody CreateAdmissionRequest request) {
        actorIdentity.assertStaff(request.nguoiYeuCau());
        AdmissionDTO created = admissions.create(request, correlationId());
        return ResponseEntity.created(URI.create("/api/v1/inpatient/admissions/" + created.maDotNoiTru()))
                .body(ApiResponse.ok(created, correlationId()));
    }

    @GetMapping("/admissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'CASHIER')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<AdmissionDTO>> getAdmission(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admissions.get(id), correlationId()));
    }

    @GetMapping("/admissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'CASHIER')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResult<AdmissionDTO>>> searchAdmissions(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) AdmissionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AdmissionSearchQuery query = new AdmissionSearchQuery(departmentId, patientId, status, from, to,
                PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(admissions.search(query), correlationId()));
    }

    @PutMapping("/admissions/{id}/bed")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> assignBed(
            @PathVariable UUID id, @Valid @RequestBody AssignBedRequest request) {
        actorIdentity.assertStaff(request.nguoiPhanGiuong());
        return ResponseEntity.ok(ApiResponse.ok(beds.assign(id, request, correlationId()), correlationId()));
    }

    @PutMapping("/admissions/{id}/bed/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> transferBed(
            @PathVariable UUID id, @Valid @RequestBody TransferBedRequest request) {
        actorIdentity.assertStaff(request.nguoiChuyenGiuong());
        return ResponseEntity.ok(ApiResponse.ok(beds.transfer(id, request, correlationId()), correlationId()));
    }

    @PutMapping("/admissions/{id}/bed/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> releaseBed(
            @PathVariable UUID id, @Valid @RequestBody ReleaseBedRequest request) {
        actorIdentity.assertStaff(request.nguoiTraGiuong());
        return ResponseEntity.ok(ApiResponse.ok(beds.release(id, request, correlationId()), correlationId()));
    }

    @PostMapping("/admissions/{id}/admit")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> admit(
            @PathVariable UUID id, @Valid @RequestBody AdmitRequest request) {
        actorIdentity.assertStaff(request.nguoiNhapVien());
        if (request.pheDuyetCapCuu() != null) {
            actorIdentity.assertApprover(request.pheDuyetCapCuu().nguoiDuyet(),
                    request.pheDuyetCapCuu().vaiTroNguoiDuyet());
        }
        return ResponseEntity.ok(ApiResponse.ok(admissions.admit(id, request, correlationId()), correlationId()));
    }

    @PostMapping("/admissions/{id}/treatments")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<TreatmentEntryDTO>> appendTreatment(
            @PathVariable UUID id, @Valid @RequestBody CreateTreatmentEntryRequest request) {
        actorIdentity.assertStaff(request.nguoiGhi());
        TreatmentEntryDTO result = treatments.append(id, request);
        return ResponseEntity.status(201).body(ApiResponse.ok(result, correlationId()));
    }

    @PostMapping("/admissions/{id}/treatments/{entryId}/corrections")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<TreatmentEntryDTO>> correctTreatment(
            @PathVariable UUID id, @PathVariable UUID entryId,
            @Valid @RequestBody CorrectTreatmentEntryRequest request) {
        actorIdentity.assertStaff(request.nguoiGhi());
        return ResponseEntity.ok(ApiResponse.ok(treatments.correct(id, entryId, request), correlationId()));
    }

    @PostMapping("/admissions/{id}/order-references")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<ClinicalOrderReferenceDTO>> registerOrder(
            @PathVariable UUID id, @Valid @RequestBody RegisterOrderReferenceRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(treatments.registerOrder(id, request), correlationId()));
    }

    @PostMapping("/admissions/{id}/medical-discharge")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> medicalDischarge(
            @PathVariable UUID id, @Valid @RequestBody MedicalDischargeRequest request) {
        actorIdentity.assertStaff(request.nguoiDuyet());
        return ResponseEntity.ok(ApiResponse.ok(
                discharges.approveMedicalDischarge(id, request, correlationId()), correlationId()));
    }

    @PostMapping("/admissions/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> close(
            @PathVariable UUID id, @Valid @RequestBody CloseAdmissionRequest request) {
        actorIdentity.assertStaff(request.nguoiDong());
        if (request.pheDuyet() != null) {
            actorIdentity.assertApprover(request.pheDuyet().nguoiDuyet(),
                    request.pheDuyet().vaiTroNguoiDuyet());
        }
        return ResponseEntity.ok(ApiResponse.ok(discharges.close(id, request, correlationId()), correlationId()));
    }

    @PostMapping("/admissions/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<AdmissionDTO>> cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelAdmissionRequest request) {
        actorIdentity.assertStaff(request.nguoiHuy());
        return ResponseEntity.ok(ApiResponse.ok(
                admissions.cancel(id, request, correlationId()), correlationId()));
    }

    @PostMapping("/beds")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BedDTO>> createBed(@Valid @RequestBody CreateBedRequest request) {
        BedDTO created = beds.create(request);
        return ResponseEntity.created(URI.create("/api/v1/inpatient/beds/" + created.maGiuong()))
                .body(ApiResponse.ok(created, correlationId()));
    }

    @PutMapping("/beds/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BedDTO>> updateBed(
            @PathVariable UUID id, @Valid @RequestBody UpdateBedRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(beds.update(id, request), correlationId()));
    }

    @GetMapping("/beds")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResult<BedDTO>>> searchBeds(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String wardCode,
            @RequestParam(required = false) BedStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(beds.search(new BedSearchQuery(departmentId, wardCode, status,
                PageQuery.of(page, size))), correlationId()));
    }

    private String correlationId() {
        UUID id = CorrelationIdRequestAttribute.read(servletRequest);
        return id == null ? UUID.randomUUID().toString() : id.toString();
    }
}
