package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.infrastructure.client.LabReleaseAuthorityFeignClient.ReleaseAuthorityBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** Organization's answer is the only way through; every failure or odd answer denies release. */
class LabReleaseAuthorityAdapterTest {
    static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    final LabReleaseAuthorityFeignClient client = mock(LabReleaseAuthorityFeignClient.class);
    final LabReleaseAuthorityAdapter adapter = new LabReleaseAuthorityAdapter(client,
            Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneOffset.UTC), SECRET, CircuitBreaker.ofDefaults("t"));
    final UUID account = UUID.randomUUID();
    final UUID staff = UUID.randomUUID();

    void answer(ReleaseAuthorityBody body) {
        when(client.authority(eq(account), any(), any())).thenReturn(ApiResponse.ok(body, "c"));
    }

    @Test void activeStaffWithCapabilityMayRelease() {
        answer(new ReleaseAuthorityBody(account, true, staff, true, true, Instant.now()));
        assertThat(adapter.canRelease(account, staff, "c")).isTrue();
    }

    @Test void missingCapabilityOrInactiveOrOtherStaffIsDenied() {
        answer(new ReleaseAuthorityBody(account, true, staff, true, false, Instant.now()));
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
        answer(new ReleaseAuthorityBody(account, false, staff, true, true, Instant.now()));
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
        answer(new ReleaseAuthorityBody(account, true, staff, false, true, Instant.now()));
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
        answer(new ReleaseAuthorityBody(account, true, UUID.randomUUID(), true, true, Instant.now()));
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
    }

    @Test void timeoutOrErrorIsDeniedNotAllowed() {
        when(client.authority(eq(account), any(), any())).thenThrow(new RuntimeException("Read timed out"));
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
        when(client.authority(eq(account), any(), any())).thenReturn(null);
        assertThat(adapter.canRelease(account, staff, "c")).isFalse();
    }
}
