package com.mediflow.billing.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.AppointmentStatusChangedEvent;
import com.mediflow.billing.application.event.LabResultCreatedEvent;
import com.mediflow.billing.application.event.MedicalRecordCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCreatedEvent;
import com.mediflow.billing.application.event.PrescriptionCancelledEvent;
import com.mediflow.billing.application.event.PrescriptionDispenseFailedEvent;
import com.mediflow.billing.application.event.PrescriptionExpiredEvent;
import com.mediflow.billing.application.event.PrescriptionFilledEvent;
import com.mediflow.billing.application.event.SurgeryCancelledEvent;
import com.mediflow.billing.application.event.SurgeryCaseCreatedEvent;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.infrastructure.config.RabbitConfig;
import com.mediflow.billing.application.port.in.ConsumePrescriptionChargeFactUseCase;

/**
 * Driving adapter nhận toàn bộ 8 routing key mà billing subscribe, trên <b>một</b> queue
 * {@value RabbitConfig#QUEUE} (backend-spec/06-billing.md §12.4).
 *
 * <p><b>Vì sao một class, không phải 6 {@code @RabbitListener} riêng như §12.1 liệt kê:</b> nhiều
 * listener cùng khai báo trên một queue vật lý sẽ cạnh tranh nhau kiểu round-robin — mỗi message
 * chỉ tới đúng MỘT trong số các listener đó theo RabbitMQ, không phải "listener nào hiểu payload
 * thì nhận". Với 8 loại payload khác nhau trên cùng một queue, cách an toàn duy nhất là một
 * consumer đọc {@link Message} thô, tự đọc routing key rồi định tuyến — cùng tinh thần với
 * {@code NotificationEventConsumer} mà backend-spec/07-notification.md §12 yêu cầu ("một class
 * consumer với switch"). Đây là sai khác có chủ ý so với §12.1, ghi lại tại đây thay vì
 * {@code THELOC-INTEGRATION-FOLLOWUP.md} vì không đổi hợp đồng JSON, chỉ đổi cách đọc.
 *
 * <p>Consumer chỉ deserialize payload rồi gọi in-port; mọi quy tắc nghiệp vụ (khử trùng lặp,
 * BR-B7, BR-B8, saga) nằm ở {@link AccrueFeeUseCase}/{@link SagaCompensationUseCase}. Lỗi
 * deserialize hoặc lỗi từ application đều được ném tiếp — không nuốt — để message vào DLQ
 * (docs/ai/06-events-rabbitmq.md).
 */
@Component
public class BillingEventConsumer {

