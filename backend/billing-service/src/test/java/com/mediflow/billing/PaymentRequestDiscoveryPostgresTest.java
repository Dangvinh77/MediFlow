package com.mediflow.billing;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;
import javax.sql.DataSource;

import com.mediflow.billing.application.dto.command.PaymentRequestSourcePurpose;
import com.mediflow.billing.application.dto.command.PaymentRequestSourceQuery;
import com.mediflow.billing.infrastructure.persistence.adapter.PaymentRequestDiscoveryAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * L1 discovery against a real PostgreSQL snapshot (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md
 * §3). Each fixture is inserted exactly the way the real issuer adapters write it
 * (ExamChargeRepositoryAdapter / LabTestChargeRepositoryAdapter / PrescriptionChargeRepositoryAdapter),
 * not a simplified shape, so these tests fail if the discovery SQL drifts from the real schema.
 */
@Testcontainers
class PaymentRequestDiscoveryPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DataSource source;
    private static JdbcTemplate jdbc;
    private UUID patient, department, episode, account;

    @BeforeAll static void migrate() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }

    @BeforeEach void setup() {
        // Private ephemeral Testcontainers DB only, never the development database.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT, BILLING_EVENT_OUTBOX CASCADE");
        patient = UUID.randomUUID(); department = UUID.randomUUID(); episode = UUID.randomUUID();
        account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,'VND',now())
                """, account, patient, department, episode);
    }

    private java.util.Optional<com.mediflow.billing.application.dto.response.PaymentRequestDiscoveryDTO> discover(
            PaymentRequestSourceQuery query) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setReadOnly(true); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(5);
        return tx.execute(status -> new PaymentRequestDiscoveryAdapter(jdbc).findExact(query));
    }

    private UUID issueCharge(UUID chargeOwner, UUID sourceId, String priceCode) {
        UUID charge = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,?,?,?,?,1,?,?,now())
                """, charge, chargeOwner, patient, department, priceCode, sourceId, priceCode, priceCode + " fixture",
                new BigDecimal("150000.00"), new BigDecimal("150000.00"));
        return charge;
    }

    private UUID issueExam(UUID appointmentId) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency) VALUES (?,?,?,'EXAM',?,'VND')",
                request, invoice, account, new BigDecimal("150000.00"));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,appointment_id) VALUES (?,?)", request, appointmentId);
        var charge = issueCharge(account, appointmentId, "EXAM");
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal("150000.00"));
        jdbc.update("""
                INSERT INTO exam_charge_source(appointment_id,source_fingerprint,payment_request_id,requested_at)
                VALUES (?,?,?,now())
                """, appointmentId, "a".repeat(64), request);
        return request;
    }

    private UUID issueLabTest(UUID labId) {
        UUID request = UUID.randomUUID(), invoice = UUID.randomUUID(), sourceOrderId = UUID.randomUUID();
        jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency) VALUES (?,?,?,'LAB_TEST',?,'VND')",
                request, invoice, account, new BigDecimal("80000.00"));
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,lab_test_ids) VALUES (?,?)");
            statement.setObject(1, request);
            statement.setArray(2, connection.createArrayOf("uuid", new UUID[]{labId}));
            return statement;
        });
        var charge = issueCharge(account, labId, "LAB_TEST");
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                request, charge, new BigDecimal("80000.00"));
        jdbc.update("""
                INSERT INTO lab_test_charge_source(lab_id,source_order_id,source_fingerprint,payment_request_id,requested_at)
                VALUES (?,?,?,?,now())
                """, labId, sourceOrderId, "b".repeat(64), request);
        return request;
    }

    private UUID issuePrescription(UUID prescriptionId, String careContext) {
        UUID charge = issueCharge(account, prescriptionId, "PRESCRIPTION");
        UUID request = null, invoice = null;
        if ("OUTPATIENT".equals(careContext)) {
            request = UUID.randomUUID(); invoice = UUID.randomUUID();
            jdbc.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency) VALUES (?,?,?,'PRESCRIPTION',?,'VND')",
                    request, invoice, account, new BigDecimal("50000.00"));
            jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,prescription_id) VALUES (?,?)", request, prescriptionId);
            jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                    request, charge, new BigDecimal("50000.00"));
        }
        jdbc.update("""
                INSERT INTO prescription_charge_source(prescription_id,source_fingerprint,account_id,charge_id,payment_request_id,invoice_id,care_context,created_at_iso)
                VALUES (?,?,?,?,?,?,?,?)
                """, prescriptionId, "c".repeat(64), account, charge, request, invoice, careContext, "2026-10-16T08:00:00Z");
        return request;
    }

    private void pay(UUID request, String amount) {
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,?,'PAYMENT','SERVICE_PAYMENT','COMPLETED',?,'VND','CASH',?,now())
                """, UUID.randomUUID(), account, request, new BigDecimal(amount), UUID.randomUUID().toString());
    }

    @Test void discover_examSource_exactMatchReturnsRequestWithRemainingAmount() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        var result = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode));
        assertThat(result).isPresent();
        var dto = result.get();
        assertThat(dto.paymentRequestId()).isEqualTo(request);
        assertThat(dto.purpose()).isEqualTo("EXAM"); assertThat(dto.sourceType()).isEqualTo("EXAM");
        assertThat(dto.appointmentId()).isEqualTo(appointment); assertThat(dto.labTestIds()).isEmpty();
        assertThat(dto.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT"); assertThat(dto.careEpisodeId()).isEqualTo(episode);
        assertThat(dto.requestedAmount()).isEqualByComparingTo("150000.00");
        assertThat(dto.paidAmount()).isEqualByComparingTo("0"); assertThat(dto.remainingAmount()).isEqualByComparingTo("150000.00");
        assertThat(dto.status()).isEqualTo("PENDING"); assertThat(dto.accountStatus()).isEqualTo("OPEN");
        assertThat(dto.currency()).isEqualTo("VND"); assertThat(dto.observedAt()).isNotNull();
    }

    @Test void discover_labTestSource_exactMatchReturnsSingletonLabIdSlice() {
        UUID labId = UUID.randomUUID();
        UUID request = issueLabTest(labId);
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.LAB_TEST, labId, patient, episode)).orElseThrow();
        assertThat(dto.paymentRequestId()).isEqualTo(request);
        assertThat(dto.labTestIds()).containsExactly(labId);
        assertThat(dto.appointmentId()).isNull(); assertThat(dto.prescriptionId()).isNull();
    }

    @Test void discover_prescriptionOutpatientSource_exactMatchReturnsRequest() {
        UUID prescriptionId = UUID.randomUUID();
        UUID request = issuePrescription(prescriptionId, "OUTPATIENT");
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.PRESCRIPTION, prescriptionId, patient, episode)).orElseThrow();
        assertThat(dto.paymentRequestId()).isEqualTo(request);
        assertThat(dto.prescriptionId()).isEqualTo(prescriptionId);
    }

    @Test void discover_prescriptionAdmissionContext_hasNoOutpatientRequest_isNotFound() {
        UUID prescriptionId = UUID.randomUUID();
        issuePrescription(prescriptionId, "ADMISSION");
        var result = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.PRESCRIPTION, prescriptionId, patient, episode));
        assertThat(result).isEmpty();
    }

    @Test void discover_asynchronousSourceNotYetIssued_isNotFoundNotZeroAmount() {
        var result = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, UUID.randomUUID(), patient, episode));
        assertThat(result).isEmpty();
    }

    @Test void discover_wrongPatientExpectation_isNotFoundNotAnotherPatientsRequest() {
        UUID appointment = UUID.randomUUID();
        issueExam(appointment);
        var result = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, UUID.randomUUID(), episode));
        assertThat(result).isEmpty();
    }

    @Test void discover_wrongCareEpisodeExpectation_isNotFound() {
        UUID appointment = UUID.randomUUID();
        issueExam(appointment);
        var result = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, UUID.randomUUID()));
        assertThat(result).isEmpty();
    }

    @Test void discover_partialPayment_remainingAmountReflectsCompletedPaymentOnly() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        pay(request, "60000.00");
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)).orElseThrow();
        assertThat(dto.paidAmount()).isEqualByComparingTo("60000.00"); assertThat(dto.remainingAmount()).isEqualByComparingTo("90000.00");
    }

    @Test void discover_paidInFull_stillReturnedWithPaidStatusNotHidden() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        pay(request, "150000.00");
        jdbc.update("UPDATE PAYMENT_REQUEST SET status='PAID' WHERE payment_request_id=?", request);
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)).orElseThrow();
        assertThat(dto.status()).isEqualTo("PAID"); assertThat(dto.remainingAmount()).isEqualByComparingTo("0");
    }

    @Test void discover_refundAfterPaidInFull_doesNotReopenRemainingAmount() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        pay(request, "150000.00");
        jdbc.update("UPDATE PAYMENT_REQUEST SET status='PAID' WHERE payment_request_id=?", request);
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                SELECT gen_random_uuid(),?,?,'REFUND','SERVICE_PAYMENT','COMPLETED',150000.00,'VND','CASH',?,transaction_id,now()
                FROM PAYMENT_TRANSACTION WHERE payment_request_id=? LIMIT 1
                """, account, request, UUID.randomUUID().toString(), request);
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)).orElseThrow();
        assertThat(dto.paidAmount()).isEqualByComparingTo("150000.00"); assertThat(dto.remainingAmount()).isEqualByComparingTo("0");
        assertThat(dto.status()).isEqualTo("PAID");
    }

    @Test void discover_expiredOrCancelledRequest_stillReturnedWithItsStatus() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        jdbc.update("UPDATE PAYMENT_REQUEST SET status='EXPIRED' WHERE payment_request_id=?", request);
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)).orElseThrow();
        assertThat(dto.status()).isEqualTo("EXPIRED");
    }

    @Test void discover_closedAccount_stillReturnedWithAccountStatus() {
        UUID appointment = UUID.randomUUID();
        issueExam(appointment);
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='CLOSED',closed_at=now() WHERE account_id=?", account);
        var dto = discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)).orElseThrow();
        assertThat(dto.accountStatus()).isEqualTo("CLOSED");
    }

    @Test void discover_sameSourceQueriedTwice_isDeterministicAndHasNoSideEffect() {
        UUID appointment = UUID.randomUUID();
        issueExam(appointment);
        var query = new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode);
        var first = discover(query).orElseThrow();
        var second = discover(query).orElseThrow();
        assertThat(second.paymentRequestId()).isEqualTo(first.paymentRequestId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX", Long.class)).isZero();
    }

    @Test void discover_brokenRequestTargetLink_isReportedAsUnavailableNotAnEmptyResult() {
        UUID appointment = UUID.randomUUID();
        UUID request = issueExam(appointment);
        // Direct SQL corruption: the target row a correct issuer always writes in the same transaction is gone.
        jdbc.update("DELETE FROM PAYMENT_REQUEST_TARGET WHERE payment_request_id=?", request);
        assertThatThrownBy(() -> discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.EXAM, appointment, patient, episode)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void discover_purposeCorruptedOnStoredRequest_isReportedAsUnavailableNotTheWrongPurpose() {
        UUID labId = UUID.randomUUID();
        UUID request = issueLabTest(labId);
        jdbc.update("UPDATE PAYMENT_REQUEST SET purpose='EXAM' WHERE payment_request_id=?", request);
        assertThatThrownBy(() -> discover(new PaymentRequestSourceQuery(PaymentRequestSourcePurpose.LAB_TEST, labId, patient, episode)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
