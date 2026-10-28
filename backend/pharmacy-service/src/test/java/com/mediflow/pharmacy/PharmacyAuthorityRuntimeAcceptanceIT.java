package com.mediflow.pharmacy;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.infrastructure.client.*;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import feign.Feign;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Packaged Core/Clinical, actual signed Pharmacy adapters. All producer setup is through their APIs. */
@Testcontainers
class PharmacyAuthorityRuntimeAcceptanceIT {
    private static final String SECRET="isolated-pharmacy-authority-runtime-secret-at-least-32-bytes";
    private static final Network NETWORK=Network.newNetwork();
    @Container static final PostgreSQLContainer<?> ORGANIZATION_DB=database("organization-db");
    @Container static final PostgreSQLContainer<?> PATIENT_DB=database("patient-db");
    @Container static final PostgreSQLContainer<?> CLINICAL_DB=database("clinical-db");
    @Container static final RabbitMQContainer BROKER=new RabbitMQContainer("rabbitmq:3.13-alpine").withNetwork(NETWORK).withNetworkAliases("rabbit");
    @Container static final GenericContainer<?> CORE=service("core",8089).dependsOn(ORGANIZATION_DB,PATIENT_DB,BROKER)
            .withEnv("MEDIFLOW_ORGANIZATION_DB_URL",url("organization-db"))
            .withEnv("MEDIFLOW_ORGANIZATION_DB_USER",ORGANIZATION_DB.getUsername()).withEnv("MEDIFLOW_ORGANIZATION_DB_PASSWORD",ORGANIZATION_DB.getPassword())
            .withEnv("MEDIFLOW_PATIENT_DB_URL",url("patient-db"))
            .withEnv("MEDIFLOW_PATIENT_DB_USER",PATIENT_DB.getUsername()).withEnv("MEDIFLOW_PATIENT_DB_PASSWORD",PATIENT_DB.getPassword());
    @Container static final GenericContainer<?> CLINICAL=service("clinical",8082).dependsOn(CLINICAL_DB,CORE,BROKER)
            .withEnv("SPRING_DATASOURCE_URL",url("clinical-db")).withEnv("MEDIFLOW_DB_USER",CLINICAL_DB.getUsername())
            .withEnv("MEDIFLOW_DB_PASSWORD",CLINICAL_DB.getPassword())
            .withEnv("MEDIFLOW_CLINICAL_PRESCRIPTION_CONTEXT_LOOKUP_ENABLED","true")
            // Use the current durable producer during public API setup; lookup acceptance must
            // not depend on the old workaround for Clinical's already-fixed timestamp regression.
            .withEnv("MEDIFLOW_CLINICAL_DURABLE_EVENTS_ENABLED","true")
            .withEnv("MEDIFLOW_ORGANIZATION_SERVICE","core-service").withEnv("MEDIFLOW_PATIENT_SERVICE","core-service")
            .withEnv("SPRING_APPLICATION_JSON","{\"spring\":{\"cloud\":{\"discovery\":{\"client\":{\"simple\":{\"instances\":{\"core-service\":[{\"uri\":\"http://core:8089\"}]}}}}}}}");
    private final ObjectMapper mapper=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test void packagedCoreAndClinicalAcceptRealPharmacyCallerWithExactCreatedIdentityAndEpisode()throws Exception {
        var department=command(CORE,8089,"/api/v1/org/departments",Map.of("departmentName","Isolated authority department",
                "abbreviation","T"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),"departmentType","CLINICAL"));
        UUID departmentId=UUID.fromString(department.path("departmentId").asText());
        var staff=command(CORE,8089,"/api/v1/org/staff",Map.of("fullName","Isolated authority doctor","departmentId",departmentId,
                "jobTitle","DOCTOR","specialization","Isolated acceptance","licenseNumber","ISOLATED-TEST",
                "identityNumber","999999999902"));
        UUID doctor=UUID.fromString(staff.path("staffId").asText());
        var patient=command(CORE,8089,"/api/v1/patients",Map.of("hoTen","Isolated authority patient","ngaySinh","1990-01-01",
                "gioiTinh","M","soCmnd",UUID.randomUUID().toString().replace("-","").substring(0,12)));
        UUID patientId=UUID.fromString(patient.path("maBenhNhan").asText());
        var identities=identities();
        var proof=identities.lookup(patientId,doctor,departmentId,UUID.randomUUID().toString());
        proof.requireExact(patientId,doctor,departmentId,Instant.now());
        assertThat(proof.patientExists()).isTrue();assertThat(proof.eligibleDoctor()).isTrue();
        var record=command(CLINICAL,8082,"/api/v1/records",Map.of("patientId",patientId,"doctorId",doctor,"departmentId",departmentId,
                "examinationDate",LocalDate.now().toString(),"symptoms","Synthetic contract acceptance",
                "diagnoses",List.of(Map.of("diagnosisName","Synthetic contract acceptance","icdCode","Z00"))));
        UUID recordId=UUID.fromString(record.path("recordId").asText());
        var context=contexts().findRecord(recordId,UUID.randomUUID().toString());
        context.requireExact(recordId,patientId,doctor,departmentId,context.careEpisodeId(),Instant.now());
        assertThat(context.careEpisodeType()).isEqualTo("OUTPATIENT_VISIT");
        // Walk-in episode identity is copied from Clinical, never invented from patient/latest record.
        assertThat(context.recordStatus()).isEqualTo("OPEN");assertThat(context.disposition()).isNull();
    }

    @Test void packagedAbsenceIsNotUnavailableAndHumanRefreshWrongCallerCannotReadServiceContext()throws Exception {
        UUID missing=UUID.randomUUID();
        var identity=identities().lookup(missing,missing,missing,UUID.randomUUID().toString());
        assertThat(identity.patientExists()).isFalse();assertThat(identity.doctorExists()).isFalse();assertThat(identity.departmentActive()).isFalse();
        var context=contexts().findRecord(missing,UUID.randomUUID().toString());assertThat(context.exists()).isFalse();assertThat(context.careEpisodeId()).isNull();
        // Core's actual filter normalizes UUID correlations. Rewritten non-UUID echoes are not accepted as correlated proof.
        assertThatThrownBy(()->identities().lookup(missing,missing,missing,"non-uuid-correlation"))
                .isInstanceOf(com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException.class);
        for(String type:List.of("access","refresh","service")) {
            String role=type.equals("service")?"SYSTEM":"ADMIN",subject=type.equals("service")?"untrusted-service":UUID.randomUUID().toString();
            var response=http.send(HttpRequest.newBuilder(URI.create(base(CLINICAL,8082)+"/api/v1/records/"+missing+"/prescription-context"))
                    .header("Authorization","Bearer "+token(subject,role,type)).header("X-Correlation-Id","denied-context").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(type).isIn(401,403);
        }
    }

    private PrescriptionIdentityAdapter identities(){return new PrescriptionIdentityAdapter(client(PharmacyPatientFeignClient.class,base(CORE,8089)),
            client(PharmacyOrganizationFeignClient.class,base(CORE,8089)),Clock.systemUTC(),new JwtProperties(SECRET),mapper);}
    private PharmacyClinicalContextAdapter contexts(){return new PharmacyClinicalContextAdapter(client(PharmacyClinicalFeignClient.class,base(CLINICAL,8082)),Clock.systemUTC(),new JwtProperties(SECRET),mapper);}
    private <T>T client(Class<T> type,String base){return Feign.builder().contract(new SpringMvcContract())
            .decoder(new ResponseEntityDecoder(new feign.codec.StringDecoder())).options(new feign.Request.Options(2,java.util.concurrent.TimeUnit.SECONDS,3,java.util.concurrent.TimeUnit.SECONDS,true)).target(type,base);}
    private JsonNode command(GenericContainer<?> app,int port,String path,Object body)throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create(base(app,port)+path)).timeout(Duration.ofSeconds(12))
                .header("Authorization","Bearer "+token(UUID.randomUUID().toString(),"ADMIN","access"))
                .header("X-Correlation-Id",UUID.randomUUID().toString()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=201) {
            var lines=app.getLogs().replaceAll("(?m)^Using generated security password:.*$","[redacted development password]").split("\\R");
            Arrays.stream(lines).filter(line->line.contains(" ERROR ") || line.contains("Exception:") || line.contains("Caused by:"))
                    .forEach(System.err::println);
            System.err.println(String.join("\n",Arrays.copyOfRange(lines,Math.max(0,lines.length-15),lines.length)));
        }
        assertThat(response.statusCode()).as(path+": "+response.body()).isEqualTo(201);
        var root=mapper.readTree(response.body());assertThat(root.path("success").asBoolean()).isTrue();return root.path("data");
    }
    private String token(String subject,String role,String type){return Jwts.builder().subject(subject).claim("role",role).claim("type",type)
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120))).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
    private static PostgreSQLContainer<?> database(String alias){return new PostgreSQLContainer<>("postgres:16-alpine").withNetwork(NETWORK).withNetworkAliases(alias);}
    private static String url(String alias){return "jdbc:postgresql://"+alias+":5432/test";}
    private static GenericContainer<?> service(String name,int port) {
        var jar=Path.of("../"+name+"-service/target/"+name+"-service-0.0.1-SNAPSHOT.jar");
        if(!Files.isRegularFile(jar))throw new IllegalStateException("Package current "+name+" first");
        var image=new ImageFromDockerfile().withFileFromPath("app.jar",jar).withDockerfileFromBuilder(b->b.from("eclipse-temurin:21-jre-alpine")
                .copy("app.jar","/app/app.jar").user("10001").entryPoint("java","-jar","/app/app.jar").build());
        return new GenericContainer<>(image).withNetwork(NETWORK).withNetworkAliases(name).withExposedPorts(port)
                .withEnv("MEDIFLOW_RABBIT_HOST","rabbit").withEnv("MEDIFLOW_RABBIT_USER",BROKER.getAdminUsername())
                .withEnv("MEDIFLOW_RABBIT_PASSWORD",BROKER.getAdminPassword()).withEnv("MEDIFLOW_JWT_SECRET",SECRET)
                .withEnv("EUREKA_CLIENT_ENABLED","false").withEnv("JAVA_TOOL_OPTIONS","-Xms64m -Xmx256m")
                .waitingFor(Wait.forHttp("/actuator/health").forPort(port).forStatusCode(200).withStartupTimeout(Duration.ofSeconds(180)));
    }
    private static String base(GenericContainer<?> app,int port){return "http://"+app.getHost()+":"+app.getMappedPort(port);}
    @AfterAll static void close(){CLINICAL.stop();CORE.stop();CLINICAL_DB.stop();PATIENT_DB.stop();ORGANIZATION_DB.stop();BROKER.stop();NETWORK.close();}
}
