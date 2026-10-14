package com.mediflow.organization.infrastructure.config;

import com.mediflow.organization.application.port.in.ManageSurgeryAuthorityUseCase;
import com.mediflow.organization.application.mapper.SurgeryAuthorityMapper;
import com.mediflow.organization.application.port.out.*;
import com.mediflow.organization.application.service.SurgeryAuthorityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class SurgeryAuthorityConfiguration {
    @Bean
    ManageSurgeryAuthorityUseCase surgeryAuthorityUseCase(SurgeryAuthorityRepository authority,
            StaffRepository staff, DepartmentRepository departments, SurgeryAuthorityEventPort events,
            CorrelationIdProvider correlations, SurgeryAuthorityMapper dtoMapper) {
        return new SurgeryAuthorityService(authority, staff, departments, events, correlations,
                Clock.systemUTC(), dtoMapper);
    }
}
