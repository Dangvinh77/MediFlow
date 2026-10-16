package com.mediflow.pharmacy.infrastructure.client;

import static org.assertj.core.api.Assertions.*;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort;
import com.mediflow.pharmacy.infrastructure.config.PharmacyClearanceAuthorityConfiguration;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real Feign transport with unmodified Billing fixtures. Not a live Billing permission acceptance. */
@SpringBootTest(classes=PharmacyBillingClearanceHttpTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"mediflow.features.care-finance-v2=true","mediflow.pharmacy.clearance-authority.enabled=true",
                "eureka.client.enabled=false","spring.cloud.discovery.enabled=false",
                "spring.cloud.openfeign.circuitbreaker.enabled=true",
                "spring.cloud.openfeign.client.config.pharmacyBillingClearanceClient.connectTimeout=500",
                "spring.cloud.openfeign.client.config.pharmacyBillingClearanceClient.readTimeout=500"})
class PharmacyBillingClearanceHttpTest {
    private static final String SECRET="billing-clearance-http-test-secret-at-least-32-bytes";
    private static final Instant NOW=Instant.parse("2026-10-06T08:00:00Z");
    private static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000046");
    private static final String CORRELATION="00000000-0000-0000-0000-000000000009";
    private static final AtomicReference<Reply> REPLY=new AtomicReference<>();
    private static final AtomicReference<String> AUTH=new AtomicReference<>(),PATH=new AtomicReference<>(),TRACE=new AtomicReference<>();
    private static HttpServer server;
    @Autowired PrescriptionClearanceAuthorityPort authority;
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,RabbitAutoConfiguration.class})
    @Import(PharmacyClearanceAuthorityConfiguration.class)
    static class Config {
        @Bean Clock clock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
        @Bean JwtProperties jwt(){return new JwtProperties(SECRET);}
    }
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry registry)throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/billing/financial-clearances/",exchange->{
            AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));PATH.set(exchange.getRequestURI().getPath());
            TRACE.set(exchange.getRequestHeaders().getFirst("X-Correlation-Id"));var reply=REPLY.get();
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id",reply.header());
            exchange.sendResponseHeaders(reply.status(),reply.body().length);
            try(var output=exchange.getResponseBody()){output.write(reply.body());}
        });
        server.start();registry.add("mediflow.pharmacy.billing.base-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
    }
    @AfterAll static void close(){if(server!=null)server.stop(0);}
    @ParameterizedTest @ValueSource(strings={"active","inactive","missing"})
    void exactLookupPathAndSignedServiceCredentialPreserveOriginalProducerBytes(String file)throws Exception {
        REPLY.set(new Reply(200,Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/clearance-authority-v1/"+file+".json")),CORRELATION));
        var observed=authority.lookup(ID,CORRELATION);
        assertThat(observed.exists()).isEqualTo(!file.equals("missing"));
        assertThat(observed.eligible()).isEqualTo(file.equals("active"));
        assertThat(PATH.get()).isEqualTo("/api/v1/billing/financial-clearances/"+ID+"/lookup");
        assertThat(TRACE.get()).isEqualTo(CORRELATION);
        var claims=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(()->Date.from(NOW)).build().parseSignedClaims(AUTH.get().substring(7)).getPayload();
        assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
        assertThat(claims).containsEntry("role","SYSTEM").containsEntry("type","service");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
        assertThat(claims).doesNotContainKeys("patientId","staffId","departmentId");
    }
    @ParameterizedTest @ValueSource(ints={401,403,404,500,503})
    void transportFailureNeverBecomesAbsentOrEligible(int status) {
        REPLY.set(new Reply(status,"PRIVATE".getBytes(StandardCharsets.UTF_8),CORRELATION));
        assertThatThrownBy(()->authority.lookup(ID,CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class).hasNoCause();
    }
    private record Reply(int status,byte[] body,String header){}
}
