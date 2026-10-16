package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.CreateContextCheckedPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreateOutpatientPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import com.mediflow.pharmacy.application.service.PublicOutpatientPrescriptionCreationService;
import com.mediflow.pharmacy.application.service.OutpatientPrescriptionLifecycleApplicationService;
import com.mediflow.pharmacy.application.port.in.OutpatientPrescriptionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.in.DispenseCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.RecordCareStockFailureUseCase;
import com.mediflow.pharmacy.application.port.in.CancelCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.ExpireCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Separate API activation; existing global V2 settings cannot silently activate public V1. */
@Configuration(proxyBeanMethods = false)
@Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2",
        "mediflow.pharmacy.identity.enabled", "mediflow.pharmacy.clinical-context.enabled",
        "mediflow.pharmacy.outpatient-api.enabled",
        "mediflow.pharmacy.clearance-consumer.enabled", "mediflow.pharmacy.clearance-authority.enabled"}, havingValue = "true")
public class PharmacyOutpatientApiConfiguration {
    @Bean
    CreateOutpatientPrescriptionUseCase publicOutpatientPrescriptionCreator(
            CreateContextCheckedPrescriptionUseCase writer, GetPrescriptionUseCase reader) {
        return new PublicOutpatientPrescriptionCreationService(writer, reader);
    }

    @Bean
    OutpatientPrescriptionLifecycleUseCase outpatientPrescriptionLifecycle(PrescriptionRepositoryPort prescriptions,
            DispenseCarePrescriptionUseCase dispense, RecordCareStockFailureUseCase failures,
            CancelCarePrescriptionUseCase cancel, ExpireCarePrescriptionUseCase expiry) {
        return new OutpatientPrescriptionLifecycleApplicationService(prescriptions, dispense, failures, cancel, expiry);
    }
}
