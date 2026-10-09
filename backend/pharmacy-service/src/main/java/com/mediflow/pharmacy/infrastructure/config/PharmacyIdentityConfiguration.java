package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyPatientFeignClient;
import com.mediflow.pharmacy.infrastructure.client.PharmacyOrganizationFeignClient;
import com.mediflow.pharmacy.infrastructure.client.PharmacyPatientFallbackFactory;
import com.mediflow.pharmacy.infrastructure.client.PharmacyOrganizationFallbackFactory;
import com.mediflow.pharmacy.infrastructure.client.PrescriptionIdentityAdapter;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {PharmacyPatientFeignClient.class, PharmacyOrganizationFeignClient.class})
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.pharmacy.identity.enabled"}, havingValue = "true")
public class PharmacyIdentityConfiguration {
    @Bean PharmacyPatientFallbackFactory pharmacyPatientFallbackFactory() { return new PharmacyPatientFallbackFactory(); }
    @Bean PharmacyOrganizationFallbackFactory pharmacyOrganizationFallbackFactory() { return new PharmacyOrganizationFallbackFactory(); }
    @Bean PrescriptionIdentityPort prescriptionIdentity(PharmacyPatientFeignClient patients, PharmacyOrganizationFeignClient organization,
            Clock clock, JwtProperties jwt, ObjectMapper json) {
        return new PrescriptionIdentityAdapter(patients, organization, clock, jwt, json);
    }
}
