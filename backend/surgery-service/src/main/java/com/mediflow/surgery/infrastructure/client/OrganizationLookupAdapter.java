package com.mediflow.surgery.infrastructure.client;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.OrganizationLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.time.Instant;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;

/** Fresh authoritative lookups; missing deployment, malformed data and outages fail closed. */
@Component
public final class OrganizationLookupAdapter implements OrganizationLookupPort {

    private final OrganizationFeignClient client;
    private final ServiceTokenFactory tokens;
    private final SurgeryClockPort clock;

    public OrganizationLookupAdapter(OrganizationFeignClient client,
                                     ServiceTokenFactory tokens,
                                     SurgeryClockPort clock) {
        this.client = client;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    public OrganizationLookupSnapshot findRoom(UUID roomId, String correlationId) {
        requireLookupIdentity(roomId, correlationId);
        try {
            var data = verifiedData(client.lookupRoom(roomId, tokens.bearerToken(), correlationId), correlationId);
            if (data.exists() == null || data.active() == null || !roomId.equals(data.roomId())) throw invalidResponse();
            requireFresh(data.observedAt());
            if (!data.exists()) {
                if (data.active() || data.departmentId() != null || data.sourceRevision() != null) throw invalidResponse();
            } else if (data.departmentId() == null || !validRevision(data.sourceRevision())) throw invalidResponse();
            return new OrganizationLookupSnapshot(ReferenceKind.ROOM, roomId,
                    !data.exists() ? ReferenceState.NOT_FOUND : data.active() ? ReferenceState.ACTIVE : ReferenceState.INACTIVE,
                    data.observedAt(), data.sourceRevision(), null, null, data.departmentId());
        } catch (UpstreamUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) {
            // This additive endpoint returns 200 for absence. A 404 may mean an older producer.
            throw new UpstreamUnavailableException("organization-service room lookup is unavailable", failure);
        }
    }

    @Override
    public SurgicalEligibilitySnapshot findSurgicalEligibility(UUID staffId, SurgeryTeamRole role,
            Instant startsAt, Instant endsAt, String correlationId) {
        requireLookupIdentity(staffId, correlationId);
        if (role == null || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Surgical eligibility role and interval are required");
        }
        try {
            var data = verifiedData(client.lookupSurgicalEligibility(staffId, role.name(), startsAt, endsAt,
                    tokens.bearerToken(), correlationId), correlationId);
            if (data.exists() == null || data.eligible() == null || !staffId.equals(data.staffId())
                    || !role.name().equals(data.teamRole()) || !startsAt.equals(data.startsAt())
                    || !endsAt.equals(data.endsAt())) throw invalidResponse();
            requireFresh(data.observedAt());
            if (!data.exists()) {
                if (data.eligible() || data.departmentId() != null || data.sourceRevision() != null) throw invalidResponse();
            } else if (data.departmentId() == null
                    || data.sourceRevision() != null && !validRevision(data.sourceRevision())
                    || data.eligible() && data.sourceRevision() == null) throw invalidResponse();
            return new SurgicalEligibilitySnapshot(staffId, role,
                    !data.exists() ? ReferenceState.NOT_FOUND : data.eligible() ? ReferenceState.ACTIVE : ReferenceState.INACTIVE,
                    data.departmentId(), data.observedAt(), data.sourceRevision(), startsAt, endsAt);
        } catch (UpstreamUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) {
            throw new UpstreamUnavailableException("organization-service surgical eligibility lookup is unavailable", failure);
        }
    }

    private void requireFresh(Instant observedAt) {
        Instant now = clock.now();
        if (observedAt == null || observedAt.isBefore(now.minusSeconds(30))
                || observedAt.isAfter(now.plusSeconds(5))) throw invalidResponse();
    }

    private static boolean validRevision(String revision) {
        try { return revision != null && revision.matches("[1-9][0-9]*") && Long.parseLong(revision) > 0; }
        catch (NumberFormatException invalid) { return false; }
    }

    @Override
    public OrganizationLookupSnapshot findStaff(UUID staffId, String correlationId) {
        requireLookupIdentity(staffId, correlationId);
        try {
            var response = client.lookupStaff(staffId, tokens.bearerToken(), correlationId);
            StaffIdentityLookupResponse data = verifiedData(response, correlationId);
            if (data.exists() == null || data.active() == null) {
                throw invalidResponse();
            }
            if (!data.exists()) {
                if (data.active() || data.jobTitle() != null || data.departmentId() != null) {
                    throw invalidResponse();
                }
                return staffSnapshot(staffId, ReferenceState.NOT_FOUND, null, null);
            }
            if (data.departmentId() == null || data.jobTitle() == null
                    || data.jobTitle().isBlank()) {
                throw invalidResponse();
            }
            return staffSnapshot(staffId,
                    data.active() ? ReferenceState.ACTIVE : ReferenceState.INACTIVE,
                    data.jobTitle(), data.departmentId());
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (isUnambiguousNotFound(exception)) {
                return staffSnapshot(staffId, ReferenceState.NOT_FOUND, null, null);
            }
            throw new UpstreamUnavailableException("organization-service staff lookup is unavailable", exception);
        }
    }

    @Override
    public OrganizationLookupSnapshot findDepartment(UUID departmentId, String correlationId) {
        requireLookupIdentity(departmentId, correlationId);
        try {
            var response = client.lookupDepartment(departmentId, tokens.bearerToken(), correlationId);
            DepartmentLookupResponse data = verifiedData(response, correlationId);
            if (data.exists() == null || data.active() == null
                    || !departmentId.equals(data.departmentId())) {
                throw invalidResponse();
            }
            if (!data.exists()) {
                if (data.active() || data.departmentName() != null || data.departmentType() != null) {
                    throw invalidResponse();
                }
                return departmentSnapshot(departmentId, ReferenceState.NOT_FOUND);
            }
            if (data.departmentName() == null || data.departmentName().isBlank()
                    || data.departmentType() == null || data.departmentType().isBlank()) {
                throw invalidResponse();
            }
            return departmentSnapshot(departmentId,
                    data.active() ? ReferenceState.ACTIVE : ReferenceState.INACTIVE);
        } catch (UpstreamUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (isUnambiguousNotFound(exception)) {
                return departmentSnapshot(departmentId, ReferenceState.NOT_FOUND);
            }
            throw new UpstreamUnavailableException("organization-service department lookup is unavailable", exception);
        }
    }

    private OrganizationLookupSnapshot staffSnapshot(UUID staffId, ReferenceState state,
                                                     String jobTitle, UUID departmentId) {
        return new OrganizationLookupSnapshot(ReferenceKind.STAFF, staffId, state,
                clock.now(), null, jobTitle, departmentId);
    }

    private OrganizationLookupSnapshot departmentSnapshot(UUID departmentId, ReferenceState state) {
        return new OrganizationLookupSnapshot(ReferenceKind.DEPARTMENT, departmentId, state,
                clock.now(), null, null);
    }

    private static <T> T verifiedData(ResponseEntity<ApiResponse<T>> response, String correlationId) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()
                || !correlationId.equals(response.getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID))) {
            throw invalidResponse();
        }
        ApiResponse<T> body = response.getBody();
        if (body == null || !body.success() || body.error() != null || body.data() == null
                || !correlationId.equals(body.correlationId())) {
            throw invalidResponse();
        }
        return body.data();
    }

    private static void requireLookupIdentity(UUID id, String correlationId) {
        if (id == null || correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Organization lookup identity and correlation are required");
        }
    }

    private static UpstreamUnavailableException invalidResponse() {
        return new UpstreamUnavailableException("organization-service returned an invalid lookup response");
    }

    private static boolean isUnambiguousNotFound(Throwable failure) {
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
