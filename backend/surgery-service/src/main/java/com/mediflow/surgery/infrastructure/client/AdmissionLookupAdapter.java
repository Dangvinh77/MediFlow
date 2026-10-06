package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.AdmissionLookupPort;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort.ReferenceState;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
public class AdmissionLookupAdapter implements AdmissionLookupPort {
    private static final Set<String> STATUSES=Set.of("REQUESTED","AWAITING_BED","AWAITING_DEPOSIT",
            "READY","ADMITTED","MEDICALLY_DISCHARGED","CLOSED","CANCELLED");
    private final InpatientFeignClient client;
    private final ServiceTokenFactory tokens;
    private final SurgeryClockPort clock;

    public AdmissionLookupAdapter(InpatientFeignClient client,ServiceTokenFactory tokens,SurgeryClockPort clock) {
        this.client=client;
        this.tokens=tokens;
        this.clock=clock;
    }

    @Override
    public AdmissionSnapshot findAdmission(UUID id,String correlation) {
        if(id==null || correlation==null || correlation.isBlank()) throw new IllegalArgumentException("Admission identity/correlation required");
        try {
            var response=client.lookupAdmission(id,tokens.bearerToken(),correlation);
            if(response==null || !response.getStatusCode().is2xxSuccessful()
                    || !correlation.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) throw invalid();
            var body=response.getBody();
            if(body==null || !body.success() || body.error()!=null || body.data()==null
                    || !correlation.equals(body.correlationId())) throw invalid();
            var data=body.data();
            Instant now=clock.now();
            if(data.exists()==null || data.eligible()==null || !id.equals(data.admissionId())
                    || data.observedAt()==null || data.observedAt().isBefore(now.minusSeconds(30))
                    || data.observedAt().isAfter(now.plusSeconds(5))) throw invalid();
            if(!data.exists()) {
                if(data.eligible() || data.patientId()!=null || data.departmentId()!=null
                        || data.sourceRecordId()!=null || data.status()!=null || data.sourceRevision()!=null) throw invalid();
            } else if(data.patientId()==null || data.departmentId()==null || data.sourceRecordId()==null
                    || data.status()==null || !STATUSES.contains(data.status()) || !validRevision(data.sourceRevision())
                    || data.eligible() && !"ADMITTED".equals(data.status())) throw invalid();
            return new AdmissionSnapshot(id,data.patientId(),data.departmentId(),data.sourceRecordId(),data.status(),
                    !data.exists()?ReferenceState.NOT_FOUND:data.eligible()?ReferenceState.ACTIVE:ReferenceState.INACTIVE,
                    data.sourceRevision(),data.observedAt());
        } catch(UpstreamUnavailableException failure) { throw failure; }
        catch(RuntimeException failure) {
            throw new UpstreamUnavailableException("inpatient-service admission authority is unavailable",failure);
        }
    }

    private static boolean validRevision(String revision) {
        try { return revision!=null && revision.matches("0|[1-9][0-9]*") && Long.parseLong(revision)>=0; }
        catch(NumberFormatException invalid) { return false; }
    }
    private static UpstreamUnavailableException invalid() { return new UpstreamUnavailableException("Invalid admission authority response"); }
}
