package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionBillingPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyBillingPrescriptionRevisionAdapter;
import com.mediflow.pharmacy.infrastructure.client.PharmacyBillingRevisionFeignClient;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.pharmacy.outpatient-api.enabled"}, havingValue = "true")
@EnableFeignClients(clients = PharmacyBillingRevisionFeignClient.class)
public class PharmacyRevisionBillingConfiguration {
    @Bean PrescriptionRevisionBillingPort prescriptionRevisionBilling(PharmacyBillingRevisionFeignClient client,
            ObjectMapper mapper, Clock clock, JwtProperties jwt) {
        return new PharmacyBillingPrescriptionRevisionAdapter(client, mapper, clock, jwt);
    }
}
