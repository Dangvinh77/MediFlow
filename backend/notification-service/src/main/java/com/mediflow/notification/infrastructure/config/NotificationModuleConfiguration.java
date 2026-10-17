package com.mediflow.notification.infrastructure.config;

import com.mediflow.notification.NotificationServiceApplication;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Public composition entry point. The standalone launcher never participates in a host. */
@Configuration(proxyBeanMethods = false)
@Profile("notification-module")
@EnableTransactionManagement
@Import(NotificationModulePersistenceConfiguration.class)
@ComponentScan(basePackages = "com.mediflow.notification",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        NotificationServiceApplication.class, NotificationModuleConfiguration.class,
                        NotificationModulePersistenceConfiguration.class, NotificationStandalonePersistenceConfiguration.class,
                        OpenApiConfig.class}),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = {
                        org.springframework.boot.context.TypeExcludeFilter.class,
                        org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter.class})})
public class NotificationModuleConfiguration {
    @Bean
    static BeanFactoryPostProcessor notificationModuleModeGuard(Environment environment) {
        return beans -> {
            if (!environment.matchesProfiles("outpatient-mvp", "mvp-outpatient")) {
                throw new IllegalStateException("Notification module requires the outpatient MVP profile");
            }
        };
    }
}
