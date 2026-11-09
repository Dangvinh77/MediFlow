package com.mediflow.pharmacy.web;

import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.SearchPrescriptionsUseCase;
import com.mediflow.pharmacy.application.service.PrescriptionReadAccessService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Web slices keep testing the controllers with the read policy off; the policy has its own unit tests. */
@TestConfiguration(proxyBeanMethods = false)
class PrescriptionReadAccessTestConfig {
    @Bean
    PrescriptionReadAccessService prescriptionReadAccessService(ObjectProvider<GetPrescriptionUseCase> prescriptions,
            ObjectProvider<SearchPrescriptionsUseCase> searches) {
        return new PrescriptionReadAccessService(prescriptions.getIfAvailable(), searches.getIfAvailable(), null, false);
    }
}
