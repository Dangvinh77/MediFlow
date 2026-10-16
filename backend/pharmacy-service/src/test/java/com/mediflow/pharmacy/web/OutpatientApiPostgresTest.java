package com.mediflow.pharmacy.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/** Signed HTTP -> production facade/writer/reader -> own real PG. Upstream observations are isolated test ports. */
@Testcontainers
@ActiveProfiles("outpatient-mvp")
@AutoConfigureMockMvc
@SpringBootTest(properties={"mediflow.jwt.secret=outpatient-api-postgres-secret-at-least-32-bytes",
        "eureka.client.enabled=false","spring.cloud.discovery.enabled=false",
        "mediflow.features.care-finance-v2=true","mediflow.pharmacy.identity.enabled=true",
        "mediflow.pharmacy.clinical-context.enabled=true","mediflow.pharmacy.clearance-consumer.enabled=true",
        "mediflow.pharmacy.clearance-authority.enabled=true","mediflow.pharmacy.outpatient-api.enabled=true",
        "mediflow.pharmacy.admission-consumer.enabled=true","mediflow.pharmacy.admission-authority.enabled=true",
        "mediflow.pharmacy.outbox.enabled=false","spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class OutpatientApiPostgresTest {
    private static final String SECRET="outpatient-api-postgres-secret-at-least-32-bytes";
    private static final String PATH="/api/v1/pharmacy/prescriptions";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock; @Autowired DrugRepositoryPort drugs; @Autowired PrescriptionClearancePort grants;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase projector;
    @Autowired com.mediflow.pharmacy.application.port.in.ExpireCarePrescriptionUseCase expiry;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockBean OutpatientPrescriptionContextPort clinical;
    @MockBean PrescriptionIdentityPort identities;
    @MockBean PrescriptionClearanceAuthorityPort authority;
    UUID patient,doctor,department,episode,record,account,drug;

    @BeforeEach void prepare() {
        jdbc.execute("TRUNCATE prescription,drug,pharmacy_event_outbox,care_prescription_creation,prescription_clearance_target,prescription_clearance_event,prescription_clearance,payment_receipt CASCADE");
        patient=UUID.randomUUID();doctor=UUID.randomUUID();department=UUID.randomUUID();episode=UUID.randomUUID();
        record=UUID.randomUUID();account=UUID.randomUUID();drug=addDrug(10,new BigDecimal("50.00"));
        when(clinical.findRecord(eq(record),anyString())).thenAnswer(call->contextProof(clock.instant()));
        when(identities.lookup(eq(patient),eq(doctor),eq(department),anyString())).thenAnswer(call->
                new PrescriptionIdentityPort.Observation(patient,true,doctor,true,true,department,department,true,clock.instant()));
        when(authority.lookup(any(),anyString())).thenAnswer(call->{
            UUID id=call.getArgument(0);
            return jdbc.queryForObject("SELECT * FROM prescription_clearance WHERE clearance_id=?",(rs,n)->
                    new PrescriptionClearanceAuthorityPort.Observation(true,id,true,rs.getObject("invoice_id",UUID.class),
                            rs.getObject("account_id",UUID.class),rs.getObject("patient_id",UUID.class),"PRESCRIPTION","OUTPATIENT_VISIT",
                            rs.getObject("care_episode_id",UUID.class),null,null,Instant.parse(rs.getString("source_granted_at")),
                            Instant.parse(rs.getString("source_expires_at")),clock.instant()),id);
        });
    }

    @Test void realHttpConcurrentCreateReplayUsesServerPriceAndOneReceiptReservationAndHeldFact()throws Exception {
        UUID key=UUID.randomUUID();var untrusted=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(request(drug,2));
        untrusted.put("totalAmount",0);((com.fasterxml.jackson.databind.node.ObjectNode)untrusted.path("lines").get(0)).put("unitPrice",0);
        String body=mapper.writeValueAsString(untrusted);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return create(key,body,201);});
            var b=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return create(key,body,201);}); start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(count("prescription")).isOne();assertThat(count("care_prescription_creation")).isOne();
        assertThat(count("stock_reservation")).isOne();assertThat(count("pharmacy_event_outbox")).isOne();
        assertThat(jdbc.queryForObject("SELECT total_amount FROM prescription",BigDecimal.class)).isEqualByComparingTo("100.00");
        assertThat(stock()).isEqualTo(10);
        jdbc.update("UPDATE drug SET price=80 WHERE drug_id=?",drug);
        create(key,body,201);
        assertThat(jdbc.queryForObject("SELECT unit_price FROM prescription_line",BigDecimal.class)).isEqualByComparingTo("50.00");
        create(key,request(drug,1),422); assertThat(count("prescription")).isOne();
    }
    @Test void oneShortLineRollsBackReceiptAggregateSlipAndEveryReservation()throws Exception {
        UUID empty=addDrug(0,new BigDecimal("90.00"));
        var root=mapper.readTree(request(drug,2));
        ((com.fasterxml.jackson.databind.node.ArrayNode)root.get("lines")).addObject().put("drugId",empty.toString()).put("quantity",1).put("dosage","test");
        create(UUID.randomUUID(),mapper.writeValueAsString(root),422);
        for(String table:List.of("prescription","prescription_line","stock_reservation","dispense_slip","care_prescription_creation","pharmacy_event_outbox")) assertThat(count(table)).as(table).isZero();
        assertThat(stock()).isEqualTo(10);
    }
    @Test void staleOrWrongEpisodeContextCannotClaimOrReserve()throws Exception {
        when(clinical.findRecord(eq(record),anyString())).thenReturn(contextProof(clock.instant().minusSeconds(31)));
        create(UUID.randomUUID(),request(drug,2),503);
        when(clinical.findRecord(eq(record),anyString())).thenReturn(new OutpatientPrescriptionContextPort.Observation(true,record,
                patient,doctor,department,"OUTPATIENT_VISIT",UUID.randomUUID(),"OPEN",null,clock.instant()));
        create(UUID.randomUUID(),request(drug,2),422);
        assertThat(count("care_prescription_creation")).isZero();assertThat(count("stock_reservation")).isZero();
    }
    @Test void clinicalProofThatExpiresWhileWaitingForDrugLockRollsBackCommandReceipt()throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var proofTime=new java.util.concurrent.atomic.AtomicReference<Instant>();
        when(clinical.findRecord(eq(record),anyString())).thenAnswer(call->{
            Instant observed=clock.instant().minusSeconds(28);proofTime.set(observed);return contextProof(observed);
        });
        String body=request(drug,2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->new TransactionTemplate(transactions).execute(s->{
                jdbc.queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=? FOR UPDATE",Integer.class,drug);
                locked.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("lock release timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var attempt=pool.submit(()->create(UUID.randomUUID(),body,503));
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(7)).until(()->Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%drug%' AND pid<>pg_backend_pid())",Boolean.class)));
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5))
                        .until(()->proofTime.get()!=null && clock.instant().isAfter(proofTime.get().plusSeconds(30)));
            } finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS);attempt.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();}
        for(String table:List.of("prescription","stock_reservation","dispense_slip","care_prescription_creation","pharmacy_event_outbox"))
            assertThat(count(table)).as(table).isZero();
        assertThat(stock()).isEqualTo(10);
    }
    @Test void signedWrongPrescriberAndRefreshTokenCannotCreate()throws Exception {
        mvc.perform(post(PATH).header("Authorization",token("DOCTOR","access",UUID.randomUUID())).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(request(drug,2))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).header("Authorization",token("ADMIN","refresh",doctor)).header("Idempotency-Key",UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(request(drug,2))).andExpect(status().isUnauthorized());
        assertThat(count("prescription")).isZero();
    }
    @Test void revokedOrUnavailableCurrentAuthorityDoesNotDispenseOrCreateFailureRefund()throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);UUID grant=seedGrant(id);
        var proof=authority.lookup(grant,"proof");
        doReturn(new PrescriptionClearanceAuthorityPort.Observation(true,grant,false,
                proof.invoiceId(),proof.accountId(),patient,"PRESCRIPTION","OUTPATIENT_VISIT",episode,null,null,
                proof.grantedAt(),proof.expiresAt(),clock.instant())).when(authority).lookup(eq(grant),anyString());
        dispense(id,422);assertPending(id);
        doThrow(new PharmacyUpstreamUnavailableException()).when(authority).lookup(eq(grant),anyString());
        dispense(id,503);assertPending(id);
        assertThat(count("payment_receipt")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1",Long.class)).isZero();
    }
    @Test void authenticatedConcurrentDispenseAndRetryWithUnavailableAuthorityKeepOneTerminalEffect()throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);seedGrant(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return dispense(id,200);});
            var b=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return dispense(id,200);});start.countDown();
            assertThat(mapper.readTree(a.get(20,TimeUnit.SECONDS)).path("data"))
                    .isEqualTo(mapper.readTree(b.get(20,TimeUnit.SECONDS)).path("data"));
        }
        doThrow(new PharmacyUpstreamUnavailableException()).when(authority).lookup(any(),anyString());
        dispense(id,200); // Committed terminal result is read without authorizing a second stock mutation.
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1 AND NOT delivery_enabled",Long.class)).isOne();
    }
    @Test void admissionBeansAndLegacyAutomaticPaymentConsumerAreAbsentDespiteGlobalAndLegacyOverrides() {
        assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view",Boolean.class)).isFalse();
        assertThat(context.getBeansOfType(AdmissionAuthorityPort.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.pharmacy.infrastructure.client.PharmacyInpatientFeignClient.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.pharmacy.messaging.consumer.AdmissionLifecycleConsumer.class)).isEmpty();
        assertThat(context.getBeansOfType(com.mediflow.pharmacy.messaging.consumer.PaymentCompletedConsumer.class)).isEmpty();
    }
    @Test void filledProducerRelationshipComesFromPersistedPrescriptionAndSlipAndReplayKeepsExactBytes() throws Exception {
        UUID prescriptionId = create(UUID.randomUUID(), request(drug, 2), 201);
        seedGrant(prescriptionId);
        var result = mapper.readTree(dispense(prescriptionId, 200)).path("data");
        var persisted = jdbc.queryForMap("""
                SELECT p.record_id, p.patient_id, p.department_id, p.care_episode_id, s.dispense_id
                FROM prescription p JOIN dispense_slip s ON s.prescription_id=p.prescription_id
                WHERE p.prescription_id=?
                """, prescriptionId);
        String original = jdbc.queryForObject("""
                SELECT payload FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'
                """, String.class, prescriptionId);
        var envelope = mapper.readTree(original);
        var payload = envelope.path("payload");
        for (var field : Map.of("recordId", "record_id", "patientId", "patient_id", "departmentId", "department_id",
                "careEpisodeId", "care_episode_id", "dispenseId", "dispense_id").entrySet()) {
            assertThat(payload.path(field.getKey()).asText()).as(field.getKey())
                    .isEqualTo(persisted.get(field.getValue()).toString());
        }
        assertThat(payload.path("recordId").asText()).isEqualTo(record.toString()).isNotEqualTo(episode.toString());
        assertThat(payload.path("dispenseId")).isEqualTo(result.path("dispenseId"));
        assertThat(payload.path("sourceId").asText()).isEqualTo(prescriptionId.toString());
        assertThat(payload.path("careContext").asText()).isEqualTo("OUTPATIENT");
        assertThat(payload.path("careEpisodeType").asText()).isEqualTo("OUTPATIENT_VISIT");
        assertThat(payload.has("admissionId")).isFalse();
        dispense(prescriptionId, 200);
        assertThat(jdbc.queryForObject("""
                SELECT payload FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'
                """, String.class, prescriptionId)).isEqualTo(original);
        assertThat(stock()).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pharmacy_event_outbox WHERE aggregate_id=? AND routing_key='prescription.filled'
                AND NOT delivery_enabled
                """, Long.class, prescriptionId)).isOne();
    }
    @ParameterizedTest @ValueSource(strings={"expiry","revocation","unavailable"})
    void authorizationChangesDuringRealDrugLockWaitAreCheckedBeforeAnyStockEffect(String change)throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);UUID grant=seedGrant(id);
        var proof=authority.lookup(grant,"seed");clearInvocations(authority);
        Instant expires=clock.instant().plusSeconds(2);
        if(change.equals("expiry")) jdbc.update("UPDATE prescription_clearance SET expires_at=CAST(? AS timestamptz),source_expires_at=? WHERE clearance_id=?",expires.toString(),expires.toString(),grant);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var holder=pool.submit(()->new TransactionTemplate(transactions).execute(s->{
                jdbc.queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=? FOR UPDATE",Integer.class,drug);
                locked.countDown();try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("lock release timed out");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var attempt=pool.submit(()->dispense(id,change.equals("unavailable")?503:422));
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(7)).until(()->Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%drug%' AND pid<>pg_backend_pid())",Boolean.class)));
                verify(authority,never()).lookup(any(),anyString());
                if(change.equals("expiry"))org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(()->clock.instant().isAfter(expires));
                else if(change.equals("unavailable"))doThrow(new PharmacyUpstreamUnavailableException()).when(authority).lookup(any(),anyString());
                else doReturn(new PrescriptionClearanceAuthorityPort.Observation(true,grant,false,proof.invoiceId(),proof.accountId(),patient,
                        "PRESCRIPTION","OUTPATIENT_VISIT",episode,null,null,proof.grantedAt(),proof.expiresAt(),clock.instant()))
                        .when(authority).lookup(any(),anyString());
            } finally {release.countDown();}
            holder.get(10,TimeUnit.SECONDS);attempt.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();}
        assertPending(id);
        assertThat(jdbc.queryForObject("SELECT target_status FROM prescription_clearance WHERE clearance_id=?",String.class,grant)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1",Long.class)).isZero();
    }
    @Test void multiDrugFailureHasNoPrefixStockMutationAndOneTerminalFailure()throws Exception {
        UUID second=addDrug(10,new BigDecimal("50.00"));var root=mapper.readTree(request(drug,2));
        ((com.fasterxml.jackson.databind.node.ArrayNode)root.get("lines")).addObject().put("drugId",second.toString()).put("quantity",2).put("dosage","test");
        UUID id=create(UUID.randomUUID(),mapper.writeValueAsString(root),201);seedGrant(id);
        jdbc.update("UPDATE drug SET stock_quantity=0 WHERE drug_id=?",second);
        assertThat(mapper.readTree(dispense(id,200)).path("data").path("status").asText()).isEqualTo("FAILED");
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=?",Integer.class,second)).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM stock_reservation WHERE prescription_id=?",String.class,id)).containsOnly("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE routing_key='prescription.dispense.failed'",Long.class)).isOne();
        dispense(id,200);assertThat(stock()).isEqualTo(10);
    }
    @Test void cancelAndAuthenticatedDispenseRaceHasOneTerminalAndNoPartialStock()throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);seedGrant(id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return mvc.perform(put(PATH+"/"+id+"/cancel")
                    .header("Authorization",token("ADMIN","access",doctor)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"Isolated race test\"}")).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{start.await(5,TimeUnit.SECONDS);return mvc.perform(put(PATH+"/"+id+"/dispense")
                    .header("Authorization",token("PHARMACIST","access",doctor))).andReturn().getResponse().getStatus();});
            start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,422);
        }
        String state=jdbc.queryForObject("SELECT status FROM prescription WHERE prescription_id=?",String.class,id);
        assertThat(stock()).isEqualTo("CANCELLED".equals(state)?10:8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1",Long.class)).isOne();
    }
    @Test void expiryAndAuthenticatedDispenseRaceCannotMutateStockOrEmitTwoTerminals()throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);seedGrant(id);
        jdbc.update("UPDATE stock_reservation SET expires_at=now()-interval '1 second' WHERE prescription_id=?",id);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var expiration=pool.submit(()->{
                if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timed out");
                try{return expiry.expireCare(id,"expiry-race");}
                catch(com.mediflow.common.exception.BusinessRuleException conflict){
                    assertThat(conflict.getCode()).isEqualTo("PHARMACY_CARE_LIFECYCLE_EVIDENCE_INVALID");return 0;
                }
            });
            var dispensing=pool.submit(()->{
                if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("start timed out");
                return mvc.perform(put(PATH+"/"+id+"/dispense").header("Authorization",token("PHARMACIST","access",doctor)))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();int released=expiration.get(20,TimeUnit.SECONDS),status=dispensing.get(20,TimeUnit.SECONDS);
            String state=jdbc.queryForObject("SELECT status FROM prescription WHERE prescription_id=?",String.class,id);
            assertThat(state).isIn("EXPIRED","DISPENSE_FAILED");
            assertThat(released).isEqualTo(state.equals("EXPIRED")?1:0);
            assertThat(status).isEqualTo(state.equals("EXPIRED")?422:200);
            assertThat(jdbc.queryForObject("SELECT status FROM stock_reservation WHERE prescription_id=?",String.class,id))
                    .isEqualTo(state.equals("EXPIRED")?"EXPIRED":"RELEASED");
        }
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1",Long.class)).isOne();
    }
    @Test void lateDuplicateGrantAfterCancellationRemainsEvidenceAndCannotReopenOrDispense()throws Exception {
        UUID id=create(UUID.randomUUID(),request(drug,2),201);seedGrant(id);
        mvc.perform(put(PATH+"/"+id+"/cancel").header("Authorization",token("ADMIN","access",doctor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cancelled before payment proof\"}"))
                .andExpect(status().isOk());
        var local=new TransactionTemplate(transactions).execute(s->grants.findByTargetForUpdate(id).getFirst());
        var late=new PrescriptionClearance(UUID.randomUUID(),local.invoiceId(),local.accountId(),id,patient,
                local.episode(),local.amount(),local.currency(),local.paymentMethod(),local.grantedAt(),local.expiresAt(),"b".repeat(64));
        var delivery=new com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand(UUID.randomUUID(),"c".repeat(64),late);
        projector.project(delivery);projector.project(delivery);
        clearInvocations(authority);dispense(id,422);verify(authority,never()).lookup(any(),anyString());
        assertThat(stock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT status FROM prescription WHERE prescription_id=?",String.class,id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM stock_reservation WHERE prescription_id=?",String.class,id)).isEqualTo("RELEASED");
        assertThat(count("prescription_clearance_event")).isOne();assertThat(count("payment_receipt")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pharmacy_event_outbox WHERE care_lifecycle_order=1",Long.class)).isOne();
    }
    private UUID addDrug(int stock,BigDecimal price){return new TransactionTemplate(transactions).execute(s->drugs.save(
            Drug.create("Isolated API test",null,"tablet",price,stock,LocalDate.now(clock).plusYears(1),null,0,LocalDate.now(clock))).getDrugId());}
    private OutpatientPrescriptionContextPort.Observation contextProof(Instant at){return new OutpatientPrescriptionContextPort.Observation(true,record,patient,doctor,department,"OUTPATIENT_VISIT",episode,"OPEN",null,at);}
    private String request(UUID drugId,int quantity)throws Exception{return mapper.writeValueAsString(Map.ofEntries(
            Map.entry("recordId",record),Map.entry("patientId",patient),Map.entry("doctorId",doctor),
            Map.entry("departmentId",department),Map.entry("prescribedDate",LocalDate.now(clock)),
            Map.entry("lines",List.of(Map.of("drugId",drugId,"quantity",quantity,"dosage","test"))),
            Map.entry("careContractVersion",1),Map.entry("careContext","OUTPATIENT"),Map.entry("careEpisodeType","OUTPATIENT_VISIT"),
            Map.entry("careEpisodeId",episode),Map.entry("priceCode","DRUG")));}
    private UUID create(UUID key,String body,int status)throws Exception {
        var response=mvc.perform(post(PATH).header("Authorization",token("ADMIN","access",doctor)).header("X-Correlation-Id","api-pg-create")
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status)).andReturn();
        return status==201?UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("data").path("prescriptionId").asText()):null;
    }
    private String dispense(UUID id,int status)throws Exception{var response=mvc.perform(put(PATH+"/"+id+"/dispense")
            .header("Authorization",token("PHARMACIST","access",doctor)).header("X-Correlation-Id","api-pg-dispense"))
            .andReturn().getResponse();assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);return response.getContentAsString();}
    private UUID seedGrant(UUID id){UUID grant=UUID.randomUUID();new TransactionTemplate(transactions).executeWithoutResult(s->{
        grants.lockTarget(id);grants.store(new PrescriptionClearance(grant,UUID.randomUUID(),UUID.randomUUID(),id,patient,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,episode),new BigDecimal("100.00"),"VND","CASH",clock.instant().minusSeconds(1),
                clock.instant().plusSeconds(600),"a".repeat(64)),false);});return grant;}
    private String token(String role,String type,UUID staff){Instant now=Instant.now();return "Bearer "+Jwts.builder().subject(account.toString())
            .claim("role",role).claim("type",type).claim("staffId",staff.toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
    private long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    private int stock(){return jdbc.queryForObject("SELECT stock_quantity FROM drug WHERE drug_id=?",Integer.class,drug);}
    private void assertPending(UUID id){assertThat(stock()).isEqualTo(10);assertThat(jdbc.queryForObject("SELECT status FROM prescription WHERE prescription_id=?",String.class,id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM dispense_slip WHERE prescription_id=?",String.class,id)).isEqualTo("PENDING");}
}
