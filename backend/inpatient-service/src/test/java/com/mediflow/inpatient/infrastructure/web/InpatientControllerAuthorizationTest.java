package com.mediflow.inpatient.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class InpatientControllerAuthorizationTest {

    private static final String BASE_PATH = InpatientController.class.getAnnotation(RequestMapping.class).value()[0];

    @Test
    void everySpecRouteHasItsExactRoleMatrix() {
        Map<String, String> actual = new LinkedHashMap<>();
        Arrays.stream(InpatientController.class.getDeclaredMethods())
                .filter(InpatientControllerAuthorizationTest::isHttpHandler)
                .forEach(method -> {
                    PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
                    assertThat(authorization)
                            .as("@PreAuthorize on %s", method.getName())
                            .isNotNull();
                    actual.put(route(method), authorization.value());
                });

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("POST /api/v1/inpatient/admissions", "hasAnyRole('ADMIN', 'DOCTOR')"),
                Map.entry("GET /api/v1/inpatient/admissions/{id}",
                        "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'CASHIER')"),
                Map.entry("GET /api/v1/inpatient/admissions",
                        "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE', 'CASHIER')"),
                Map.entry("PUT /api/v1/inpatient/admissions/{id}/bed", "hasAnyRole('ADMIN', 'NURSE')"),
                Map.entry("PUT /api/v1/inpatient/admissions/{id}/bed/transfer",
                        "hasAnyRole('ADMIN', 'NURSE')"),
                Map.entry("PUT /api/v1/inpatient/admissions/{id}/bed/release",
                        "hasAnyRole('ADMIN', 'NURSE')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/admit",
                        "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/treatments",
                        "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/treatments/{entryId}/corrections",
                        "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/order-references",
                        "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/medical-discharge",
                        "hasAnyRole('ADMIN', 'DOCTOR')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/close",
                        "hasAnyRole('ADMIN', 'CASHIER')"),
                Map.entry("POST /api/v1/inpatient/admissions/{id}/cancel",
                        "hasAnyRole('ADMIN', 'DOCTOR')"),
                Map.entry("POST /api/v1/inpatient/beds", "hasAnyRole('ADMIN', 'MANAGER')"),
                Map.entry("PUT /api/v1/inpatient/beds/{id}", "hasAnyRole('ADMIN', 'MANAGER')"),
                Map.entry("GET /api/v1/inpatient/beds",
                        "hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")));
    }

    private static boolean isHttpHandler(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class);
    }

    private static String route(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return "GET " + BASE_PATH + method.getAnnotation(GetMapping.class).value()[0];
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST " + BASE_PATH + method.getAnnotation(PostMapping.class).value()[0];
        }
        return "PUT " + BASE_PATH + method.getAnnotation(PutMapping.class).value()[0];
    }
}
