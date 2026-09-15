package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.PatientLookupPort;

@Component
public class PatientLookupAdapter implements PatientLookupPort {

    private final PatientFeignClient client;

    public PatientLookupAdapter(PatientFeignClient client) {
        this.client = client;
    }

    @Override
    public boolean exists(UUID patientId) {
        try {
            ResponseEntity<Void> response = client.findById(patientId);
            if (response == null) {
                throw new UpstreamUnavailableException("patient-service returned an invalid response");
            }
            if (response.getStatusCode().is2xxSuccessful()) {
                return true;
            }
            if (response.getStatusCode().value() == 404) {
                return false;
            }
            throw new UpstreamUnavailableException(
                    "patient-service returned HTTP " + response.getStatusCode().value());
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (UpstreamExceptionClassifier.classify(exception)
                    == UpstreamExceptionClassifier.Classification.NOT_FOUND) {
                return false;
            }
            throw new UpstreamUnavailableException("patient-service is unavailable", exception);
        }
    }
}
