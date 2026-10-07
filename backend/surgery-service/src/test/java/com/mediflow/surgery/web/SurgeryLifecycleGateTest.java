package com.mediflow.surgery.web;

import com.mediflow.surgery.application.port.in.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SurgeryLifecycleGateTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(SurgeryLifecycleController.class);
    @Test void missingOrSingleFlagNeverRegistersLifecycleBoundary() {
        for(String[] flags:new String[][]{ {},{"mediflow.features.surgery.enabled=true"},
                {"mediflow.surgery.lifecycle.api.enabled=true"},
                {"mediflow.features.surgery.enabled=false","mediflow.surgery.lifecycle.api.enabled=true"}})
            context.withPropertyValues(flags).run(result -> assertThat(result).doesNotHaveBean(SurgeryLifecycleController.class));
    }
    @Test void enablingBothWithoutAuthorityBackedUseCasesFailsClosed() {
        context.withPropertyValues("mediflow.features.surgery.enabled=true","mediflow.surgery.lifecycle.api.enabled=true")
                .run(result -> assertThat(result).hasFailed());
    }
    @Test void bothFlagsPlusExplicitUseCasesRegisterOnlyBoundary() {
        context.withPropertyValues("mediflow.features.surgery.enabled=true","mediflow.surgery.lifecycle.api.enabled=true")
                .withBean(EvaluateSurgeryReadinessUseCase.class,() -> mock(EvaluateSurgeryReadinessUseCase.class))
                .withBean(FinalizeSurgeryScheduleUseCase.class,() -> mock(FinalizeSurgeryScheduleUseCase.class))
                .withBean(StartSurgeryUseCase.class,() -> mock(StartSurgeryUseCase.class))
                .withBean(CompleteSurgeryUseCase.class,() -> mock(CompleteSurgeryUseCase.class))
                .run(result -> assertThat(result).hasSingleBean(SurgeryLifecycleController.class));
    }
}
