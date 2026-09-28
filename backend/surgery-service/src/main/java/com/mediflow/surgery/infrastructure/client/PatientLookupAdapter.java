package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.PatientLookupPort;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Component
public final class PatientLookupAdapter implements PatientLookupPort {

    private final PatientFeignClient client;
    private final ServiceTokenFactory tokens;

    public PatientLookupAdapter(PatientFeignClient client, ServiceTokenFactory tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public boolean exists(UUID patientId, UUID correlationId) {
        if (patientId == null || correlationId == null) {
            throw new IllegalArgumentException("Patient ID và correlation ID là bắt buộc");
        }
        try {
            ApiResponse<PatientLookupResponse> response = client.exists(
                    patientId, tokens.bearerToken(), correlationId.toString());
            if (response == null || !response.success() || response.error() != null
                    || response.data() == null || response.data().exists() == null
                    || !patientId.equals(response.data().patientId())) {
                throw new UpstreamUnavailableException("patient-service returned an invalid response");
            }
            return response.data().exists();
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (isUnambiguousNotFound(exception)) {
                return false;
            }
            throw new UpstreamUnavailableException("patient-service is unavailable", exception);
        }
    }

    private boolean isUnambiguousNotFound(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean notFound = false;
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (current instanceof RetryableException
                    || current instanceof IOException || current instanceof TimeoutException) {
                return false;
            }
            if (current instanceof FeignException feign && feign.status() == 404) {
                notFound = true;
            } else if (current instanceof FeignException) {
                return false;
            }
            current = current.getCause();
        }
        return notFound;
    }
}
