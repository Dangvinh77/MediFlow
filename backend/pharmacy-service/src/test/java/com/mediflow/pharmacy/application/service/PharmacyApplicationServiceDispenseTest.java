package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

/** Kiểm tra các invariant cấp thuốc trong transaction nghiệp vụ. */
class PharmacyApplicationServiceDispenseTest {

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

    /** Khởi tạo service với các out-port cô lập để chỉ kiểm tra orchestration. */
    @BeforeEach
    void setUp() {
        service = new PharmacyApplicationService(
                drugRepo, prescriptionRepo, dispenseSlipRepo, processedEventPort,
                reservationRepo, eventPublisher, drugDtoMapper, prescriptionDtoMapper,
                dispenseDtoMapper, self);
    }

    /** Cấp thành công phải cập nhật cả phiếu, reservation, đơn và trừ kho đúng một lần. */
    @Test
    void dispense_success_marksPrescriptionFulfilledAndReservationFulfilled() {
        UUID prescriptionId = UUID.randomUUID();
        UUID drugId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, drugId);
        DispenseSlip slip = DispenseSlip.restore(
                UUID.randomUUID(), prescriptionId, DispenseStatus.PENDING,
                null, null, null, Instant.now(), Instant.now());
        Drug drug = drug(drugId, 10);
        StockReservation reservation = reservation(prescriptionId, drugId, 2, Instant.now().plusSeconds(3600));
        DispenseDTO dto = new DispenseDTO(slip.getDispenseId(), prescriptionId,
                DispenseStatus.DISPENSED, Instant.now(), actorId, null);

        when(dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(prescriptionRepo.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByPrescriptionForUpdate(prescriptionId, drugId))
                .thenReturn(Optional.of(reservation));
        when(dispenseSlipRepo.save(any(DispenseSlip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(prescriptionRepo.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepo.save(any(StockReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(drugRepo.save(any(Drug.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(drugRepo.findById(drugId)).thenReturn(Optional.of(drug));
        when(dispenseDtoMapper.toDto(slip)).thenReturn(dto);

        DispenseDTO result = service.dispenseInTransaction(prescriptionId, actorId);

        assertThat(result.status()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.FULFILLED);
        assertThat(slip.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(drug.getStockQuantity()).isEqualTo(8);
        verify(prescriptionRepo).save(prescription);
    }

    /** Reservation hết TTL phải chặn cấp trước khi trừ tồn vật lý. */
    @Test
    void dispense_expiredReservation_doesNotReduceStock() {
        UUID prescriptionId = UUID.randomUUID();
        UUID drugId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, drugId);
        DispenseSlip slip = DispenseSlip.restore(
                UUID.randomUUID(), prescriptionId, DispenseStatus.PENDING,
                null, null, null, Instant.now(), Instant.now());
        Drug drug = drug(drugId, 10);
        StockReservation reservation = reservation(prescriptionId, drugId, 2, Instant.now().minusSeconds(1));

        when(dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(prescriptionRepo.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(drugRepo.findByIdForUpdate(drugId)).thenReturn(Optional.of(drug));
        when(reservationRepo.findReservedByPrescriptionForUpdate(prescriptionId, drugId))
                .thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.dispenseInTransaction(prescriptionId, UUID.randomUUID()))
                .isInstanceOfSatisfying(StockReservationRuleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("RESERVATION_EXPIRED"));
        assertThat(drug.getStockQuantity()).isEqualTo(10);
        assertThat(slip.getStatus()).isEqualTo(DispenseStatus.PENDING);
    }

    /** Gọi lại sau khi đã cấp phải trả phiếu hiện hữu và không đụng tới tồn kho. */
    @Test
    void dispense_alreadyDispensed_returnsExistingSlipIdempotently() {
        UUID prescriptionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        prescription.markFulfilled(Instant.now());
        DispenseSlip slip = DispenseSlip.restore(
                UUID.randomUUID(), prescriptionId, DispenseStatus.DISPENSED,
                Instant.now(), actorId, null, Instant.now(), Instant.now());
        DispenseDTO dto = new DispenseDTO(slip.getDispenseId(), prescriptionId,
                DispenseStatus.DISPENSED, slip.getDispensedAt(), actorId, null);

        when(prescriptionRepo.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(dispenseSlipRepo.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(dispenseDtoMapper.toDto(slip)).thenReturn(dto);

        assertThat(service.dispenseInTransaction(prescriptionId, actorId, "retry-correlation"))
                .isSameAs(dto);
        verify(drugRepo, org.mockito.Mockito.never()).findByIdForUpdate(any());
        verify(reservationRepo, org.mockito.Mockito.never())
                .findReservedByPrescriptionForUpdate(any(), any());
    }

    /** Dựng đơn một dòng để test không phụ thuộc persistence adapter. */
    private Prescription prescription(UUID prescriptionId, UUID drugId) {
        Prescription created = Prescription.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), List.of(PrescriptionLine.create(
                        drugId, 2, new BigDecimal("100.00"), "Ngày 2 lần")));
        return Prescription.restore(prescriptionId, created.getRecordId(), created.getPatientId(),
                created.getDoctorId(), created.getDepartmentId(), created.getPrescribedDate(),
                created.getTotalAmount(), created.getLines(), PrescriptionStatus.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
    }

    /** Dựng thuốc còn hạn với tồn kho xác định. */
    private Drug drug(UUID drugId, int stock) {
        return Drug.restore(drugId, "Paracetamol", "Paracetamol", "viên",
                new BigDecimal("100.00"), stock, LocalDate.now().plusYears(1),
                "Dược phẩm VN", 2, Instant.now(), Instant.now());
    }

    /** Dựng reservation ở trạng thái RESERVED cho đơn đang cấp. */
    private StockReservation reservation(UUID prescriptionId, UUID drugId, int quantity, Instant expiresAt) {
        return StockReservation.restore(UUID.randomUUID(), drugId, prescriptionId, quantity,
                ReservationStatus.RESERVED, Instant.now(), expiresAt, Instant.now());
    }
}
