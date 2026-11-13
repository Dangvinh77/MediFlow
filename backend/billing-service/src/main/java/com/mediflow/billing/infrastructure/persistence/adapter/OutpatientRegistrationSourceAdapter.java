package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientRegistrationChangedEvent;
import com.mediflow.billing.application.service.IssueOutpatientQuoteService;
import com.mediflow.billing.application.port.out.OutpatientPublicationCohortPort;
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
    private final OutpatientPublicationCohortPort cohort;

    public OutpatientRegistrationSourceAdapter(JdbcTemplate jdbc, ObjectMapper mapper,
            ObjectProvider<IssueOutpatientQuoteService> quoteIssuer,
            @Value("${mediflow.billing.outpatient-quotes.enabled:false}") boolean quotesEnabled,
            OutpatientPublicationCohortPort cohort) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.quoteIssuer = quoteIssuer;
        this.quotesEnabled = quotesEnabled;
        this.cohort = cohort;
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
            if (!sameImmutableBinding(fact, current) || !sameSchedule(fact, current)) conflict("BILLING_REGISTRATION_SOURCE_CONFLICT");
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
        recordSettlement(event, payloadFingerprint);
    }

    private void recordSettlement(OutpatientRegistrationChangedEvent event, String fingerprint) {
        var fact = event.payload();
        boolean cancelled = "CANCELLED".equals(fact.status());
        if (!cancelled && fact.reschedule() == null) return;
        UUID requestId = findSource(fact.registrationId(), false).orElseThrow().paymentRequestId();
        var allocations = requestId == null ? List.<java.util.Map<String, Object>>of() : jdbc.queryForList("""
                SELECT p.transaction_id,a.charge_id,p.account_id,p.currency,
                  a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                    WHERE r.charge_id=a.charge_id AND t.original_transaction_id=p.transaction_id
                    AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0) AS due
                FROM PAYMENT_ALLOCATION a JOIN PAYMENT_TRANSACTION p ON p.transaction_id=a.transaction_id
                WHERE p.payment_request_id=? AND p.transaction_type='PAYMENT' AND p.status='COMPLETED'
                ORDER BY p.transaction_id,a.charge_id
                """, requestId);
        BigDecimal collected = allocations.stream().map(row -> (BigDecimal) row.get("due")).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fact.reschedule() != null) {
            var move = fact.reschedule();
            insertSettlement(fact, "RESCHEDULED", requestId, collected, move.actorAccountId(), move.actorStaffId(), move.reason(),
                    move.previousSessionId(), fact.sessionId(), move.rescheduledAt());
            return;
        }
        var cancellation = fact.cancellation();
        boolean refundDue = collected.signum() > 0;
        insertSettlement(fact, refundDue ? "REFUND_DUE" : "VOID_UNPAID", requestId, refundDue ? collected : BigDecimal.ZERO,
                cancellation.actorAccountId(), cancellation.actorStaffId(), cancellation.reason(), null, null, cancellation.cancelledAt());
        if (refundDue) recordRefundEvidence(event, fingerprint, requestId, allocations);
    }

    private void insertSettlement(OutpatientRegistrationChangedEvent.RegistrationFact fact, String kind, UUID requestId, BigDecimal amount,
            UUID actor, UUID staff, String reason, UUID previousSession, UUID newSession, java.time.Instant at) {
        jdbc.update("""
                INSERT INTO outpatient_registration_settlement(registration_id,registration_revision,kind,payment_request_id,amount,currency,
                    source_type,source_id,actor_account_id,actor_staff_id,reason,previous_session_id,new_session_id,occurred_at)
                VALUES (?,?,?,?,?,'VND',?,?,?,?,?,?,?,?) ON CONFLICT(registration_id,registration_revision) DO NOTHING
                """, fact.registrationId(), fact.registrationRevision(), kind, requestId, amount, fact.sourceType(), fact.sourceId(),
                actor, staff, reason, previousSession, newSession, Timestamp.from(at));
    }

    /** Same durable evidence an exam cancellation leaves, so the existing refund path applies unchanged. */
    private void recordRefundEvidence(OutpatientRegistrationChangedEvent event, String fingerprint, UUID requestId,
            List<java.util.Map<String, Object>> allocations) {
        var fact = event.payload();
        var cancellation = fact.cancellation();
        jdbc.query("SELECT account_id FROM BILLING_ACCOUNT WHERE account_id=? FOR UPDATE", (rs, row) -> uuid(rs, "account_id"),
                allocations.getFirst().get("account_id"));
        jdbc.update("""
                INSERT INTO outpatient_cancellation_source(source_type,source_id,event_type,source_fingerprint,first_event_id,correlation_id,
                    patient_id,department_id,care_episode_type,care_episode_id,cancelled_by,reason,cancelled_at_iso,status,applied_at)
                VALUES ('EXAM',?,'appointment.exam.cancelled',?,?,?,?,?,?,?,?,?,?,'APPLIED',?) ON CONFLICT(source_type,source_id) DO NOTHING
                """, fact.registrationId(), fingerprint, event.eventId(), event.correlationId(), fact.patientId(), fact.departmentId(),
                fact.careEpisodeType(), fact.careEpisodeId(), cancellation.actorAccountId(), cancellation.reason(),
                cancellation.cancelledAt().toString(), Timestamp.from(fact.changedAt()));
        for (var allocation : allocations) {
            if (((BigDecimal) allocation.get("due")).signum() <= 0) continue;
            jdbc.update("""
                    INSERT INTO outpatient_cancellation_refund_due(source_type,source_id,original_transaction_id,charge_id,account_id,amount,currency)
                    VALUES ('EXAM',?,?,?,?,?,?) ON CONFLICT DO NOTHING
                    """, fact.registrationId(), allocation.get("transaction_id"), allocation.get("charge_id"), allocation.get("account_id"),
                    allocation.get("due"), allocation.get("currency"));
            jdbc.update("UPDATE CHARGE SET status='VOIDED',void_reason='OUTPATIENT_CANCELLATION' WHERE charge_id=? AND status='POSTED'",
                    allocation.get("charge_id"));
        }
        jdbc.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=? WHERE payment_request_id=? AND revoked_at IS NULL",
                Timestamp.from(fact.changedAt()), requestId);
    }

    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 3)
    public com.mediflow.common.api.PageResult<com.mediflow.billing.application.dto.response.RegistrationSettlementDTO> findSettlements(
            String kind, com.mediflow.common.api.PageQuery page) {
        String where = kind == null ? "" : " WHERE z.kind=?";
        var args = new java.util.ArrayList<Object>();
        if (kind != null) args.add(kind);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM outpatient_registration_settlement z" + where, Long.class, args.toArray());
        args.add(page.size());
        args.add((long) page.page() * page.size());
        var rows = jdbc.query("""
                SELECT z.*,CASE WHEN z.kind='REFUND_DUE' THEN COALESCE(x.outstanding,0) ELSE 0 END AS outstanding
                  FROM outpatient_registration_settlement z LEFT JOIN LATERAL (
                    SELECT SUM(GREATEST(0,LEAST(d.amount,a.amount-COALESCE((SELECT SUM(r.amount) FROM PAYMENT_ALLOCATION r
                        JOIN PAYMENT_TRANSACTION t ON t.transaction_id=r.transaction_id
                        WHERE t.original_transaction_id=d.original_transaction_id AND r.charge_id=d.charge_id
                          AND t.transaction_type IN ('REFUND','REVERSAL') AND t.status='COMPLETED'),0)))) AS outstanding
                      FROM outpatient_cancellation_refund_due d JOIN PAYMENT_ALLOCATION a
                        ON a.transaction_id=d.original_transaction_id AND a.charge_id=d.charge_id
                     WHERE d.source_type=z.source_type AND d.source_id=z.source_id
                  ) x ON TRUE
                """ + where + " ORDER BY z.occurred_at DESC,z.registration_id,z.registration_revision LIMIT ? OFFSET ?", (rs, row) -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal outstanding = rs.getBigDecimal("outstanding");
                    boolean refundDue = "REFUND_DUE".equals(rs.getString("kind"));
                    return new com.mediflow.billing.application.dto.response.RegistrationSettlementDTO(uuid(rs, "registration_id"),
                            rs.getLong("registration_revision"), rs.getString("kind"), uuid(rs, "payment_request_id"), amount,
                            refundDue ? amount.subtract(outstanding) : BigDecimal.ZERO, outstanding, rs.getString("currency").trim(),
                            rs.getString("source_type"), uuid(rs, "source_id"), uuid(rs, "actor_account_id"), rs.getString("reason"),
                            rs.getTimestamp("occurred_at").toInstant());
                }, args.toArray());
        return com.mediflow.common.api.PageResult.of(rows, count == null ? 0 : count, page.page(), page.size());
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
            // The exam quote never outlives the two-hour unpaid hold set by Clinical.
            java.time.Instant quoteMaximum = fact.changedAt().plus(java.time.Duration.ofHours(2));
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
        cohort.enrollIfEligible(requestId);
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
                && fact.departmentId().equals(current.departmentId())
                && fact.visitType().equals(current.visitType())
                && fact.reservationId().equals(current.reservationId())
                && fact.priceCode().equals(current.priceCode())
                && fact.sourceType().equals(current.sourceType())
                && fact.sourceId().equals(current.sourceId())
                && fact.careEpisodeType().equals(current.careEpisodeType())
                && fact.careEpisodeId().equals(current.careEpisodeId());
    }

    /** The place in the schedule stays fixed unless this revision carries a move away from the session Billing holds. */
    private static boolean sameSchedule(OutpatientRegistrationChangedEvent.RegistrationFact fact, Source current) {
        if (fact.reschedule() != null) {
            return fact.reschedule().previousSessionId().equals(current.sessionId())
                    && ("AWAITING_PAYMENT".equals(current.registrationStatus()) || "CONFIRMED".equals(current.registrationStatus()));
        }
        return fact.doctorId().equals(current.doctorId())
                && fact.sessionId().equals(current.sessionId())
                && fact.roomId().equals(current.roomId())
                && fact.sessionDate().equals(current.sessionDate())
                && fact.startTime().equals(current.startTime())
                && fact.endTime().equals(current.endTime());
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
