package com.mediflow.clinical.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.invocation.Invocation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.mediflow.clinical.application.event.AppointmentCreatedEvent;
import com.mediflow.clinical.application.event.AppointmentStatusChangedEvent;
import com.mediflow.clinical.application.event.DiagnosisAddedEvent;
import com.mediflow.clinical.application.event.MedicalRecordCreatedEvent;
import com.mediflow.clinical.application.port.out.ClinicalOutboxPort;
import com.mediflow.clinical.application.port.out.LegacyClinicalEventMetadata;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.config.ClinicalApplicationModeConfiguration;
import com.mediflow.clinical.domain.model.AppointmentStatus;

class ClinicalDurableAppointmentPublisherTest {

    private static final RabbitTemplate RABBIT_TEMPLATE = mock(RabbitTemplate.class);
    private static final ClinicalOutboxPort OUTBOX = mock(ClinicalOutboxPort.class);
    private static final Jackson2JsonMessageConverter CONVERTER = new Jackson2JsonMessageConverter();
    private static final byte[] EXPECTED_WIRE_BYTES = ("{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
            + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
            + "\"appointmentId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"patientId\":\"33333333-3333-3333-3333-333333333333\","
            + "\"doctorId\":\"44444444-4444-4444-4444-444444444444\","
            + "\"departmentId\":\"55555555-5555-5555-5555-555555555555\","
            + "\"appointmentDate\":[2026,10,15],\"appointmentTime\":\"09:30\"}")
            .getBytes(StandardCharsets.UTF_8);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PublisherConfiguration.class)
            .withPropertyValues("mediflow.clinical.durable-events.enabled=true",
                    "mediflow.features.outpatient-mvp.enabled=false");

    @BeforeEach
    void configureConverter() {
        org.mockito.Mockito.clearInvocations(RABBIT_TEMPLATE, OUTBOX);
        when(RABBIT_TEMPLATE.getMessageConverter()).thenReturn(CONVERTER);
    }

    @Test
    void durableAppointmentCreatedEnqueuesExactFlatWireBytesWithoutSendingToRabbit() {
        AppointmentCreatedEvent event = new AppointmentCreatedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-10-14T10:53:00Z"), "correlation-test",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                LocalDate.parse("2026-10-15"), LocalTime.parse("09:30"));

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(ClinicalEventPublisherAdapter.class).publishAppointmentCreated(event);
        });

        assertThat(org.mockito.Mockito.mockingDetails(OUTBOX).getInvocations()).hasSize(1);
        Invocation invocation = org.mockito.Mockito.mockingDetails(OUTBOX).getInvocations().iterator().next();
        assertThat(invocation.getArguments()).hasSize(2);
        assertThat(invocation.getArguments()[0]).isEqualTo(new LegacyClinicalEventMetadata(
                event.eventId(), AppointmentCreatedEvent.ROUTING_KEY, event.correlationId(),
                event.appointmentId(), event.occurredAt()));
        assertThat((byte[]) invocation.getArguments()[1]).containsExactly(EXPECTED_WIRE_BYTES);
        verify(RABBIT_TEMPLATE, never()).convertAndSend(
                eq(RabbitConfig.EVENTS_EXCHANGE), eq(AppointmentCreatedEvent.ROUTING_KEY), any(Object.class));
        verify(RABBIT_TEMPLATE, never()).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class));
    }

    @Test
    void activeOutpatientMvpProfileKeepsDurabilityOnWhenPropertyIsExplicitlyFalse() {
        AppointmentCreatedEvent event = event();
        ApplicationContextRunner mvpProfileRunner = new ApplicationContextRunner()
                .withUserConfiguration(PublisherConfiguration.class)
                .withPropertyValues("mediflow.clinical.durable-events.enabled=false")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("outpatient-mvp"));

        mvpProfileRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(ClinicalEventPublisherAdapter.class).publishAppointmentCreated(event);
        });

        assertDurableOutboxCall(event);
    }

    @Test
    void compatibilityDefaultKeepsAppointmentCreatedOnTheOriginalV0RabbitPath() {
        AppointmentCreatedEvent event = event();
        ApplicationContextRunner compatibilityRunner = new ApplicationContextRunner()
                .withUserConfiguration(PublisherConfiguration.class)
                .withPropertyValues("mediflow.clinical.durable-events.enabled=false",
                        "mediflow.features.outpatient-mvp.enabled=false");

        compatibilityRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(ClinicalEventPublisherAdapter.class).publishAppointmentCreated(event);
        });

        verify(RABBIT_TEMPLATE).convertAndSend(RabbitConfig.EVENTS_EXCHANGE,
                AppointmentCreatedEvent.ROUTING_KEY, event);
        verifyNoInteractions(OUTBOX);
    }

    @Test
    void allFourDurableLegacyPublishersKeepTheirFlatProducerBytesAndIdentifiers() {
        UUID appointmentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID patientId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID doctorId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID departmentId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID recordId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Instant occurredAt = Instant.parse("2026-10-14T10:53:00Z");
        AppointmentCreatedEvent created = new AppointmentCreatedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), occurredAt, "correlation-test",
                appointmentId, patientId, doctorId, departmentId,
                LocalDate.parse("2026-10-15"), LocalTime.parse("09:30"));
        AppointmentStatusChangedEvent status = new AppointmentStatusChangedEvent(
                UUID.fromString("77777777-7777-7777-7777-777777777777"), occurredAt, "correlation-test",
                appointmentId, recordId, patientId, departmentId, AppointmentStatus.ARRIVED);
        MedicalRecordCreatedEvent record = new MedicalRecordCreatedEvent(
                UUID.fromString("88888888-8888-8888-8888-888888888888"), occurredAt, "correlation-test",
                recordId, patientId, doctorId, departmentId, "Flu", LocalDate.parse("2026-10-14"));
        DiagnosisAddedEvent diagnosis = new DiagnosisAddedEvent(
                UUID.fromString("99999999-9999-9999-9999-999999999999"), occurredAt, "correlation-test",
                recordId, "J10.1", "Influenza");

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ClinicalEventPublisherAdapter publisher = context.getBean(ClinicalEventPublisherAdapter.class);
            publisher.publishAppointmentCreated(created);
            publisher.publishAppointmentStatusChanged(status);
            publisher.publishMedicalRecordCreated(record);
            publisher.publishDiagnosisAdded(diagnosis);
        });

        List<Invocation> invocations = new ArrayList<>(org.mockito.Mockito.mockingDetails(OUTBOX).getInvocations());
        assertThat(invocations).hasSize(4);
        assertLegacyInvocation(invocations.get(0), created.eventId(), AppointmentCreatedEvent.ROUTING_KEY,
                appointmentId, occurredAt,
                "{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
                        + "\"appointmentId\":\"22222222-2222-2222-2222-222222222222\","
                        + "\"patientId\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"doctorId\":\"44444444-4444-4444-4444-444444444444\","
                        + "\"departmentId\":\"55555555-5555-5555-5555-555555555555\","
                        + "\"appointmentDate\":[2026,10,15],\"appointmentTime\":\"09:30\"}");
        assertLegacyInvocation(invocations.get(1), status.eventId(), AppointmentStatusChangedEvent.ROUTING_KEY,
                appointmentId, occurredAt,
                "{\"eventId\":\"77777777-7777-7777-7777-777777777777\","
                        + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
                        + "\"appointmentId\":\"22222222-2222-2222-2222-222222222222\","
                        + "\"recordId\":\"66666666-6666-6666-6666-666666666666\","
                        + "\"patientId\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"departmentId\":\"55555555-5555-5555-5555-555555555555\","
                        + "\"status\":\"ARRIVED\"}");
        assertLegacyInvocation(invocations.get(2), record.eventId(), MedicalRecordCreatedEvent.ROUTING_KEY,
                recordId, occurredAt,
                "{\"eventId\":\"88888888-8888-8888-8888-888888888888\","
                        + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
                        + "\"recordId\":\"66666666-6666-6666-6666-666666666666\","
                        + "\"patientId\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"doctorId\":\"44444444-4444-4444-4444-444444444444\","
                        + "\"departmentId\":\"55555555-5555-5555-5555-555555555555\","
                        + "\"diagnosis\":\"Flu\",\"examinationDate\":[2026,10,14]}");
        assertLegacyInvocation(invocations.get(3), diagnosis.eventId(), DiagnosisAddedEvent.ROUTING_KEY,
                recordId, occurredAt,
                "{\"eventId\":\"99999999-9999-9999-9999-999999999999\","
                        + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
                        + "\"recordId\":\"66666666-6666-6666-6666-666666666666\","
                        + "\"diagnosisCode\":\"J10.1\",\"diagnosisName\":\"Influenza\"}");
        assertThat(org.mockito.Mockito.mockingDetails(RABBIT_TEMPLATE).getInvocations())
                .noneMatch(invocation -> invocation.getMethod().getName().equals("convertAndSend")
                        || invocation.getMethod().getName().equals("send"));
    }

    private static void assertLegacyInvocation(
            Invocation invocation,
            UUID eventId,
            String eventType,
            UUID aggregateId,
            Instant occurredAt,
            String expectedWire) {
        assertThat(invocation.getMethod().getName()).isEqualTo("appendLegacyEvent");
        assertThat(invocation.getArguments()[0]).isEqualTo(new LegacyClinicalEventMetadata(
                eventId, eventType, "correlation-test", aggregateId, occurredAt));
        assertThat(new String((byte[]) invocation.getArguments()[1], StandardCharsets.UTF_8)).isEqualTo(expectedWire);
    }

    private static AppointmentCreatedEvent event() {
        return new AppointmentCreatedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-10-14T10:53:00Z"), "correlation-test",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                LocalDate.parse("2026-10-15"), LocalTime.parse("09:30"));
    }

    private static void assertDurableOutboxCall(AppointmentCreatedEvent event) {
        assertThat(org.mockito.Mockito.mockingDetails(OUTBOX).getInvocations()).hasSize(1);
        Invocation invocation = org.mockito.Mockito.mockingDetails(OUTBOX).getInvocations().iterator().next();
        assertThat(invocation.getArguments()).hasSize(2);
        assertThat(invocation.getArguments()[0]).isEqualTo(new LegacyClinicalEventMetadata(
                event.eventId(), AppointmentCreatedEvent.ROUTING_KEY, event.correlationId(),
                event.appointmentId(), event.occurredAt()));
        assertThat((byte[]) invocation.getArguments()[1]).containsExactly(EXPECTED_WIRE_BYTES);
        verify(RABBIT_TEMPLATE, never()).convertAndSend(
                eq(RabbitConfig.EVENTS_EXCHANGE), eq(AppointmentCreatedEvent.ROUTING_KEY), any(Object.class));
        verify(RABBIT_TEMPLATE, never()).send(anyString(), anyString(), any(org.springframework.amqp.core.Message.class));
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({ClinicalEventPublisherAdapter.class, ClinicalApplicationModeConfiguration.class})
    static class PublisherConfiguration {
        @Bean
        RabbitTemplate rabbitTemplate() {
            return RABBIT_TEMPLATE;
        }

        @Bean
        ClinicalOutboxPort clinicalOutboxPort() {
            return OUTBOX;
        }
    }
}
