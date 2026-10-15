package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;

/** PostgreSQL transaction coverage for atomic receipt/effect rollback and redelivery. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class ClinicalIntegrationTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ReactToLabResultUseCase integration;
    @Autowired private ConsumerInboxBoundary inbox;
    @Autowired private InboxEvidenceFactory evidenceFactory;
    @Autowired private ExternalResultPersistenceAdapter externalResults;
    @Autowired private JdbcTemplate jdbc;

    @MockBean
    private AttachExternalResultUseCase attachments;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedEffect_rollsBackDeliveryAndOperationThenRetryCommitsOneAttachment() {
        assertThat(AopUtils.isAopProxy(inbox)).isTrue();

        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        insertMedicalRecord(recordId);

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("database unavailable");
            }
            externalResults.attach(recordId, ExternalResultType.LAB, labId, "Normal");
            return true;
        }).when(attachments).attachLabResult(recordId, labId, "Normal");

        byte[] body = ("{\"eventId\":\"" + eventId + "\",\"eventType\":\"lab.result.created\","
                + "\"recordId\":\"" + recordId + "\",\"labId\":\"" + labId
                + "\",\"conclusion\":\"Normal\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ConsumerInboxClaim claim = evidenceFactory.create("clinical-lab-result", "lab.result.created",
                eventId, "clinical:lab-result:v0:" + labId, body);

        assertThatThrownBy(() -> inbox.execute(claim, () -> integration.onLabResultCreated(
                new com.mediflow.clinical.application.dto.command.LabResultCreatedCommand(
                        eventId, recordId, labId, "Normal"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("consumer_inbox_delivery", eventId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_operation WHERE operation_key = ?",
                Integer.class, "clinical:lab-result:v0:" + labId)).isZero();

        inbox.execute(claim, () -> integration.onLabResultCreated(
                new com.mediflow.clinical.application.dto.command.LabResultCreatedCommand(
                        eventId, recordId, labId, "Normal")));

        assertThat(attempts).hasValue(2);
        assertThat(count("consumer_inbox_delivery", eventId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_operation WHERE operation_key = ?",
                Integer.class, "clinical:lab-result:v0:" + labId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attached_result WHERE record_id = ? AND type = ? AND reference_id = ?",
                Integer.class, recordId, "LAB", labId)).isOne();
    }

    private void insertMedicalRecord(UUID recordId) {
        jdbc.update("""
                INSERT INTO medical_record(record_id, patient_id, doctor_id, department_id,
                                           examination_date, symptoms)
                VALUES (?, ?, ?, ?, ?, ?)
                """, recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "Fever");
    }

    private int count(String table, UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE event_id = ?",
                Integer.class, eventId);
    }
}
