package com.mediflow.lab.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.mediflow.lab.application.port.in.ManageLabTestUseCase;
import com.mediflow.lab.application.service.LabReadAccessService;

/** Web slices exercise the controller with the read policy off; the policy has its own unit tests. */
@TestConfiguration(proxyBeanMethods = false)
public class LabReadAccessTestConfig {
    @Bean
    LabReadAccessService labReadAccessService(ManageLabTestUseCase labs) {
        return new LabReadAccessService(labs, null, null, null, false);
    }
}