    private final AccrueFeeUseCase accrueFeeUseCase;
    private final SagaCompensationUseCase sagaCompensationUseCase;
    private final SurgeryChargeUseCase surgeryChargeUseCase;
    private final ObjectMapper objectMapper;
    private final SurgeryChargeConsumer plannedRequestConsumer;
    private final SurgeryCancellationConsumer cancellationConsumer;
    private final ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer;
    private final com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose;
    private final com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase surgeryCompletionProof;
    private final LabTestChargeConsumer labTestChargeConsumer;
    private final AdmissionDepositRequestConsumer admissionDepositRequestConsumer;
    private final DischargeApprovalConsumer dischargeApprovalConsumer;
    private final ExamChargeConsumer examChargeConsumer;

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, null, null, null, null, null, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper,
                plannedRequestConsumer, cancellationConsumer, prescriptionChargeConsumer, null, null, null, null, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose) {
        this(accrueFeeUseCase,sagaCompensationUseCase,surgeryChargeUseCase,objectMapper,plannedRequestConsumer,
                cancellationConsumer,prescriptionChargeConsumer,admissionChargeClose,null,null,null,null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase surgeryCompletionProof) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, prescriptionChargeConsumer, admissionChargeClose, surgeryCompletionProof, null, null, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable LabTestChargeConsumer labTestChargeConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, null, null, null, labTestChargeConsumer, null, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable LabTestChargeConsumer labTestChargeConsumer,
                                @Nullable AdmissionDepositRequestConsumer admissionDepositRequestConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, null, null, null, labTestChargeConsumer, admissionDepositRequestConsumer, null);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable LabTestChargeConsumer labTestChargeConsumer,
                                @Nullable AdmissionDepositRequestConsumer admissionDepositRequestConsumer,
                                @Nullable DischargeApprovalConsumer dischargeApprovalConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, null, null, null, labTestChargeConsumer, admissionDepositRequestConsumer, dischargeApprovalConsumer);
    }

    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase surgeryCompletionProof,
                                @Nullable LabTestChargeConsumer labTestChargeConsumer,
                                @Nullable AdmissionDepositRequestConsumer admissionDepositRequestConsumer,
                                @Nullable DischargeApprovalConsumer dischargeApprovalConsumer) {
        this(accrueFeeUseCase, sagaCompensationUseCase, surgeryChargeUseCase, objectMapper, plannedRequestConsumer,
                cancellationConsumer, prescriptionChargeConsumer, admissionChargeClose, surgeryCompletionProof,
                labTestChargeConsumer, admissionDepositRequestConsumer, dischargeApprovalConsumer, null);
    }

    @Autowired
    public BillingEventConsumer(AccrueFeeUseCase accrueFeeUseCase, SagaCompensationUseCase sagaCompensationUseCase,
                                SurgeryChargeUseCase surgeryChargeUseCase, ObjectMapper objectMapper,
                                @Nullable SurgeryChargeConsumer plannedRequestConsumer,
                                @Nullable SurgeryCancellationConsumer cancellationConsumer,
                                @Nullable ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose,
                                @Nullable com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase surgeryCompletionProof,
                                @Nullable LabTestChargeConsumer labTestChargeConsumer,
                                @Nullable AdmissionDepositRequestConsumer admissionDepositRequestConsumer,
                                @Nullable DischargeApprovalConsumer dischargeApprovalConsumer,
                                @Nullable ExamChargeConsumer examChargeConsumer) {
        this.accrueFeeUseCase = accrueFeeUseCase;
        this.sagaCompensationUseCase = sagaCompensationUseCase;
        this.surgeryChargeUseCase = surgeryChargeUseCase;
        this.objectMapper = objectMapper;
        this.plannedRequestConsumer = plannedRequestConsumer;
        this.cancellationConsumer = cancellationConsumer;
        this.prescriptionChargeConsumer = prescriptionChargeConsumer;
        this.admissionChargeClose = admissionChargeClose;
        this.surgeryCompletionProof = surgeryCompletionProof;
        this.labTestChargeConsumer = labTestChargeConsumer;
        this.admissionDepositRequestConsumer = admissionDepositRequestConsumer;
        this.dischargeApprovalConsumer = dischargeApprovalConsumer;
        this.examChargeConsumer = examChargeConsumer;
    }

    @RabbitListener(id = "billingEvents", queues = RabbitConfig.QUEUE)
    public void onMessage(Message message) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        byte[] body = message.getBody();
        // Versioned lifecycle facts use exactly one strict issuer/adjustment path;
        // none may enter V0 compensation as a fallback.
        if (prescriptionChargeConsumer != null && routingKey != null && routingKey.startsWith("prescription.")
                && prescriptionChargeConsumer.receiveIfVersioned(routingKey, body)) return;
        switch (routingKey) {
            case RabbitConfig.RK_MEDICAL_RECORD_CREATED ->
                    accrueFeeUseCase.onMedicalRecordCreated(read(body, MedicalRecordCreatedEvent.class));
            case RabbitConfig.RK_LAB_RESULT_CREATED ->
                    accrueFeeUseCase.onLabResultCreated(read(body, LabResultCreatedEvent.class));
            case RabbitConfig.RK_APPOINTMENT_STATUS_CHANGED -> {
                // Exactly one writer: once the opt-in V1 EXAM issuer is configured, it alone reads
                // this routing key. A valid transition other than the check-in charge trigger is a
                // no-op there, not a fallback into the V0 flat-shape compatibility handler below.
                if (examChargeConsumer != null) examChargeConsumer.receive(message);
                else accrueFeeUseCase.onAppointmentStatusChanged(read(body, AppointmentStatusChangedEvent.class));
            }
            case RabbitConfig.RK_PRESCRIPTION_CREATED ->
                    accrueFeeUseCase.onPrescriptionCreated(read(body, PrescriptionCreatedEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_FILLED ->
                    sagaCompensationUseCase.onPrescriptionFilled(read(body, PrescriptionFilledEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_DISPENSE_FAILED ->
                    sagaCompensationUseCase.onDispenseFailed(read(body, PrescriptionDispenseFailedEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_CANCELLED ->
                    sagaCompensationUseCase.onPrescriptionCancelled(read(body, PrescriptionCancelledEvent.class));
            case RabbitConfig.RK_PRESCRIPTION_EXPIRED ->
                    sagaCompensationUseCase.onPrescriptionExpired(read(body, PrescriptionExpiredEvent.class));
            case RabbitConfig.RK_SURGERY_CASE_CREATED -> {
                // One queue and exactly one writer. The opt-in issuer validates the original bytes,
                // not a reserialized/partially decoded compatibility DTO. Never fall back on failure.
                if (plannedRequestConsumer != null) plannedRequestConsumer.receive(message);
                else surgeryChargeUseCase.onSurgeryCaseCreated(read(body, SurgeryCaseCreatedEvent.class));
            }
            case RabbitConfig.RK_SURGERY_COMPLETED -> {
                if(surgeryCompletionProof!=null)surgeryCompletionProof.receive(routingKey,body);
                else surgeryChargeUseCase.onSurgeryCompleted(read(body, SurgeryCompletedEvent.class));
            }
            case RabbitConfig.RK_SURGERY_CANCELLED -> {
                if (cancellationConsumer != null) cancellationConsumer.receive(message);
                else surgeryChargeUseCase.onSurgeryCancelled(read(body, SurgeryCancelledEvent.class));
            }
            case RabbitConfig.RK_LAB_REQUEST_CREATED -> {
                // Bound only when LabTestChargeConfiguration's flags are both true; see RabbitConfig.
                if (labTestChargeConsumer != null) labTestChargeConsumer.receive(message);
                else throw new IllegalArgumentException(
                        "Routing key không được hỗ trợ trên " + RabbitConfig.QUEUE + ": " + routingKey);
            }
            case RabbitConfig.RK_ADMISSION_DEPOSIT_REQUESTED -> {
                // Bound only when AdmissionDepositRequestConfiguration's flags are both true.
                if (admissionDepositRequestConsumer != null) admissionDepositRequestConsumer.receive(message);
                else throw new IllegalArgumentException(
                        "Routing key không được hỗ trợ trên " + RabbitConfig.QUEUE + ": " + routingKey);
            }
            case RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED -> {
                // Exactly one writer: the opt-in proof-retaining path also closes charges.
                // Keep master's freeze-only path when the strict path is not configured;
                // a strict failure must never fall back and lose its evidence requirements.
                if (admissionChargeClose != null) admissionChargeClose.receive(routingKey, body);
                else if (dischargeApprovalConsumer != null) dischargeApprovalConsumer.receive(message);
                else throw new IllegalArgumentException(
                        "Routing key không được hỗ trợ trên " + RabbitConfig.QUEUE + ": " + routingKey);
            }
            default -> throw new IllegalArgumentException(
                    "Routing key không được hỗ trợ trên " + RabbitConfig.QUEUE + ": " + routingKey);
        }
    }

    private <T> T read(byte[] body, Class<T> type) throws IOException {
        return objectMapper.readValue(body, type);
    }
}
