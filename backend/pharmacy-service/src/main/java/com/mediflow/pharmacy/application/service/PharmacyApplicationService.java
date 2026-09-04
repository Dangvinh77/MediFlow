package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionLineDTO;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.mapper.PrescriptionDtoMapper;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import com.mediflow.pharmacy.application.port.in.ReactToPaymentUseCase;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DispenseNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionNotFoundException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Application service của pharmacy — hiện thực 5 in-port nghiệp vụ.
 *
 * <p>Đây là nơi "điều phối": nhận lệnh từ in-port, hỏi out-port, gọi domain model, phát event.
 * Không import JPA/AMQP/HTTP (chỉ dùng @Service/@Transactional — chấp nhận từ docs/ai/04).
 *
 * <p>Saga: kê đơn → prescription.created → billing tạo hóa đơn → payment.completed → dispense.
 * Nhánh bù trừ khi xuất thất bại: phiếu FAILED trong transaction riêng (REQUIRES_NEW) + event
 * prescription.dispense.failed để billing hoàn/hủy hóa đơn.
 */
@Service
public class PharmacyApplicationService implements
        ManageDrugUseCase, CreatePrescriptionUseCase, DispensePrescriptionUseCase,
        ReactToPaymentUseCase {

    /** id "hệ thống" dùng khi xuất thuốc do consumer payment.completed kích hoạt (không phải dược sĩ). */
    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** Routing key được ghi vào sổ idempotency sau khi xử lý thanh toán thành công. */
    private static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";

    /** Thời gian một dòng giữ chỗ tồn kho có hiệu lực — hết hạn thì job TTL trả lại chỗ. */
    private static final java.time.Duration RESERVATION_TTL = java.time.Duration.ofHours(24);

    private final DrugRepositoryPort drugRepo;
    private final PrescriptionRepositoryPort prescriptionRepo;
    private final DispenseSlipRepositoryPort dispenseSlipRepo;
    private final ProcessedEventPort processedEventPort;
    private final StockReservationRepositoryPort reservationRepo;
    private final PharmacyEventPublisherPort eventPublisher;
    private final DrugDtoMapper drugDtoMapper;
    private final PrescriptionDtoMapper prescriptionDtoMapper;
    private final DispenseDtoMapper dispenseDtoMapper;

    /**
     * Self-reference để gọi {@link #markDispenseFailed(UUID, UUID, String)} qua proxy —
     * bắt buộc để @Transactional(REQUIRES_NEW) thực sự mở transaction mới khi transaction chính
     * chuẩn bị rollback (spec §7.2 bước 6). Dùng @Lazy tránh vòng phụ thuộc.
     */
    private final PharmacyApplicationService self;

    public PharmacyApplicationService(DrugRepositoryPort drugRepo, PrescriptionRepositoryPort prescriptionRepo,
                                      DispenseSlipRepositoryPort dispenseSlipRepo, ProcessedEventPort processedEventPort,
                                      StockReservationRepositoryPort reservationRepo,
                                      PharmacyEventPublisherPort eventPublisher, DrugDtoMapper drugDtoMapper,
                                      PrescriptionDtoMapper prescriptionDtoMapper, DispenseDtoMapper dispenseDtoMapper,
                                      @Lazy PharmacyApplicationService self) {
        this.drugRepo = drugRepo;
        this.prescriptionRepo = prescriptionRepo;
        this.dispenseSlipRepo = dispenseSlipRepo;
        this.processedEventPort = processedEventPort;
        this.reservationRepo = reservationRepo;
        this.eventPublisher = eventPublisher;
        this.drugDtoMapper = drugDtoMapper;
        this.prescriptionDtoMapper = prescriptionDtoMapper;
        this.dispenseDtoMapper = dispenseDtoMapper;
        this.self = self;
    }

    // ============================================================
    // ManageDrugUseCase
    // ============================================================

    @Override
    @Transactional
    public DrugDTO create(CreateDrugRequest rq) {
        Drug drug = Drug.create(
                rq.drugName(), rq.activeIngredient(), rq.unit(), rq.price(),
                rq.stockQuantity() == null ? 0 : rq.stockQuantity(),
                rq.expiryDate(), rq.manufacturer(),
                rq.lowStockThreshold() == null ? 10 : rq.lowStockThreshold());
        return drugDtoMapper.toDto(drugRepo.save(drug));
    }

    @Override
    @Transactional(readOnly = true)
    public DrugDTO getById(UUID id) {
        return drugRepo.findById(id)
                .map(drugDtoMapper::toDto)
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DrugDTO> search(String keyword, PageQuery page) {
        return drugRepo.search(keyword, page).map(drugDtoMapper::toDto);
    }

    @Override
    @Transactional
    public DrugDTO adjustStock(UUID id, AdjustStockRequest rq) {
        Drug drug = drugRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + id));
        drug.adjustStock(rq.quantity());
        return drugDtoMapper.toDto(drugRepo.save(drug));
    }

    // ============================================================
    // CreatePrescriptionUseCase — spec §7.1
    // ============================================================

  @Override
