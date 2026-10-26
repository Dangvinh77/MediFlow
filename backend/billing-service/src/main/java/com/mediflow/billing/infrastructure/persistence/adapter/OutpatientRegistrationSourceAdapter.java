package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.service.IssueOutpatientQuoteService;
import com.mediflow.billing.application.port.out.OutpatientRegistrationSourcePort;
import com.mediflow.billing.application.port.out.PriceCatalogPort.PriceSnapshot;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.domain.model.RegistrationPaymentSourceSnapshot;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutpatientRegistrationSourceAdapter implements OutpatientRegistrationSourcePort {
    private static final String SOURCE_SELECT = "SELECT * FROM outpatient_registration_source WHERE registration_id=?";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ObjectProvider<IssueOutpatientQuoteService> quoteIssuer;
    private final boolean quotesEnabled;

    public OutpatientRegistrationSourceAdapter(JdbcTemplate jdbc, ObjectMapper mapper,
            ObjectProvider<IssueOutpatientQuoteService> quoteIssuer,
            @Value("${mediflow.billing.outpatient-quotes.enabled:false}") boolean quotesEnabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.quoteIssuer = quoteIssuer;
        this.quotesEnabled = quotesEnabled;
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public boolean requiresPrice(UUID registrationId) {
        List<Boolean> rows = jdbc.query("""
                SELECT payment_request_id IS NULL AND registration_status='AWAITING_PAYMENT'
                  FROM outpatient_registration_source WHERE registration_id=?
                """, (rs, row) -> rs.getBoolean(1), registrationId);
        return rows.isEmpty() || rows.getFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void apply(OutpatientRegistrationChangedEvent event, byte[] rawEnvelope,
                      String payloadFingerprint, PriceSnapshot price, OutpatientQuoteLine quoteLine) {
        var fact = event.payload();
        jdbc.update("""
                INSERT INTO outpatient_registration_event_receipt(event_id,registration_id,registration_revision,payload_sha256,raw_envelope)
                VALUES (?,?,?,?,?) ON CONFLICT(event_id) DO NOTHING
                """, event.eventId(), fact.registrationId(), fact.registrationRevision(), payloadFingerprint, rawEnvelope);
        byte[] previousBytes = jdbc.query("SELECT raw_envelope FROM outpatient_registration_event_receipt WHERE event_id=?",
                (rs, row) -> rs.getBytes(1), event.eventId()).getFirst();
        if (!Arrays.equals(previousBytes, rawEnvelope)) conflict("BILLING_REGISTRATION_EVENT_CONFLICT");

        jdbc.execute("SET LOCAL lock_timeout='3s'");
        Source current = findSource(fact.registrationId(), true).orElse(null);
        if (current == null) {
            if (fact.registrationRevision() != 1 && !terminal(fact.status())) {
                conflict("BILLING_REGISTRATION_REVISION_GAP");
            }
            insertSource(event, payloadFingerprint);
            current = findSource(fact.registrationId(), true).orElseThrow();
        } else if (fact.registrationRevision() < current.revision()) {
            return;
        } else if (fact.registrationRevision() == current.revision()) {
            if (!payloadFingerprint.equals(current.payloadFingerprint())) conflict("BILLING_REGISTRATION_REVISION_CONFLICT");
        } else {
            if (fact.registrationRevision() != current.revision() + 1) conflict("BILLING_REGISTRATION_REVISION_GAP");
            if (!sameImmutableBinding(fact, current)) conflict("BILLING_REGISTRATION_SOURCE_CONFLICT");
            if (terminal(current.registrationStatus()) && !terminal(fact.status())) {
                conflict("BILLING_REGISTRATION_TERMINAL_CONFLICT");
            }
            updateSource(event, payloadFingerprint);
            current = findSource(fact.registrationId(), true).orElseThrow();
        }
        if (current.paymentRequestId() == null && "AWAITING_PAYMENT".equals(fact.status())
                && "HELD".equals(fact.reservationStatus())) {
            if (price == null && quoteLine == null) {
                jdbc.update("UPDATE outpatient_registration_source SET request_issue_status='PENDING_PRICE',updated_at=? WHERE registration_id=?",
                        Timestamp.from(fact.changedAt()), fact.registrationId());
            } else {
                createPaymentRequest(event, price, quoteLine);
            }
        }
        closeRequestIfSourceTerminal(fact.registrationId(), fact.status(), fact.changedAt());
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public Optional<RegistrationPaymentSourceSnapshot> findPaymentRequestByRegistration(UUID registrationId) {
        return jdbc.query("""
                SELECT s.registration_id,s.reservation_id,s.patient_id,s.department_id,s.source_type,s.source_id,
                       s.care_episode_type,s.care_episode_id,s.payment_request_id,s.invoice_id,s.account_id,
                       pr.requested_amount,pr.currency,pr.status,pr.expires_at,
                       COALESCE((SELECT SUM(t.amount) FROM PAYMENT_TRANSACTION t
                                  WHERE t.payment_request_id=pr.payment_request_id
                                    AND t.transaction_type='PAYMENT' AND t.status='COMPLETED'),0) AS paid_amount,
                       statement_timestamp() AS observed_at
                  FROM outpatient_registration_source s
                  LEFT JOIN PAYMENT_REQUEST pr ON pr.payment_request_id=s.payment_request_id
                 WHERE s.registration_id=?
                """, (rs, row) -> {
                    UUID requestId = uuid(rs, "payment_request_id");
                    if (requestId == null) return null;
                    BigDecimal requested = rs.getBigDecimal("requested_amount");
                    BigDecimal paid = rs.getBigDecimal("paid_amount");
                    if (requested == null || paid == null || uuid(rs, "invoice_id") == null
                            || uuid(rs, "account_id") == null) {
                        throw new org.springframework.dao.DataIntegrityViolationException(
                                "Scheduled registration source has an incomplete request binding");
                    }
                    return new RegistrationPaymentSourceSnapshot(requestId, uuid(rs, "invoice_id"),
                            uuid(rs, "account_id"), uuid(rs, "registration_id"), uuid(rs, "reservation_id"),
                            uuid(rs, "patient_id"), uuid(rs, "department_id"), rs.getString("source_type"),
                            uuid(rs, "source_id"), rs.getString("care_episode_type"), uuid(rs, "care_episode_id"),
                            requested.toPlainString(), paid.toPlainString(),
                            requested.subtract(paid).max(BigDecimal.ZERO).toPlainString(), rs.getString("currency").trim(),
                            rs.getString("status"), instant(rs, "expires_at"), rs.getTimestamp("observed_at").toInstant());
                }, registrationId).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    private void createPaymentRequest(OutpatientRegistrationChangedEvent event, PriceSnapshot price,
                                      OutpatientQuoteLine quoteLine) {
        var fact = event.payload();
        if (quoteLine != null) {
            if (!quotesEnabled || !fact.registrationId().equals(quoteLine.sourceId())
                    || !fact.sourceType().equals(quoteLine.sourceType()) || !fact.priceCode().equals(quoteLine.priceCode())) {
                conflict("BILLING_REGISTRATION_QUOTE_CONFLICT");
            }
        } else if (price == null || price.description() == null || price.description().isBlank()
                || price.description().length() > 255 || price.unitAmount() == null || price.unitAmount().signum() <= 0
                || price.unitAmount().stripTrailingZeros().scale() > 2
                || price.unitAmount().precision() - price.unitAmount().scale() > 17) {
            jdbc.update("UPDATE outpatient_registration_source SET request_issue_status='PRICE_INVALID',updated_at=? WHERE registration_id=?",
                    Timestamp.from(fact.changedAt()), fact.registrationId());
            return;
        }
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,?,?,'VND',?) ON CONFLICT(care_episode_type,care_episode_id) DO NOTHING
                """, accountId, fact.patientId(), fact.departmentId(), "OUTPATIENT_VISIT", fact.registrationId(),
                Timestamp.from(fact.changedAt()));
        var accountRows = jdbc.query("""
                SELECT account_id,patient_id,department_id,status,currency FROM BILLING_ACCOUNT
                 WHERE care_episode_type='OUTPATIENT_VISIT' AND care_episode_id=? FOR UPDATE
                """, (rs, row) -> new Object[]{uuid(rs, "account_id"), uuid(rs, "patient_id"),
                    uuid(rs, "department_id"), rs.getString("status"), rs.getString("currency").trim()}, fact.registrationId());
        if (accountRows.isEmpty()) throw new org.springframework.dao.DataIntegrityViolationException("Billing account was not opened");
        Object[] account = accountRows.getFirst();
        if (!fact.patientId().equals(account[1]) || !fact.departmentId().equals(account[2])
                || !"OPEN".equals(account[3]) || !"VND".equals(account[4])) {
            conflict("BILLING_SCHEDULED_ACCOUNT_CONFLICT");
        }
        accountId = (UUID) account[0];
        UUID requestId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        BigDecimal amount = quoteLine == null ? price.unitAmount() : quoteLine.patientPayable();
        BigDecimal gross = quoteLine == null ? amount : quoteLine.grossAmount();
        BigDecimal quantity = quoteLine == null ? BigDecimal.ONE : quoteLine.quantity();
        BigDecimal unitAmount = quoteLine == null ? amount : quoteLine.unitPrice();
        String description = quoteLine == null ? price.description() : quoteLine.description();
        java.time.Instant requestExpiresAt = fact.reservationExpiresAt();
        if (quoteLine != null) {
            java.time.Instant quoteMaximum = fact.changedAt().plusSeconds(15 * 60L);
            if (quoteMaximum.isBefore(requestExpiresAt)) requestExpiresAt = quoteMaximum;
            if (!requestExpiresAt.isAfter(fact.changedAt())) {
                jdbc.update("UPDATE outpatient_registration_source SET request_issue_status='PRICE_INVALID',updated_at=? WHERE registration_id=?",
                        Timestamp.from(fact.changedAt()), fact.registrationId());
                return;
            }
        }
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency,
                    expires_at,created_at)
                VALUES (?,?,?,'EXAM',?,'VND',?,?)
                """, requestId, invoiceId, accountId, amount, Timestamp.from(requestExpiresAt),
                Timestamp.from(fact.changedAt()));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,record_id) VALUES (?,?)", requestId, fact.registrationId());
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                    description,quantity,unit_amount,gross_amount,incurred_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, chargeId, accountId, fact.patientId(), fact.departmentId(), "EXAM", fact.registrationId(),
                fact.priceCode(), description, quantity, unitAmount, gross, Timestamp.from(fact.changedAt()));
        jdbc.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,?)",
                requestId, chargeId, amount);
        if (quoteLine != null) {
            IssueOutpatientQuoteService issuer = quoteIssuer.getIfAvailable();
            if (issuer == null) throw new IllegalStateException("Outpatient quote issuer is not configured");
            issuer.issue(requestId, fact.patientId(), fact.careEpisodeId(), List.of(quoteLine),
                    fact.changedAt(), requestExpiresAt);
        }
        int updated = jdbc.update("""
                UPDATE outpatient_registration_source
                   SET payment_request_id=?,invoice_id=?,account_id=?,request_issue_status='READY',updated_at=?
                 WHERE registration_id=? AND payment_request_id IS NULL AND registration_status='AWAITING_PAYMENT'
                """, requestId, invoiceId, accountId, Timestamp.from(fact.changedAt()), fact.registrationId());
        if (updated != 1) conflict("BILLING_REGISTRATION_REQUEST_RACE");
    }

    private void closeRequestIfSourceTerminal(UUID registrationId, String status, java.time.Instant at) {
        String requestStatus = switch (status) {
            case "CANCELLED" -> "CANCELLED";
            case "EXPIRED" -> "EXPIRED";
            default -> null;
        };
        if (requestStatus == null) return;
        jdbc.update("""
                UPDATE PAYMENT_REQUEST SET status=?,completed_at=?
                 WHERE payment_request_id=(SELECT payment_request_id FROM outpatient_registration_source WHERE registration_id=?)
                   AND status='PENDING'
                """, requestStatus, Timestamp.from(at), registrationId);
    }

    private Optional<Source> findSource(UUID registrationId, boolean forUpdate) {
        return jdbc.query(SOURCE_SELECT + (forUpdate ? " FOR UPDATE" : ""), SOURCE_MAPPER, registrationId).stream().findFirst();
    }

    private void insertSource(OutpatientRegistrationChangedEvent event, String fingerprint) {
        var f = event.payload();
        jdbc.update("""
                INSERT INTO outpatient_registration_source(registration_id,registration_revision,patient_id,doctor_id,
                    department_id,session_id,room_id,session_date,start_time,end_time,visit_type,reservation_id,
                    reservation_status,registration_status,checked_in_at,queue_number,reservation_expires_at,price_code,
                    source_type,source_id,care_episode_type,care_episode_id,cancellation,changed_at,payload_fingerprint)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)
                """, f.registrationId(), f.registrationRevision(), f.patientId(), f.doctorId(), f.departmentId(),
                f.sessionId(), f.roomId(), f.sessionDate(), f.startTime(), f.endTime(), f.visitType(), f.reservationId(),
                f.reservationStatus(), f.status(), timestamp(f.checkedInAt()), f.queueNumber(),
                Timestamp.from(f.reservationExpiresAt()), f.priceCode(), f.sourceType(), f.sourceId(), f.careEpisodeType(),
                f.careEpisodeId(), cancellationJson(f.cancellation()), Timestamp.from(f.changedAt()), fingerprint);
    }

    private void updateSource(OutpatientRegistrationChangedEvent event, String fingerprint) {
        var f = event.payload();
        int updated = jdbc.update("""
                UPDATE outpatient_registration_source SET registration_revision=?,doctor_id=?,department_id=?,session_id=?,
                    room_id=?,session_date=?,start_time=?,end_time=?,visit_type=?,reservation_id=?,reservation_status=?,
                    registration_status=?,checked_in_at=?,queue_number=?,reservation_expires_at=?,price_code=?,
                    cancellation=?::jsonb,changed_at=?,payload_fingerprint=?,updated_at=? WHERE registration_id=?
                """, f.registrationRevision(), f.doctorId(), f.departmentId(), f.sessionId(), f.roomId(), f.sessionDate(),
                f.startTime(), f.endTime(), f.visitType(), f.reservationId(), f.reservationStatus(), f.status(),
                timestamp(f.checkedInAt()), f.queueNumber(), Timestamp.from(f.reservationExpiresAt()), f.priceCode(),
                cancellationJson(f.cancellation()), Timestamp.from(f.changedAt()), fingerprint, Timestamp.from(f.changedAt()),
                f.registrationId());
        if (updated != 1) conflict("BILLING_REGISTRATION_SOURCE_CONFLICT");
    }

    private String cancellationJson(OutpatientRegistrationChangedEvent.Cancellation cancellation) {
        if (cancellation == null) return null;
        try { return mapper.writeValueAsString(cancellation); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize cancellation evidence", exception); }
    }

    private static boolean terminal(String status) {
        return "CANCELLED".equals(status) || "EXPIRED".equals(status) || "COMPLETED".equals(status);
    }

    private static boolean sameImmutableBinding(OutpatientRegistrationChangedEvent.RegistrationFact fact, Source current) {
        return fact.registrationId().equals(current.registrationId())
                && fact.patientId().equals(current.patientId())
                && fact.doctorId().equals(current.doctorId())
                && fact.departmentId().equals(current.departmentId())
                && fact.sessionId().equals(current.sessionId())
                && fact.roomId().equals(current.roomId())
                && fact.sessionDate().equals(current.sessionDate())
                && fact.startTime().equals(current.startTime())
                && fact.endTime().equals(current.endTime())
                && fact.visitType().equals(current.visitType())
                && fact.reservationId().equals(current.reservationId())
                && fact.priceCode().equals(current.priceCode())
                && fact.sourceType().equals(current.sourceType())
                && fact.sourceId().equals(current.sourceId())
                && fact.careEpisodeType().equals(current.careEpisodeType())
                && fact.careEpisodeId().equals(current.careEpisodeId());
    }

    private static void conflict(String code) { throw new BillingRuleException(code, code); }
    private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
    private static UUID uuid(ResultSet rs, String name) throws SQLException { return rs.getObject(name, UUID.class); }
    private static java.time.Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private record Source(UUID registrationId, long revision, UUID patientId, UUID doctorId, UUID departmentId,
            UUID sessionId, UUID roomId, java.time.LocalDate sessionDate, java.time.LocalTime startTime,
            java.time.LocalTime endTime, String visitType, UUID reservationId, String priceCode, String sourceType,
            UUID sourceId, String careEpisodeType, UUID careEpisodeId, String registrationStatus,
            String payloadFingerprint, UUID paymentRequestId) { }

    private static final RowMapper<Source> SOURCE_MAPPER = (ResultSet rs, int row) -> new Source(
            uuid(rs, "registration_id"), rs.getLong("registration_revision"), uuid(rs, "patient_id"),
            uuid(rs, "doctor_id"), uuid(rs, "department_id"), uuid(rs, "session_id"), uuid(rs, "room_id"),
            rs.getDate("session_date").toLocalDate(), rs.getTime("start_time").toLocalTime(),
            rs.getTime("end_time").toLocalTime(), rs.getString("visit_type"), uuid(rs, "reservation_id"),
            rs.getString("price_code"), rs.getString("source_type"), uuid(rs, "source_id"),
            rs.getString("care_episode_type"), uuid(rs, "care_episode_id"), rs.getString("registration_status"),
            rs.getString("payload_fingerprint"), uuid(rs, "payment_request_id"));
}
