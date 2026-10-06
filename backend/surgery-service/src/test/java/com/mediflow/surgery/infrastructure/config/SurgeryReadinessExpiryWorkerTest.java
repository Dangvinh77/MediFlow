package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ExpireSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase;
import com.mediflow.surgery.application.port.in.QueryExpiredSurgeryReadinessUseCase.Candidate;
import com.mediflow.surgery.application.port.in.RecordSurgeryReadinessExpiryRetryUseCase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SurgeryReadinessExpiryWorkerTest {
    private final QueryExpiredSurgeryReadinessUseCase query = mock(QueryExpiredSurgeryReadinessUseCase.class);
    private final ExpireSurgeryReadinessUseCase expire = mock(ExpireSurgeryReadinessUseCase.class);
    private final RecordSurgeryReadinessExpiryRetryUseCase retry = mock(RecordSurgeryReadinessExpiryRetryUseCase.class);
    private final Candidate first = new Candidate(UUID.randomUUID(), UUID.randomUUID());
    private final Candidate second = new Candidate(UUID.randomUUID(), UUID.randomUUID());

    @Test void runBatch_failedCandidateIsDeferredAndDoesNotStopAnotherCase() {
        when(query.findDue(20)).thenReturn(List.of(first, second));
        when(expire.expire(eq(first), anyString())).thenThrow(new IllegalStateException("Private clinical data"));
        new SurgeryReadinessExpiryWorker(query, expire, retry, 20).runBatch();
        verify(retry).defer(first, "IllegalStateException");
        verify(expire).expire(eq(second), anyString());
        verifyNoMoreInteractions(retry);
    }

    @Test void runBatch_retryStorageFailureDoesNotStopNextCase() {
        when(query.findDue(20)).thenReturn(List.of(first, second));
        when(expire.expire(eq(first), anyString())).thenThrow(new IllegalStateException());
        doThrow(new IllegalStateException()).when(retry).defer(first, "IllegalStateException");
        new SurgeryReadinessExpiryWorker(query, expire, retry, 20).runBatch();
        verify(expire).expire(eq(second), anyString());
    }

    @Test void runBatch_noOpCandidateDoesNotCreateRetry() {
        when(query.findDue(20)).thenReturn(List.of(first));
        when(expire.expire(eq(first), anyString())).thenReturn(false);
        new SurgeryReadinessExpiryWorker(query, expire, retry, 20).runBatch();
        verifyNoInteractions(retry);
    }

    @Test void runBatch_queryFailureDoesNotMutate() {
        when(query.findDue(20)).thenThrow(new IllegalStateException("Private storage details"));
        new SurgeryReadinessExpiryWorker(query, expire, retry, 20).runBatch();
        verifyNoInteractions(expire, retry);
    }

    @Test void construct_invalidBatchIsRejected() {
        assertThatThrownBy(() -> new SurgeryReadinessExpiryWorker(query, expire, retry, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurgeryReadinessExpiryWorker(query, expire, retry, 101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void configure_workerRequiresBothFlagsAndIsDisabledByDefault() {
        for (boolean business : List.of(false, true)) {
            for (boolean job : List.of(false, true)) {
                context().withPropertyValues("mediflow.features.surgery.enabled=" + business,
                        "mediflow.surgery.readiness.expiry.enabled=" + job).run(application -> {
                    assertThat(application).hasNotFailed();
                    assertThat(application.getBeansOfType(SurgeryReadinessExpiryWorker.class))
                            .hasSize(business && job ? 1 : 0);
                });
            }
        }
        context().run(application -> assertThat(application).doesNotHaveBean(SurgeryReadinessExpiryWorker.class));
    }

    @Test void configure_testProfileDoesNotStartWorker() {
        context().withPropertyValues("spring.profiles.active=test", "mediflow.features.surgery.enabled=true",
                "mediflow.surgery.readiness.expiry.enabled=true")
                .run(application -> assertThat(application).doesNotHaveBean(SurgeryReadinessExpiryWorker.class));
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(SurgeryReadinessExpiryConfiguration.class)
                .withBean(QueryExpiredSurgeryReadinessUseCase.class, () -> query)
                .withBean(ExpireSurgeryReadinessUseCase.class, () -> expire)
                .withBean(RecordSurgeryReadinessExpiryRetryUseCase.class, () -> retry)
                .withPropertyValues("mediflow.surgery.readiness.expiry.poll-interval-ms=600000");
    }
}
