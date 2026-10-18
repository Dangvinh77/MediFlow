# Vinh / Loc outpatient backend acceptance

This directory is the durable, synthetic-data evidence bundle for the closeout at baseline
`e44ddf487523f8e781e53196d28f17db32beed1e` plus the working patch identified in `manifest.json`.
Raw response/event JSON is copied without reformatting. SHA-256 values in the manifest cover the
files actually delivered here. No credentials, login responses, real patient data or full test XML
environment dumps are included.

`billing/` contains the eight publication cases, original broker bodies, separately captured stored
outbox bodies, HTTP discovery/payment responses, Flyway version/checksum inventory and outbox
progress snapshots. Directory names identify the owning test case. Each positive broker event is
matched to its original stored body by event ID and hash. Deferred/historical and partial-payment
cases retain their negative controls. Billing source events in this suite are original producer
fixtures; this suite alone does not prove a live Clinical/Pharmacy producer workflow.

`integrated/` contains evidence from the actual Gateway/Core/Clinical/Lab/Billing packaged chain.
EXAM and LAB_TEST requests originate from public clinical work, are discovered and paid through
Gateway, and are published by Billing. The test-only ACK barrier pauses the production consumer
after its durable effect, then kills the child JVM. The same JAR restarts on the same stores/broker;
the broker redelivers the original bytes. The three checkpoints are Clinical EXAM clearance, Lab
LAB_TEST clearance, and Clinical Lab result attachment. Original/redelivered bodies and distinct
process IDs are recorded. Effects and inbox identity remain singular. The launcher loads only a
test barrier around the production listener (clearance factory) or retry interceptor (generic events), preserving production retry and AUTO ACK behavior; no production recovery hook exists.

Timestamp regression uses `2026-10-18T08:00:00.123456100Z` and
`2026-10-18T08:00:00.999999900Z`. First payment, PostgreSQL readback, HTTP retry and receipt
`completedAt` must all agree at microsecond precision. Expiry checks and clearance grant instants
keep the full clock precision. Existing stored event bodies and migrations are unchanged.

Run from the repository root with Java 21 and Docker available:

```powershell
mvn -q -pl backend/billing-service -am -Poutpatient-publication-acceptance verify
mvn -q -pl backend/notification-service -am test
mvn -q -pl backend/clinical-service,backend/lab-service,backend/gateway -am test
mvn -q -pl backend/eureka-server,backend/core-service,backend/gateway,backend/billing-service,backend/clinical-service,backend/lab-service -am -DskipTests package
mvn -q -f backend/clinical-service/pom.xml '-Ppackaged-acceptance,process-recovery' '-Dit.test=ClinicalExamPackagedAcceptanceIT,ConsumerProcessRecoveryIT' test-compile failsafe:integration-test failsafe:verify
mvn -q -f backend/lab-service/pom.xml -Pprocess-recovery '-Dit.test=ConsumerProcessRecoveryIT' test-compile failsafe:integration-test failsafe:verify
$taskRoot=(Get-Location).Path
mvn -q -f backend/lab-service/pom.xml -Ppackaged-slice test-compile `
  "-Dlab.acceptance.core.jar=$taskRoot/backend/core-service/target/core-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.gateway.jar=$taskRoot/backend/gateway/target/gateway-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.eureka.jar=$taskRoot/backend/eureka-server/target/eureka-server-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.clinical.jar=$taskRoot/backend/clinical-service/target/clinical-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.lab.jar=$taskRoot/backend/lab-service/target/lab-service-0.0.1-SNAPSHOT.jar" `
  "-Dlab.acceptance.billing.jar=$taskRoot/backend/billing-service/target/billing-service-0.0.1-SNAPSHOT.jar" `
  failsafe:integration-test failsafe:verify
git diff --check
```

Raw Billing exports regenerate in `backend/billing-service/target/outpatient-handoff/raw/`.
Integrated exports regenerate in `backend/lab-service/target/integrated-recovery/` and
`target/paid-lab-{evidence,clearance}.json`; Clinical EXAM exports are in its own `target/`.
Surefire and Failsafe counts are separate in the manifest; selected reruns are not added to full
suite totals. Runtime flags/catalogs are isolated acceptance settings, never a production activation.
Injected NACK/confirm timeout proof is explicitly separate from actual-broker publication/recovery.
Cross-owner global rollout, frontend/mobile and deferred Inpatient/Surgery are outside this bundle.
