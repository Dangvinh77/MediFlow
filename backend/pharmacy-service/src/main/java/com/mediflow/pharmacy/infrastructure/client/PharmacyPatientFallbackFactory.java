package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;

public final class PharmacyPatientFallbackFactory implements FallbackFactory<PharmacyPatientFeignClient> {
    @Override public PharmacyPatientFeignClient create(Throwable cause) {
        return (id, authorization, correlation) -> { throw new PharmacyUpstreamUnavailableException(); };
    }
}
