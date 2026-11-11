package com.mediflow.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.dto.request.CapabilityGrantCommand;
import com.mediflow.organization.application.dto.request.CapabilityRevocationCommand;
import com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO;
import com.mediflow.organization.application.exception.SchedulingScopeDeniedException;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository;
import com.mediflow.organization.application.port.out.SchedulingAuthorityRepository.AccountAuthority;
import com.mediflow.organization.application.port.out.StaffCapabilityGrantRepository;
import com.mediflow.organization.application.service.StaffCapabilityGrantService;
import com.mediflow.organization.domain.model.Role;

class StaffCapabilityGrantServiceTest {
    private static final String RELEASE = "LAB_RESULT_RELEASE";
    private static final String RECORD_REVIEW = "MEDICAL_RECORD_REVIEW";

    private final FakeGrants grants = new FakeGrants();
    private final SchedulingAuthorityRepository authority = mock(SchedulingAuthorityRepository.class);
    private final StaffCapabilityGrantService service = new StaffCapabilityGrantService(grants, authority);

    private final UUID adminId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void admin() {
        when(authority.lockAccount(adminId)).thenReturn(Optional.of(new AccountAuthority(adminId, true, Role.ADMIN, null)));
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.LAB_TECH, true, true));
    }

    @Test
    void grantsOnlyToActiveLabTech() {
        UUID nurse = UUID.randomUUID();
        grants.accounts.put(nurse, new Person(UUID.randomUUID(), nurse, Role.NURSE, true, true));
        UUID inactive = UUID.randomUUID();
        grants.accounts.put(inactive, new Person(UUID.randomUUID(), inactive, Role.LAB_TECH, false, true));

        assertThat(service.grant(adminId, Role.ADMIN, staffId, command()).capability()).isEqualTo(RELEASE);
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, nurse, command()))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, inactive, command()))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");
    }

    @Test
    void grantsRecordReviewToActiveDoctor() {
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, true, true));

        var grant = service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "Hospital record reviewer"));

        assertThat(grant.capability()).isEqualTo(RECORD_REVIEW);
        var authority = service.recordReviewAuthority(accountId);
        assertThat(authority.actorAccountId()).isEqualTo(accountId);
        assertThat(authority.staffId()).isEqualTo(staffId);
        assertThat(authority.canReviewRecords()).isTrue();
    }

    @Test
    void recordReviewRefusedForNurseLabTechOrInactiveStaff() {
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.NURSE, true, true));
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "review")))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.LAB_TECH, true, true));
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "review")))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, false, true));
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "review")))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, true, false));
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "review")))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");
    }

    @Test
    void labReleaseStillOnlyForLabTech() {
        assertThat(service.grant(adminId, Role.ADMIN, staffId, command()).capability()).isEqualTo(RELEASE);

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, true, true));
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId, command()))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_INELIGIBLE");
    }

    @Test
    void onlyActiveAdminCanGrant() {
        when(authority.lockAccount(adminId)).thenReturn(Optional.of(new AccountAuthority(adminId, false, Role.ADMIN, null)));

        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId, command()))
                .isInstanceOf(SchedulingScopeDeniedException.class);
        assertThat(grants.rows).isEmpty();
    }

    @Test
    void unknownCapabilityIsRejected() {
        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId, new CapabilityGrantCommand("PRESCRIBE", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminAccountItselfGetsNoReleaseAuthority() {
        UUID adminStaff = UUID.randomUUID();
        UUID adminAccount = UUID.randomUUID();
        grants.accounts.put(adminStaff, new Person(adminAccount, adminStaff, Role.ADMIN, true, true));
        grants.rows.put(UUID.randomUUID(), new FakeGrants.Row(adminStaff, RELEASE));

        assertThat(service.labReleaseAuthority(adminAccount).canReleaseLabResults()).isFalse();
    }

    @Test
    void oneActiveGrantPerStaffAndCapability() {
        service.grant(adminId, Role.ADMIN, staffId, command());

        assertThatThrownBy(() -> service.grant(adminId, Role.ADMIN, staffId, command()))
                .isInstanceOf(DuplicateResourceException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_ACTIVE");
    }

    @Test
    void revokedGrantGivesNoAuthority() {
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, true, true));
        var grant = service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "Hospital record reviewer"));
        assertThat(service.recordReviewAuthority(accountId).canReviewRecords()).isTrue();

        service.revoke(adminId, Role.ADMIN, grant.grantId(), new CapabilityRevocationCommand("Assignment ended"));

        assertThat(service.recordReviewAuthority(accountId).canReviewRecords()).isFalse();
    }

    @Test
    void recordReviewAuthorityRequiresActiveDoctorDespiteRetainedGrant() {
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, true, true));
        service.grant(adminId, Role.ADMIN, staffId,
                new CapabilityGrantCommand(RECORD_REVIEW, "Hospital record reviewer"));
        assertThat(service.recordReviewAuthority(accountId).canReviewRecords()).isTrue();

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.DOCTOR, false, true));
        assertThat(service.recordReviewAuthority(accountId).canReviewRecords()).isFalse();

        grants.accounts.put(staffId, new Person(accountId, staffId, Role.ADMIN, true, true));
        assertThat(service.recordReviewAuthority(accountId).canReviewRecords()).isFalse();
    }

    @Test
    void revokedGrantStopsAuthorityImmediately() {
        StaffCapabilityGrantDTO grant = service.grant(adminId, Role.ADMIN, staffId, command());
        assertThat(service.labReleaseAuthority(accountId).canReleaseLabResults()).isTrue();

        service.revoke(adminId, Role.ADMIN, grant.grantId(), new CapabilityRevocationCommand("Chuyen cong tac"));

        assertThat(service.labReleaseAuthority(accountId).canReleaseLabResults()).isFalse();
        assertThat(service.grant(adminId, Role.ADMIN, staffId, command()).grantId()).isNotEqualTo(grant.grantId());
    }

    @Test
    void inactiveStaffHasNoAuthorityDespiteGrant() {
        service.grant(adminId, Role.ADMIN, staffId, command());
        grants.accounts.put(staffId, new Person(accountId, staffId, Role.LAB_TECH, false, true));

        var view = service.labReleaseAuthority(accountId);

        assertThat(view.staffActive()).isFalse();
        assertThat(view.canReleaseLabResults()).isFalse();
    }

    @Test
    void unknownAccountHasNoAuthority() {
        var view = service.labReleaseAuthority(UUID.randomUUID());

        assertThat(view.accountActive()).isFalse();
        assertThat(view.canReleaseLabResults()).isFalse();
    }

    @Test
    void grantAndRevocationRecordActorAndReason() {
        StaffCapabilityGrantDTO grant = service.grant(adminId, Role.ADMIN, staffId, command());
        assertThat(grant.grantedBy()).isEqualTo(adminId);
        assertThat(grant.grantReason()).isEqualTo("Duoc phan cong");
        assertThat(grant.grantedAt()).isNotNull();

        StaffCapabilityGrantDTO revoked =
                service.revoke(adminId, Role.ADMIN, grant.grantId(), new CapabilityRevocationCommand("Chuyen cong tac"));

        assertThat(revoked.revokedBy()).isEqualTo(adminId);
        assertThat(revoked.revocationReason()).isEqualTo("Chuyen cong tac");
        assertThat(revoked.revokedAt()).isNotNull();
    }

    @Test
    void secondRevocationIsRejected() {
        StaffCapabilityGrantDTO grant = service.grant(adminId, Role.ADMIN, staffId, command());
        service.revoke(adminId, Role.ADMIN, grant.grantId(), new CapabilityRevocationCommand("a"));

        assertThatThrownBy(() -> service.revoke(adminId, Role.ADMIN, grant.grantId(), new CapabilityRevocationCommand("b")))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("CAPABILITY_GRANT_ALREADY_REVOKED");
    }

    private static CapabilityGrantCommand command() {
        return new CapabilityGrantCommand(RELEASE, "Duoc phan cong");
    }

    private record Person(UUID accountId, UUID staffId, Role role, boolean staffActive, boolean accountActive) {}

    private static final class FakeGrants implements StaffCapabilityGrantRepository {
        record Row(UUID staffId, String capability) {}

        final Map<UUID, Person> accounts = new HashMap<>();
        final Map<UUID, Row> rows = new HashMap<>();
        final Map<UUID, StaffCapabilityGrantDTO> grants = new HashMap<>();

        @Override
        public Optional<GrantTarget> lockTarget(UUID staffId) {
            return Optional.ofNullable(accounts.get(staffId))
                    .map(p -> new GrantTarget(p.staffId(), p.role(), p.staffActive(), p.accountActive()));
        }

        @Override
        public boolean insertIfNoActive(StaffCapabilityGrantDTO grant) {
            if (rows.containsValue(new Row(grant.staffId(), grant.capability()))) {
                return false;
            }
            rows.put(grant.grantId(), new Row(grant.staffId(), grant.capability()));
            grants.put(grant.grantId(), grant);
            return true;
        }

        @Override
        public Optional<StaffCapabilityGrantDTO> lockGrant(UUID grantId) {
            return Optional.ofNullable(grants.get(grantId));
        }

        @Override
        public void revoke(UUID grantId, UUID actorId, String reason, Instant at) {
            rows.remove(grantId);
            var g = grants.get(grantId);
            grants.put(grantId, new StaffCapabilityGrantDTO(g.grantId(), g.staffId(), g.capability(), g.grantedBy(),
                    g.grantReason(), g.grantedAt(), actorId, reason, at));
        }

        @Override
        public LabReleaseData findLabRelease(UUID accountId) {
            return accounts.values().stream().filter(p -> p.accountId().equals(accountId)).findFirst()
                    .map(p -> new LabReleaseData(true, p.accountActive(), p.role(), p.staffId(), p.staffActive(),
                            rows.containsValue(new Row(p.staffId(), RELEASE))))
                    .orElse(new LabReleaseData(false, false, null, null, false, false));
        }

        @Override
        public RecordReviewData findRecordReview(UUID accountId) {
            return accounts.values().stream().filter(p -> p.accountId().equals(accountId)).findFirst()
                    .map(p -> new RecordReviewData(true, p.accountActive(), p.role(), p.staffId(), p.staffActive(),
                            rows.containsValue(new Row(p.staffId(), RECORD_REVIEW))))
                    .orElse(new RecordReviewData(false, false, null, null, false, false));
        }
    }
}
