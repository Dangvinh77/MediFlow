package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.PatientLookupPort;

import feign.FeignException;

@Component
public class PatientLookupAdapter implements PatientLookupPort {

    private final PatientFeignClient client;

    public PatientLookupAdapter(PatientFeignClient client) {
        this.client = client;
    }

    @Override
    public boolean exists(UUID patientId) {
        try {
            return client.findById(patientId).getStatusCode().is2xxSuccessful();
        } catch (FeignException.NotFound exception) {
            return false;
        } catch (RuntimeException exception) {
            throw new UpstreamUnavailableException("patient-service is unavailable", exception);
        }
    }
}
