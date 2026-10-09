package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;

public final class PharmacyClinicalContextFallbackFactory implements FallbackFactory<PharmacyClinicalFeignClient> {
    @Override public PharmacyClinicalFeignClient create(Throwable cause) {
        return (id, authorization, correlation) -> { throw new PharmacyUpstreamUnavailableException(); };
    }
}
