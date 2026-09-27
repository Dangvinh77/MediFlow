package com.mediflow.inpatient.infrastructure.config;

import com.mediflow.inpatient.application.mapper.InpatientDtoMapper;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.application.service.InpatientApplicationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DepositSuggestionProperties.class)
public class InpatientApplicationConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock inpatientClock() {
        return Clock.systemUTC();
    }

    @Bean
    InpatientApplicationService inpatientApplicationService(
            AdmissionRepositoryPort admissions,
            BedRepositoryPort beds,
            BedAssignmentRepositoryPort assignments,
            TreatmentEntryRepositoryPort treatments,
            ClinicalOrderReferenceRepositoryPort references,
            DischargeSummaryRepositoryPort discharges,
            ProcessedEventPort processedEvents,
            InpatientEventStorePort eventStore,
            InpatientOutboxPort outbox,
            DepositSuggestionPolicyPort depositSuggestions,
            InpatientDtoMapper mapper,
            Clock clock) {
        return new InpatientApplicationService(admissions, beds, assignments, treatments, references,
                discharges, processedEvents, eventStore, outbox, depositSuggestions, mapper, clock);
    }
}
