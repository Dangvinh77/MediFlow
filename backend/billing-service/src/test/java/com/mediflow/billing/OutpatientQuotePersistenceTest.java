package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.domain.model.OutpatientQuote;
import com.mediflow.billing.domain.model.OutpatientQuoteLine;
import com.mediflow.billing.infrastructure.persistence.adapter.OutpatientQuoteRepositoryAdapter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@JdbcTest(properties = {"mediflow.billing.ledger.enabled=true", "mediflow.billing.outpatient-quotes.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OutpatientQuoteRepositoryAdapter.class, OutpatientQuotePersistenceTest.JsonConfiguration.class})
@Testcontainers
class OutpatientQuotePersistenceTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", PG::getJdbcUrl); p.add("spring.datasource.username", PG::getUsername);
        p.add("spring.datasource.password", PG::getPassword);
    }
    @TestConfiguration static class JsonConfiguration {
        @Bean ObjectMapper quoteTestMapper() { return new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired OutpatientQuoteRepositoryAdapter quotes;
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
    private UUID request, patient, episode, account, charge;
    private OutpatientQuoteLine line;
    @BeforeEach void fixture() {
        request = UUID.randomUUID(); patient = UUID.randomUUID(); episode = UUID.randomUUID();
        account = UUID.randomUUID(); charge = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        line = OutpatientQuoteLine.calculate(episode, "EXAM", "OUTPATIENT_EXAM", "catalogue-1", "policy-1",
                "Khám", BigDecimal.ONE, new BigDecimal("100000"), new BigDecimal("0.5"));
        jdbc.update("INSERT INTO billing_account(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at) VALUES (?,?,?,'OUTPATIENT_VISIT',?,?)",
                account, patient, department, episode, Timestamp.from(now));
        jdbc.update("INSERT INTO payment_request(payment_request_id,invoice_id,account_id,purpose,requested_amount,expires_at) VALUES (?,?,?,'EXAM',50000,?)",
                request, UUID.randomUUID(), account, Timestamp.from(now.plusSeconds(900)));
        jdbc.update("INSERT INTO charge(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,description,quantity,unit_amount,gross_amount,incurred_at) VALUES (?,?,?,?,'EXAM',?,'OUTPATIENT_EXAM','Khám',1,100000,100000,?)",
                charge, account, patient, department, episode, Timestamp.from(now));
        jdbc.update("INSERT INTO payment_request_charge(payment_request_id,charge_id,requested_amount) VALUES (?,?,50000)", request, charge);
    }
    @Test void exactBoundQuoteRoundTripsAndOldSnapshotSurvivesRequote() {
        var first = quote(patient, line); quotes.append(request, first);
        assertThat(quotes.findCurrent(request)).contains(first);
        var nextLine = OutpatientQuoteLine.calculate(episode, "EXAM", "OUTPATIENT_EXAM", "catalogue-2", "policy-2",
                "Khám", BigDecimal.ONE, new BigDecimal("100000"), new BigDecimal("0.7"));
        jdbc.update("UPDATE payment_request SET requested_amount=30000 WHERE payment_request_id=?", request);
        jdbc.update("UPDATE payment_request_charge SET requested_amount=30000 WHERE payment_request_id=?", request);
        var next = quote(patient, nextLine); quotes.append(request, next);
        assertThat(quotes.findCurrent(request)).contains(next);
        assertThat(jdbc.queryForObject("SELECT patient_payable FROM outpatient_quote WHERE quote_id=?", BigDecimal.class, first.quoteId()))
                .isEqualByComparingTo("50000");
    }
    @Test void differentPatientOrAmountCannotBindToRequest() {
        assertThatThrownBy(() -> quotes.append(request, quote(UUID.randomUUID(), line))).hasMessageContaining("Quote does not match");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_quote", Integer.class)).isZero();
    }
    @Test void databaseRejectsSnapshotMutation() {
        var quote = quote(patient, line); quotes.append(request, quote);
        assertThatThrownBy(() -> jdbc.update("UPDATE outpatient_quote SET patient_payable=0 WHERE quote_id=?", quote.quoteId()))
                .hasMessageContaining("immutable");
    }
    @Test void historicalRequestWithoutQuoteIsExplicitlyAbsent() { assertThat(quotes.findCurrent(request)).isEmpty(); }
    private OutpatientQuote quote(UUID patientId, OutpatientQuoteLine item) {
        return new OutpatientQuote(UUID.randomUUID(), patientId, "OUTPATIENT_VISIT", episode, List.of(item), now, now.plusSeconds(900));
    }
}
