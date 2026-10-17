# MediFlow Review 1 — Standard Report (English)

| Item | Description |
|---|---|
| Project | MediFlow: A Microservices-Based Hospital Information System for Outpatient Care Management |
| Review | Review 1 — Problem Definition and Customer Requirements Specification |
| Version | Standard — updated 17 October 2026 |
| Requirements baseline | MVP design in `docs/plans/`, checked against repository structure |
| Status | Requirements specification; does not certify that every function is complete |

## Part A — Problem Definition

### A1. Introduction and Context

MediFlow is a distributed hospital information system focused on multi-specialty clinics and hospital outpatient departments. Its academic MVP covers outpatient care; admission, beds, inpatient treatment and surgery are reserved for future development. It is not a completed hospital-wide production system.

MediFlow coordinates departments throughout an outpatient journey: reception, examination, laboratory tests, prescribing, payment, and dispensing. Records share a patient identity while each department remains responsible for its own data and business decisions. Web and Flutter clients access the backend through the API Gateway. The mobile foundation currently implements real login and read-only Appointment/Lab slices; other mobile contexts remain unfinished.

The project focuses on an outpatient workflow that can be verified end to end, including message redelivery and temporary service outages. Review 1 follows the six-service target; inpatient care and surgery are outside mandatory MVP requirements.

### A2. Problem Identification

- Disconnected patient information, appointments, and clinical records cause repeated entry and make progress difficult to track.
- Doctors, the laboratory, and the pharmacy lack consistent handoff status, increasing the risk of missed orders or results.
- Payment status is not clearly tied to each visit, test, or prescription, making service authorization difficult to control.
- Poor coordination of stock, reservations, and dispensing can cause duplicate dispensing or insufficient stock.
- Successful payment followed by dispensing failure requires traceable compensation rather than a lost transaction.
- Reports and notifications lag behind operations; users struggle to distinguish missing data from a measured zero.
- Medical and financial information requires access restrictions based on role, work scope, and record ownership.

### A3. Project Objectives

1. Link identity, appointments, visits, tests, prescriptions, and payments through explicit references.
2. Enforce the relevant business and financial conditions before examination, test processing, and dispensing.
3. Prevent duplicate financial or stock effects when requests or events are processed again.
4. Update reports and notifications from events while preserving fault tracing and recovery.
5. Define acceptance criteria for successful workflows, access denial, redelivery, and partial failure.

### A4. Scope and System Structure

#### A4.1. Six Target Services

| Service | MVP responsibility | Source contexts/modules |
|---|---|---|
| Core | Accounts, staff, departments, reference room data, and patient identity | Organization + Patient |
| Clinical | Appointments, check-in, records, diagnosis, orders, and outpatient completion | Clinical |
| Lab | Accept test requests, track processing, and return results | Lab |
| Pharmacy | Drug catalogue, stock, prescriptions, reservations, and dispensing | Pharmacy |
| Billing | Charges, invoices/payment requests, payments, service clearance, and compensation/refunds | Billing |
| Support | Reporting read models and in-app notifications | Report + Notification |

Gateway and Eureka are infrastructure; `common` is a shared library. Core and Support co-host modules while retaining each context's data and business boundaries. The design retains eight active context stores; six services do not imply six databases.

#### A4.2. Outside the MVP

- Admission, beds, inpatient treatment, inpatient settlement, and surgery. Existing source and data are preserved for later work.
- Full financial/operational Report V2, post-settlement corrections, and advanced KPIs.
- Integration with banks, external payment gateways, insurers, medical devices, or national systems.
- Advanced email/SMS and reminders; in-app notifications are sufficient for the MVP.
- Mobile expansion or database consolidation as conditions for backend acceptance.

### A5. Users and Stakeholders

