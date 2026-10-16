package com.mediflow.report.infrastructure.config;

import com.mediflow.report.ReportServiceApplication;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Public composition entry point for the plain library; does not scan another context. */
@Configuration(proxyBeanMethods = false)
@Profile("report-module")
@EnableTransactionManagement
@Import(ReportModulePersistenceConfiguration.class)
@ComponentScan(basePackages = "com.mediflow.report",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                ReportServiceApplication.class, ReportModuleConfiguration.class,
                ReportModulePersistenceConfiguration.class, ReportStandalonePersistenceConfiguration.class}),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = {
                        org.springframework.boot.context.TypeExcludeFilter.class,
                        org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter.class})})
public class ReportModuleConfiguration {
    @Bean
    static BeanFactoryPostProcessor reportModuleModeGuard(Environment environment) {
        return beans -> {
            if (!environment.matchesProfiles("outpatient-mvp", "mvp-outpatient")) {
                throw new IllegalStateException("Report module requires an explicit outpatient MVP profile");
            }
        };
    }
}
