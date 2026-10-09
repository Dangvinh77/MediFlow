package com.mediflow.clinical.infrastructure.config;

import com.mediflow.clinical.application.port.in.GetPrescriptionContextUseCase;
import com.mediflow.clinical.application.port.out.PrescriptionContextReadPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.clinical.prescription-context-lookup.enabled", havingValue = "true")
public class PrescriptionContextConfiguration {
    @Bean GetPrescriptionContextUseCase prescriptionContext(PrescriptionContextReadPort repository) {
        return repository::find;
    }
}
