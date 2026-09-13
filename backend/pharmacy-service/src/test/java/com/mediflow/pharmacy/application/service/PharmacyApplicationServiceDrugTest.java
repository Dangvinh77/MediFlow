package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.mapper.PrescriptionDtoMapper;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import com.mediflow.pharmacy.domain.exception.DrugRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra orchestration của các use case quản lý danh mục thuốc. */
class PharmacyApplicationServiceDrugTest {

    private final DrugRepositoryPort drugRepo = mock(DrugRepositoryPort.class);
    private final StockReservationRepositoryPort reservationRepo = mock(StockReservationRepositoryPort.class);
    private final DrugDtoMapper drugDtoMapper = mock(DrugDtoMapper.class);

    private final PharmacyApplicationService service = new PharmacyApplicationService(
            drugRepo,
            mock(PrescriptionRepositoryPort.class),
            mock(DispenseSlipRepositoryPort.class),
            mock(ProcessedEventPort.class),
            reservationRepo,
            mock(PharmacyEventPublisherPort.class),
            drugDtoMapper,
            mock(PrescriptionDtoMapper.class),
            mock(DispenseDtoMapper.class),
            mock(PharmacyApplicationService.class));

    /** Điều chỉnh tồn kho phải đọc bằng khóa ghi trước khi thay đổi số lượng. */
    @Test
    void adjustStock_usesLockedReadBeforeSaving() {
        UUID drugId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Drug drug = Drug.restore(
                drugId,
                "Paracetamol 500mg",
                "Paracetamol",
                "viên",
                new BigDecimal("1200.00"),
                100,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                20,
                createdAt,
                createdAt);
        DrugDTO expected = mock(DrugDTO.class);

        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByDrug(drugId)).thenReturn(List.of());
        when(drugRepo.save(drug)).thenReturn(drug);
        when(drugDtoMapper.toDto(drug)).thenReturn(expected);

        DrugDTO result = service.adjustStock(drugId, new AdjustStockRequest(-25, "Kiểm kê"));

        assertThat(result).isSameAs(expected);
        assertThat(drug.getStockQuantity()).isEqualTo(75);
        verify(drugRepo).findByIdForUpdate(drugId);
        verify(drugRepo, never()).findById(drugId);
        verify(drugRepo).save(drug);
    }

    /** Không được điều chỉnh tồn xuống thấp hơn tổng lượng reservation đang giữ. */
    @Test
    void adjustStock_belowReservedQuantity_rejectedWithoutSaving() {
        UUID drugId = UUID.randomUUID();
        Drug drug = Drug.restore(
                drugId, "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"),
                10, LocalDate.now().plusYears(1), "MediFlow", 2,
                Instant.now(), Instant.now());
        StockReservation reservation = StockReservation.restore(
                UUID.randomUUID(), drugId, UUID.randomUUID(), 8, ReservationStatus.RESERVED,
                Instant.now(), Instant.now().plusSeconds(3600), null);
        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByDrug(drugId)).thenReturn(List.of(reservation));

        assertThatThrownBy(() -> service.adjustStock(drugId, new AdjustStockRequest(-3, "Kiểm kê")))
                .isInstanceOf(StockReservationRuleException.class)
                .hasMessageContaining("lượng đang giữ");

        verify(drugRepo, never()).save(drug);
        assertThat(drug.getStockQuantity()).isEqualTo(10);
    }

    /** Điều chỉnh vượt giới hạn int phải bị chặn trước khi domain thực hiện phép cộng. */
    @Test
    void adjustStock_integerOverflow_rejectedWithoutSaving() {
        UUID drugId = UUID.randomUUID();
        Drug drug = Drug.restore(
                drugId, "Paracetamol", "Paracetamol", "viên", new BigDecimal("1200.00"),
                Integer.MAX_VALUE, LocalDate.now().plusYears(1), "MediFlow", 2,
                Instant.now(), Instant.now());
        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByDrug(drugId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.adjustStock(drugId, new AdjustStockRequest(1, "Kiểm kê")))
                .isInstanceOf(DrugRuleException.class)
                .hasMessageContaining("giới hạn");

        verify(drugRepo, never()).save(drug);
    }
}
