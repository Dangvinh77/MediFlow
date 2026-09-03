package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.event.PrescriptionCreatedEvent;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.mapper.DrugDtoMapper;
import com.mediflow.pharmacy.application.mapper.PrescriptionDtoMapper;
import com.mediflow.pharmacy.application.port.out.DispenseSlipRepositoryPort;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.application.port.out.PharmacyEventPublisherPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.application.port.out.StockReservationRepositoryPort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.domain.exception.StockReservationRuleException;
import com.mediflow.pharmacy.domain.model.DispenseSlip;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.StockReservation;
import com.mediflow.pharmacy.domain.model.enums.DispenseStatus;
import com.mediflow.pharmacy.domain.model.enums.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Kiểm tra orchestration của use case kê đơn thuốc. */
class PharmacyApplicationServicePrescriptionTest {

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

    @BeforeEach
    void setUp() {
        service = new PharmacyApplicationService(
                drugRepo,
                prescriptionRepo,
                dispenseSlipRepo,
                processedEventPort,
                reservationRepo,
                eventPublisher,
                drugDtoMapper,
                prescriptionDtoMapper,
                dispenseDtoMapper,
                self);
    }

    @Test
    void create_multipleDrugs_savesAggregateReservationsPendingSlipAndOneEvent() {
        UUID firstDrugId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondDrugId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID prescriptionId = UUID.randomUUID();

        when(drugRepo.findByIdForUpdate(firstDrugId))
                .thenReturn(Optional.of(drug(firstDrugId, "Paracetamol", 100, "1000.00")));
        when(drugRepo.findByIdForUpdate(secondDrugId))
                .thenReturn(Optional.of(drug(secondDrugId, "Amoxicillin", 50, "2500.00")));
        when(reservationRepo.findReservedByDrug(firstDrugId)).thenReturn(List.of());
        when(reservationRepo.findReservedByDrug(secondDrugId)).thenReturn(List.of());
        stubSavedPrescription(prescriptionId);

        service.create(requestOf(List.of(
                new PrescriptionLineRequest(secondDrugId, 2, "Ngày 2 lần"),
                new PrescriptionLineRequest(firstDrugId, 3, "Ngày 3 lần"))));

        InOrder lockOrder = inOrder(drugRepo);
        lockOrder.verify(drugRepo).findByIdForUpdate(firstDrugId);
        lockOrder.verify(drugRepo).findByIdForUpdate(secondDrugId);

        ArgumentCaptor<Prescription> prescriptionCaptor =
                ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepo).save(prescriptionCaptor.capture());
        assertThat(prescriptionCaptor.getValue().getLines()).hasSize(2);
        assertThat(prescriptionCaptor.getValue().getTotalAmount())
                .isEqualByComparingTo("8000.00");

        ArgumentCaptor<StockReservation> reservationCaptor =
                ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepo, times(2)).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getAllValues())
                .extracting(StockReservation::getDrugId)
                .containsExactly(firstDrugId, secondDrugId);
        assertThat(reservationCaptor.getAllValues())
                .allSatisfy(reservation -> {
                    assertThat(reservation.getPrescriptionId()).isEqualTo(prescriptionId);
                    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                    assertThat(reservation.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 3600));
                });

        ArgumentCaptor<DispenseSlip> slipCaptor = ArgumentCaptor.forClass(DispenseSlip.class);
        verify(dispenseSlipRepo).save(slipCaptor.capture());
        assertThat(slipCaptor.getValue().getPrescriptionId()).isEqualTo(prescriptionId);
        assertThat(slipCaptor.getValue().getStatus()).isEqualTo(DispenseStatus.PENDING);

        ArgumentCaptor<PrescriptionCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PrescriptionCreatedEvent.class);
        verify(eventPublisher, times(1)).publishPrescriptionCreated(eventCaptor.capture());
        PrescriptionCreatedEvent event = eventCaptor.getValue();
        assertThat(event.prescriptionId()).isEqualTo(prescriptionId);
        assertThat(event.totalAmount()).isEqualByComparingTo("8000.00");
        assertThat(event.items()).extracting(PrescriptionCreatedEvent.Item::drugName)
                .containsExactly("Paracetamol", "Amoxicillin");
        assertThat(event.items().get(0).price()).isEqualByComparingTo("1000.00");
        assertThat(event.items().get(1).price()).isEqualByComparingTo("2500.00");
    }

    @Test
    void create_duplicateDrug_throwsBeforeReadingOrSaving() {
        UUID drugId = UUID.randomUUID();

        CreatePrescriptionRequest request = requestOf(List.of(
                new PrescriptionLineRequest(drugId, 2, "Buổi sáng"),
                new PrescriptionLineRequest(drugId, 3, "Buổi tối")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(
                        PrescriptionRuleException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PRESCRIPTION_DUPLICATE_DRUG"));

        verifyNoInteractions(drugRepo);
        verifyNoInteractions(prescriptionRepo);
        verifyNoInteractions(reservationRepo);
        verifyNoInteractions(dispenseSlipRepo);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void create_secondDrugHasInsufficientAvailableStock_savesNothing() {
        UUID firstDrugId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondDrugId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Drug firstDrug = drug(firstDrugId, "Paracetamol", 100, "1000.00");
        Drug secondDrug = drug(secondDrugId, "Amoxicillin", 10, "2500.00");
        StockReservation existingReservation = StockReservation.restore(
                UUID.randomUUID(),
                secondDrugId,
                UUID.randomUUID(),
                7,
                ReservationStatus.RESERVED,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null);

        when(drugRepo.findByIdForUpdate(firstDrugId)).thenReturn(Optional.of(firstDrug));
        when(drugRepo.findByIdForUpdate(secondDrugId)).thenReturn(Optional.of(secondDrug));
        when(reservationRepo.findReservedByDrug(firstDrugId)).thenReturn(List.of());
        when(reservationRepo.findReservedByDrug(secondDrugId))
                .thenReturn(List.of(existingReservation));

        CreatePrescriptionRequest request = requestOf(List.of(
                new PrescriptionLineRequest(firstDrugId, 2, "Ngày 2 lần"),
                new PrescriptionLineRequest(secondDrugId, 4, "Ngày 2 lần")));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(
                        StockReservationRuleException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("INSUFFICIENT_AVAILABLE_STOCK"));

        verify(prescriptionRepo, never()).save(any());
        verify(reservationRepo, never()).save(any());
        verify(dispenseSlipRepo, never()).save(any());
        verify(eventPublisher, never()).publishPrescriptionCreated(any());
    }

    private void stubSavedPrescription(UUID prescriptionId) {
        when(prescriptionRepo.save(any(Prescription.class)))
                .thenAnswer(invocation -> {
                    Prescription unsaved = invocation.getArgument(0);
                    return Prescription.restore(
                            prescriptionId,
                            unsaved.getRecordId(),
                            unsaved.getPatientId(),
                            unsaved.getDoctorId(),
                            unsaved.getDepartmentId(),
                            unsaved.getPrescribedDate(),
                            unsaved.getTotalAmount(),
                            unsaved.getLines(),
                            Instant.now());
                });
        when(dispenseSlipRepo.save(any(DispenseSlip.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreatePrescriptionRequest requestOf(List<PrescriptionLineRequest> lines) {
        return new CreatePrescriptionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                lines);
    }

    private Drug drug(UUID id, String name, int stock, String price) {
        Instant now = Instant.now();
        return Drug.restore(
                id,
                name,
                name,
                "viên",
                new BigDecimal(price),
                stock,
                LocalDate.now().plusYears(1),
                "Dược phẩm VN",
                10,
                now,
                now);
    }
}
