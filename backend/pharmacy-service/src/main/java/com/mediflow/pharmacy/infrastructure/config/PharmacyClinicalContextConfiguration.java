package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.in.CreateContextCheckedPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionWithContextUseCase;
import com.mediflow.pharmacy.application.service.ContextCheckedPrescriptionCreationService;
import com.mediflow.pharmacy.infrastructure.client.*;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = PharmacyClinicalFeignClient.class)
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.pharmacy.clinical-context.enabled"}, havingValue = "true")
public class PharmacyClinicalContextConfiguration {
    @Bean PharmacyClinicalContextFallbackFactory pharmacyClinicalContextFallbackFactory() { return new PharmacyClinicalContextFallbackFactory(); }
    @Bean OutpatientPrescriptionContextPort outpatientPrescriptionContext(PharmacyClinicalFeignClient client,
            Clock clock, JwtProperties jwt, ObjectMapper mapper) { return new PharmacyClinicalContextAdapter(client, clock, jwt, mapper); }
    @Bean CreateContextCheckedPrescriptionUseCase contextCheckedPrescriptionCreation(OutpatientPrescriptionContextPort contexts,
            CreateCarePrescriptionWithContextUseCase writer, Clock clock,
            com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort identities) {
        return new ContextCheckedPrescriptionCreationService(contexts, writer, clock, identities);
    }
}
