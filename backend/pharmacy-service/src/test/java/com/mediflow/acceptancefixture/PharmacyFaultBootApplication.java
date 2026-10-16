package com.mediflow.acceptancefixture;

import com.mediflow.pharmacy.PharmacyServiceApplication;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionUseCase;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.domain.model.PaymentReceipt;
import com.mediflow.pharmacy.domain.model.enums.PaymentReceiptStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * TEST CLASSPATH ONLY. Loaded explicitly by PropertiesLauncher in disposable acceptance containers.
 * Outside the production component-scan root; never included in the shipped Boot JAR.
 */
public final class PharmacyFaultBootApplication {
    public static void main(String[] arguments) {
        SpringApplication.run(new Class<?>[]{PharmacyServiceApplication.class, FaultConfiguration.class}, arguments);
    }

    @Configuration(proxyBeanMethods=false)
    static class FaultConfiguration {
        @Bean static FaultControl acceptanceFaultControl() { return new FaultControl(); }
        @Bean static BeanPostProcessor acceptanceFaultInterceptor(FaultControl control) {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    if (!java.util.Set.of("paymentReceiptPersistenceAdapter","processedEventPersistenceAdapter",
                            "pharmacyOutboxClaimService").contains(name)) return bean;
                    var proxy=new ProxyFactory(bean);proxy.setProxyTargetClass(true);
                    proxy.addAdvice((MethodInterceptor) call->{
                        if(name.equals("paymentReceiptPersistenceAdapter") && call.getMethod().getName().equals("save")
                                && call.getArguments()[0] instanceof PaymentReceipt receipt
                                && receipt.getStatus()!=PaymentReceiptStatus.RECEIVED)
                            control.pause("BEFORE_FINALIZE",receipt.getPrescriptionId());
                        if(name.equals("pharmacyOutboxClaimService") && call.getMethod().getName().equals("markPublished"))
                            control.pausePublishedEvent((UUID)call.getArguments()[0]);
                        Object result=call.proceed();
                        if(name.equals("processedEventPersistenceAdapter") && call.getMethod().getName().equals("claimIfAbsent"))
                            control.pauseProcessedEvent((UUID)call.getArguments()[0]);
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
        @Bean AcceptanceController acceptanceController(FaultControl control,JdbcTemplate jdbc,
                ApplicationContext context,CreateCarePrescriptionUseCase creator) {
            control.jdbc=jdbc;return new AcceptanceController(control,jdbc,context,creator);
        }
    }

    static final class FaultControl {
        volatile String point="NONE";
        volatile UUID target;
        volatile JdbcTemplate jdbc;
        final AtomicBoolean reached=new AtomicBoolean();
        volatile CountDownLatch release=new CountDownLatch(1);
        synchronized void arm(String mode,UUID prescription) {
            release.countDown();point=mode;target=prescription;reached.set(false);release=new CountDownLatch(1);
        }
        void pause(String mode,UUID prescription) throws InterruptedException {
            if(mode.equals(point) && prescription.equals(target) && reached.compareAndSet(false,true)
                    && !release.await(120,TimeUnit.SECONDS))
                throw new IllegalStateException("Isolated acceptance fault wait timed out");
        }
        void pauseProcessedEvent(UUID event) throws InterruptedException {
            if(!"BEFORE_ACK".equals(point))return;
            var ids=jdbc.queryForList("SELECT prescription_id FROM payment_receipt WHERE event_id=?",UUID.class,event);
            if(!ids.isEmpty())pause("BEFORE_ACK",ids.getFirst());
        }
        void pausePublishedEvent(UUID event) throws InterruptedException {
            if(!"BEFORE_PUBLISH_MARK".equals(point))return;
            var ids=jdbc.queryForList("SELECT aggregate_id FROM pharmacy_event_outbox WHERE event_id=? AND routing_key='prescription.filled'",UUID.class,event);
            if(!ids.isEmpty())pause("BEFORE_PUBLISH_MARK",ids.getFirst());
        }
    }

    @RestController
    @RequestMapping("/api/v1/pharmacy/admin/acceptance")
    @PreAuthorize("hasRole('ADMIN')")
    static class AcceptanceController {
        private final FaultControl control;
        private final JdbcTemplate jdbc;
        private final ApplicationContext context;
        private final CreateCarePrescriptionUseCase creator;
        AcceptanceController(FaultControl control,JdbcTemplate jdbc,ApplicationContext context,CreateCarePrescriptionUseCase creator) {
            this.control=control;this.jdbc=jdbc;this.context=context;this.creator=creator;
        }
        @PostMapping("/faults/{point}/{prescription}")
        Map<String,Object> arm(@PathVariable String point,@PathVariable UUID prescription) {
            if(!java.util.Set.of("NONE","BEFORE_FINALIZE","BEFORE_ACK","BEFORE_PUBLISH_MARK").contains(point))
                throw new IllegalArgumentException("Unknown isolated fault point");
            control.arm(point,prescription);return Map.of("testOnly",true,"point",point);
        }
        @GetMapping("/state/{prescription}")
        Map<String,Object> state(@PathVariable UUID prescription) {
            return Map.ofEntries(Map.entry("testOnly",true),Map.entry("paused",control.reached.get()),
                    Map.entry("prescription",jdbc.queryForMap("SELECT status,care_contract_version FROM prescription WHERE prescription_id=?",prescription)),
                    Map.entry("slip",jdbc.queryForMap("SELECT status FROM dispense_slip WHERE prescription_id=?",prescription)),
                    Map.entry("stock",jdbc.queryForList("SELECT d.stock_quantity FROM drug d JOIN prescription_line l ON d.drug_id=l.drug_id WHERE l.prescription_id=? ORDER BY d.drug_id",Integer.class,prescription)),
                    Map.entry("reservations",jdbc.queryForList("SELECT status FROM stock_reservation WHERE prescription_id=? ORDER BY drug_id",String.class,prescription)),
                    Map.entry("receipts",jdbc.queryForList("SELECT status FROM payment_receipt WHERE prescription_id=? ORDER BY event_id",String.class,prescription)),
                    Map.entry("grants",jdbc.queryForObject("SELECT count(*) FROM prescription_clearance WHERE prescription_id=?",Long.class,prescription)),
                    Map.entry("outbox",jdbc.queryForList("SELECT event_id,routing_key,payload,published_at IS NOT NULL AS published FROM pharmacy_event_outbox WHERE aggregate_id=? ORDER BY created_at,event_id",prescription)));
        }
        @GetMapping("/mode")
        Map<String,Object> mode() {
            return Map.of("testOnly",true,"application",context.getEnvironment().getProperty("spring.application.name"),
                    "admissionPorts",context.getBeansOfType(com.mediflow.pharmacy.application.port.out.AdmissionAuthorityPort.class).size(),
                    "admissionConsumers",context.getBeansOfType(com.mediflow.pharmacy.messaging.consumer.AdmissionLifecycleConsumer.class).size(),
                    "inpatientClients",context.getBeansOfType(com.mediflow.pharmacy.infrastructure.client.PharmacyInpatientFeignClient.class).size(),
                    "legacyPaymentConsumers",context.getBeansOfType(com.mediflow.pharmacy.messaging.consumer.PaymentCompletedConsumer.class).size(),
                    "patientDestination",context.getEnvironment().getProperty("mediflow.pharmacy.patient.service-id","patient-service"),
                    "organizationDestination",context.getEnvironment().getProperty("mediflow.pharmacy.organization.service-id","organization-service"),
                    "openInView",context.getEnvironment().getProperty("spring.jpa.open-in-view",Boolean.class,true));
        }
        @PostMapping("/expire/{prescription}")
        Map<String,Object> expire(@PathVariable UUID prescription) {
            jdbc.update("UPDATE stock_reservation SET expires_at=? WHERE prescription_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(5)),prescription);
            context.getBean(com.mediflow.pharmacy.application.service.ExpirePrescriptionTransaction.class).expire(prescription,Instant.now());
            return Map.of("testOnly",true);
        }
        @PostMapping("/shortage/{prescription}")
        Map<String,Object> shortage(@PathVariable UUID prescription) {
            jdbc.update("UPDATE drug SET stock_quantity=0 WHERE drug_id IN (SELECT drug_id FROM prescription_line WHERE prescription_id=?)",prescription);
            return Map.of("testOnly",true);
        }
        @PostMapping("/outbox-rejection/{enabled}")
        Map<String,Object> rejectOutbox(@PathVariable boolean enabled) {
            if(enabled) {
                jdbc.execute("CREATE FUNCTION reject_isolated_filled() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.routing_key='prescription.filled' THEN RAISE EXCEPTION 'isolated outbox failure'; END IF; RETURN NEW; END $$");
                jdbc.execute("CREATE TRIGGER isolated_filled_failure BEFORE INSERT ON pharmacy_event_outbox FOR EACH ROW EXECUTE FUNCTION reject_isolated_filled()");
            } else {
                jdbc.execute("DROP TRIGGER IF EXISTS isolated_filled_failure ON pharmacy_event_outbox");
                jdbc.execute("DROP FUNCTION IF EXISTS reject_isolated_filled()");
            }
            return Map.of("testOnly",true);
        }
        /** Only test data in Pharmacy's own store, using the real trusted V1 transaction kernel. */
        @PostMapping("/outpatient-prescriptions")
        Map<String,Object> createOutpatient(@RequestBody CreatePrescriptionRequest request) {
            UUID id=creator.createCare(UUID.randomUUID(),new CreatePrescriptionCommand(request,
                    new ActorIdentity(UUID.randomUUID(),null,"ADMIN"),"isolated-v1-kernel"));
            return Map.of("testOnly",true,"prescriptionId",id);
        }
        /** Fixture-owned IDs from original Billing bytes, not a real Clinical authority/creation claim. */
        @PostMapping("/clearance-fixture-target")
        Map<String,Object> seedFixtureTarget(@RequestBody FixtureTarget seed) {
            jdbc.update("""
                    INSERT INTO prescription(prescription_id,patient_id,doctor_id,department_id,prescribed_date,total_amount,
                        care_contract_version,care_context,care_episode_type,care_episode_id,price_code)
                    VALUES (?,?,?,?,CURRENT_DATE,100,1,'OUTPATIENT','OUTPATIENT_VISIT',?,'DRUG')
                    """,seed.prescriptionId(),seed.patientId(),UUID.randomUUID(),UUID.randomUUID(),seed.episodeId());
            jdbc.update("INSERT INTO prescription_line(line_id,prescription_id,drug_id,quantity,unit_price,line_total,drug_name_snapshot) VALUES (?,?,?,1,100,100,'ISOLATED fixture target')",
                    UUID.randomUUID(),seed.prescriptionId(),seed.drugId());
            jdbc.update("INSERT INTO dispense_slip(dispense_id,prescription_id,status) VALUES (?,?,'PENDING')",UUID.randomUUID(),seed.prescriptionId());
            return Map.of("testOnly",true);
        }
    }
    record FixtureTarget(UUID prescriptionId,UUID patientId,UUID episodeId,UUID drugId){}
}
