package com.mediflow.lab.infrastructure.security;

import com.mediflow.common.security.JwtClaims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class LabLifecycleServiceCredentialTest {
    private static final String SECRET = "lifecycle-test-secret-longer-than-32-bytes";
    private static final String PATH = "/api/v2/lab/source-orders/00000000-0000-4000-8000-000000000001/lifecycle";
    private final JwtAuthFilter filter = new JwtAuthFilter(new JwtProperties(SECRET));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void currentClinicalServiceCredentialCanOnlyAuthenticateExactLifecycleRead() throws Exception {
        run("GET", PATH, "clinical-service", 60, false);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting("authority").containsExactly("ROLE_SYSTEM");
        for (String[] route : new String[][]{{"POST", PATH}, {"GET", "/api/v1/lab"}, {"GET", PATH + "/extra"}}) {
            clear(); run(route[0], route[1], "clinical-service", 60, false);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Test void otherSubjectLongLifetimeAndHumanIdentityClaimsCannotAuthenticate() throws Exception {
        for (String subject : new String[]{"billing-service", "gateway"}) {
            run("GET", PATH, subject, 60, false); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); clear();
        }
        run("GET", PATH, "clinical-service", 61, false); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull(); clear();
        run("GET", PATH, "clinical-service", 60, true); assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void run(String method, String path, String subject, long ttl, boolean staffClaim) throws Exception {
        Instant now = Instant.now();
        var token = Jwts.builder().subject(subject).claim(JwtClaims.ROLE, "SYSTEM")
                .claim(JwtClaims.TYPE, JwtClaims.SERVICE_TOKEN_TYPE).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttl)));
        if (staffClaim) token.claim(JwtClaims.STAFF_ID, java.util.UUID.randomUUID().toString());
        String compact = token.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        var request = new MockHttpServletRequest(method, path); request.addHeader("Authorization", "Bearer " + compact);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
