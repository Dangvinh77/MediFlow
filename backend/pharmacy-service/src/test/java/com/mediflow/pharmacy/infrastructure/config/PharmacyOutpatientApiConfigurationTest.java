package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mediflow.pharmacy.application.port.in.CreateContextCheckedPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreateOutpatientPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispenseCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.RecordCareStockFailureUseCase;
import com.mediflow.pharmacy.application.port.in.CancelCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ExpireCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.OutpatientPrescriptionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PharmacyOutpatientApiConfigurationTest {
    static java.util.stream.IntStream combinations() {
        return java.util.stream.IntStream.range(0, 64);
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void requiresEveryGateAndNeverFallsBack(int flags) {
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("mvp-outpatient"))
                .withPropertyValues(gates(flags)).run(context -> {
                    assertThat(context).hasNotFailed();
                    if (flags == 63) {
                        assertThat(context).hasSingleBean(CreateOutpatientPrescriptionUseCase.class);
                        assertThat(context).hasSingleBean(OutpatientPrescriptionLifecycleUseCase.class);
                    } else {
                        assertThat(context).doesNotHaveBean(CreateOutpatientPrescriptionUseCase.class);
                        assertThat(context).doesNotHaveBean(OutpatientPrescriptionLifecycleUseCase.class);
                    }
                });
    }

    @Test
    void legacyProfileDoesNotActivatePublicV1EvenWithAllGates() {
        runner().withPropertyValues(gates(63)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(CreateOutpatientPrescriptionUseCase.class);
        });
    }

    @Test
    void missingProofCheckedWriterFailsStartupInsteadOfUsingLegacyWriter() {
        new ApplicationContextRunner().withUserConfiguration(PharmacyOutpatientApiConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("mvp-outpatient"))
                .withBean(GetPrescriptionUseCase.class, () -> mock(GetPrescriptionUseCase.class))
                .withPropertyValues(gates(63)).run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PharmacyOutpatientApiConfiguration.class)
                .withBean(java.time.Clock.class, java.time.Clock::systemUTC)
                .withBean(com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort.class, () -> mock(com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort.class))
                .withBean(CreateContextCheckedPrescriptionUseCase.class,
                        () -> mock(CreateContextCheckedPrescriptionUseCase.class))
                .withBean(GetPrescriptionUseCase.class, () -> mock(GetPrescriptionUseCase.class))
                .withBean(PrescriptionRepositoryPort.class, () -> mock(PrescriptionRepositoryPort.class))
                .withBean(DispenseCarePrescriptionUseCase.class, () -> mock(DispenseCarePrescriptionUseCase.class))
                .withBean(RecordCareStockFailureUseCase.class, () -> mock(RecordCareStockFailureUseCase.class))
                .withBean(CancelCarePrescriptionUseCase.class, () -> mock(CancelCarePrescriptionUseCase.class))
                .withBean(ExpireCarePrescriptionUseCase.class, () -> mock(ExpireCarePrescriptionUseCase.class));
    }

    private String[] gates(int flags) {
        return new String[]{
            "mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
            "mediflow.pharmacy.identity.enabled=" + ((flags & 2) != 0),
            "mediflow.pharmacy.clinical-context.enabled=" + ((flags & 4) != 0),
            "mediflow.pharmacy.outpatient-api.enabled=" + ((flags & 8) != 0),
            "mediflow.pharmacy.clearance-consumer.enabled=" + ((flags & 16) != 0),
            "mediflow.pharmacy.clearance-authority.enabled=" + ((flags & 32) != 0)
        };
    }
}
