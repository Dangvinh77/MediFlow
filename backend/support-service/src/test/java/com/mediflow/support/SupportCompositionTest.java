package com.mediflow.support;

import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.infrastructure.persistence.repository.NotificationJpaRepository;
import com.mediflow.notification.infrastructure.persistence.repository.ProcessedEventJpaRepository;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.report.infrastructure.persistence.repository.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Loads the real host/component graph. Store/broker mocks make this a wiring test, not runtime acceptance. */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.jwt.secret=support-composition-test-secret-at-least-thirty-two-bytes"})
class SupportCompositionTest {
    @Autowired ApplicationContext context;
    @MockBean(name = "reportDataSource") HikariDataSource reportDataSource;
    @MockBean(name = "notificationDataSource") HikariDataSource notificationDataSource;
    @MockBean(name = "reportFlyway") Flyway reportFlyway;
    @MockBean(name = "notificationFlyway") Flyway notificationFlyway;
    @MockBean(name = "reportEntityManagerFactory", answer = Answers.RETURNS_DEEP_STUBS) EntityManagerFactory reportEmf;
    @MockBean(name = "notificationEntityManagerFactory", answer = Answers.RETURNS_DEEP_STUBS) EntityManagerFactory notificationEmf;
    @MockBean(name = "reportTransactionManager") PlatformTransactionManager reportTx;
    @MockBean(name = "notificationTransactionManager") PlatformTransactionManager notificationTx;
    @MockBean DailyVisitReportJpaRepository visits;
    @MockBean DrugStatisticJpaRepository drugs;
    @MockBean MonthlyRevenueReportJpaRepository revenues;
    @MockBean PaymentContributionJpaRepository payments;
    @MockBean com.mediflow.report.infrastructure.persistence.repository.ProcessedEventJpaRepository reportEvents;
    @MockBean NotificationJpaRepository notifications;
    @MockBean ProcessedEventJpaRepository notificationEvents;
    @MockBean ConnectionFactory rabbit;

    @Test void twoModulesComposeWithoutBeanOverridesOrStandaloneLaunchers() {
        assertThat(context.getBeansOfType(ReadReportUseCase.class)).hasSize(1);
        assertThat(context.getBeansOfType(ReadNotificationUseCase.class)).hasSize(1);
        assertThat(context.getBeansOfType(com.mediflow.report.ReportServiceApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.NotificationServiceApplication.class)).isEmpty();
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(2);
        assertThat(context.containsBean("transactionManager")).isFalse();
        assertThat(context.getBean("reportJdbcTemplate", JdbcTemplate.class).getDataSource()).isSameAs(reportDataSource);
        assertThat(context.getBean("notificationJdbcTemplate", JdbcTemplate.class).getDataSource()).isSameAs(notificationDataSource);
        assertThat(context.getBeansOfType(org.springframework.security.web.SecurityFilterChain.class)).hasSize(3);
    }

    @Test void defaultOffAndDeferredConsumersDoNotLeakIntoHost() {
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.CareProjectionWireHandler.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.SurgeryNoticeConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.SurgeryPaymentNoticeConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.report.messaging.consumer.ReportEventConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.report.messaging.consumer.OutpatientReportFactConsumer.class)).isEmpty();
        assertThat(context.getBean("reportTaskScheduler")).isNotNull();
        assertThat(context.getBean("notificationListenerFactory")).isNotSameAs(context.getBean("reportLegacyListenerFactory"));
    }

    @Test void notificationReadUsesOnlyItsOwnTransactionManager() {
        when(notificationTx.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        assertThatThrownBy(() -> context.getBean(ReadNotificationUseCase.class)
                .getById(java.util.UUID.randomUUID(), null, true))
                .isInstanceOf(com.mediflow.notification.domain.exception.NotificationNotFoundException.class);
        verify(notificationTx).getTransaction(any());
        verify(notificationTx).rollback(any());
        verifyNoInteractions(reportTx);
    }
}
