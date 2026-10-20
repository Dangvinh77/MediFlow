package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.AcceptOutpatientCoverageUseCase;
import com.mediflow.report.application.port.out.OutpatientCoverageAcceptancePort;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort;
import com.mediflow.report.application.service.OutpatientCoverageAcceptanceService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.outpatient-read.enabled",
        "mediflow.report.outpatient-coverage-acceptance.enabled"}, havingValue="true")
public class ReportOutpatientCoverageAcceptanceConfiguration {
    @Bean AcceptOutpatientCoverageUseCase outpatientCoverageAcceptance(OutpatientCoverageEvidencePort evidence,
            OutpatientCoverageAcceptancePort store, @Qualifier("reportZoneId") ZoneId zone,
            @Qualifier("reportClock") ObjectProvider<Clock> clocks) {
        return new OutpatientCoverageAcceptanceService(evidence,store,zone,clocks.getIfAvailable(Clock::systemUTC));
    }
}
