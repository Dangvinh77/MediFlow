package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.report.application.port.in.ApplyFinalSettlementRecognitionUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class FinalSettlementRecognitionConsumer {
    private final CareFinanceWirePort wire;
    private final ApplyFinalSettlementRecognitionUseCase recognition;
    public FinalSettlementRecognitionConsumer(CareFinanceWirePort wire,ApplyFinalSettlementRecognitionUseCase recognition){this.wire=wire;this.recognition=recognition;}
    @RabbitListener(id="reportFinalRecognition",queues="report.final-recognition-v2.q",containerFactory="reportFinalRecognitionListenerFactory")
    public void receive(Message message) {
        try{recognition.apply(wire.decode(message.getMessageProperties().getReceivedRoutingKey(),message.getBody()));}
        catch(ReportEventValidationException | IllegalArgumentException | BusinessRuleException invalid){throw new AmqpRejectAndDontRequeueException("Invalid or conflicting final recognition");}
    }
}
