package com.mediflow.billing.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.ConsumeAdmissionMedicalDischargeUseCase;
import com.mediflow.billing.application.port.in.ConsumePrescriptionChargeFactUseCase;
import com.mediflow.billing.application.port.in.ConsumeSurgeryCompletionUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.infrastructure.config.RabbitConfig;

/**
 * Contract tests for the merged single-queue Billing dispatcher.
 *
 * <p>The strict handlers are mocked at the dispatcher boundary. Their own wire decoding,
 * idempotency and persistence rules are covered by their focused tests; this class verifies that
 * the merged dispatcher selects exactly one owner and never falls back to a compatibility writer
 * after a strict path has claimed or rejected a versioned fact.</p>
 */
class BillingMergedDispatcherTest {

    private static final byte[] BODY = "{\"version\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    void prescriptionV1_strictHandlerClaimsFact_onceWithoutCompatibilityFallback() throws Exception {
        var compatibility = mock(AccrueFeeUseCase.class);
        var saga = mock(SagaCompensationUseCase.class);
        var rx = mock(ConsumePrescriptionChargeFactUseCase.class);
        when(rx.receiveIfVersioned("prescription.created", BODY)).thenReturn(true);
        var dispatcher = dispatcherWithPrescription(compatibility, saga, rx, null, null, null, null);

        dispatcher.onMessage(message("prescription.created", BODY));

