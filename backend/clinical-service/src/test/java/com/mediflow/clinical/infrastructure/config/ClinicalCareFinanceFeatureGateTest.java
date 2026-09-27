package com.mediflow.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.in.ManageExamGateUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.web.ClinicalCareFinanceController;

class ClinicalCareFinanceFeatureGateTest {

    @Test
    void controller_disabledByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalCareFinanceController.class)
                .run(context -> assertThat(context).doesNotHaveBean(ClinicalCareFinanceController.class));
    }

    @Test
    void controller_enabledOnlyWhenExplicitlyConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalCareFinanceController.class)
                .withPropertyValues("mediflow.features.care-finance-v2.enabled=true")
                .withBean(ManageExamGateUseCase.class, () -> mock(ManageExamGateUseCase.class))
                .withBean(CompleteMedicalRecordUseCase.class, () -> mock(CompleteMedicalRecordUseCase.class))
                .withBean(CorrelationIdProvider.class, () -> mock(CorrelationIdProvider.class))
                .run(context -> assertThat(context).hasSingleBean(ClinicalCareFinanceController.class));
    }
}
