package com.mediflow.report.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CoreReportAuthorityAdapterTest {
    @Test @Timeout(5)
    void timeoutDeniesThenOpenCircuitStopsFurtherLookups() throws Exception {
        var requests=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/v1/org/me/capabilities",exchange->{
            requests.incrementAndGet();
            try { Thread.sleep(2000); } catch(InterruptedException ex) {Thread.currentThread().interrupt();}
            exchange.close();
        });
        server.start();
        try {
            var breaker=CircuitBreaker.of("timeout-test",CircuitBreakerConfig.custom().minimumNumberOfCalls(1)
                    .slidingWindowSize(1).failureRateThreshold(50).build());
            var adapter=new CoreReportAuthorityAdapter(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                    new ObjectMapper(),()->java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()),breaker,Duration.ofMillis(500));
            long started=System.nanoTime();
            assertThat(adapter.permits(UUID.randomUUID(),"DOCTOR",UUID.randomUUID(),"Bearer verified",null)).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(2));
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(adapter.permits(UUID.randomUUID(),"DOCTOR",UUID.randomUUID(),"Bearer verified",null)).isFalse();
            assertThat(requests.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test @Timeout(5)
    void repeatedForbiddenActorsDoNotOpenCircuitForAnotherCurrentActor() throws Exception {
        UUID revokedActor=UUID.randomUUID(), currentActor=UUID.randomUUID(), department=UUID.randomUUID();
        var requests=new AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var mapper=new ObjectMapper();
        var envelope=mapper.createObjectNode();
        envelope.put("success",true).putNull("error").put("timestamp",java.time.Instant.now().toString());
        var data=envelope.putObject("data");
        data.put("accountId",currentActor.toString()).put("staffId",UUID.randomUUID().toString())
                .put("role","DOCTOR").put("observedAt",java.time.Instant.now().toString());
        data.putArray("managementDepartments").addObject().put("departmentId",department.toString())
                .put("departmentName","Cardiology").put("appointmentId",UUID.randomUUID().toString()).put("revision",0);
        byte[] valid=envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/api/v1/org/me/capabilities",exchange->{
            int status=requests.incrementAndGet()<=5?403:200;
            byte[] bytes=status==200?valid:"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var breaker=CircuitBreaker.of("actor-denial-test",CircuitBreakerConfig.custom().minimumNumberOfCalls(5)
                    .slidingWindowSize(10).failureRateThreshold(50).build());
            var adapter=new CoreReportAuthorityAdapter(HttpClient.newHttpClient(),mapper,
                    ()->java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()),breaker,Duration.ofSeconds(2));
            for(int attempt=0;attempt<5;attempt++) {
                assertThat(adapter.permits(revokedActor,"DOCTOR",department,"Bearer revoked",null)).isFalse();
            }
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(adapter.permits(currentActor,"DOCTOR",department,"Bearer current",null)).isTrue();
            assertThat(requests.get()).isEqualTo(6);
        } finally { server.stop(0); }
    }

}
