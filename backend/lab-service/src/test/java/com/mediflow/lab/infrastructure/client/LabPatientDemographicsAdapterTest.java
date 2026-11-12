package com.mediflow.lab.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.port.out.PatientDemographicsPort.PatientDemographics;
import com.mediflow.lab.infrastructure.client.LabPatientDemographicsFeignClient.DemographicsBody;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/** Patient's gender code becomes the reference-range code; anything doubtful becomes "unknown". */
class LabPatientDemographicsAdapterTest {
    static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    static final LocalDate DOB = LocalDate.of(1990, 1, 2);
    final LabPatientDemographicsFeignClient client = mock(LabPatientDemographicsFeignClient.class);
    final LabPatientDemographicsAdapter adapter = new LabPatientDemographicsAdapter(client,
            Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC), SECRET, CircuitBreaker.ofDefaults("t"));
    final UUID patient = UUID.randomUUID();

    void answer(DemographicsBody body) {
        when(client.demographics(eq(patient), any(), any())).thenReturn(ApiResponse.ok(body, "c"));
    }

    @Test void genderCodesMapToReferenceRangeSexes() {
        answer(new DemographicsBody(patient, "F", DOB));
        assertThat(adapter.find(patient, "c")).contains(new PatientDemographics("FEMALE", DOB));
        answer(new DemographicsBody(patient, "M", DOB));
        assertThat(adapter.find(patient, "c")).contains(new PatientDemographics("MALE", DOB));
    }

    @Test void unknownGenderKeepsTheAgeButNoSex() {
        answer(new DemographicsBody(patient, "X", DOB));
        assertThat(adapter.find(patient, "c")).contains(new PatientDemographics(null, DOB));
    }

    @Test void answerAboutAnotherPatientIsIgnored() {
        answer(new DemographicsBody(UUID.randomUUID(), "F", DOB));
        assertThat(adapter.find(patient, "c")).isEmpty();
    }

    @Test void timeoutErrorOrEmptyAnswerIsUnknown() {
        when(client.demographics(eq(patient), any(), any())).thenThrow(new RuntimeException("Read timed out"));
        assertThat(adapter.find(patient, "c")).isEmpty();
        when(client.demographics(eq(patient), any(), any())).thenReturn(null);
        assertThat(adapter.find(patient, "c")).isEmpty();
        when(client.demographics(eq(patient), any(), any())).thenReturn(ApiResponse.ok(null, "c"));
        assertThat(adapter.find(patient, "c")).isEmpty();
    }

    @Test void callsPatientWithAShortLivedServiceToken() {
        answer(new DemographicsBody(patient, "F", DOB));
        adapter.find(patient, "corr-1");
        org.mockito.Mockito.verify(client).demographics(eq(patient),
                org.mockito.ArgumentMatchers.startsWith("Bearer "), eq("corr-1"));
    }
}
