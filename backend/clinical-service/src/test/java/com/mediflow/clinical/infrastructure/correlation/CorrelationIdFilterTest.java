package com.mediflow.clinical.infrastructure.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import com.mediflow.common.security.JwtClaims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class CorrelationIdFilterTest {

    private final ThreadLocalCorrelationIdProvider provider = new ThreadLocalCorrelationIdProvider();
    private final CorrelationIdFilter filter = new CorrelationIdFilter(provider);

    @AfterEach
    void clearContext() {
        provider.clear();
    }

    @Test
    void filter_usesOnlyManagedProviderAndProviderStateIsInstanceScoped() {
        Constructor<?>[] constructors = CorrelationIdFilter.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(ThreadLocalCorrelationIdProvider.class);

        Field holder = Arrays.stream(ThreadLocalCorrelationIdProvider.class.getDeclaredFields())
                .filter(field -> field.getType().equals(ThreadLocal.class))
                .findFirst()
                .orElseThrow();
        assertThat(Modifier.isStatic(holder.getModifiers())).isFalse();
    }

    @Test
    void doFilter_validHeader_usesCanonicalIdAndClearsContext() throws Exception {
        UUID expected = UUID.randomUUID();
        MockHttpServletRequest request = requestWithHeader(expected.toString().toUpperCase());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(provider.currentOrCreate()).isEqualTo(expected);
        });

        assertThat(response.getHeader(JwtClaims.HEADER_CORRELATION_ID))
                .isEqualTo(expected.toString());
        assertThat(provider.peek()).isNull();
    }

    @Test
    void doFilter_missingHeader_generatesOneAndReusesItDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UUID[] observed = new UUID[2];

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            observed[0] = provider.currentOrCreate();
            observed[1] = provider.currentOrCreate();
        });

        assertThat(observed[0]).isNotNull();
        assertThat(observed[1]).isEqualTo(observed[0]);
        assertThat(response.getHeader(JwtClaims.HEADER_CORRELATION_ID))
                .isEqualTo(observed[0].toString());
        assertThat(provider.peek()).isNull();
    }

    @Test
    void doFilter_malformedHeader_replacesItWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = requestWithHeader("malformed-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(provider.currentOrCreate()).isNotNull();
        });

        UUID generated = UUID.fromString(
                response.getHeader(JwtClaims.HEADER_CORRELATION_ID));
        assertThat(generated).isNotNull();
        assertThat(response.getHeader(JwtClaims.HEADER_CORRELATION_ID))
                .isNotEqualTo("malformed-correlation-id");
        assertThat(provider.peek()).isNull();
    }

    @Test
    void doFilter_chainThrows_clearsContextBeforePropagating() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletException failure = new ServletException("boom");

        assertThatThrownBy(() -> filter.doFilter(request, response,
                throwingChain(failure)))
                .isSameAs(failure);

        assertThat(provider.peek()).isNull();
    }

    @Test
    void doFilter_nextRequest_doesNotInheritPreviousCorrelationId() throws Exception {
        UUID first = runAndReadResponseHeader(new MockHttpServletRequest());
        UUID second = runAndReadResponseHeader(new MockHttpServletRequest());

        assertThat(second).isNotEqualTo(first);
        assertThat(provider.peek()).isNull();
    }

    private UUID runAndReadResponseHeader(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, noOpChain());
        return UUID.fromString(response.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }

    private MockHttpServletRequest requestWithHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(JwtClaims.HEADER_CORRELATION_ID, value);
        return request;
    }

    private FilterChain noOpChain() {
        return (request, response) -> {
        };
    }

    private FilterChain throwingChain(ServletException failure) {
        return (request, response) -> {
            throw failure;
        };
    }
}
