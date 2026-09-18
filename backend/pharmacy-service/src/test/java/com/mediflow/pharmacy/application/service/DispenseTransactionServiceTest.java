package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.event.PrescriptionFilledEvent;
import com.mediflow.pharmacy.application.event.StockLowEvent;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Exercises the dedicated production transaction used for BR-D4, BR-D9, BR-D10 and BR-D11. */
class DispenseTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    private final PrescriptionRepositoryPort prescriptionRepo = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort dispenseSlipRepo = mock(DispenseSlipRepositoryPort.class);
    private final DrugRepositoryPort drugRepo = mock(DrugRepositoryPort.class);
    private final StockReservationRepositoryPort reservationRepo = mock(StockReservationRepositoryPort.class);
    private final PharmacyEventPublisherPort eventPublisher = mock(PharmacyEventPublisherPort.class);
    private final DispenseDtoMapper mapper = mock(DispenseDtoMapper.class);
    private final DispenseTransactionService service = new DispenseTransactionService(
            prescriptionRepo, dispenseSlipRepo, drugRepo, reservationRepo, eventPublisher,
            mapper, Clock.fixed(NOW, ZoneOffset.UTC));

    private UUID prescriptionId;
    private UUID drugId;
    private Prescription prescription;
    private DispenseSlip slip;
    private Drug drug;
    private StockReservation reservation;

    /** Builds one internally consistent aggregate for each test. */
    @BeforeEach
    void setUp() {
        prescriptionId = UUID.randomUUID();
        drugId = UUID.randomUUID();
        PrescriptionLine line = PrescriptionLine.create(
                drugId, 2, new BigDecimal("500.00"), "Ngày 2 lần");
        prescription = Prescription.restore(
                prescriptionId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 9, 13), new BigDecimal("1000.00"), List.of(line),
                PrescriptionStatus.ACTIVE, null, null, null, NOW.minusSeconds(60), NOW.minusSeconds(60));
        slip = DispenseSlip.restore(
                UUID.randomUUID(), prescriptionId, DispenseStatus.PENDING,
                null, null, null, NOW.minusSeconds(60), NOW.minusSeconds(60));
        drug = Drug.restore(
                drugId, "Paracetamol", "Paracetamol", "viên", new BigDecimal("500.00"),
                3, LocalDate.of(2027, 1, 1), "MediFlow", 1, NOW.minusSeconds(60), NOW.minusSeconds(60));
        reservation = StockReservation.restore(
                UUID.randomUUID(), drugId, prescriptionId, 2, ReservationStatus.RESERVED,
                NOW.minusSeconds(60), NOW.plusSeconds(3600), NOW.minusSeconds(60), null, null, null);

        when(prescriptionRepo.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(reservationRepo.findByPrescription(prescriptionId)).thenReturn(List.of(reservation));
        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByPrescriptionForUpdate(prescriptionId, drugId))
                .thenReturn(Optional.of(reservation));
        when(dispenseSlipRepo.save(slip)).thenReturn(slip);
    }

    /** Successful dispense decrements stock once and preserves correlation on both events. */
    @Test
    void execute_validReservation_dispensesAndPublishesCorrelatedEvents() {
        UUID actorId = UUID.randomUUID();
        DispenseDTO expected = new DispenseDTO(
                slip.getDispenseId(), prescriptionId, DispenseStatus.DISPENSED, NOW, actorId, null);
        when(mapper.toDto(slip)).thenReturn(expected);

        DispenseDTO result = service.execute(prescriptionId, actorId, "dispense-correlation");

        assertThat(result).isEqualTo(expected);
        assertThat(drug.getStockQuantity()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.FULFILLED);
        ArgumentCaptor<StockLowEvent> stockCaptor = ArgumentCaptor.forClass(StockLowEvent.class);
        ArgumentCaptor<PrescriptionFilledEvent> filledCaptor =
                ArgumentCaptor.forClass(PrescriptionFilledEvent.class);
        verify(eventPublisher).publishStockLow(stockCaptor.capture());
        verify(eventPublisher).publishPrescriptionFilled(filledCaptor.capture());
        assertThat(stockCaptor.getValue().correlationId()).isEqualTo("dispense-correlation");
        assertThat(filledCaptor.getValue().correlationId()).isEqualTo("dispense-correlation");
        assertThat(filledCaptor.getValue().prescriptionId()).isEqualTo(prescriptionId);
        assertThat(filledCaptor.getValue().recordId()).isEqualTo(prescription.getRecordId());
    }

    /** A missing locked reservation fails before stock mutation or event publication. */
    @Test
    void execute_missingReservation_doesNotMutateStock() {
        when(reservationRepo.findReservedByPrescriptionForUpdate(prescriptionId, drugId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(prescriptionId, UUID.randomUUID(), "correlation"))
                .isInstanceOf(StockReservationRuleException.class);

        assertThat(drug.getStockQuantity()).isEqualTo(3);
        verify(drugRepo, never()).save(any());
        verify(eventPublisher, never()).publishPrescriptionFilled(any());
    }
}
