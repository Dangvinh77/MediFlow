package com.mediflow.gateway.filter;

import org.springframework.http.HttpMethod;

import java.util.Set;

/** Exact method-and-path allowlist for unauthenticated Gateway endpoints. */
public final class PublicEndpointPolicy {
    private PublicEndpointPolicy() { }

    public static boolean isPublic(String path, HttpMethod method) {
        if (method == null) return false;
        if ((HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method))
                && Set.of("/actuator/health", "/actuator/info").contains(path)) return true;
        if (HttpMethod.POST.equals(method)) {
            return Set.of("/api/v1/auth/login", "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").contains(path);
        }
        if (!HttpMethod.GET.equals(method)) return false;
        if (Set.of("/api/v1/demo/mailbox/config", "/api/v1/demo/mailbox/accounts").contains(path)) return true;
        String id = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
        return path.matches("/api/v1/demo/mailbox/accounts/" + id + "/messages")
                || path.matches("/api/v1/demo/mailbox/accounts/" + id + "/messages/" + id);
    }

    public static boolean isPublicAuthMutation(String path, HttpMethod method) {
        return HttpMethod.POST.equals(method)
                && Set.of("/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").contains(path);
    }

    public static boolean isDemoMailbox(String path, HttpMethod method) {
        return HttpMethod.GET.equals(method) && isPublic(path, method)
                && (path.equals("/api/v1/demo/mailbox/config") || path.startsWith("/api/v1/demo/mailbox/accounts"));
    }
}
