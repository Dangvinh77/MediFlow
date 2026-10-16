package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.*;

/** Actual own DB/outbox/leases/broker; acceptance reference is isolated-test only, never producer sign-off. */
@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({PharmacyOutboxClaimService.class,OutpatientDeliveryPostgresTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Testcontainers
class OutpatientDeliveryPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer BROKER=new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager transactions;
    @Autowired PharmacyOutboxClaimService claims;@Autowired PharmacyEventOutboxJpaRepository repository;
    private final PrescriptionCareEventCodec codec=new PrescriptionCareEventCodec(new ObjectMapper().findAndRegisterModules());
    private CachingConnectionFactory connection;private RabbitTemplate rabbit;private RabbitAdmin admin;
    private PrescriptionCareEventWriterAdapter active,held;
    private static final String QUEUE="isolated.pharmacy.outpatient.delivery.q";
    @BeforeEach void prepare(){
        jdbc.execute("TRUNCATE pharmacy_event_outbox,pharmacy_outpatient_delivery_cohort,prescription CASCADE");
        active=new PrescriptionCareEventWriterAdapter(jdbc,codec,new OutpatientEventActivation(OutpatientEventActivationTest.enabled()));
        held=new PrescriptionCareEventWriterAdapter(jdbc,codec);
        connection=new CachingConnectionFactory(BROKER.getHost(),BROKER.getAmqpPort());
        connection.setUsername(BROKER.getAdminUsername());connection.setPassword(BROKER.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);connection.setPublisherReturns(true);
        rabbit=new RabbitTemplate(connection);rabbit.setMandatory(true);admin=new RabbitAdmin(connection);
        admin.declareExchange(new TopicExchange("mediflow.events",true,false));admin.deleteQueue(QUEUE);
    }
    @AfterEach void close(){if(connection!=null)connection.destroy();}
    @Test void defaultHeldAndHistoricalReplayNeverJoinNewCohort()throws Exception{
        var created=fixture(EventType.CREATED);seed(created);tx(()->held.storeHeld(created));tx(()->active.storeHeld(created));
        assertThat(count("pharmacy_outpatient_delivery_cohort")).isZero();
        assertThat(jdbc.queryForObject("SELECT delivery_enabled FROM pharmacy_event_outbox",Boolean.class)).isFalse();
        assertThatThrownBy(()->jdbc.update("UPDATE pharmacy_event_outbox SET delivery_enabled=true")).hasMessageContaining("historical HELD");
        assertThat(claims.claim(10,"replica-a")).isEmpty();
    }
    @Test void newCohortKeepsLifecycleOrderEvenWhenClockMovesBackAndProofSurvivesPublication()throws Exception{
        var created=fixture(EventType.CREATED);var filled=fixture(EventType.FILLED);seed(created);
        tx(()->active.storeHeld(created));tx(()->held.storeHeld(filled)); // Disabling NEW enrollment still completes an existing cohort.
        jdbc.update("UPDATE pharmacy_event_outbox SET created_at=created_at-interval '1 day' WHERE event_id=?",filled.eventId());
        var first=claims.claim(10,"a");assertThat(first).extracting(e->e.getEventId()).containsExactly(created.eventId());
        assertThat(claims.markPublished(created.eventId(),"a",Instant.now())).isTrue();
        assertThat(claims.claim(10,"b")).extracting(e->e.getEventId()).containsExactly(filled.eventId());
        assertThat(claims.markPublished(filled.eventId(),"b",Instant.now())).isTrue();
        tx(()->assertThat(active.findHeld(filled.payload().prescriptionId(),EventType.FILLED)).contains(filled));
        assertThat(claims.deletePublishedBefore(Instant.now().plusSeconds(3600))).isZero();
        assertThat(count("pharmacy_event_outbox")).isEqualTo(2);
    }
    @Test void cohortAndCreationOutboxRollbackTogetherOnInsertFailure()throws Exception{
        var created=fixture(EventType.CREATED);seed(created);
        jdbc.execute("CREATE FUNCTION reject_test_delivery() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'isolated insertion failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_test_delivery BEFORE INSERT ON pharmacy_event_outbox FOR EACH ROW EXECUTE FUNCTION reject_test_delivery()");
        try{assertThatThrownBy(()->tx(()->active.storeHeld(created))).hasMessageContaining("isolated insertion failure");
            assertThat(count("pharmacy_outpatient_delivery_cohort")).isZero();assertThat(count("pharmacy_event_outbox")).isZero();}
        finally{jdbc.execute("DROP TRIGGER reject_test_delivery ON pharmacy_event_outbox");jdbc.execute("DROP FUNCTION reject_test_delivery()");}
    }
    @Test void unroutableConfirmStaysPendingThenRoutesOriginalBytesExactly()throws Exception{
        var created=fixture(EventType.CREATED);seed(created);tx(()->active.storeHeld(created));
        dispatcher().dispatchPending();
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL AND attempts=1 FROM pharmacy_event_outbox",Boolean.class)).isTrue();
        route();jdbc.update("UPDATE pharmacy_event_outbox SET available_at=now()-interval '1 second'");dispatcher().dispatchPending();
        var message=rabbit.receive(QUEUE,5000);assertThat(message).isNotNull();assertThat(message.getBody()).isEqualTo(codec.encode(created));
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM pharmacy_event_outbox",Boolean.class)).isTrue();
        assertThat(rabbit.receive(QUEUE,200)).isNull();
    }
    @Test void lostPublishMarkAndExpiredLeaseRedeliverOriginalIdentityWithoutClaimingExactlyOnce()throws Exception{
        route();var created=fixture(EventType.CREATED);seed(created);tx(()->active.storeHeld(created));
        var claim=claims.claim(1,"crashed").getFirst();var confirmation=new CorrelationData(claim.getEventId().toString());
        rabbit.send("mediflow.events",claim.getRoutingKey(),MessageBuilder.withBody(claim.getPayload().getBytes(StandardCharsets.UTF_8)).build(),confirmation);
        assertThat(confirmation.getFuture().get(10,TimeUnit.SECONDS).isAck()).isTrue();
        // Publisher disappears before marking. A different lease owner must resend the SAME committed bytes.
        jdbc.update("UPDATE pharmacy_event_outbox SET locked_at=now()-interval '1 minute'");dispatcher().dispatchPending();
        var first=rabbit.receive(QUEUE,5000);var second=rabbit.receive(QUEUE,5000);
        assertThat(first).isNotNull();assertThat(second).isNotNull();assertThat(first.getBody()).isEqualTo(second.getBody()).isEqualTo(codec.encode(created));
        assertThat(claims.markPublished(created.eventId(),"crashed",Instant.now())).isFalse();
        assertThat(count("pharmacy_event_outbox")).isOne();
    }
    @Test void brokerRestartPreservesPendingBytesAndResumesFromANewDispatcher()throws Exception {
        route();var created=fixture(EventType.CREATED);seed(created);tx(()->active.storeHeld(created));
        assertThat(BROKER.execInContainer("rabbitmqctl","stop_app").getExitCode()).isZero();
        try {
            connection.resetConnection();
            dispatcher().dispatchPending();
            assertThat(jdbc.queryForObject("SELECT published_at IS NULL AND attempts=1 AND quarantined_at IS NULL FROM pharmacy_event_outbox",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT payload FROM pharmacy_event_outbox",String.class))
                    .isEqualTo(new String(codec.encode(created),StandardCharsets.UTF_8));
        } finally {
            assertThat(BROKER.execInContainer("rabbitmqctl","start_app").getExitCode()).isZero();
            connection.resetConnection();
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(admin.getQueueProperties(QUEUE)).isNotNull());
        jdbc.update("UPDATE pharmacy_event_outbox SET available_at=now()-interval '1 second'");
        dispatcher().dispatchPending();
        var delivered=rabbit.receive(QUEUE,5000);assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).isEqualTo(codec.encode(created));
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(created.eventId().toString());
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL AND attempts=1 FROM pharmacy_event_outbox",Boolean.class)).isTrue();
        assertThat(rabbit.receive(QUEUE,200)).isNull();
    }
    @Test void boundedQuarantineBlocksTerminalUntilOriginalCreationIsExplicitlyReplayed()throws Exception {
        var created=fixture(EventType.CREATED);var filled=fixture(EventType.FILLED);seed(created);
        tx(()->active.storeHeld(created));tx(()->active.storeHeld(filled));
        for(int attempt=0;attempt<3;attempt++) {
            jdbc.update("UPDATE pharmacy_event_outbox SET available_at=now()-interval '1 second'");
            dispatcher().dispatchPending();
        }
        assertThat(jdbc.queryForObject("SELECT attempts=3 AND quarantined_at IS NOT NULL AND published_at IS NULL FROM pharmacy_event_outbox WHERE event_id=?",Boolean.class,created.eventId())).isTrue();
        assertThat(claims.claim(10,"blocked-terminal")).isEmpty();
        route();
        tx(()->assertThat(repository.replay(created.eventId(),Instant.now().minusSeconds(1))).isOne());
        dispatcher().dispatchPending();
        var delivered=rabbit.receive(QUEUE,5000);assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).isEqualTo(codec.encode(created));
        assertThat(claims.claim(10,"terminal-after-replay")).extracting(e->e.getEventId()).containsExactly(filled.eventId());
        tx(()->assertThat(active.findHeld(created.payload().prescriptionId(),EventType.CREATED)).contains(created));
    }
    private void route(){var queue=new Queue(QUEUE,true,false,false);admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange("mediflow.events",true,false)).with("prescription.created"));}
    private PharmacyOutboxDispatcher dispatcher(){return new PharmacyOutboxDispatcher(rabbit,Clock.systemUTC(),10,5000,claims,10,100,3,new SimpleMeterRegistry());}
    private PrescriptionCareEvent fixture(EventType type)throws Exception{try(var in=getClass().getResourceAsStream("/contracts/care-finance-v1/"+type.routingKey()+".outpatient.v1.json")){
        if(in==null)throw new IllegalStateException("fixture missing");return codec.decode(type.routingKey(),in.readAllBytes());}}
    private void seed(PrescriptionCareEvent event){var p=event.payload();jdbc.update("""
            INSERT INTO prescription(prescription_id,record_id,patient_id,doctor_id,department_id,prescribed_date,total_amount,
                care_contract_version,care_context,care_episode_type,care_episode_id,price_code)
            VALUES (?,?,?,?,?,CURRENT_DATE,?,1,'OUTPATIENT','OUTPATIENT_VISIT',?,?)
            """,p.prescriptionId(),UUID.randomUUID(),p.patientId(),UUID.randomUUID(),p.departmentId(),p.totalAmount(),p.careEpisodeId(),p.priceCode());}
    private void tx(Runnable action){new TransactionTemplate(transactions).executeWithoutResult(s->action.run());}
    private long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    @TestConfiguration static class Config{@Bean Clock clock(){return Clock.systemUTC();}}
}