        verify(rx).receiveIfVersioned("prescription.created", BODY);
        verifyNoInteractions(compatibility, saga);
        verifyNoMoreInteractions(rx);
    }

    @Test
    void prescriptionV1_strictHandlerRejects_withoutLegacyFallback() throws Exception {
        var compatibility = mock(AccrueFeeUseCase.class);
        var saga = mock(SagaCompensationUseCase.class);
        var rx = mock(ConsumePrescriptionChargeFactUseCase.class);
        var rejection = new AmqpRejectAndDontRequeueException("invalid Rx V1");
        doThrow(rejection).when(rx).receiveIfVersioned("prescription.created", BODY);
        var dispatcher = dispatcherWithPrescription(compatibility, saga, rx, null, null, null, null);

        assertThatThrownBy(() -> dispatcher.onMessage(message("prescription.created", BODY)))
                .isSameAs(rejection);

        verify(rx).receiveIfVersioned("prescription.created", BODY);
        verifyNoInteractions(compatibility, saga);
        verifyNoMoreInteractions(rx);
    }

    @Test
    void labCharge_enabledConsumer_receivesOriginalMessage() throws Exception {
        var lab = mock(LabTestChargeConsumer.class);
        var dispatcher = dispatcher(null, null, null, null, lab, null, null, null);
        var message = message(RabbitConfig.RK_LAB_REQUEST_CREATED, BODY);

        dispatcher.onMessage(message);

        verify(lab).receive(same(message));
    }

    @Test
    void labCharge_disabledConsumer_rejectsWithoutCompatibilityDispatch() {
        var compatibility = mock(AccrueFeeUseCase.class);
        var dispatcher = dispatcher(compatibility, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_LAB_REQUEST_CREATED, BODY)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(compatibility);
    }

    @Test
    void admissionDeposit_enabledConsumer_receivesOriginalMessage() throws Exception {
        var deposit = mock(AdmissionDepositRequestConsumer.class);
        var dispatcher = dispatcher(null, null, null, deposit, null, null, null, null);
        var message = message(RabbitConfig.RK_ADMISSION_DEPOSIT_REQUESTED, BODY);

        dispatcher.onMessage(message);

        verify(deposit).receive(same(message));
    }

    @Test
    void admissionDeposit_disabledConsumer_rejectsWithoutCompatibilityDispatch() {
        var compatibility = mock(AccrueFeeUseCase.class);
        var dispatcher = dispatcher(compatibility, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_ADMISSION_DEPOSIT_REQUESTED, BODY)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(compatibility);
    }

    @Test
    void surgeryCompletion_strictProofConsumer_claimsOnceWithoutCompatibilityFallback() throws Exception {
        var compatibility = mock(SurgeryChargeUseCase.class);
        var proof = mock(ConsumeSurgeryCompletionUseCase.class);
        var dispatcher = dispatcher(null, null, compatibility, null, null, null, proof, null);

        dispatcher.onMessage(message(RabbitConfig.RK_SURGERY_COMPLETED, BODY));

        verify(proof).receive(RabbitConfig.RK_SURGERY_COMPLETED, BODY);
        verifyNoInteractions(compatibility);
        verifyNoMoreInteractions(proof);
    }

    @Test
    void surgeryCompletion_strictProofFailure_doesNotFallbackToCompatibility() throws Exception {
        var compatibility = mock(SurgeryChargeUseCase.class);
        var proof = mock(ConsumeSurgeryCompletionUseCase.class);
        var rejection = new AmqpRejectAndDontRequeueException("invalid surgery proof");
        doThrow(rejection).when(proof).receive(RabbitConfig.RK_SURGERY_COMPLETED, BODY);
        var dispatcher = dispatcher(null, null, compatibility, null, null, null, proof, null);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_SURGERY_COMPLETED, BODY)))
                .isSameAs(rejection);

        verify(proof).receive(RabbitConfig.RK_SURGERY_COMPLETED, BODY);
        verifyNoInteractions(compatibility);
    }

    @Test
    void discharge_bothConsumersAvailable_prefersHuyAdmissionChargeClose() throws Exception {
        var admissionChargeClose = mock(ConsumeAdmissionMedicalDischargeUseCase.class);
        var dischargeApproval = mock(DischargeApprovalConsumer.class);
        var dispatcher = dispatcher(null, null, null, null, null, admissionChargeClose, null, dischargeApproval);

        dispatcher.onMessage(message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY));

        verify(admissionChargeClose).receive(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY);
        verifyNoInteractions(dischargeApproval);
    }

    @Test
    void discharge_onlyHuyConsumerAvailable_usesAdmissionChargeClose() throws Exception {
        var admissionChargeClose = mock(ConsumeAdmissionMedicalDischargeUseCase.class);
        var dispatcher = dispatcher(null, null, null, null, null, admissionChargeClose, null, null);

        dispatcher.onMessage(message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY));

        verify(admissionChargeClose).receive(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY);
    }

    @Test
    void discharge_onlyMasterConsumerAvailable_fallsBackToDischargeApprovalConsumer() throws Exception {
        var dischargeApproval = mock(DischargeApprovalConsumer.class);
        var dispatcher = dispatcher(null, null, null, null, null, null, null, dischargeApproval);
        var message = message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY);

        dispatcher.onMessage(message);

        verify(dischargeApproval).receive(same(message));
    }

    @Test
    void discharge_huyConsumerRejects_doesNotFallbackToMaster() throws Exception {
        var admissionChargeClose = mock(ConsumeAdmissionMedicalDischargeUseCase.class);
        var dischargeApproval = mock(DischargeApprovalConsumer.class);
        var rejection = new AmqpRejectAndDontRequeueException("invalid medical discharge fact");
        doThrow(rejection).when(admissionChargeClose)
                .receive(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY);
        var dispatcher = dispatcher(null, null, null, null, null, admissionChargeClose, null, dischargeApproval);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY)))
                .isSameAs(rejection);

        verify(admissionChargeClose).receive(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY);
        verifyNoInteractions(dischargeApproval);
    }

    @Test
    void discharge_neitherConsumerAvailable_rejectsWithoutCompatibilityDispatch() {
        var compatibility = mock(SurgeryChargeUseCase.class);
        var dispatcher = dispatcher(null, null, compatibility, null, null, null, null, null);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(compatibility);
    }

    @Test
    void discharge_masterConsumerRejects_doesNotFallbackToHuy() throws Exception {
        var dischargeApproval = mock(DischargeApprovalConsumer.class);
        var rejection = new AmqpRejectAndDontRequeueException("invalid discharge proof");
        doThrow(rejection).when(dischargeApproval).receive(org.mockito.ArgumentMatchers.any(Message.class));
        var dispatcher = dispatcher(null, null, null, null, null, null, null, dischargeApproval);

        assertThatThrownBy(() -> dispatcher.onMessage(message(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED, BODY)))
                .isSameAs(rejection);

        verify(dischargeApproval).receive(org.mockito.ArgumentMatchers.any(Message.class));
    }

    private BillingEventConsumer dispatcher(
            AccrueFeeUseCase accrueFeeUseCase,
            SagaCompensationUseCase sagaCompensationUseCase,
            SurgeryChargeUseCase surgeryChargeUseCase,
            AdmissionDepositRequestConsumer admissionDepositRequestConsumer,
            LabTestChargeConsumer labTestChargeConsumer,
            ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose,
            ConsumeSurgeryCompletionUseCase surgeryCompletionProof,
            DischargeApprovalConsumer dischargeApprovalConsumer) {
        return new BillingEventConsumer(
                accrueFeeUseCase,
                sagaCompensationUseCase,
                surgeryChargeUseCase,
                new ObjectMapper(),
                null,
                null,
                null,
                admissionChargeClose,
                surgeryCompletionProof,
                labTestChargeConsumer,
                admissionDepositRequestConsumer,
                dischargeApprovalConsumer);
    }

    private BillingEventConsumer dispatcherWithPrescription(
            AccrueFeeUseCase accrueFeeUseCase,
            SagaCompensationUseCase sagaCompensationUseCase,
            ConsumePrescriptionChargeFactUseCase prescriptionChargeConsumer,
            AdmissionDepositRequestConsumer admissionDepositRequestConsumer,
            LabTestChargeConsumer labTestChargeConsumer,
            ConsumeAdmissionMedicalDischargeUseCase admissionChargeClose,
            ConsumeSurgeryCompletionUseCase surgeryCompletionProof) {
        return new BillingEventConsumer(
                accrueFeeUseCase,
                sagaCompensationUseCase,
                null,
                new ObjectMapper(),
                null,
                null,
                prescriptionChargeConsumer,
                admissionChargeClose,
                surgeryCompletionProof,
                labTestChargeConsumer,
                admissionDepositRequestConsumer,
                null);
    }

    private static Message message(String routingKey, byte[] body) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return new Message(body, properties);
    }
}
