package com.mediflow.surgery.infrastructure.client;

import java.time.Instant;
import java.util.Objects;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import org.springframework.stereotype.Component;

@Component
public class FinancialClearanceLookupAdapter implements FinancialClearanceLookupPort {
    private final BillingFeignClient client;
    private final ServiceTokenFactory tokens;
    private final SurgeryClockPort clock;

    public FinancialClearanceLookupAdapter(BillingFeignClient client, ServiceTokenFactory tokens, SurgeryClockPort clock) {
        this.client = client; this.tokens = tokens; this.clock = clock;
    }

    @Override public Observation observe(SurgeryFinancialClearance expected, String correlation) {
        if (expected == null || correlation == null || correlation.isBlank() || correlation.length() > 120)
            throw new IllegalArgumentException("Financial identity and correlation required");
        try {
            var response = client.lookup(expected.clearanceId(), tokens.bearerToken(), correlation);
            if (response == null || !response.getStatusCode().is2xxSuccessful()
                    || !correlation.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) throw invalid();
            var body = response.getBody();
            if (body == null || !body.success() || body.error() != null || body.data() == null
                    || !correlation.equals(body.correlationId())) throw invalid();
            var data = body.data();
            Instant now = clock.now();
            if (data.exists() == null || data.eligible() == null || !expected.clearanceId().equals(data.clearanceId())
                    || data.observedAt() == null || data.observedAt().isBefore(now.minusSeconds(30))
                    || data.observedAt().isAfter(now.plusSeconds(5))) throw invalid();
            if (!data.exists()) {
                if (data.eligible() || data.invoiceId() != null || data.accountId() != null || data.patientId() != null
                        || data.purpose() != null || data.careEpisodeType() != null || data.careEpisodeId() != null
                        || data.admissionId() != null || data.surgeryCaseId() != null || data.grantedAt() != null
                        || data.expiresAt() != null) throw invalid();
            } else if (!expected.invoiceId().equals(data.invoiceId()) || !expected.accountId().equals(data.accountId())
                    || !expected.patientId().equals(data.patientId()) || !"SURGERY".equals(data.purpose())
                    || !expected.episodeType().name().equals(data.careEpisodeType())
                    || !expected.episodeId().equals(data.careEpisodeId())
                    || !Objects.equals(expected.admissionId(), data.admissionId())
                    || !expected.surgeryCaseId().equals(data.surgeryCaseId()) || data.grantedAt() == null
                    || data.expiresAt() != null && !data.expiresAt().isAfter(data.grantedAt())
                    || data.eligible() && (data.grantedAt().isAfter(data.observedAt())
                        || data.expiresAt() != null && !data.observedAt().isBefore(data.expiresAt()))) throw invalid();
            // Freshness is checked again by the readiness engine after lock waits. Do not turn
            // a read's freshness window into the persisted schedule's business expiry.
            return new Observation(data.eligible(), data.observedAt(), data.expiresAt());
        } catch (UpstreamUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) { throw new UpstreamUnavailableException("Billing financial authority unavailable", failure); }
    }

    private static UpstreamUnavailableException invalid() { return new UpstreamUnavailableException("Invalid Billing financial authority response"); }
}
