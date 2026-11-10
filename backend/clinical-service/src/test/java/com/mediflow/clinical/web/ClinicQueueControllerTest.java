package com.mediflow.clinical.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.response.ClinicQueueTicketDTO;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.ClinicQueueApplicationService;
import com.mediflow.clinical.domain.model.ClinicQueueTicketStatus;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.OutpatientReservationStatus;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

class ClinicQueueControllerTest {
    private static final UUID REGISTRATION = UUID.randomUUID();
    private static final String PATH = "/api/v2/outpatient-registrations/" + REGISTRATION + "/room-queue-ticket";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Nested
    @WebMvcTest(value = {ClinicQueueController.class, VitalSignController.class}, properties = {"mediflow.clinical.scheduling.enabled=true", "mediflow.v2.clinic-intake=true"})
    @Import({MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
    class WhenEnabled {
        @Autowired MockMvc mvc;
        @MockBean ClinicQueueApplicationService queue;
        @MockBean com.mediflow.clinical.application.service.VitalSignApplicationService vitals;

        @Test void vitalSignsAreRecordedByNursesOnly() throws Exception {
            String path = "/api/v2/outpatient-registrations/" + REGISTRATION + "/vital-signs";
            mvc.perform(post(path).with(csrf()).with(user("doctor").roles("DOCTOR")).header("Idempotency-Key", "k")
                    .contentType("application/json").content("{\"pulseBpm\":80}")).andExpect(status().isForbidden());
            verifyNoInteractions(vitals);
        }

        @Test void issueRequiresIdempotencyKey() throws Exception {
            mvc.perform(post(PATH).with(csrf()).with(user("nurse").roles("NURSE"))).andExpect(status().isBadRequest());
            verifyNoInteractions(queue);
        }

        @ParameterizedTest @ValueSource(strings = {"ADMIN", "DOCTOR", "CASHIER", "PATIENT"})
        void issueIsNurseOnly(String role) throws Exception {
            mvc.perform(post(PATH).with(csrf()).with(user("actor").roles(role)).header("Idempotency-Key", "k")).andExpect(status().isForbidden());
            verifyNoInteractions(queue);
        }

        @Test void nurseGetsCreatedTicket() throws Exception {
            var dto = new ClinicQueueTicketDTO(UUID.randomUUID(), 1, REGISTRATION, UUID.randomUUID(), LocalDate.of(2026, 11, 10), 5,
                    ClinicQueueTicketStatus.WAITING, false, Instant.parse("2026-11-10T01:00:00Z"), null);
            when(queue.issue(eq(REGISTRATION), any())).thenReturn(new IdempotentCommandPort.Result<>(dto, 201, null, false));
            mvc.perform(post(PATH).with(csrf()).with(user("nurse").roles("NURSE")).header("Idempotency-Key", "k"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roomQueueNumber").value(5));
        }
    }

    @Test void controllerAbsentWhenFlagOff() {
        new ApplicationContextRunner().withBean(ClinicQueueApplicationService.class, () -> org.mockito.Mockito.mock(ClinicQueueApplicationService.class))
                .withBean(com.mediflow.clinical.application.port.out.CorrelationIdProvider.class, () -> org.mockito.Mockito.mock(com.mediflow.clinical.application.port.out.CorrelationIdProvider.class))
                .withUserConfiguration(ClinicQueueController.class)
                .withPropertyValues("mediflow.clinical.scheduling.enabled=true", "mediflow.v2.clinic-intake=false")
                .run(context -> assertThat(context).doesNotHaveBean(ClinicQueueController.class));
    }

    @Test void roomQueueNumberIsOmittedWithoutTicket() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var without = new OutpatientRegistrationDTO(UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2026, 11, 10), java.time.LocalTime.of(8, 0), java.time.LocalTime.of(11, 0),
                OutpatientReceptionMode.AT_COUNTER, OutpatientVisitType.NEW_VISIT, null, OutpatientRegistrationStatus.CONFIRMED, UUID.randomUUID(), 1,
                OutpatientReservationStatus.CONFIRMED, Instant.parse("2026-11-10T01:15:00Z"), null, 3, false, Instant.parse("2026-11-10T01:00:00Z"),
                Instant.parse("2026-11-10T01:00:00Z"));
        assertThat(mapper.readTree(mapper.writeValueAsString(without)).has("roomQueueNumber")).isFalse();
        assertThat(mapper.readTree(mapper.writeValueAsString(without.withRoomQueueNumber(4))).get("roomQueueNumber").asInt()).isEqualTo(4);
    }
}
