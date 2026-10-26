package com.mediflow.clinical.application.port.out;

import java.util.function.Function;
import java.util.function.Supplier;

public interface IdempotentCommandPort {
    <T> Result<T> execute(String actorKey, String method, String canonicalPath, String idempotencyKey,
            String requestFingerprint, Class<T> responseType, int successStatus,
            Function<T, String> location, Supplier<T> command);

    record Result<T>(T data, int status, String location, boolean replayed) {}
}
