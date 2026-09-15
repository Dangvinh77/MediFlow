package com.mediflow.clinical.infrastructure.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

final class UpstreamExceptionClassifier {

    enum Classification {
        NOT_FOUND,
        UNAVAILABLE
    }

    private UpstreamExceptionClassifier() {
    }

    static Classification classify(Throwable failure) {
        boolean notFound = contains(failure, UpstreamExceptionClassifier::isNotFound);
        boolean unavailable = contains(failure, UpstreamExceptionClassifier::isUnavailable);
        return notFound && !unavailable ? Classification.NOT_FOUND : Classification.UNAVAILABLE;
    }

    private static boolean isNotFound(Throwable failure) {
        return failure instanceof FeignException.NotFound
                || failure instanceof FeignException feignException && feignException.status() == 404;
    }

    private static boolean isUnavailable(Throwable failure) {
        return failure instanceof RetryableException
                || failure instanceof CallNotPermittedException
                || failure instanceof SocketTimeoutException
                || failure instanceof ConnectException
                || failure instanceof TimeoutException
                || failure instanceof IOException
                || failure instanceof FeignException feignException && feignException.status() >= 500;
    }

    private static boolean contains(Throwable failure, Predicate<Throwable> predicate) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (predicate.test(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