The eight human roles are `ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, and `PATIENT`. `SYSTEM` is a service identity for automated processing, not a human login account. Functions for each role are listed in B1.

Stakeholders include patients, reception staff, doctors, the laboratory, the pharmacy, cashiers, management, and the development team. Departments agree on requirements and verify outcomes within their authorized responsibilities.

### A6. Proposed Solution

The Next.js/TypeScript/Tailwind web application accesses the backend through Gateway. The backend uses Java 21, Spring Boot, Maven, PostgreSQL, RabbitMQ, and Eureka. Each context owns its data; contexts do not query each other's databases. REST supplies information needed immediately; events support handoffs and asynchronous updates. Outbox delivery, duplicate protection, bounded retries, and dead-letter queues support recovery.

The main workflow is **identity → check-in → examination payment → examination → tests if needed → prescription if needed → medication payment → dispensing → outpatient completion → report/notification updates**. When tests are ordered, their fees and authorization are checked before protected laboratory actions.

### A7. Current State and Evidence Limits

The repository contains `backend/core-service`; Organization and Patient source packages and persistence boundaries are hosted inside Core; their old standalone source modules are no longer in the root Maven reactor. The root reactor contains 12 Maven modules, including infrastructure/common and retained deferred apps. There is no `backend/support-service` directory yet; Report and Notification remain separate modules. Inpatient and Surgery source is retained. Therefore, six services describe the deployment target, not evidence that six deployments have passed acceptance.

APIs, screens, and gated workflows must be checked against live contracts and the acceptance profile. This report does not certify that every function below is delivered, and historical test results do not count as current verification.

## Part B — Customer Requirements Specification (CRS)

### B1. Functional Requirements — Functions by Role

The following functions are MVP requirements. Each action must respect endpoint permissions, clinical authority, and record access boundaries. A role does not grant access to every record. This list neither adds APIs nor expands default permissions.

#### ADMIN — Administrator

- Manage accounts, roles, active status, and account locking.
- Manage departments, staff, and reference data required for outpatient care.
- Link accounts to staff or patients using an authoritative identity source.
- Inspect system status, history, and processing failures within permitted administration scope.
- Perform authorized recovery operations with an audit trail, without bypassing financial or clinical conditions.

#### DOCTOR — Doctor

- View appointments and patient records within the assigned scope.
- Start an examination only with valid `EXAM` clearance for the exact visit; emergency exceptions use only the supported, audited mechanism.
- Record examination findings, diagnosis, and outpatient disposition.
- Create explicit laboratory orders and view results returned for the corresponding record.
- Author prescription content; Pharmacy validates and owns prescriptions, prices, stock, and reservations.
- Follow test results and dispensing status, and complete the outpatient record when its conditions are met.

#### NURSE — Nurse / Reception Support

- Search, create, and update patient information within granted permissions.
- Assist with appointment registration and departmental schedule tracking.
- Perform check-in, update permitted reception status, and hand patients over to doctors.
- View visit information and processing progress needed for care coordination.
- Check-in does not itself authorize examination or replace financial verification.

#### LAB_TECH — Laboratory Technician

- View valid existing test requests and permitted related information.
- Start test processing only with unexpired `LAB_TEST` clearance matching the exact test.
- Update processing status and cancel when the workflow permits.
- Record a non-empty result with a valid performed date and complete the test.
- Publish results to relevant departments through the Lab workflow; terminal results are not edited in the MVP.

#### PHARMACIST — Pharmacist

- Manage and search the drug catalogue, stock, available quantities, and expiry information.
- Receive doctor-authored prescription content and verify patient identity and outpatient clinical context.
- Create/process permitted prescriptions using server catalogue prices and line-level reservations.
- Check `PRESCRIPTION` clearance for the exact prescription, reservation validity, and stock before dispensing.
- Confirm dispensing; apply the stock effect once only when all lines satisfy the conditions.
- Cancel or expire prescriptions through the supported workflow, release reservations, and report failures to Billing when financial adjustment is required.
- A payment receipt does not automatically replace dispensing authorization in the target MVP workflow.

#### CASHIER — Cashier

- Find charges belonging to the exact visit/patient within permitted service scope.
- Create invoices or payment requests from valid charges in the same visit account.
- Record payments and inspect transaction status and corresponding documents.
- Track financial clearance for the exact examination, test, or prescription; do not manually grant access using a patient-wide paid flag.
- Process compensation/refunds through Billing's workflow and authorization when cancellation or dispensing failure requires it.
- Inspect transaction history for reconciliation; do not rewrite or delete history to force a balance match.

#### MANAGER — Manager

- View reports on visits, tests, prescriptions, and dispensed quantities.
- View cash received, refunds, and net cash from available authoritative sources.
- Filter reports using criteria supported by the reporting contract.
- Distinguish missing source coverage from zero, and cash received from earned revenue.
- Track report freshness without modifying source business data from reporting screens.

#### PATIENT — Patient

- Sign in and access information associated with their own patient identity.
- View and update permitted personal information.
- Request, view, and track their own appointments through supported workflows.
- View their own examination information, test results, and prescriptions approved for patient access.
- View their own charges, invoices, payment status, and dispensing status through available contracts.
- View in-app notifications addressed to them; do not access another patient's records or notifications.

#### SYSTEM — Service Identity

- Transfer valid outpatient events between contexts while preserving identifiers and traceability.
- Automatically create charges/service clearance from valid business sources in the selected profile.
- Handle redelivery, retries, and pending-work recovery without duplicate financial or stock effects.
- Update reporting read models and create in-app notifications for the correct recipients.
- Move messages that remain unprocessable after bounded retries to dead-letter handling for inspection.
- Do not substitute service tokens for human accounts on operations requiring an authenticated human actor.

### B2. Client Hardware Requirements

These are planning recommendations for browser access, not measured capacity guarantees.

| Device | Indicative minimum | Recommended |
|---|---|---|
| Staff / reception / Lab workstation | 64-bit dual-core CPU, 4 GB RAM, 1366×768 display, stable network | 4-core CPU, 8 GB RAM, Full HD, wired or reliable Wi-Fi |
| Patient device | Smartphone, tablet, or computer with a compatible browser | Device receiving security updates, 4 GB RAM or more |
| Development workstation running the demo stack | 8 logical cores, 16 GB RAM, 40 GB free SSD | 32 GB RAM and at least 60 GB free SSD |

### B3. Server Hardware Requirements

| Environment | CPU | RAM | Storage |
|---|---|---|---|
| Local development demonstration | 8 logical cores | 16 GB | 40 GB free SSD |
| Team integration | 16 logical cores | 32 GB | 150 GB SSD |

These profiles support planning for applications, PostgreSQL, and RabbitMQ. Actual capacity depends on enabled services, datasets, and test workloads. Production sizing, redundancy, and backup storage require load measurements and recovery tests; Review 1 does not promise production capacity.

### B4. Software, Security, and Reliability Requirements

- Clients use a JavaScript-enabled browser and HTTPS; development uses Node.js/pnpm compatible with the frontend package/lockfile.
- The backend uses Java 21, Spring Boot/Maven, PostgreSQL, RabbitMQ, Eureka, and Gateway as configured in the repository. Dependency versions come from current manifests.
- Verify JWTs and permissions at Gateway and downstream services; deny by default. Patients access only resources with verified ownership.
- Use UUID references across contexts; do not perform cross-context SQL access. Co-hosted Core/Support modules retain separate datasources and transactions.
- Represent money with exact decimals. Charges, payments, refunds, and dispensing require traceable history and duplicate protection.
- Persist critical changes and their outbox records in the same transaction; consumers use deduplication, bounded retries, and dead-letter handling.
- When required authority or data sources are unavailable, do not assume payment, permission, or a measured zero.
- Protect configuration secrets, limit sensitive log data, and verify backup/restore. Performance objectives require measurement and agreement in the acceptance environment.

### B5. Acceptance Criteria

1. Verify login and reject locked accounts, wrong roles, wrong token types, and cross-patient access.
2. Register a patient and appointment and perform check-in; examination is blocked without clearance and permitted with the exact valid clearance.
3. Create a test request; missing, wrong, or expired clearance blocks processing; valid results reach Clinical.
4. Create a prescription with server prices and reservations; pay for the correct target; dispense once without negative stock or partial order effects.
5. Repeated payment requests/events do not duplicate payments, charges, or stock effects.
6. Stop/restart Pharmacy and simulate failure after commit; pending work resumes without lost or duplicate effects.
7. Cancellation, expiry, dispensing failure, and late payment preserve terminal states; Billing compensates once in the selected mode and does not claim a completed refund without evidence.
8. Reports and notifications update asynchronously for the correct recipients; report replay does not resend notifications.
9. Run the six-service profile with infrastructure; keep Inpatient/Surgery disabled in the MVP and preserve accepted data/effects during rollback.

Results must identify the commit, profile/flags, fixtures, and actual test evidence specified in the [acceptance runbook](../../../plans/06-acceptance-runbook.md). These are criteria to demonstrate, not recorded passing results.

## References

- [System design and MVP scope](../../../plans/01-system-design.md)
- [Business workflows](../../../plans/02-business-flows.md)
- [Integration contracts and RBAC](../../../plans/03-contracts.md)
- [Implementation plan](../../../plans/05-implementation-plan.md)
- [Acceptance and operations](../../../plans/06-acceptance-runbook.md)
