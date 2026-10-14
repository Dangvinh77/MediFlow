package com.mediflow.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.in.ManageExamGateUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.web.ClinicalAdmissionReferralController;
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

    @Test
    void admissionController_staysAbsentWhenDeferredGateIsOff() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalAdmissionReferralController.class)
                .withPropertyValues(
                        "mediflow.features.care-finance-v2.enabled=true",
                        "mediflow.features.admission-referral.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ClinicalAdmissionReferralController.class));
    }

    @Test
    void admissionController_requiresBothCareFinanceAndAdmissionGates() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalAdmissionReferralController.class)
                .withPropertyValues(
                        "mediflow.features.care-finance-v2.enabled=true",
                        "mediflow.features.admission-referral.enabled=true")
                .withBean(CompleteMedicalRecordUseCase.class, () -> mock(CompleteMedicalRecordUseCase.class))
                .withBean(CorrelationIdProvider.class, () -> mock(CorrelationIdProvider.class))
                .run(context -> assertThat(context).hasSingleBean(ClinicalAdmissionReferralController.class));
    }

    @Test
    void admissionController_staysAbsentEvenWhenFlagsAreOverriddenInOutpatientProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalAdmissionReferralController.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("outpatient-mvp"))
                .withPropertyValues(
                        "mediflow.features.care-finance-v2.enabled=true",
                        "mediflow.features.admission-referral.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ClinicalAdmissionReferralController.class));
    }

    @Test
    void admissionController_staysAbsentWhenOutpatientModeIsSelectedByPropertyOnly() {
        new ApplicationContextRunner()
                .withUserConfiguration(ClinicalAdmissionReferralController.class)
                .withPropertyValues(
                        "mediflow.features.outpatient-mvp.enabled=true",
                        "mediflow.features.care-finance-v2.enabled=true",
                        "mediflow.features.admission-referral.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ClinicalAdmissionReferralController.class));
    }
}
