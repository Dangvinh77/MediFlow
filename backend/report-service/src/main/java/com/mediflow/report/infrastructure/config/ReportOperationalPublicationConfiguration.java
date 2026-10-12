package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.out.OperationalPublicationStorePort;
import com.mediflow.report.application.service.OperationalPublicationApplicationService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.operational-publication.enabled"}, havingValue="true")
public class ReportOperationalPublicationConfiguration {
    @Bean OperationalPublicationApplicationService operationalPublications(OperationalPublicationStorePort store,
            ObjectProvider<Clock> clocks, ZoneId reportZoneId) {
        return new OperationalPublicationApplicationService(store, clocks.getIfAvailable(Clock::systemUTC), reportZoneId);
    }
}
