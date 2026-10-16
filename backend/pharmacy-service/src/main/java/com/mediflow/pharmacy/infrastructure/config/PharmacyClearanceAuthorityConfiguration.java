package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort;
import com.mediflow.pharmacy.infrastructure.client.*;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.pharmacy.clearance-authority.enabled"},havingValue="true")
@EnableFeignClients(clients=PharmacyBillingClearanceFeignClient.class)
public class PharmacyClearanceAuthorityConfiguration {
    @Bean PharmacyBillingClearanceFallbackFactory pharmacyBillingClearanceFallbackFactory(){return new PharmacyBillingClearanceFallbackFactory();}
    @Bean PrescriptionClearanceAuthorityPort prescriptionClearanceAuthority(PharmacyBillingClearanceFeignClient client,
            ObjectMapper mapper,Clock clock,JwtProperties jwt){return new PharmacyBillingClearanceAdapter(client,mapper,clock,jwt);}
}
