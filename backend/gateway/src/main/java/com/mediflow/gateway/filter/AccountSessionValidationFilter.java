package com.mediflow.gateway.filter;

import com.mediflow.common.security.Roles;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.gateway.auth.OrganizationAuthClient;
import com.mediflow.gateway.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Objects;
import java.util.UUID;

/** No cache: the next authorized request observes a committed lock/password/identity change. */
@Component
public class AccountSessionValidationFilter implements GlobalFilter, Ordered {
    private final JwtTokenService jwt;
    private final OrganizationAuthClient accounts;
    private final GatewayErrorResponseWriter errors;

    public AccountSessionValidationFilter(JwtTokenService jwt, OrganizationAuthClient accounts,
            GatewayErrorResponseWriter errors) {
        this.jwt = jwt;
        this.accounts = accounts;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Authentication sets this only for protected requests; public auth and health bypass it.
        if (exchange.getAttribute(RouteAuthorizationFilter.ROLE_ATTRIBUTE) == null
                || Roles.SYSTEM.equals(exchange.getAttribute(RouteAuthorizationFilter.ROLE_ATTRIBUTE))) return chain.filter(exchange);
        final Claims claims;
        final UUID accountId;
        final long version;
        try {
            String bearer = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            claims = jwt.parse(bearer.substring(7));
            accountId = UUID.fromString(claims.getSubject());
            Number value = claims.get(JwtTokenService.CREDENTIAL_VERSION_CLAIM, Number.class);
            version = value == null ? 0 : value.longValue();
            if (version < 0) return rejected(exchange);
        } catch (Exception exception) { return rejected(exchange); }
        String correlationId = exchange.getRequest().getHeaders().getFirst(JwtClaims.HEADER_CORRELATION_ID);
        // Error recovery applies to the lookup only: downstream failures retain their own response semantics.
        return accounts.lookupCurrentIdentity(accountId, correlationId)
                .map(identity -> new Decision(identity, null))
                .switchIfEmpty(Mono.just(new Decision(null, HttpStatus.SERVICE_UNAVAILABLE)))
                .onErrorResume(exception -> Mono.just(new Decision(null,
                        exception instanceof OrganizationAuthClient.UpstreamTimeoutException
                                ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE)))
                .flatMap(decision -> {
                    if (decision.error() != null) return errors.write(exchange, decision.error(),
                            "AUTH_UPSTREAM_UNAVAILABLE", "Chưa thể xác minh trạng thái phiên. Hãy thử lại.");
                    var identity = decision.identity();
                    if (!identity.exists() || !identity.active() || !accountId.equals(identity.accountId())
                            || version != identity.credentialVersion()
                            || !Objects.equals(identity.role(), claims.get(JwtClaims.ROLE, String.class))
                            || !same(identity.staffId(), claims, JwtClaims.STAFF_ID)
                            || !same(identity.departmentId(), claims, JwtClaims.DEPARTMENT_ID)
                            || !same(identity.patientId(), claims, JwtClaims.PATIENT_ID)) return rejected(exchange);
                    return chain.filter(exchange);
                });
    }

    private boolean same(UUID current, Claims claims, String key) {
        return Objects.equals(current == null ? null : current.toString(), claims.get(key, String.class));
    }

    private Mono<Void> rejected(ServerWebExchange exchange) {
        return errors.write(exchange, HttpStatus.UNAUTHORIZED, "AUTH_SESSION_REVOKED",
                "Phiên truy cập không còn hợp lệ. Vui lòng đăng nhập lại.");
    }

    private record Decision(OrganizationAuthClient.CurrentIdentity identity, HttpStatus error) { }

    @Override
    public int getOrder() { return 1; }
}
