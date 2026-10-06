package com.mediflow.surgery;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.infrastructure.client.*;
import com.mediflow.surgery.infrastructure.security.JwtProperties;
import feign.Feign;
import feign.Request;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Real packaged Billing JVM/HTTP/PG/MQ -> production Surgery Feign interface and adapter.
 * Does not claim public Surgery lifecycle activation or a distributed financial fence. */
@Testcontainers
class SurgeryBillingRuntimeAcceptanceIT {
    private static final String SECRET="billing-surgery-runtime-only-secret-at-least-32-bytes";
    private static final Network NETWORK=Network.newNetwork();
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine")
            .withNetwork(NETWORK).withNetworkAliases("billing-db");
    @Container static final RabbitMQContainer MQ=new RabbitMQContainer("rabbitmq:3.13-alpine")
            .withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final GenericContainer<?> BILLING=billing().dependsOn(PG,MQ)
            .withNetwork(NETWORK).withExposedPorts(8086)
            .withEnv("SPRING_DATASOURCE_URL","jdbc:postgresql://billing-db:5432/"+PG.getDatabaseName())
            .withEnv("MEDIFLOW_DB_USER",PG.getUsername()).withEnv("MEDIFLOW_DB_PASSWORD",PG.getPassword())
            .withEnv("MEDIFLOW_RABBIT_HOST","rabbit").withEnv("MEDIFLOW_RABBIT_USER",MQ.getAdminUsername())
            .withEnv("MEDIFLOW_RABBIT_PASSWORD",MQ.getAdminPassword())
            .withEnv("MEDIFLOW_JWT_SECRET",SECRET).withEnv("EUREKA_CLIENT_ENABLED","false")
            .withEnv("MEDIFLOW_BILLING_LEDGER_ENABLED","true")
            .withEnv("MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED","true")
            .withEnv("MEDIFLOW_BILLING_OUTBOX_ENABLED","false")
            .withEnv("MEDIFLOW_BILLING_OUTBOX_METRICS_ENABLED","false")
            .withEnv("MEDIFLOW_BILLING_OUTBOX_MAINTENANCE_ENABLED","false")
            .withEnv("SPRING_RABBITMQ_LISTENER_SIMPLE_AUTOSTARTUP","false")
            .withEnv("JAVA_TOOL_OPTIONS","-Xms64m -Xmx256m")
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8086).forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)));
    private final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private JdbcTemplate db;
    private FinancialClearanceLookupAdapter consumer;
    private UUID account,patient,episode,caseId,request,invoice;
    private SurgeryFinancialClearance grant;

    @BeforeEach void paidAuthoritativeBillingFixture() throws Exception {
        db=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        db.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX CASCADE"); // Only this test-owned Billing database.
        account=UUID.randomUUID(); patient=UUID.randomUUID(); episode=UUID.randomUUID(); caseId=UUID.randomUUID();
        request=UUID.randomUUID(); invoice=UUID.randomUUID(); UUID department=UUID.randomUUID(),charge=UUID.randomUUID();
        db.update("INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at) VALUES (?,?,?,'ADMISSION',?,now())",
                account,patient,department,episode);
        db.update("INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount) VALUES (?,?,?,'SURGERY',100)",request,invoice,account);
        db.update("INSERT INTO PAYMENT_REQUEST_TARGET(payment_request_id,admission_id,surgery_case_id) VALUES (?,?,?)",request,episode,caseId);
        db.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,description,unit_amount,gross_amount,incurred_at)
                VALUES (?,?,?,?,'SURGERY',?,'TEST_ONLY_PRICE','Runtime test fixture',100,100,now())
                """,charge,account,patient,department,caseId);
        db.update("INSERT INTO PAYMENT_REQUEST_CHARGE(payment_request_id,charge_id,requested_amount) VALUES (?,?,100)",request,charge);
        var payment=http.send(HttpRequest.newBuilder(URI.create(base()+"/api/v1/billing/payment-requests/"+request+"/payments"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+humanToken()).header("Content-Type","application/json")
                .header("X-Correlation-Id","runtime-financial").POST(HttpRequest.BodyPublishers.ofString(
                        "{\"idempotencyKey\":\"runtime-payment\",\"amount\":100,\"currency\":\"VND\",\"paymentMethod\":\"CASH\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(payment.statusCode()).as(payment.body()).isEqualTo(200);
        UUID clearance=db.queryForObject("SELECT clearance_id FROM FINANCIAL_CLEARANCE",UUID.class);
        var issued=db.queryForObject("SELECT granted_at FROM FINANCIAL_CLEARANCE",java.sql.Timestamp.class).toInstant();
        grant=new SurgeryFinancialClearance(clearance,invoice,account,patient,caseId,CareEpisodeType.ADMISSION,episode,episode,
                new BigDecimal("100"),"VND","CASH",issued,null,"a".repeat(64));
        var converters=new HttpMessageConverters(new MappingJackson2HttpMessageConverter(mapper));
        var client=Feign.builder().contract(new SpringMvcContract()).decoder(new ResponseEntityDecoder(new SpringDecoder(()->converters)))
                .options(new Request.Options(2,TimeUnit.SECONDS,3,TimeUnit.SECONDS,true))
                .target(BillingFeignClient.class,base()+"/api/v1/billing");
        consumer=new FinancialClearanceLookupAdapter(client,new ServiceTokenFactory(new JwtProperties(SECRET)),Instant::now);
    }

    @Test void exactPayment_liveBillingHttp_grantsOnlyFinancialProof() {
        var proof=consumer.observe(grant,"runtime-financial");
        assertThat(proof.eligible()).isTrue();
        assertThat(proof.validUntil()).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX WHERE contract_version=1 AND publication_enabled=FALSE",Integer.class)).isEqualTo(2);
    }
    @Test void completedRefund_liveBillingRead_deniesEvenWithStoredGrantAndPaidRequest() {
        db.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,payment_request_id,transaction_type,classification,status,
                    amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                SELECT ?,account_id,payment_request_id,'REFUND',classification,'COMPLETED',1,currency,payment_method,'runtime-refund',transaction_id,now()
                    FROM PAYMENT_TRANSACTION WHERE transaction_type='PAYMENT'
                """,UUID.randomUUID());
        assertThat(consumer.observe(grant,"runtime-financial").eligible()).isFalse();
    }
    @Test void revokedAndExpired_liveBillingRead_denies() {
        db.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=now()");
        assertThat(consumer.observe(grant,"runtime-financial").eligible()).isFalse();
        db.update("UPDATE FINANCIAL_CLEARANCE SET revoked_at=NULL,expires_at=granted_at+interval '1 microsecond'");
        assertThat(consumer.observe(grant,"runtime-financial").eligible()).isFalse();
    }
    @Test void mismatchedLocalGrant_liveProducerContext_cannotAuthorizeAnotherCase() {
        var foreign=new SurgeryFinancialClearance(grant.clearanceId(),invoice,account,patient,UUID.randomUUID(),CareEpisodeType.ADMISSION,
                episode,episode,grant.amount(),"VND","CASH",grant.grantedAt(),null,grant.fingerprint());
        assertThatThrownBy(()->consumer.observe(foreign,"runtime-financial")).isInstanceOf(UpstreamUnavailableException.class);
    }
    @Test void storageFailure_liveBilling503_cannotBeMistakenForAbsence() {
        db.execute("ALTER TABLE FINANCIAL_CLEARANCE RENAME TO test_unavailable_clearance");
        try {
            assertThatThrownBy(()->consumer.observe(grant,"runtime-financial")).isInstanceOf(UpstreamUnavailableException.class);
        } finally {
            db.execute("ALTER TABLE test_unavailable_clearance RENAME TO FINANCIAL_CLEARANCE");
        }
    }

    private String humanToken() {
        return Jwts.builder().subject(UUID.randomUUID().toString()).claim("role","CASHIER").claim("type","access")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private static String base() { return "http://"+BILLING.getHost()+":"+BILLING.getMappedPort(8086); }
    private static GenericContainer<?> billing() {
        Path jar=Path.of("../billing-service/target/billing-service-0.0.1-SNAPSHOT.jar");
        if(!Files.isRegularFile(jar)) throw new IllegalStateException("Package current Billing before this runtime profile");
        var image=new ImageFromDockerfile().withFileFromPath("app.jar",jar).withDockerfileFromBuilder(builder ->
                builder.from("eclipse-temurin:21-jre-alpine").copy("app.jar","/app/app.jar").user("10001")
                        .entryPoint("java","-jar","/app/app.jar").build());
        return new GenericContainer<>(image);
    }
    @AfterAll static void shutdown() { BILLING.stop(); MQ.stop(); PG.stop(); NETWORK.close(); }
}
