package com.mediflow.clinical.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestTemplate;

class AuthorizationForwardingInterceptorTest {

    private final AuthorizationForwardingInterceptor interceptor = new AuthorizationForwardingInterceptor();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void apply_requestHasBearerToken_forwardsIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer test-token");
    }

    @Test
    void apply_noWebRequest_leavesHeadersEmpty() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }
}
