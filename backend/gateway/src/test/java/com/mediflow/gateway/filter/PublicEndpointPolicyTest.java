package com.mediflow.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointPolicyTest {
    @Test
    void onlyExactRecoveryMethodsArePublic() {
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/forgot-password", HttpMethod.POST)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/reset-password", HttpMethod.POST)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/login", HttpMethod.POST)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/refresh", HttpMethod.POST)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/login/extra", HttpMethod.POST)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/forgot-password/extra", HttpMethod.POST)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/reset-password", HttpMethod.GET)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/auth/forgot-password", HttpMethod.PUT)).isFalse();
    }

    @Test
    void actuatorAllowlistDoesNotExposeNestedHealthRoutes() {
        assertThat(PublicEndpointPolicy.isPublic("/actuator/health", HttpMethod.GET)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/actuator/info", HttpMethod.HEAD)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/actuator/health/env", HttpMethod.GET)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/actuator/info/env", HttpMethod.GET)).isFalse();
    }

    @Test
    void mailboxAllowsOnlyItsReadRoutesAndUuidShapes() {
        String account = "2a6d16a1-8cbb-48bd-893d-84ba9876c3d2";
        String message = "904a65b8-028b-466a-8cb0-9d209c9dad8c";
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/config", HttpMethod.GET)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts", HttpMethod.GET)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts/" + account + "/messages", HttpMethod.GET)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts/" + account + "/messages/" + message, HttpMethod.GET)).isTrue();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts/not-a-uuid/messages", HttpMethod.GET)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts/" + account + "/messages/" + message + "/extra", HttpMethod.GET)).isFalse();
        assertThat(PublicEndpointPolicy.isPublic("/api/v1/demo/mailbox/accounts", HttpMethod.DELETE)).isFalse();
    }
}
