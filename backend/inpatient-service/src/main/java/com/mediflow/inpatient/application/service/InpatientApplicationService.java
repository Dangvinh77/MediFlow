package com.mediflow.inpatient.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.dto.command.DepositTopupRequiredCommand;
import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;
import com.mediflow.inpatient.application.dto.command.FinancialClearanceCommand;
import com.mediflow.inpatient.application.dto.command.LabResultFactCommand;
import com.mediflow.inpatient.application.dto.command.PrescriptionFilledFactCommand;
import com.mediflow.inpatient.application.dto.command.SettlementCompletedCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCancelledFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCompletedFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryReadyFactCommand;
import com.mediflow.inpatient.application.dto.event.AdmissionClosedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionDepositRequestedEvent;
import com.mediflow.inpatient.application.dto.event.AdmissionStartedEvent;
import com.mediflow.inpatient.application.dto.event.DepositSuggestion;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import com.mediflow.inpatient.application.dto.event.MedicalDischargeApprovedEvent;
import com.mediflow.inpatient.application.dto.query.AdmissionSearchQuery;
import com.mediflow.inpatient.application.dto.query.BedSearchQuery;
import com.mediflow.inpatient.application.dto.request.AdmitRequest;
import com.mediflow.inpatient.application.dto.request.AssignBedRequest;
import com.mediflow.inpatient.application.dto.request.CancelAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CloseAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CloseOverrideRequest;
import com.mediflow.inpatient.application.dto.request.CorrectTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.CreateAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CreateBedRequest;
import com.mediflow.inpatient.application.dto.request.CreateTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.EmergencyOverrideRequest;
import com.mediflow.inpatient.application.dto.request.MedicalDischargeRequest;
import com.mediflow.inpatient.application.dto.request.RegisterOrderReferenceRequest;
import com.mediflow.inpatient.application.dto.request.ReleaseBedRequest;
import com.mediflow.inpatient.application.dto.request.TransferBedRequest;
import com.mediflow.inpatient.application.dto.request.UpdateBedRequest;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;
import com.mediflow.inpatient.application.dto.response.BedDTO;
import com.mediflow.inpatient.application.dto.response.ClinicalOrderReferenceDTO;
import com.mediflow.inpatient.application.dto.response.TreatmentEntryDTO;
import com.mediflow.inpatient.application.mapper.InpatientDtoMapper;
import com.mediflow.inpatient.application.port.in.ManageAdmissionUseCase;
import com.mediflow.inpatient.application.port.in.ManageBedUseCase;
import com.mediflow.inpatient.application.port.in.ManageDischargeUseCase;
import com.mediflow.inpatient.application.port.in.ManageTreatmentUseCase;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.BedAssignment;
import com.mediflow.inpatient.domain.model.CloseOverride;
import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.DepositTopupRequest;
import com.mediflow.inpatient.domain.model.DischargeSummary;
import com.mediflow.inpatient.domain.model.EmergencyOverride;
import com.mediflow.inpatient.domain.model.FinancialClearance;
import com.mediflow.inpatient.domain.model.SettlementSnapshot;
import com.mediflow.inpatient.domain.model.TreatmentEntry;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import com.mediflow.inpatient.domain.model.enums.CareEpisodeType;
import com.mediflow.inpatient.domain.model.enums.ClearancePurpose;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import com.mediflow.inpatient.domain.model.enums.OverrideType;
import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class InpatientApplicationService implements ManageAdmissionUseCase, ManageBedUseCase,
        ManageTreatmentUseCase, ManageDischargeUseCase, ReactToAdmissionReferralUseCase,
        ReactToFinancialClearanceUseCase, ReactToSettlementUseCase, ReactToDepositTopupUseCase,
        ReactToExternalOrderUseCase {

    private static final int CONTRACT_VERSION = 1;
    private static final String PRODUCER = "inpatient-service";

    private final AdmissionRepositoryPort admissions;
    private final BedRepositoryPort beds;
    private final BedAssignmentRepositoryPort assignments;
    private final TreatmentEntryRepositoryPort treatments;
    private final ClinicalOrderReferenceRepositoryPort references;
    private final DischargeSummaryRepositoryPort discharges;
    private final ProcessedEventPort processedEvents;
    private final InpatientEventStorePort eventStore;
    private final InpatientOutboxPort outbox;
    private final DepositSuggestionPolicyPort depositSuggestions;
    private final InpatientDtoMapper mapper;
    private final Clock clock;

    public InpatientApplicationService(
            AdmissionRepositoryPort admissions,
            BedRepositoryPort beds,
            BedAssignmentRepositoryPort assignments,
            TreatmentEntryRepositoryPort treatments,
            ClinicalOrderReferenceRepositoryPort references,
            DischargeSummaryRepositoryPort discharges,
            ProcessedEventPort processedEvents,
            InpatientEventStorePort eventStore,
            InpatientOutboxPort outbox,
            DepositSuggestionPolicyPort depositSuggestions,
            InpatientDtoMapper mapper,
            Clock clock) {
        this.admissions = admissions;
        this.beds = beds;
        this.assignments = assignments;
        this.treatments = treatments;
        this.references = references;
        this.discharges = discharges;
        this.processedEvents = processedEvents;
        this.eventStore = eventStore;
        this.outbox = outbox;
        this.depositSuggestions = depositSuggestions;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public AdmissionDTO create(CreateAdmissionRequest request, String correlationId) {
        Admission admission = createAdmissionModel(request, correlationId);
        return admissionDto(admission);
    }

    @Override
    public AdmissionDTO get(UUID admissionId) {
        return admissionDto(requireAdmission(admissionId));
    }

    @Override
    public PageResult<AdmissionDTO> search(AdmissionSearchQuery query) {
        if (query.tuNgay() != null && query.denNgay() != null
                && query.denNgay().isBefore(query.tuNgay())) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "End date must not precede start date");
        }
        PageQuery page = query.phanTrang() == null
                ? PageQuery.of(null, null)
                : PageQuery.of(query.phanTrang().page(), query.phanTrang().size());
        AdmissionSearchQuery normalized = new AdmissionSearchQuery(query.maKhoa(), query.maBenhNhan(),
                query.status(), query.tuNgay(), query.denNgay(), page);
        return admissions.search(normalized).map(this::admissionDto);
    }

    @Override
    public AdmissionDTO admit(UUID admissionId, AdmitRequest request, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        BedAssignment assignment = assignments.findActiveByAdmissionId(admissionId)
                .orElseThrow(() -> violation("INPATIENT_ACTIVE_BED_REQUIRED",
                        "An active bed assignment is required"));
        Bed bed = requireBedForUpdate(assignment.bedId());
        Instant now = clock.instant();
        AdmissionStatus from = admission.status();
        EmergencyOverride emergency = toEmergencyOverride(request.pheDuyetCapCuu());
        admission.admit(now, true, emergency);
        if (emergency != null) {
            eventStore.saveOverride(emergency.maPheDuyet(), admissionId, OverrideType.EMERGENCY_ADMIT,
                    emergency.nguoiDuyet(), emergency.vaiTroNguoiDuyet(), emergency.lyDo(),
                    emergency.thoiGianDuyet());
        }
        admissions.save(admission);
        appendTransition(admission, from, request.nguoiNhapVien(), null, correlationId, now);
        appendEvent(admissionId, "admission.started", correlationId, now,
                new AdmissionStartedEvent(admissionId, admission.patientId(), bed.bedId(),
                        admission.departmentId(), now, admission.emergency(), admission.emergencyOverrideId()));
        return admissionDto(admission);
    }

    @Override
    public AdmissionDTO cancel(UUID admissionId, CancelAdmissionRequest request, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        Instant now = clock.instant();
        AdmissionStatus from = admission.status();
        releaseActiveAssignment(admission, request.nguoiHuy(), request.lyDo(), now, false);
        admission.cancel(request.nguoiHuy(), request.lyDo(), now);
        admissions.save(admission);
        appendTransition(admission, from, request.nguoiHuy(), request.lyDo(), correlationId, now);
        return admissionDto(admission);
    }

    @Override
    public BedDTO create(CreateBedRequest request) {
        Bed bed = Bed.create(UUID.randomUUID(), request.maKhoa(), request.maKhu(), request.maPhong(),
                request.maGiuong(), request.loaiGiuong());
        return mapper.toBedDto(beds.save(bed));
    }

    @Override
    public BedDTO update(UUID bedId, UpdateBedRequest request) {
        Bed bed = requireBedForUpdate(bedId);
        boolean hasAssignment = assignments.findActiveByBedId(bedId).isPresent();
        bed.update(request.loaiGiuong(), request.status(), request.active(), hasAssignment);
        return mapper.toBedDto(beds.save(bed));
    }

    @Override
    public PageResult<BedDTO> search(BedSearchQuery query) {
        PageQuery page = query.phanTrang() == null
                ? PageQuery.of(null, null)
                : PageQuery.of(query.phanTrang().page(), query.phanTrang().size());
        return beds.search(new BedSearchQuery(query.maKhoa(), query.maKhu(), query.status(), page))
                .map(mapper::toBedDto);
    }

    @Override
    public AdmissionDTO assign(UUID admissionId, AssignBedRequest request, String correlationId) {
        Admission admission = assignBed(admissionId, request.maGiuong(), request.nguoiPhanGiuong(),
                clock.instant(), correlationId);
        return admissionDto(admission);
    }

    @Override
    public AdmissionDTO transfer(UUID admissionId, TransferBedRequest request, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        requireAssignEligible(admission);
        BedAssignment current = assignments.findActiveByAdmissionId(admissionId)
                .orElseThrow(() -> violation("INPATIENT_ACTIVE_BED_REQUIRED",
                        "An active bed assignment is required before transfer"));
        if (current.bedId().equals(request.maGiuongDich())) {
            return admissionDto(admission);
        }

        Instant now = clock.instant();
        List<UUID> sortedIds = List.of(current.bedId(), request.maGiuongDich()).stream()
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, Bed> lockedBeds = beds.findByIdsForUpdateInOrder(sortedIds).stream()
                .collect(Collectors.toMap(Bed::bedId, Function.identity()));
        Bed oldBed = lockedBeds.get(current.bedId());
        Bed targetBed = lockedBeds.get(request.maGiuongDich());
        if (oldBed == null) {
            throw bedNotFound(current.bedId());
        }
        if (targetBed == null) {
            throw bedNotFound(request.maGiuongDich());
        }
        targetBed.assign();
        current.release(request.nguoiChuyenGiuong(), request.lyDo(), now);
        assignments.save(current);
        oldBed.release();
        beds.save(oldBed);
        beds.save(targetBed);
        assignments.save(BedAssignment.create(UUID.randomUUID(), admissionId, targetBed.bedId(),
                request.nguoiChuyenGiuong(), now));
        AdmissionStatus from = admission.status();
        admission.applyBedAssignment(true, now);
        admissions.save(admission);
        appendTransition(admission, from, request.nguoiChuyenGiuong(), request.lyDo(), correlationId, now);
        return admissionDto(admission);
    }

    @Override
    public AdmissionDTO release(UUID admissionId, ReleaseBedRequest request, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        Instant now = clock.instant();
        AdmissionStatus from = admission.status();
        releaseActiveAssignment(admission, request.nguoiTraGiuong(), request.lyDo(), now, true);
        if (isPreAdmission(admission.status())) {
            admission.applyBedAssignment(false, now);
            admissions.save(admission);
            appendTransition(admission, from, request.nguoiTraGiuong(), request.lyDo(), correlationId, now);
        }
        return admissionDto(admission);
    }

    @Override
    public TreatmentEntryDTO append(UUID admissionId, CreateTreatmentEntryRequest request) {
        Admission admission = requireAdmission(admissionId);
        requireStatus(admission, AdmissionStatus.ADMITTED);
        return mapper.toTreatmentDto(treatments.save(TreatmentEntry.create(admissionId,
                request.loaiMuc(), request.noiDung(), request.nguoiGhi(), request.thoiGianGhi())));
    }

    @Override
    public TreatmentEntryDTO correct(UUID admissionId, UUID entryId, CorrectTreatmentEntryRequest request) {
        Admission admission = requireAdmission(admissionId);
        requireStatus(admission, AdmissionStatus.ADMITTED);
        TreatmentEntry original = treatments.findByAdmissionAndEntryId(admissionId, entryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INPATIENT_TREATMENT_ENTRY_NOT_FOUND", "Treatment entry was not found"));
        TreatmentEntry correction = TreatmentEntry.correctForAdmission(admissionId, original,
                request.noiDungDaDinhChinh(), request.nguoiGhi(), request.thoiGianGhi());
        return mapper.toTreatmentDto(treatments.save(correction));
    }

    @Override
    public ClinicalOrderReferenceDTO registerOrder(UUID admissionId, RegisterOrderReferenceRequest request) {
        Admission admission = requireAdmission(admissionId);
        if (admission.status() == AdmissionStatus.CLOSED || admission.status() == AdmissionStatus.CANCELLED) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "Ended admission cannot register an order");
        }
        Optional<ClinicalOrderReference> existing = references.findByTypeAndExternalId(
                request.loaiYLenh(), request.maYLenhBenNgoai());
        if (existing.isPresent()) {
            ClinicalOrderReference reference = existing.get();
            if (!reference.admissionId().equals(admissionId)) {
                throw externalOrderMismatch();
            }
            return mapper.toOrderDto(reference);
        }
        ClinicalOrderReference created = ClinicalOrderReference.create(admissionId, request.loaiYLenh(),
                request.maYLenhBenNgoai(), request.status(), request.tomTat(), null);
        return mapper.toOrderDto(references.save(created));
    }

    @Override
    public AdmissionDTO approveMedicalDischarge(UUID admissionId, MedicalDischargeRequest request,
                                                String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        Instant now = clock.instant();
        AdmissionStatus from = admission.status();
        DischargeSummary summary = new DischargeSummary(request.maTomTat(), admissionId,
                request.tomTatChanDoan(), request.tomTatDieuTri(), request.ketQua(),
                request.keHoachTheoDoi(), request.nguoiDuyet(), request.thoiGianDuyet());
        discharges.save(summary);
        admission.medicallyDischarge(summary.maTomTat(), request.thoiGianDuyet());
        admissions.save(admission);
        appendTransition(admission, from, request.nguoiDuyet(), null, correlationId, now);
        appendEvent(admissionId, "discharge.medically.approved", correlationId,
                request.thoiGianDuyet(), new MedicalDischargeApprovedEvent(admissionId,
                        admission.patientId(), summary.maTomTat(), request.nguoiDuyet(), request.thoiGianDuyet()));
        return admissionDto(admission);
    }

    @Override
    public AdmissionDTO close(UUID admissionId, CloseAdmissionRequest request, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        boolean activeBed = assignments.findActiveByAdmissionId(admissionId).isPresent();
        Optional<SettlementSnapshot> latest = eventStore.findLatestSettlement(admissionId);
        SettlementOutcome outcome = latest.map(SettlementSnapshot::ketQua).orElse(null);
        UUID settlementId = admission.settlementId();
        CloseOverride override = toCloseOverride(request.pheDuyet());
        Instant now = clock.instant();
        AdmissionStatus from = admission.status();
        admission.close(activeBed, outcome, settlementId, override, now);
        if (override != null) {
            eventStore.saveOverride(override.maPheDuyet(), admissionId, override.loaiPheDuyet(),
                    override.nguoiDuyet(), override.vaiTroNguoiDuyet(), override.lyDo(),
                    override.thoiGianDuyet());
        }
        admissions.save(admission);
        appendTransition(admission, from, request.nguoiDong(), null, correlationId, now);
        appendEvent(admissionId, "admission.closed", correlationId, now,
                new AdmissionClosedEvent(admissionId, admission.patientId(), admission.settlementId(),
                        admission.closeOverrideId(), now));
        return admissionDto(admission);
    }

    @Override
    public void onAdmissionRequested(AdmissionRequestedCommand command) {
        validateEnvelope(command.maSuKien(), command.phienBan(), command.xayRaLuc(), command.maTuongQuan());
        require(command.maYeuCauNoiTru(), "admissionRequestId");
        require(command.maHoSo(), "recordId");
        require(command.maBenhNhan(), "patientId");
        require(command.maKhoa(), "departmentId");
        require(command.nguoiYeuCau(), "requestedBy");
        requireText(command.tomTatChanDoan(), "diagnosisSummary");
        require(command.doUuTien(), "priority");
        require(command.thoiGianYeuCau(), "requestedAt");
        if (!processedEvents.tryClaim(command.maSuKien(), "admission.requested")) {
            return;
        }
        if (admissions.findByAdmissionRequestId(command.maYeuCauNoiTru()).isPresent()) {
            return;
        }
        CreateAdmissionRequest request = new CreateAdmissionRequest(command.maYeuCauNoiTru(),
                command.maHoSo(), command.maBenhNhan(), command.maKhoa(), command.nguoiYeuCau(),
                command.tomTatChanDoan(), command.doUuTien(), command.capCuu(), command.thoiGianYeuCau());
        createAdmissionModel(request, command.maTuongQuan());
    }

    @Override
    public void onFinancialClearance(FinancialClearanceCommand command) {
        validateEnvelope(command.maSuKien(), command.phienBan(), command.xayRaLuc(), command.maTuongQuan());
        require(command.maXacNhan(), "clearanceId");
        require(command.maHoaDon(), "invoiceId");
        require(command.maTaiKhoan(), "accountId");
        require(command.maBenhNhan(), "patientId");
        require(command.maTapNoiTru(), "careEpisodeId");
        require(command.maDotNoiTru(), "admissionId");
        require(command.soTien(), "amount");
        requireText(command.tienTe(), "currency");
        requireText(command.phuongThucThanhToan(), "paymentMethod");
        if (command.mucDich() != ClearancePurpose.ADMISSION_DEPOSIT
                || command.loaiTapNoiTru() != CareEpisodeType.ADMISSION
                || !command.maTapNoiTru().equals(command.maDotNoiTru())) {
            throw violation("INPATIENT_CLEARANCE_TARGET_MISMATCH",
                    "Clearance must target this admission episode and ADMISSION_DEPOSIT purpose");
        }
        if (!processedEvents.tryClaim(command.maSuKien(), "financial.clearance.granted")) {
            return;
        }
        Admission admission = requireAdmissionForUpdate(command.maDotNoiTru());
        if (!admission.patientId().equals(command.maBenhNhan())) {
            throw violation("INPATIENT_CLEARANCE_TARGET_MISMATCH",
                    "Clearance patient does not match the admission");
        }
        if (command.soTien().signum() < 0 || command.tienTe().length() != 3) {
            throw violation("INPATIENT_CLEARANCE_TARGET_MISMATCH", "Financial clearance fields are invalid");
        }
        FinancialClearance clearance = new FinancialClearance(command.maXacNhan(), command.maSuKien(),
                command.maHoaDon(), command.maTaiKhoan(), command.maDotNoiTru(), command.maBenhNhan(),
                command.soTien(), command.tienTe(), command.phuongThucThanhToan(), command.hetHanLuc(),
                command.capCuuNgoaiLe(), command.xayRaLuc());
        eventStore.saveClearance(clearance);
        boolean activeBed = assignments.findActiveByAdmissionId(admission.admissionId()).isPresent();
        AdmissionStatus from = admission.status();
        admission.applyFinancialClearance(clearance.maXacNhan(), clearance.hetHanLuc(), activeBed, clock.instant());
        admissions.save(admission);
        appendTransition(admission, from, null, null, command.maTuongQuan(), clock.instant());
    }

    @Override
    public void onSettlementCompleted(SettlementCompletedCommand command) {
        validateEnvelope(command.maSuKien(), command.phienBan(), command.xayRaLuc(), command.maTuongQuan());
        require(command.maQuyetToan(), "settlementId");
        require(command.maDotNoiTru(), "admissionId");
        require(command.maTaiKhoan(), "accountId");
        if (!processedEvents.tryClaim(command.maSuKien(), "settlement.completed")) {
            return;
        }
        Admission admission = requireAdmissionForUpdate(command.maDotNoiTru());
        SettlementSnapshot settlement = new SettlementSnapshot(command.maQuyetToan(), command.maSuKien(),
                command.maDotNoiTru(), command.maTaiKhoan(), command.tongTien(), command.baoHiemThanhToan(),
                command.benhNhanPhaiTra(), command.daThanhToan(), command.daHoanTien(), command.soDu(),
                command.ketQua(), command.hoanTatLuc());
        eventStore.saveSettlement(settlement);
        if (admission.status() == AdmissionStatus.MEDICALLY_DISCHARGED) {
            admission.acceptSettlement(settlement.maQuyetToan(), settlement.ketQua());
            admissions.save(admission);
        }
    }

    @Override
    public void onDepositTopupRequired(DepositTopupRequiredCommand command) {
        validateEnvelope(command.maSuKien(), command.phienBan(), command.xayRaLuc(), command.maTuongQuan());
        require(command.maTaiKhoan(), "accountId");
        require(command.maDotNoiTru(), "admissionId");
        require(command.soDuHienTai(), "currentBalance");
        require(command.soTienYeuCau(), "requestedAmount");
        requireText(command.lyDo(), "reason");
        if (!processedEvents.tryClaim(command.maSuKien(), "deposit.topup.required")) {
            return;
        }
        requireAdmission(command.maDotNoiTru());
        eventStore.saveTopupRequest(new DepositTopupRequest(UUID.randomUUID(), command.maSuKien(),
                command.maDotNoiTru(), command.maTaiKhoan(), command.soDuHienTai(),
                command.soTienYeuCau(), command.lyDo(), command.xayRaLuc()));
    }

    @Override
    public void onExternalOrderFact(ExternalOrderFactCommand command) {
        validateEnvelope(command.maSuKien(), command.phienBan(), command.xayRaLuc(), command.maTuongQuan());
        require(command.maYLenhBenNgoai(), "externalOrderId");
        require(command.maDotNoiTru(), "admissionId");
        if (!processedEvents.tryClaim(command.maSuKien(), externalEventType(command))) {
            return;
        }
        Admission admission = requireAdmissionForUpdate(command.maDotNoiTru());
        ClinicalOrderType type = externalOrderType(command);
        ClinicalOrderReference reference = references.findByTypeAndExternalId(type, command.maYLenhBenNgoai())
                .orElseThrow(InpatientApplicationService::externalOrderMismatch);
        if (command instanceof LabResultFactCommand lab && !admission.patientId().equals(lab.maBenhNhan())) {
            throw externalOrderMismatch();
        }
        if (command instanceof PrescriptionFilledFactCommand prescription
                && !admission.patientId().equals(prescription.maBenhNhan())) {
            throw externalOrderMismatch();
        }
        ExternalOrderStatus nextStatus;
        String summary = null;
        Integer version = null;
        if (command instanceof LabResultFactCommand lab) {
            nextStatus = ExternalOrderStatus.COMPLETED;
            summary = lab.ketLuan();
            version = lab.phienBanKetQua();
        } else if (command instanceof PrescriptionFilledFactCommand) {
            nextStatus = ExternalOrderStatus.COMPLETED;
        } else if (command instanceof SurgeryReadyFactCommand) {
            nextStatus = ExternalOrderStatus.READY;
        } else if (command instanceof SurgeryCompletedFactCommand surgery) {
            nextStatus = ExternalOrderStatus.COMPLETED;
            summary = surgery.tomTatBienChung();
        } else if (command instanceof SurgeryCancelledFactCommand) {
            nextStatus = ExternalOrderStatus.CANCELLED;
        } else {
            throw new AdmissionRuleViolationException("INPATIENT_EXTERNAL_ORDER_MISMATCH",
                    "Unsupported external order event type");
        }
        boolean changed = reference.applyFact(command.maDotNoiTru(), command.maYLenhBenNgoai(),
                nextStatus, summary, version);
        if (!changed) {
            return;
        }
        references.save(reference);
        if (command instanceof SurgeryCompletedFactCommand surgery) {
            requireStatus(admission, AdmissionStatus.ADMITTED);
            String content = "Surgery completed: " + surgery.maKetQuaMo()
                    + (surgery.tomTatBienChung() == null ? "" : "; complications: " + surgery.tomTatBienChung());
            treatments.save(TreatmentEntry.create(admission.admissionId(), TreatmentEntryType.SURGERY,
                    content, admission.requestedBy(), surgery.hoanTatLuc()));
        } else if (command instanceof SurgeryCancelledFactCommand surgery) {
            requireStatus(admission, AdmissionStatus.ADMITTED);
            String content = "Surgery cancelled at " + surgery.giaiDoanHuy() + ": " + surgery.lyDo();
            treatments.save(TreatmentEntry.create(admission.admissionId(), TreatmentEntryType.SURGERY,
                    content, admission.requestedBy(), surgery.huyLuc()));
        }
    }

    private Admission createAdmissionModel(CreateAdmissionRequest request, String correlationId) {
        Optional<Admission> existing = admissions.findByAdmissionRequestId(request.maYeuCauNoiTru());
        if (existing.isPresent()) {
            return existing.get();
        }
        Admission admission = Admission.create(UUID.randomUUID(), request.maYeuCauNoiTru(),
                request.maBenhNhan(), request.maHoSoNguon(), request.nguoiYeuCau(),
                request.tomTatChanDoan(), request.thoiGianYeuCau(), request.maKhoa(),
                request.doUuTien(), request.capCuu());
        admissions.saveAndFlush(admission);
        Instant now = clock.instant();
        appendTransition(admission, null, request.nguoiYeuCau(), "Admission requested", correlationId, now);
        admission.markAwaitingBed();
        appendTransition(admission, AdmissionStatus.REQUESTED, request.nguoiYeuCau(), null, correlationId, now);
        return admissions.save(admission);
    }

    private Admission assignBed(UUID admissionId, UUID bedId, UUID assignedBy,
                                Instant now, String correlationId) {
        Admission admission = requireAdmissionForUpdate(admissionId);
        requireAssignEligible(admission);
        Optional<BedAssignment> activeAssignment = assignments.findActiveByAdmissionId(admissionId);
        if (activeAssignment.isPresent()) {
            if (activeAssignment.get().bedId().equals(bedId)) {
                return admission;
            }
            throw violation("INPATIENT_BED_UNAVAILABLE", "Admission already has an active bed assignment");
        }
        Bed bed = requireBedForUpdate(bedId);
        AdmissionStatus from = admission.status();
        bed.assign();
        beds.save(bed);
        assignments.save(BedAssignment.create(UUID.randomUUID(), admissionId, bedId, assignedBy, now));
        admission.applyBedAssignment(true, now);
        if (admission.markDepositRequested(now)) {
            appendDepositRequested(admission, correlationId, now);
        }
        admissions.save(admission);
        appendTransition(admission, from, assignedBy, null, correlationId, now);
        return admission;
    }

    private void appendDepositRequested(Admission admission, String correlationId, Instant now) {
        DepositSuggestion suggestion = depositSuggestions.suggest(admission.priority(), admission.departmentId());
        UUID admissionId = admission.admissionId();
        AdmissionDepositRequestedEvent payload = new AdmissionDepositRequestedEvent(admissionId,
                admission.patientId(), admission.departmentId(), "ADMISSION", admissionId,
                "ADMISSION_DEPOSIT", admissionId, suggestion.priceCode(), suggestion.amount(), suggestion.reason());
        appendEvent(admissionId, "admission.deposit.requested", correlationId, now, payload);
    }

    private void releaseActiveAssignment(Admission admission, UUID actorId, String reason,
                                        Instant at, boolean missingIsError) {
        Optional<BedAssignment> active = assignments.findActiveByAdmissionId(admission.admissionId());
        if (active.isEmpty()) {
            if (missingIsError) {
                throw violation("INPATIENT_ACTIVE_BED_REQUIRED", "Admission has no active bed assignment");
            }
            return;
        }
        BedAssignment assignment = active.get();
        Bed bed = requireBedForUpdate(assignment.bedId());
        assignment.release(actorId, reason, at);
        assignments.save(assignment);
        bed.release();
        beds.save(bed);
    }

    private void appendEvent(UUID aggregateId, String eventType, String correlationId,
                             Instant occurredAt, Object payload) {
        requireText(correlationId, "correlationId");
        outbox.append(aggregateId, new DomainEventEnvelope<>(UUID.randomUUID(), eventType,
                CONTRACT_VERSION, occurredAt, correlationId, PRODUCER, payload));
    }

    private void appendTransition(Admission admission, AdmissionStatus from, UUID actorId,
                                  String reason, String correlationId, Instant changedAt) {
        if (from == admission.status()) {
            return;
        }
        eventStore.appendHistory(new AdmissionStatusHistory(UUID.randomUUID(), admission.admissionId(),
                from, admission.status(), actorId, reason, correlationId, changedAt));
    }

    private AdmissionDTO admissionDto(Admission admission) {
        UUID activeBedId = assignments.findActiveByAdmissionId(admission.admissionId())
                .map(BedAssignment::bedId).orElse(null);
        return mapper.toAdmissionDto(admission, activeBedId,
                references.findByAdmissionId(admission.admissionId()));
    }

    private Admission requireAdmission(UUID admissionId) {
        return admissions.findById(admissionId).orElseThrow(() -> admissionNotFound(admissionId));
    }

    private Admission requireAdmissionForUpdate(UUID admissionId) {
        return admissions.findByIdForUpdate(admissionId).orElseThrow(() -> admissionNotFound(admissionId));
    }

    private Bed requireBedForUpdate(UUID bedId) {
        return beds.findByIdForUpdate(bedId).orElseThrow(() -> bedNotFound(bedId));
    }

    private static ResourceNotFoundException admissionNotFound(UUID id) {
        return new ResourceNotFoundException("INPATIENT_ADMISSION_NOT_FOUND", "Admission was not found: " + id);
    }

    private static ResourceNotFoundException bedNotFound(UUID id) {
        return new ResourceNotFoundException("INPATIENT_BED_NOT_FOUND", "Bed was not found: " + id);
    }

    private static void requireAssignEligible(Admission admission) {
        if (admission.status() == AdmissionStatus.MEDICALLY_DISCHARGED
                || admission.status() == AdmissionStatus.CLOSED
                || admission.status() == AdmissionStatus.CANCELLED) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION", "Admission cannot accept a bed assignment");
        }
    }

    private static boolean isPreAdmission(AdmissionStatus status) {
        return status == AdmissionStatus.REQUESTED || status == AdmissionStatus.AWAITING_BED
                || status == AdmissionStatus.AWAITING_DEPOSIT || status == AdmissionStatus.READY;
    }

    private static void requireStatus(Admission admission, AdmissionStatus expected) {
        if (admission.status() != expected) {
            throw violation("INPATIENT_INVALID_STATUS_TRANSITION",
                    "Operation requires " + expected + " admission status");
        }
    }

    private static EmergencyOverride toEmergencyOverride(EmergencyOverrideRequest request) {
        return request == null ? null : new EmergencyOverride(request.maPheDuyet(), request.nguoiDuyet(),
                request.vaiTroNguoiDuyet(), request.lyDo(), request.thoiGianDuyet());
    }

    private static CloseOverride toCloseOverride(CloseOverrideRequest request) {
        return request == null ? null : new CloseOverride(request.maPheDuyet(), request.loaiPheDuyet(),
                request.nguoiDuyet(), request.vaiTroNguoiDuyet(), request.lyDo(), request.thoiGianDuyet());
    }

    private static void validateEnvelope(UUID eventId, int version, Instant occurredAt, String correlationId) {
        require(eventId, "eventId");
        require(occurredAt, "occurredAt");
        requireText(correlationId, "correlationId");
        if (version != CONTRACT_VERSION) {
            throw violation("INPATIENT_EXTERNAL_ORDER_MISMATCH", "Unsupported event version: " + version);
        }
    }

    private static ClinicalOrderType externalOrderType(ExternalOrderFactCommand command) {
        if (command instanceof LabResultFactCommand) {
            return ClinicalOrderType.LAB_TEST;
        }
        if (command instanceof PrescriptionFilledFactCommand) {
            return ClinicalOrderType.PRESCRIPTION;
        }
        return ClinicalOrderType.SURGERY;
    }

    private static String externalEventType(ExternalOrderFactCommand command) {
        if (command instanceof LabResultFactCommand) return "lab.result.created";
        if (command instanceof PrescriptionFilledFactCommand) return "prescription.filled";
        if (command instanceof SurgeryReadyFactCommand) return "surgery.ready";
        if (command instanceof SurgeryCompletedFactCommand) return "surgery.completed";
        if (command instanceof SurgeryCancelledFactCommand) return "surgery.cancelled";
        return "unsupported";
    }

    private static AdmissionRuleViolationException externalOrderMismatch() {
        return violation("INPATIENT_EXTERNAL_ORDER_MISMATCH", "External order reference does not match");
    }

    private static AdmissionRuleViolationException violation(String code, String message) {
        return new AdmissionRuleViolationException(code, message);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw violation("INPATIENT_EXTERNAL_ORDER_MISMATCH", name + " is required");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw violation("INPATIENT_EXTERNAL_ORDER_MISMATCH", name + " is required");
        }
        return value;
    }
}