@Transactional
public PrescriptionDTO create(CreatePrescriptionRequest request) {
    // 1. Thất bại sớm nếu một thuốc xuất hiện nhiều lần.
    validateNoDuplicateDrugIds(request.lines());

    // 2. Khóa theo thứ tự ổn định để giảm nguy cơ deadlock.
    List<PrescriptionLineRequest> sortedRequests =
            request.lines().stream()
                    .sorted(java.util.Comparator.comparing(
                            PrescriptionLineRequest::drugId))
                    .toList();

    // 3. Khóa thuốc, kiểm tra tồn khả dụng, chụp giá và tên.
    List<ResolvedPrescriptionLine> resolvedLines =
            sortedRequests.stream()
                    .map(this::resolvePrescriptionLine)
                    .toList();

    List<PrescriptionLine> prescriptionLines =
            resolvedLines.stream()
                    .map(ResolvedPrescriptionLine::line)
                    .toList();

    // 4. Domain tính lineTotal và totalAmount từ giá server đã chụp.
    Prescription prescription = Prescription.create(
            request.recordId(),
            request.patientId(),
            request.doctorId(),
            request.departmentId(),
            request.prescribedDate(),
            prescriptionLines);

    // 5. Lưu aggregate Prescription + PrescriptionLine.
    Prescription savedPrescription =
            prescriptionRepo.save(prescription);

    // 6. Một thời điểm hết hạn thống nhất cho toàn bộ đơn.
    Instant reservationExpiresAt =
            Instant.now().plus(RESERVATION_TTL);

    for (ResolvedPrescriptionLine resolved : resolvedLines) {
        PrescriptionLine line = resolved.line();

        StockReservation reservation = StockReservation.create(
                line.getDrugId(),
                savedPrescription.getPrescriptionId(),
                line.getQuantity(),
                reservationExpiresAt);

        reservationRepo.save(reservation);
    }

    // 7. Mỗi đơn luôn có đúng một phiếu xuất PENDING.
    DispenseSlip pendingSlip = dispenseSlipRepo.save(
            DispenseSlip.createPending(
                    savedPrescription.getPrescriptionId()));

    // 8. Lưu tên thuốc để dùng cho DTO và event.
    Map<UUID, String> drugNames = new LinkedHashMap<>();

    for (ResolvedPrescriptionLine resolved : resolvedLines) {
        drugNames.put(
                resolved.line().getDrugId(),
                resolved.drugName());
    }

    // Adapter sẽ trì hoãn việc gửi RabbitMQ tới sau commit.
    publishCreated(savedPrescription, drugNames);

    return toPrescriptionDto(
            savedPrescription,
            pendingSlip.getStatus(),
            drugNames);
}

  /**
 * Chuyển đơn thuốc thành DTO bằng tên thuốc đã được giải quyết trong use case.
 */
