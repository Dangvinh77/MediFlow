package com.mediflow.surgery.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Caller verifies authorization before claim/replay and remains in one DB transaction. */
public interface SurgeryCommandReceiptPort {

    Claim claim(Key key, String fingerprint);

    void complete(UUID receiptId, UUID caseId, String responseCode, byte[] response, Instant at);

    record Key(String actorScope, String operation, String idempotencyKey) {
        public Key {
            if (actorScope == null || actorScope.isBlank() || actorScope.length() > 160
                    || operation == null || operation.isBlank() || operation.length() > 64
                    || idempotencyKey == null || idempotencyKey.isBlank()
                    || idempotencyKey.length() > 160) {
                throw new IllegalArgumentException("Command receipt key không hợp lệ");
            }
        }
    }

    enum State { NEW, REPLAY, CONFLICT, IN_PROGRESS }

    record Claim(State state, UUID receiptId, String responseCode, byte[] response) {
        public Claim {
            response = response == null ? null : response.clone();
        }

        @Override
        public byte[] response() {
            return response == null ? null : response.clone();
        }
    }
}
