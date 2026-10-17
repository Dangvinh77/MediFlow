package com.mediflow.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in must compose too; enabling outpatient gates must not activate deferred consumers. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.jwt.secret=" + SupportSecurityTest.SECRET, "mediflow.features.care-finance-v2=true",
        "mediflow.report.outpatient-consumer.enabled=true", "mediflow.report.outpatient-read.enabled=true",
        "mediflow.notification.care-v1.enabled=true", "mediflow.notification.refund-consumer.enabled=true",
        "mediflow.notification.outpatient-prescription-consumer.enabled=true",
        "mediflow.report.cash-refund-consumer.recovery-initial-delay-ms=3600000"})
class SupportOutpatientCompositionTest extends SupportCompositionTest {
    @Override @Test void defaultOffAndDeferredConsumersDoNotLeakIntoHost() {
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.CareProjectionWireHandler.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.SurgeryNoticeConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.SurgeryPaymentNoticeConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.report.messaging.consumer.ReportEventConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler.class)).hasSize(1);
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.RefundNoticeConsumer.class)).hasSize(1);
        assertThat(context.getBeansOfType(com.mediflow.report.messaging.consumer.OutpatientReportFactConsumer.class)).hasSize(1);
    }
}
