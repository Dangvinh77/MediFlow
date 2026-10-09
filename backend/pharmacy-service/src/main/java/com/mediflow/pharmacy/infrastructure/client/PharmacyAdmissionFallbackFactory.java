package com.mediflow.pharmacy.infrastructure.client;

import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;

/** Never downgrade transport failure, 404 or open circuit to confirmed absence/eligibility. */
public final class PharmacyAdmissionFallbackFactory implements FallbackFactory<PharmacyInpatientFeignClient> {
    @Override public PharmacyInpatientFeignClient create(Throwable cause) {
        return (id, authorization, correlation) -> { throw new PharmacyUpstreamUnavailableException(); };
    }
}
