package com.mediflow.report.infrastructure.config;

import java.time.ZoneId;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.application.service.FinancialSnapshotReadService;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.financial-read.enabled"},havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class ReportFinancialReadConfiguration {
    @Bean ReadFinancialSnapshotUseCase financialSnapshotReader(FinancialSnapshotReadPort store,
            @Value("${mediflow.report.zone-id:Asia/Bangkok}") String zone,
            @Value("${mediflow.report.financial-read.currency:VND}") String currency, ObjectProvider<Clock> clocks) {
        // Do not register a global Clock in regular configuration: optional test/platform clocks
        // may be registered later. Resolve once at construction without ordering-sensitive beans.
        return new FinancialSnapshotReadService(store,ZoneId.of(zone),currency,clocks.getIfAvailable(Clock::systemUTC));
    }
}
