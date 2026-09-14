package com.mediflow.pharmacy.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.event.StockAdjustedEvent;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockAdjustmentRepositoryPort;
import com.mediflow.pharmacy.domain.exception.DrugNotFoundException;
import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.StockAdjustment;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service cho feature danh mục thuốc và điều chỉnh tồn kho.
 *
 * <p>Lớp này chỉ điều phối out-port và domain model. Cơ chế khóa, JPA và outbox vẫn nằm ở
 * infrastructure adapter; vì vậy use-case test không cần khởi động Spring Data.</p>
 */
@Service
public class DrugApplicationService implements ManageDrugUseCase {

    private final DrugRepositoryPort drugRepository;
    private final StockReservationRepositoryPort reservationRepository;
    private final PharmacyEventPublisherPort eventPublisher;
    private final DrugDtoMapper drugDtoMapper;
    private final Clock clock;
    private final StockAdjustmentRepositoryPort adjustmentRepository;

    /**
     * Creates the drug feature service with durable stock audit persistence.
     *
     * @param drugRepository drug catalogue port
     * @param reservationRepository reservation lookup port
     * @param eventPublisher domain event port
     * @param drugDtoMapper response mapper
     * @param clock business clock
     * @param adjustmentRepository stock audit port
     */
    public DrugApplicationService(
            DrugRepositoryPort drugRepository,
            StockReservationRepositoryPort reservationRepository,
            PharmacyEventPublisherPort eventPublisher,
            DrugDtoMapper drugDtoMapper,
            Clock clock,
            StockAdjustmentRepositoryPort adjustmentRepository) {
        this.drugRepository = drugRepository;
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
        this.drugDtoMapper = drugDtoMapper;
        this.clock = clock;
        this.adjustmentRepository = adjustmentRepository;
    }

    /** Creates a drug after the domain validates price, stock, threshold and expiry. */
    @Override
    @Transactional
    public DrugDTO create(CreateDrugRequest request) {
        Drug drug = Drug.create(
                request.drugName(),
                request.activeIngredient(),
                request.unit(),
                request.price(),
                request.stockQuantity() == null ? 0 : request.stockQuantity(),
                request.expiryDate(),
                request.manufacturer(),
                request.lowStockThreshold() == null ? 10 : request.lowStockThreshold(),
                java.time.LocalDate.now(clock));
        return drugDtoMapper.toDto(drugRepository.save(drug));
    }

    /** Reads one drug without taking a write lock. */
    @Override
    @Transactional(readOnly = true)
    public DrugDTO getById(UUID id) {
        return drugRepository.findById(id)
                .map(drugDtoMapper::toDto)
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + id));
    }

    /** Searches the catalog through the framework-free page contract. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<DrugDTO> search(String keyword, PageQuery page) {
        return drugRepository.search(keyword, page).map(drugDtoMapper::toDto);
    }

    /**
     * Adjusts on-hand stock while preserving all RESERVED quantities.
     *
     * <p>The drug row is locked before the reserved snapshot is read. The adjustment event is
     * written through the publisher port in the same transaction as the stock mutation.</p>
     */
    @Override
    @Transactional
    public DrugDTO adjustStock(UUID id, AdjustStockRequest request) {
        return adjustStock(id, request, null, null);
    }

    /** Adjusts stock with actor and correlation data for audit. */
    @Override
    @Transactional
    public DrugDTO adjustStock(UUID id, AdjustStockRequest request, UUID actorId, String correlationId) {
        if (request == null || request.quantity() == null) {
            throw new DrugRuleException("DRUG_QUANTITY_INVALID", "Số lượng điều chỉnh là bắt buộc");
        }
        if (request.quantity() < 0 && (request.reason() == null || request.reason().isBlank())) {
            throw new DrugRuleException(
                    "DRUG_ADJUSTMENT_REASON_REQUIRED", "Lý do là bắt buộc khi giảm tồn kho");
        }

        Drug drug = drugRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DrugNotFoundException("Không tìm thấy thuốc id=" + id));
        long adjustedStock = (long) drug.getStockQuantity() + request.quantity();
        if (adjustedStock > Integer.MAX_VALUE || adjustedStock < Integer.MIN_VALUE) {
            throw new DrugRuleException(
                    "DRUG_QUANTITY_INVALID", "Điều chỉnh làm vượt giới hạn số lượng tồn kho");
        }

        long reservedStock = reservationRepository.findReservedByDrug(id).stream()
                .mapToLong(StockReservation::getQuantity)
                .sum();
        if (adjustedStock < reservedStock) {
            throw new StockReservationRuleException(
                    "STOCK_BELOW_RESERVED",
                    "Không thể giảm tồn kho thấp hơn lượng đang giữ: " + reservedStock);
        }

        int beforeStock = drug.getStockQuantity();
        Instant adjustedAt = Instant.now(clock);
        drug.adjustStock(request.quantity(), adjustedAt);
        Drug saved = drugRepository.save(drug);
        adjustmentRepository.save(StockAdjustment.create(
                id, beforeStock, request.quantity(), saved.getStockQuantity(),
                request.reason(), actorId, correlationId, adjustedAt));
        eventPublisher.publishStockAdjusted(new StockAdjustedEvent(
                UUID.randomUUID(),
                adjustedAt,
                correlationId,
                actorId,
                id,
                beforeStock,
                saved.getStockQuantity(),
                request.quantity(),
                normalizeReason(request.reason())));
        return drugDtoMapper.toDto(saved);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNSPECIFIED";
        }
        String normalized = reason.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }
}
