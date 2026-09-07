package com.mediflow.billing.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.InvoiceCreatedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.out.BillingEventPublisherPort;
import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.application.port.out.InvoiceRepositoryPort;
import com.mediflow.billing.application.port.out.LabTestTypePort;
import com.mediflow.billing.application.port.out.PriceListPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.domain.model.Invoice;

/**
 * Hiện thực {@link AccrueFeeUseCase} — sinh viện phí khi có việc xảy ra ở service khác
 * (backend-spec/06-billing.md §7). Do các consumer trong {@code messaging/consumer} (Phần 5/5)
 * gọi vào, đã gói payload RabbitMQ thành record event.
 *
 * <p>Mọi handler: chống xử lý trùng theo {@code eventId} ({@link ProcessedEventPort}, BR-B6),
 * kiểm {@code existsBySource} trước khi tạo phí (BR-B7), và mọi khoản phí luôn có
 * {@code departmentId} lấy từ event (BR-B8). Tất cả chạy trong một {@code @Transactional} —
 * đánh dấu đã xử lý nằm cùng transaction với hiệu ứng.
 */
@Service
public class FeeAccrualService implements AccrueFeeUseCase {

    private static final Logger log = LoggerFactory.getLogger(FeeAccrualService.class);

    private static final String RK_MEDICAL_RECORD_CREATED = "medicalrecord.created";
    private static final String RK_LAB_RESULT_CREATED = "lab.result.created";
    private static final String RK_APPOINTMENT_STATUS_CHANGED = "appointment.status.changed";
    private static final String RK_PRESCRIPTION_CREATED = "prescription.created";
    private static final String STATUS_ARRIVED = "ARRIVED";

    private final ProcessedEventPort processedEvent;
    private final FeeRepositoryPort feeRepo;
    private final InvoiceRepositoryPort invoiceRepo;
    private final BillingEventPublisherPort eventPublisher;
    private final PriceListPort priceList;
    private final LabTestTypePort labTestTypePort;

    public FeeAccrualService(ProcessedEventPort processedEvent, FeeRepositoryPort feeRepo,
                             InvoiceRepositoryPort invoiceRepo, BillingEventPublisherPort eventPublisher,
                             PriceListPort priceList, LabTestTypePort labTestTypePort) {
        this.processedEvent = processedEvent;
        this.feeRepo = feeRepo;
        this.invoiceRepo = invoiceRepo;
        this.eventPublisher = eventPublisher;
        this.priceList = priceList;
        this.labTestTypePort = labTestTypePort;
    }

    /** {@code medicalrecord.created} → phí {@code EXAM}, giá {@code priceList.examFee(departmentId)}. */
    @Override
    @Transactional
    public void onMedicalRecordCreated(MedicalRecordCreatedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        if (!feeRepo.existsBySource(FeeType.EXAM, e.recordId())) {   // BR-B7
            Fee fee = Fee.create(e.patientId(), e.recordId(), e.departmentId(), e.recordId(),
                    FeeType.EXAM, incurredDateOr(e.examinationDate()), priceList.examFee(e.departmentId()));
            feeRepo.save(fee);
        }
        processedEvent.markProcessed(e.eventId(), RK_MEDICAL_RECORD_CREATED);
    }

    /** {@code lab.result.created} → phí {@code LAB}, giá {@code priceList.labFee(labType)}. */
    @Override
    @Transactional
    public void onLabResultCreated(LabResultCreatedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        Optional<String> labType = labTestTypePort.labType(e.labId());
        if (labType.isEmpty()) {
            // Chưa biết loại xét nghiệm → không đặt giá mặc định, bỏ qua lần này
            // (bảng chiếu lab.request.created sẽ có sau; xem THELOC-INTEGRATION-FOLLOWUP.md mục 2).
            log.warn("Bỏ qua sinh phí LAB: chưa có labType cho labId={} (event {})", e.labId(), e.eventId());
            processedEvent.markProcessed(e.eventId(), RK_LAB_RESULT_CREATED);
            return;
        }
        if (!feeRepo.existsBySource(FeeType.LAB, e.labId())) {   // BR-B7
            Fee fee = Fee.create(e.patientId(), e.recordId(), e.departmentId(), e.labId(),
                    FeeType.LAB, LocalDate.now(), priceList.labFee(labType.get()));
            feeRepo.save(fee);
        }
        processedEvent.markProcessed(e.eventId(), RK_LAB_RESULT_CREATED);
    }

    /**
     * {@code appointment.status.changed} — <b>V1: không sinh phí</b>. Phí EXAM được sinh dứt điểm
     * từ {@code medicalrecord.created}; sinh thêm từ nhánh ARRIVED sẽ tính tiền hai lần vì hai
     * event chưa có id chung để chống trùng chéo. Vẫn ghi sổ đã xử lý để consumer không lặp.
     * Xem {@code THELOC-INTEGRATION-FOLLOWUP.md} mục 1.
     */
    @Override
    @Transactional
    public void onAppointmentStatusChanged(AppointmentStatusChangedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        if (STATUS_ARRIVED.equals(e.status())) {
            log.debug("Bỏ qua sinh phí EXAM từ appointment.status.changed ARRIVED (appointmentId={}): "
                    + "phí khám lấy từ medicalrecord.created", e.appointmentId());
        }
        processedEvent.markProcessed(e.eventId(), RK_APPOINTMENT_STATUS_CHANGED);
    }

    /**
     * {@code prescription.created} — <b>cửa vào saga</b>: tạo phí {@code DRUG} + hóa đơn ở
     * {@code AWAITING_PAYMENT}, rồi publish {@code invoice.created}. Mỗi đơn tối đa một hóa đơn
     * (BR-B6): đã có hóa đơn theo {@code prescriptionId} thì bỏ qua.
     */
    @Override
    @Transactional
    public void onPrescriptionCreated(PrescriptionCreatedEvent e) {
        if (processedEvent.alreadyProcessed(e.eventId())) {
            return;
        }
        if (invoiceRepo.findByPrescription(e.prescriptionId()).isPresent()) {   // BR-B6
            processedEvent.markProcessed(e.eventId(), RK_PRESCRIPTION_CREATED);
            return;
        }

        Fee drugFee = Fee.create(e.patientId(), e.recordId(), e.departmentId(), e.prescriptionId(),
                FeeType.DRUG, LocalDate.now(), e.totalAmount());
        Fee savedFee = feeRepo.save(drugFee);

        Invoice invoice = Invoice.createFromPrescription(e.patientId(), e.prescriptionId(), List.of(savedFee));
        Invoice savedInvoice = invoiceRepo.save(invoice);

        savedFee.assignToInvoice(savedInvoice.getInvoiceId());
        feeRepo.save(savedFee);

        publishInvoiceCreated(savedInvoice, savedFee, e.departmentId(), e.correlationId());
        processedEvent.markProcessed(e.eventId(), RK_PRESCRIPTION_CREATED);
    }

    // ------------------------------------------------------------

    private void publishInvoiceCreated(Invoice invoice, Fee fee, UUID departmentId, String correlationId) {
        eventPublisher.publishInvoiceCreated(new InvoiceCreatedEvent(
                UUID.randomUUID(), Instant.now(), correlationId,
                invoice.getInvoiceId(), invoice.getPatientId(), departmentId, invoice.getTotalAmount(),
                List.of(new InvoiceCreatedEvent.Item(fee.getFeeId(), fee.getFeeType(), fee.getAmount()))));
    }

    private static LocalDate incurredDateOr(LocalDate date) {
        return date != null ? date : LocalDate.now();
    }
}
