package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.application.service.OutpatientReportReadService;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false) @Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.outpatient-read.enabled"},havingValue="true")
public class ReportOutpatientReadConfiguration {
    @Bean ReadOutpatientReportUseCase outpatientReportReader(OutpatientReportReadPort store,@org.springframework.beans.factory.annotation.Qualifier("reportZoneId") ZoneId reportZoneId,@org.springframework.beans.factory.annotation.Qualifier("reportClock") ObjectProvider<Clock> clocks){
        return new OutpatientReportReadService(store,reportZoneId,clocks.getIfAvailable(Clock::systemUTC));
    }
}