private PrescriptionDTO toPrescriptionDto(
        Prescription prescription,
        DispenseStatus status,
        Map<UUID, String> drugNames) {

    List<PrescriptionLineDTO> lineDtos =
            prescription.getLines().stream()
                    .map(line -> prescriptionDtoMapper.toLineDto(
                            line,
                            drugNames.get(line.getDrugId())))
                    .toList();

    return prescriptionDtoMapper.toDto(
            prescription,
            status,
            lineDtos);
}


    /**
 * Kết quả nội bộ sau khi đã khóa thuốc, kiểm tra tồn và chụp giá.
 *
 * <p>Record này không đi qua HTTP hoặc persistence. Nó chỉ giữ tên thuốc cạnh
 * dòng đơn để tạo DTO và event mà không cần truy vấn database lần nữa.</p>
 */
private record ResolvedPrescriptionLine(
        PrescriptionLine line,
        String drugName
) {
}

   /**
 * Khóa thuốc, kiểm tra tồn có thể bán và chụp giá hiện tại.
 *
 * @param request dòng thuốc từ request
 * @return dòng đơn đã xác định giá, kèm tên thuốc
 */
private ResolvedPrescriptionLine resolvePrescriptionLine(
        PrescriptionLineRequest request) {

    Drug drug = drugRepo.findByIdForUpdate(request.drugId())
            .orElseThrow(() -> new DrugNotFoundException(
                    "Không tìm thấy thuốc id=" + request.drugId()));

    int reservedQuantity = reservationRepo
            .findReservedByDrug(request.drugId())
            .stream()
            .mapToInt(StockReservation::getQuantity)
            .sum();

    int availableQuantity =
            drug.getStockQuantity() - reservedQuantity;

    if (availableQuantity < request.quantity()) {
        throw new StockReservationRuleException(
                "INSUFFICIENT_AVAILABLE_STOCK",
                "Không đủ thuốc có thể bán cho '"
                        + drug.getDrugName()
                        + "': cần " + request.quantity()
                        + ", còn " + Math.max(availableQuantity, 0));
    }

    PrescriptionLine line = PrescriptionLine.create(
            request.drugId(),
            request.quantity(),
            drug.getPrice(),
            request.dosage());

    return new ResolvedPrescriptionLine(
            line,
            drug.getDrugName());
}

   /**
 * Tạo yêu cầu phát event cho đơn đã lưu.
 *
 * <p>Việc gửi RabbitMQ thật được adapter trì hoãn tới sau khi transaction
 * database commit thành công.</p>
 */
private void publishCreated(
        Prescription prescription,
        Map<UUID, String> drugNames) {

    List<PrescriptionCreatedEvent.Item> items =
            prescription.getLines().stream()
                    .map(line -> new PrescriptionCreatedEvent.Item(
                            line.getDrugId(),
                            drugNames.get(line.getDrugId()),
                            line.getQuantity(),
                            line.getUnitPrice()))
                    .toList();

    PrescriptionCreatedEvent event =
            new PrescriptionCreatedEvent(
                    UUID.randomUUID(),
                    Instant.now(),
                    null,
                    prescription.getPrescriptionId(),
                    prescription.getPatientId(),
                    prescription.getRecordId(),
                    prescription.getDepartmentId(),
                    prescription.getTotalAmount(),
                    items);

    eventPublisher.publishPrescriptionCreated(event);
}

    /**
 * Bảo đảm mỗi thuốc chỉ xuất hiện một lần trong đơn.
 *
 * <p>Kiểm tra được thực hiện trước khi khóa thuốc hoặc ghi database để request
 * không hợp lệ thất bại sớm và không tạo công việc thừa.</p>
 *
 * @param requests các dòng thuốc do client gửi
 * @throws PrescriptionRuleException nếu một drugId xuất hiện nhiều hơn một lần
 */
