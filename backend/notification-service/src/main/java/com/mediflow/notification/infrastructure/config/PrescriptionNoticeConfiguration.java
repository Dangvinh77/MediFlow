package com.mediflow.notification.infrastructure.config;

import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.dto.command.PrescriptionNoticeCommand;
import com.mediflow.notification.application.port.in.ReactToPrescriptionNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.application.port.out.PrescriptionNoticeWirePort;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import com.mediflow.notification.application.service.PrescriptionNoticeService;
import com.mediflow.notification.application.service.SurgeryPaymentNoticeService;
import com.mediflow.notification.infrastructure.messaging.PrescriptionNoticeDecoder;
import com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler;
import com.mediflow.notification.messaging.consumer.PrescriptionPaymentNoticeWireHandler;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.notification.care-v1.enabled",
        "mediflow.notification.outpatient-prescription-consumer.enabled"}, havingValue = "true")
public class PrescriptionNoticeConfiguration {
    @Bean PrescriptionNoticeWirePort prescriptionNoticeWire(ObjectMapper mapper) {
        return new PrescriptionNoticeDecoder(mapper);
    }

    @Bean ReactToPrescriptionNoticeUseCase prescriptionNotices(CareNotificationRepositoryPort history,
            NotificationSourcePort sources, @org.springframework.beans.factory.annotation.Qualifier("notificationTransactionManager") PlatformTransactionManager manager) {
        var service = new PrescriptionNoticeService(history, sources);
        var tx = new TransactionTemplate(manager);
        tx.setTimeout(5);
        return command -> tx.executeWithoutResult(status -> service.receive(command));
    }

    @Bean PrescriptionNoticeWireHandler prescriptionNoticeWireHandler(PrescriptionNoticeWirePort wire,
            ReactToPrescriptionNoticeUseCase notices) {
        return new PrescriptionNoticeWireHandler(wire, notices);
    }

    @Bean PrescriptionPaymentNoticeWireHandler prescriptionPaymentNoticeWireHandler(SurgeryPaymentNoticeWirePort wire,
            CareNotificationRepositoryPort history, NotificationSourcePort sources, @org.springframework.beans.factory.annotation.Qualifier("notificationTransactionManager") PlatformTransactionManager manager) {
        var service = new SurgeryPaymentNoticeService(history, sources);
        var tx = new TransactionTemplate(manager);
        tx.setTimeout(5);
        return new PrescriptionPaymentNoticeWireHandler(wire,
                command -> tx.executeWithoutResult(status -> service.receive(command)));
    }

    @Bean Declarables outpatientPrescriptionNoticeBindings() {
        var bindings = new ArrayList<Binding>();
        // filled already belongs to the retained compatibility queue; never replace that queue.
        for (String key : PrescriptionNoticeCommand.EVENT_TYPES) {
            if (!key.equals(RabbitConfig.RK_PRESCRIPTION_FILLED)) {
                bindings.add(new Binding(RabbitConfig.QUEUE, Binding.DestinationType.QUEUE, RabbitConfig.EXCHANGE, key, null));
            }
        }
        return new Declarables(bindings);
    }
}
