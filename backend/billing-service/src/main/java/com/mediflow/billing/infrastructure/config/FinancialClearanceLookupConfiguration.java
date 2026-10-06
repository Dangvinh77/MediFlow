package com.mediflow.billing.infrastructure.config;

import java.time.Clock;
import com.mediflow.billing.application.port.in.LookupFinancialClearanceUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.service.LookupFinancialClearanceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.billing.clearance-lookup.enabled", havingValue = "true")
public class FinancialClearanceLookupConfiguration {
    @Bean LookupFinancialClearanceUseCase lookupFinancialClearanceUseCase(FinancialClearanceAuthorityPort repository) {
        return new LookupFinancialClearanceService(repository, Clock.systemUTC());
    }
}
