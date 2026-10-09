package com.mediflow.report.infrastructure.config;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.mediflow.report.application.mapper.LabOperationalContributionMapper;
import com.mediflow.report.application.mapper.AdmissionReportFactMapper;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;

/** Infrastructure wiring for report-specific runtime configuration. */
@Configuration
public class ReportConfiguration {
    @Bean
    com.mediflow.report.application.mapper.BillingCashRefundMapper billingCashRefundMapper(ZoneId reportZoneId) {
        return new com.mediflow.report.application.mapper.BillingCashRefundMapper(reportZoneId);
    }

    @Bean
    BillingCashReceiptMapper billingCashReceiptMapper(ZoneId reportZoneId) {
        return new BillingCashReceiptMapper(reportZoneId);
    }

    @Bean
    AdmissionReportFactMapper admissionReportFactMapper() {
        return new AdmissionReportFactMapper();
    }

    @Bean
    LabOperationalContributionMapper labOperationalContributionMapper(ZoneId reportZoneId) {
        return new LabOperationalContributionMapper(reportZoneId);
    }

    @Bean
    ZoneId reportZoneId(@Value("${mediflow.report.zone-id:Asia/Bangkok}") String configuredZone) {
        try {
            return ZoneId.of(configuredZone);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("Invalid mediflow.report.zone-id: " + configuredZone, ex);
        }
    }
}
