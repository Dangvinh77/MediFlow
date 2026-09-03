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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra orchestration của các use case quản lý danh mục thuốc. */
class PharmacyApplicationServiceDrugTest {

    private final DrugRepositoryPort drugRepo = mock(DrugRepositoryPort.class);
    private final DrugDtoMapper drugDtoMapper = mock(DrugDtoMapper.class);

    private final PharmacyApplicationService service = new PharmacyApplicationService(
            drugRepo,
            mock(PrescriptionRepositoryPort.class),
            mock(DispenseSlipRepositoryPort.class),
            mock(ProcessedEventPort.class),
            mock(StockReservationRepositoryPort.class),
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
        when(drugRepo.save(drug)).thenReturn(drug);
        when(drugDtoMapper.toDto(drug)).thenReturn(expected);

        DrugDTO result = service.adjustStock(drugId, new AdjustStockRequest(-25, "Kiểm kê"));

        assertThat(result).isSameAs(expected);
        assertThat(drug.getStockQuantity()).isEqualTo(75);
        verify(drugRepo).findByIdForUpdate(drugId);
        verify(drugRepo, never()).findById(drugId);
        verify(drugRepo).save(drug);
    }
}