private void validateNoDuplicateDrugIds(
        List<PrescriptionLineRequest> requests) {

    Set<UUID> seenDrugIds = new HashSet<>();

    for (PrescriptionLineRequest request : requests) {
        if (!seenDrugIds.add(request.drugId())) {
            throw new PrescriptionRuleException(
                    "PRESCRIPTION_DUPLICATE_DRUG",
                    "Thuốc id=" + request.drugId()
                            + " xuất hiện nhiều hơn một lần trong đơn");
        }
    }
}

    // ============================================================
    // DispensePrescriptionUseCase — spec §7.2
    // ============================================================

    @Override
    @Transactional
    public DispenseDTO dispense(UUID prescriptionId, UUID dispensedBy) {
        // 1: phiếu theo đơn — không có thì DISPENSE_NOT_FOUND.
        DispenseSlip slip = dispenseSlipRepo.findByPrescription(prescriptionId)
                .orElseThrow(() -> new DispenseNotFoundException("Không tìm thấy phiếu xuất của đơn id=" + prescriptionId));

        // 2: chống xuất 2 lần (BR-D9) — payment.completed gửi lại không được xuất thêm.
        if (!slip.isPending()) {
            throw new com.mediflow.pharmacy.domain.exception.DispenseRuleException(
                    "DISPENSE_ALREADY_DONE", "Phiếu không còn ở trạng thái chờ xuất");
        }

        try {
            // 3-5: nạp đơn + các dòng, sắp xếp theo drugId, khóa ghi từng dòng, chuyển giữ chỗ → xuất thật.
            Prescription prescription = prescriptionRepo.findById(prescriptionId)
                    .orElseThrow(() -> new PrescriptionNotFoundException("Không tìm thấy đơn id=" + prescriptionId));

            List<UUID> sortedDrugIds = prescription.getLines().stream()
                    .map(PrescriptionLine::getDrugId)
                    .sorted()
                    .toList();

            // Khóa ghi + trừ kho + xác nhận giữ chỗ, trong một lần duy nhất cho mỗi thuốc (tránh đọc 2 lần).
            Map<UUID, Drug> locked = new LinkedHashMap<>();
            for (UUID drugId : sortedDrugIds) {
                Drug drug = drugRepo.findByIdForUpdate(drugId)
                        .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + drugId));
                int qty = prescription.getLines().stream()
                        .filter(l -> l.getDrugId().equals(drugId))
                        .map(PrescriptionLine::getQuantity)
                        .findFirst().orElse(0);

                // Giữ chỗ phải còn RESERVED — nếu mất (data inconsistency) thì đây là lỗi hệ thống,
                // rơi vào nhánh bù trừ. Kê đơn đã đảm bảo đủ hàng, nên "hết hàng" không còn là
                // đường ray nghiệp vụ chính ở bước này.
                StockReservation reservation = reservationRepo
                        .findReservedByPrescriptionForUpdate(prescriptionId, drugId)
                        .orElseThrow(() -> new StockReservationRuleException("RESERVATION_MISSING",
                                "Không tìm thấy giữ chỗ của thuốc id=" + drugId + " cho đơn " + prescriptionId));
                if (!reservation.isReserved()) {
                    throw new StockReservationRuleException("RESERVATION_INVALID_TRANSITION",
                            "Giữ chỗ của thuốc id=" + drugId + " không còn hiệu lực");
                }

                drug.dispenseStock(qty); // trừ kho thật (BR-D4); BR-D2 (hết hạn) vẫn được kiểm tra lúc xuất
                reservation.markFulfilled(); // RESERVED → FULFILLED — giữ chỗ đã hoàn thành
                reservationRepo.save(reservation);

                locked.put(drugId, drug);
            }

            // 7: lưu từng thuốc đã trừ kho (trong transaction — sẽ rollback nếu bước sau lỗi).
            locked.values().forEach(drugRepo::save);

            // 7: đánh dấu phiếu DISPENSED và lưu.
            slip.markDispensed(dispensedBy, Instant.now());
            DispenseSlip saved = dispenseSlipRepo.save(slip);

            // 8-9: stock.low cho thuốc chạm ngưỡng (BR-D11) + filled (kết thúc thành công).
            publishStockLowIfNeeded(sortedDrugIds);
            publishFilled(saved, prescription);

            return dispenseDtoMapper.toDto(saved);
        } catch (RuntimeException ex) {
            // 6: nhánh bù trừ — mọi lỗi (hết hàng, hết hạn, không đủ) → phiếu FAILED trong
            // transaction RIÊNG (REQUIRES_NEW) + event prescription.dispense.failed để billing bù trừ (BR-D6, BR-D12).
            self.markDispenseFailed(prescriptionId, dispensedBy, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Ghi phiếu FAILED trong transaction mới — tách riêng để transaction trừ kho đã rollback
     * không cuốn theo việc ghi trạng thái thất bại. REQUIRES_NEW phải đi qua proxy Spring,
     * nên gọi qua {@code self} (xem @Lazy self).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispenseFailed(UUID prescriptionId, UUID dispensedBy, String reason) {
        dispenseSlipRepo.findByPrescription(prescriptionId)
                .ifPresent(slip -> {
                    slip.markFailed(reason == null || reason.isBlank() ? "Xuất thuốc thất bại" : reason);
                    dispenseSlipRepo.save(slip);
                });
        // BR-D6: phát event bù trừ để billing hoàn/hủy hóa đơn. failedItems rỗng khi lỗi
        // không gắn với thuốc cụ thể (vd lỗi hạ tầng); khi mất giữ chỗ thì liệt kê từng thuốc.
        eventPublisher.publishPrescriptionDispenseFailed(new PrescriptionDispenseFailedEvent(
                UUID.randomUUID(), Instant.now(), null,
                prescriptionId, null /* invoiceId — chưa biết ở pharmacy */, null /* patientId */, reason,
                List.of()));
    }

    private void publishStockLowIfNeeded(List<UUID> drugIds) {
        for (UUID drugId : drugIds) {
            Drug drug = drugRepo.findById(drugId).orElse(null);
            if (drug != null && drug.belowLowStockThreshold()) { // BR-D11
                eventPublisher.publishStockLow(new StockLowEvent(
                        UUID.randomUUID(), Instant.now(), null,
                        drugId, drug.getDrugName(), drug.getStockQuantity(), drug.getLowStockThreshold()));
            }
        }
    }

    private void publishFilled(DispenseSlip slip, Prescription prescription) {
        List<PrescriptionFilledEvent.DispensedItem> items = prescription.getLines().stream()
                .map(l -> new PrescriptionFilledEvent.DispensedItem(
                        l.getDrugId(),
                        drugRepo.findById(l.getDrugId()).map(Drug::getDrugName).orElse(null),
                        l.getQuantity()))
                .toList();
        eventPublisher.publishPrescriptionFilled(new PrescriptionFilledEvent(
                UUID.randomUUID(), Instant.now(), null,
                slip.getPrescriptionId(), prescription.getPatientId(),
                prescription.getDepartmentId(), prescription.getTotalAmount(), items));
    }

    // ============================================================
    // ReactToPaymentUseCase — spec §7.3
    // ============================================================

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedCommand command) {
        // 1: chống trùng theo eventId thật — RabbitMQ có thể gửi lại cùng một message (BR-D9).
        if (processedEventPort.alreadyProcessed(command.eventId())) {
            return;
        }

        // 2: gọi lại dispense với người thực hiện là hệ thống.
        dispense(command.prescriptionId(), SYSTEM_USER);

        // 3: đánh dấu đã xử lý trong cùng transaction.
        processedEventPort.markProcessed(command.eventId(), PAYMENT_COMPLETED_ROUTING_KEY);
    }

}
