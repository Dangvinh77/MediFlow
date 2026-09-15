package com.mediflow.pharmacy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionCancellationForbiddenException;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationReleaseReason;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;

/** Kiểm tra quyền và tính nguyên tử của luồng hủy đơn thuốc. */
class CancelPrescriptionServiceTest {

    private final PrescriptionRepositoryPort prescriptionRepository = mock(PrescriptionRepositoryPort.class);
    private final DispenseSlipRepositoryPort dispenseSlipRepository = mock(DispenseSlipRepositoryPort.class);
    private final StockReservationRepositoryPort reservationRepository = mock(StockReservationRepositoryPort.class);
    private final PharmacyEventPublisherPort eventPublisher = mock(PharmacyEventPublisherPort.class);

    private CancelPrescriptionService service;

    /** Khởi tạo service với các port giả lập để cô lập quy tắc hủy. */
    @BeforeEach
    void setUp() {
        service = new CancelPrescriptionService(
                prescriptionRepository, dispenseSlipRepository, reservationRepository, eventPublisher,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC));
    }

    /** Bác sĩ không sở hữu đơn không được phép đọc tiếp hoặc giải phóng reservation. */
    @Test
    void cancel_doctorMismatch_rejectsBeforeMutatingAggregate() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        UUID anotherDoctor = UUID.randomUUID();
        when(prescriptionRepository.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.cancel(new CancelPrescriptionCommand(
                prescriptionId,
                new ActorIdentity(UUID.randomUUID(), anotherDoctor, "DOCTOR"),
                "Sai đơn", "cancel-correlation")))
                .isInstanceOf(PrescriptionCancellationForbiddenException.class);

        verify(dispenseSlipRepository, never()).findByPrescriptionForUpdate(any());
        verify(reservationRepository, never()).findByPrescriptionForUpdate(any());
        verify(eventPublisher, never()).publishPrescriptionCancelled(any());
    }

    /** ADMIN được override ownership và phải giải phóng reservation với lý do audit riêng. */
    @Test
    void cancel_adminOverride_releasesReservationsAndPublishesEvent() {
        UUID prescriptionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        DispenseSlip slip = pendingSlip(prescriptionId);
        StockReservation reservation = reservation(
                prescriptionId, prescription.getLines().get(0).getDrugId());
        when(prescriptionRepository.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(dispenseSlipRepository.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(reservationRepository.findByPrescriptionForUpdate(prescriptionId))
                .thenReturn(List.of(reservation));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dispenseSlipRepository.save(any(DispenseSlip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CancelPrescriptionResult result = service.cancel(new CancelPrescriptionCommand(
                prescriptionId,
                new ActorIdentity(actorId, null, "ADMIN"),
                "Điều chỉnh khẩn", "cancel-correlation"));

        assertThat(result.status()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(result.releasedReservations()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleaseReason()).isEqualTo(ReservationReleaseReason.ADMIN_OVERRIDE);
        assertThat(reservation.getReleasedBy()).isEqualTo(actorId);
        assertThat(slip.getStatus()).isEqualTo(DispenseStatus.CANCELLED);
        verify(eventPublisher).publishPrescriptionCancelled(any());
    }

    /** Gọi lặp lại trên đơn đã hủy phải trả kết quả idempotent và không khóa phiếu lần nữa. */
    @Test
    void cancel_alreadyCancelled_isIdempotent() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        prescription.cancel(UUID.randomUUID(), "Đã hủy trước đó", Instant.now());
        when(prescriptionRepository.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));

        CancelPrescriptionResult result = service.cancel(new CancelPrescriptionCommand(
                prescriptionId,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"),
                "Retry", "retry-correlation"));

        assertThat(result.status()).isEqualTo(PrescriptionStatus.CANCELLED);
        assertThat(result.releasedReservations()).isZero();
        verify(dispenseSlipRepository, never()).findByPrescriptionForUpdate(any());
        verify(reservationRepository, never()).findByPrescriptionForUpdate(any());
    }

    /** Đơn đã cấp thuốc không thể bị hủy ngược và không được chạm reservation. */
    @Test
    void cancel_fulfilledPrescription_rejectsWithoutMutation() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        prescription.markFulfilled(Instant.now());
        when(prescriptionRepository.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> service.cancel(new CancelPrescriptionCommand(
                prescriptionId,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"),
                "Late cancel", "late-correlation")))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("ACTIVE");

        verify(dispenseSlipRepository, never()).findByPrescriptionForUpdate(any());
        verify(reservationRepository, never()).findByPrescriptionForUpdate(any());
        verify(eventPublisher, never()).publishPrescriptionCancelled(any());
    }

    /** A mismatched reservation quantity is reported before any release or lifecycle transition. */
    @Test
    void cancel_reservationQuantityMismatch_rejectsWithoutRelease() {
        UUID prescriptionId = UUID.randomUUID();
        Prescription prescription = prescription(prescriptionId, UUID.randomUUID());
        DispenseSlip slip = pendingSlip(prescriptionId);
        StockReservation mismatched = StockReservation.restore(
                UUID.randomUUID(), prescription.getLines().get(0).getDrugId(), prescriptionId, 99,
                ReservationStatus.RESERVED, Instant.now(), Instant.now().plusSeconds(3600),
                Instant.now(), null, null, null);
        when(prescriptionRepository.findByIdForUpdate(prescriptionId)).thenReturn(Optional.of(prescription));
        when(dispenseSlipRepository.findByPrescriptionForUpdate(prescriptionId)).thenReturn(Optional.of(slip));
        when(reservationRepository.findByPrescriptionForUpdate(prescriptionId))
                .thenReturn(List.of(mismatched));

        assertThatThrownBy(() -> service.cancel(new CancelPrescriptionCommand(
                prescriptionId,
                new ActorIdentity(UUID.randomUUID(), null, "ADMIN"),
                "Mismatched reservation", "mismatch-correlation")))
                .isInstanceOf(PrescriptionRuleException.class)
                .hasMessageContaining("số lượng giữ chỗ");

        assertThat(mismatched.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(eventPublisher, never()).publishPrescriptionCancelled(any());
    }

    /** Dựng đơn ACTIVE một dòng với bác sĩ sở hữu xác định. */
    private Prescription prescription(UUID prescriptionId, UUID doctorId) {
        Prescription created = Prescription.create(
                UUID.randomUUID(), UUID.randomUUID(), doctorId, UUID.randomUUID(), LocalDate.now(),
                List.of(PrescriptionLine.create(UUID.randomUUID(), 2, new BigDecimal("100.00"), "Ngày 2 lần")));
        return Prescription.restore(prescriptionId, created.getRecordId(), created.getPatientId(), created.getDoctorId(),
                created.getDepartmentId(), created.getPrescribedDate(), created.getTotalAmount(), created.getLines(),
                PrescriptionStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
    }

    /** Dựng phiếu xuất đang chờ hủy. */
    private DispenseSlip pendingSlip(UUID prescriptionId) {
        return DispenseSlip.restore(UUID.randomUUID(), prescriptionId, DispenseStatus.PENDING,
                null, null, null, Instant.now(), Instant.now());
    }

    /** Dựng reservation RESERVED để xác minh release reason và actor audit. */
    private StockReservation reservation(UUID prescriptionId, UUID drugId) {
        return StockReservation.restore(UUID.randomUUID(), drugId, prescriptionId, 2,
                ReservationStatus.RESERVED, Instant.now(), Instant.now().plusSeconds(3600),
                Instant.now(), null, null, null);
    }
}
