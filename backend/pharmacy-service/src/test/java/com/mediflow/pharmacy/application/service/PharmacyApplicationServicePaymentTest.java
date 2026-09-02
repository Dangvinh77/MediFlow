package com.mediflow.pharmacy.application.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.PaymentCompletedCommand;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.mapper.PrescriptionDtoMapper;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;

/**
 * Kiểm tra application flow khi nhận event thanh toán từ billing-service.
 */
class PharmacyApplicationServicePaymentTest {

    private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final DrugRepositoryPort drugRepo = mock(DrugRepositoryPort.class);
    private final PrescriptionRepositoryPort prescriptionRepo = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort dispenseSlipRepo = mock(DispenseSlipRepositoryPort.class);
    private final ProcessedEventPort processedEventPort = mock(ProcessedEventPort.class);
    private final StockReservationRepositoryPort reservationRepo = mock(StockReservationRepositoryPort.class);
    private final PharmacyEventPublisherPort eventPublisher = mock(PharmacyEventPublisherPort.class);
    private final DrugDtoMapper drugDtoMapper = mock(DrugDtoMapper.class);
    private final PrescriptionDtoMapper prescriptionDtoMapper = mock(PrescriptionDtoMapper.class);
    private final DispenseDtoMapper dispenseDtoMapper = mock(DispenseDtoMapper.class);
    private final PharmacyApplicationService self = mock(PharmacyApplicationService.class);

    private PharmacyApplicationService service;

    /** Tạo spy để chỉ cô lập use case dispense, còn idempotency chạy bằng code thật. */
    @BeforeEach
    void setUp() {
        service = spy(new PharmacyApplicationService(
                drugRepo,
                prescriptionRepo,
                dispenseSlipRepo,
                processedEventPort,
                reservationRepo,
                eventPublisher,
                drugDtoMapper,
                prescriptionDtoMapper,
                dispenseDtoMapper,
                self
        ));
    }

    /**
     * Hai lần giao cùng eventId chỉ được kích hoạt xuất thuốc một lần.
     */
    @Test
    void onPaymentCompleted_sameEventTwice_dispensesOnce() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        PaymentCompletedCommand command = command(eventId, prescriptionId);
        DispenseDTO result = new DispenseDTO(
                UUID.randomUUID(), prescriptionId, DispenseStatus.DISPENSED,
                Instant.parse("2026-08-31T03:01:00Z"), SYSTEM_USER, null);

        when(processedEventPort.alreadyProcessed(eventId))
                .thenReturn(false)
                .thenReturn(true);
        doReturn(result).when(service).dispense(prescriptionId, SYSTEM_USER);

        service.onPaymentCompleted(command);
        service.onPaymentCompleted(command);

        verify(service, times(1)).dispense(prescriptionId, SYSTEM_USER);
        verify(processedEventPort, times(1)).markProcessed(eventId, "payment.completed");
    }

    private PaymentCompletedCommand command(UUID eventId, UUID prescriptionId) {
        return new PaymentCompletedCommand(
                eventId,
                Instant.parse("2026-08-31T03:00:00Z"),
                "payment-flow-001",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                prescriptionId,
                new BigDecimal("125000.00"),
                "CASH"
        );
    }
}
