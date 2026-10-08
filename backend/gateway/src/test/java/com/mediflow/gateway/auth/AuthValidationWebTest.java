package com.mediflow.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false"
        })
@AutoConfigureWebTestClient
class AuthValidationWebTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void login_emptyObject_returnsValidationDetails() {
        String correlationId = UUID.randomUUID().toString();

        webTestClient.post().uri("/api/v1/auth/login")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-Correlation-Id", correlationId)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals("X-Correlation-Id", correlationId)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.correlationId").isEqualTo(correlationId)
                .jsonPath("$.error.details.length()").isEqualTo(2);
    }

    @Test
    void login_missingFields_returnsValidationError() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"admin\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.error.details[0].field").isEqualTo("password");

        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"password\":\"password\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.error.details[0].field").isEqualTo("username");
    }

    @Test
    void login_blankFields_returnsValidationError() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\" \",\"password\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.error.details.length()").isEqualTo(2);
    }

    @Test
    void login_malformedJson_returnsValidationError() {
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void refresh_nullOrBlankToken_returnsValidationError() {
        for (String body : new String[]{"{\"refreshToken\":null}", "{\"refreshToken\":\"\"}"}) {
            webTestClient.post().uri("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.error.code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.error.details[0].field").isEqualTo("refreshToken");
        }
    }
}
