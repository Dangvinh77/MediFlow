package com.mediflow.clinical.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;

import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.port.out.AdmissionReferralRepositoryPort;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.EmergencyOverrideRepositoryPort;
import com.mediflow.clinical.application.port.out.ExamClearanceRepositoryPort;
import com.mediflow.clinical.application.port.out.FinancialExecutionAuthorityPort;
import com.mediflow.clinical.application.port.out.ExamPricePolicyPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.application.service.ClinicalCareFinanceApplicationService;

@Configuration
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalCareFinanceFeatureConfiguration {

    @Bean
    public Clock clinicalCareFinanceClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClinicalCareFinanceApplicationService clinicalCareFinanceApplicationService(
            AppointmentRepositoryPort appointments,
            MedicalRecordRepositoryPort records,
            ExamClearanceRepositoryPort clearances,
            FinancialExecutionAuthorityPort executionAuthority,
            EmergencyOverrideRepositoryPort overrides,
            AdmissionReferralRepositoryPort referrals,
            ExamPricePolicyPort prices,
            ClinicalOutboxPort outbox,
            CurrentClinicalActorPort actors,
            StaffLookupPort staff,
            ClinicalDtoMapper mapper,
            CorrelationIdProvider correlationIds,
            @Qualifier("clinicalCareFinanceClock") Clock clinicalCareFinanceClock,
            @Value("${mediflow.features.admission-referral.enabled:false}") boolean admissionReferralsEnabled,
            OutpatientMvpMode outpatientMvpMode) {
        return new ClinicalCareFinanceApplicationService(appointments, records, clearances, executionAuthority, overrides,
                referrals, prices, outbox, actors, staff, mapper, correlationIds, clinicalCareFinanceClock,
                admissionReferralsEnabled && !outpatientMvpMode.enabled());
    }
}
