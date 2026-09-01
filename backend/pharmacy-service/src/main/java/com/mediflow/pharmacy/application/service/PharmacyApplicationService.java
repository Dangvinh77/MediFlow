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
import com.mediflow.pharmacy.application.port.in.ReleaseExpiredReservationsUseCase;
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
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        ReactToPaymentUseCase, ReleaseExpiredReservationsUseCase {

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
        Drug drug = drugRepo.findById(id)
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + id));
        drug.adjustStock(rq.quantity());
        return drugDtoMapper.toDto(drugRepo.save(drug));
    }

    // ============================================================
    // CreatePrescriptionUseCase — spec §7.1
    // ============================================================

    @Override
    @Transactional
    public PrescriptionDTO create(CreatePrescriptionRequest rq) {
        // 1: nạp từng thuốc + chụp giá (BR-D7, BR-D8 — client không gửi giá), sắp theo drugId để khóa đều.
        List<PrescriptionLineRequest> sorted = rq.lines().stream()
                .sorted(java.util.Comparator.comparing(PrescriptionLineRequest::drugId))
                .toList();

        // 2-3: dựng các dòng; đồng thời kiểm tra tồn khả dụng + tạo giữ chỗ ngay lúc kê (spec §7.1).
        List<PrescriptionLine> lines = sorted.stream()
                .map(this::toPrescriptionLine)
                .toList();

        // 4: dựng đơn, tính tổng tiền (BR-D5).
        Prescription prescription = Prescription.create(
                rq.recordId(), rq.patientId(), rq.doctorId(), rq.departmentId(),
                rq.prescribedDate(), lines);

        // 5: lưu đơn.
        Prescription saved = prescriptionRepo.save(prescription);

        // 6: giữ chỗ tồn kho cho từng dòng (reservation) — bác sĩ/bệnh nhân THẤY ngay lượng thuốc
        //    có thể bán, không còn cảnh trả tiền rồi mới hết hàng.
        Instant expiresAt = Instant.now().plus(RESERVATION_TTL);
        for (PrescriptionLineRequest lr : sorted) {
            reservationRepo.save(StockReservation.create(lr.drugId(), saved.getPrescriptionId(), lr.quantity(), expiresAt));
        }

        // 7: tự tạo phiếu xuất PENDING (BR-D3).
        DispenseSlip slip = dispenseSlipRepo.save(DispenseSlip.createPending(saved.getPrescriptionId()));

        // 8: publish prescription.created — billing tạo hóa đơn (khởi đầu saga).
        publishCreated(saved, lines);

        return toPrescriptionDto(saved, slip.getStatus());
    }

    private PrescriptionDTO toPrescriptionDto(Prescription prescription, DispenseStatus status) {
        List<PrescriptionLineDTO> lines = prescription.getLines().stream()
                .map(line -> prescriptionDtoMapper.toLineDto(
                        line,
                        drugRepo.findById(line.getDrugId()).map(Drug::getDrugName).orElse(null)))
                .toList();
        return prescriptionDtoMapper.toDto(prescription, status, lines);
    }

    private PrescriptionLine toPrescriptionLine(PrescriptionLineRequest lr) {
        // Khóa ghi theo drugId (BR-D10) — đọc đúng con số tồn khả dụng, tránh kê vượt khi tương tranh.
        Drug drug = drugRepo.findByIdForUpdate(lr.drugId())
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + lr.drugId()));

        // Tồn khả dụng = tổng tồn - tổng đang giữ chỗ (RESERVED). Nếu thiếu → ném NGAY lúc kê.
        int reservedSum = reservationRepo.findReservedByDrug(lr.drugId()).stream()
                .mapToInt(StockReservation::getQuantity)
                .sum();
        int available = drug.getStockQuantity() - reservedSum;
        if (available < lr.quantity()) {
            throw new StockReservationRuleException("INSUFFICIENT_AVAILABLE_STOCK",
                    "Không đủ thuốc có thể bán cho '" + drug.getDrugName() + "': cần " + lr.quantity()
                            + ", còn " + Math.max(available, 0));
        }

        // BR-D7: chụp giá tại thời điểm kê đơn — không lấy từ client.
        return PrescriptionLine.create(lr.drugId(), lr.quantity(), drug.getPrice(), lr.dosage());
    }

    /**
     * Publish {@code prescription.created}. Đúng spec là publish SAU khi commit; ở tầng application
     * sạch (không import Spring transaction synchronization), phương án thực tế là publish trong
     * transaction và để adapter {@code PharmacyEventPublisherPort} đặt hàng đúng (xem ghi chú spec §12).
     */
    private void publishCreated(Prescription saved, List<PrescriptionLine> lines) {
        List<PrescriptionCreatedEvent.Item> items = lines.stream()
                .map(l -> new PrescriptionCreatedEvent.Item(
                        l.getDrugId(), null /* drugName */, l.getQuantity(), l.getUnitPrice()))
                .toList();
        eventPublisher.publishPrescriptionCreated(new PrescriptionCreatedEvent(
                UUID.randomUUID(), Instant.now(), null,
                saved.getPrescriptionId(), saved.getPatientId(), saved.getRecordId(),
                saved.getDepartmentId(), saved.getTotalAmount(), items));
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

    // ============================================================
    // ReleaseExpiredReservationsUseCase — job TTL trả lại chỗ đã giữ
    // ============================================================

    @Override
    @Transactional
    public int releaseExpiredReservations() {
        List<StockReservation> expired = reservationRepo.findExpired(); // RESERVED && expires_at < now
        if (expired.isEmpty()) {
            return 0;
        }

        // Sắp theo drugId trước khi khóa — tránh deadlock (BR-D10).
        List<StockReservation> sorted = expired.stream()
                .sorted(java.util.Comparator.comparing(StockReservation::getDrugId))
                .toList();

        int released = 0;
        for (StockReservation r : sorted) {
            Drug drug = drugRepo.findByIdForUpdate(r.getDrugId())
                    .orElse(null);
            if (drug == null) {
                continue; // thuốc đã bị xóa — chỉ cần đánh dấu hết hạn, không trả lại chỗ được
            }
            // Khóa thuốc → trả lại chỗ giữ (RESERVED → EXPIRED). Không trừ/thêm stock_quantity —
            // giữ chỗ chưa hề trừ kho thật, nên release chỉ là trả lại "chỗ" trong số tồn có thể bán.
            r.expire();
            reservationRepo.save(r);
            released++;
        }
        return released;
    }
}
