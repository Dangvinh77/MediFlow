package com.mediflow.clinical.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.exception.UpstreamUnavailableException;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.common.api.ApiResponse;

@Component
public class PatientLookupAdapter implements PatientLookupPort {

    private final PatientFeignClient client;

    public PatientLookupAdapter(PatientFeignClient client) {
        this.client = client;
    }

    @Override
    public boolean exists(UUID patientId) {
        try {
            ApiResponse<PatientLookupResponse> response = client.exists(patientId);
            if (response == null || !response.success() || response.data() == null) {
                throw new UpstreamUnavailableException("patient-service returned an invalid response");
            }
            PatientLookupResponse patient = response.data();
            if (!patientId.equals(patient.patientId())) {
                throw new UpstreamUnavailableException("patient-service returned an invalid response");
            }
            return patient.exists();
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
