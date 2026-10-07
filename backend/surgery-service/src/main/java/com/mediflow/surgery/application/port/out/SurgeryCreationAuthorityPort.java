package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase.Command;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** No production provider exists until referral relationship/requester/template authority is verified. */
public interface SurgeryCreationAuthorityPort {
    /** Must reauthorize EVERY attempt, including replay; account UUID is not requester staff UUID. */
    void authorize(Command command);

    /** Outside transactions: verify exact relationship, selected episode, requester and approved template. */
    Approval observe(Command command, String intentFingerprint);

    record Approval(String intentFingerprint, UUID templateId, long templateRevision,
                    Instant observedAt, Instant validUntil) {
        public Approval {
            if (intentFingerprint == null || !intentFingerprint.matches("[a-f0-9]{64}")
                    || templateRevision < 1) throw new IllegalArgumentException("Invalid creation authority proof");
            Objects.requireNonNull(templateId);
            Objects.requireNonNull(observedAt);
            Objects.requireNonNull(validUntil);
            if (!validUntil.isAfter(observedAt)) throw new IllegalArgumentException("Invalid creation authority interval");
        }
    }
}
