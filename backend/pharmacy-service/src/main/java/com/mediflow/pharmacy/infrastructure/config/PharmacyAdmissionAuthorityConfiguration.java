package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort;
import com.mediflow.pharmacy.infrastructure.client.PharmacyAdmissionAuthorityAdapter;
import com.mediflow.pharmacy.infrastructure.client.PharmacyAdmissionFallbackFactory;
import com.mediflow.pharmacy.infrastructure.client.PharmacyInpatientFeignClient;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Only the read adapter is activated; public admission prescribing/dispensing remain fail-closed. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = PharmacyInpatientFeignClient.class)
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2", "mediflow.pharmacy.admission-authority.enabled"}, havingValue = "true")
public class PharmacyAdmissionAuthorityConfiguration {
    @Bean PharmacyAdmissionFallbackFactory pharmacyAdmissionFallbackFactory() { return new PharmacyAdmissionFallbackFactory(); }
    @Bean AdmissionAuthorityPort admissionAuthority(PharmacyInpatientFeignClient client, Clock clock, JwtProperties properties,
            ObjectMapper mapper) { return new PharmacyAdmissionAuthorityAdapter(client, clock, properties, mapper); }
}
