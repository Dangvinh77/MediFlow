# Phase 1 Source-Alignment Report

## 1. Report metadata

| Item | Value |
|---|---|
| Project | MediFlow Hospital Management System |
| Phase | Phase 1 — Project initiation and requirements specification |
| Review date | 10 October 2026 |
| Source baseline | Commit `3f10ea6` |
| Evidence scope | Backend, gateway, web frontend, Flutter mobile application, database migrations, security rules, integration events, and automated tests |
| Document scope | Markdown and Mermaid assets in this Phase 1 folder only |

## 2. Executive conclusion

The Phase 1 package has been rewritten in English and reconciled with the current repository. The earlier material described an eight-service target and treated Inpatient and Surgery as future work. The source now contains ten implemented business services, their database migrations, security rules, REST endpoints, event publishers and consumers, and automated tests. The new requirements baseline therefore recognizes all ten contexts as implemented scope.

The source also disproves the earlier statements that authorization and messaging were absent. Controllers use endpoint-level authorization, the gateway validates access before routing, services publish and consume RabbitMQ integration events, and critical event paths use outbox dispatch, receipt-based idempotency, retry policies, and dead-letter queues.

This report does not claim that every product or operational decision is final. Matters such as production service-level objectives, disaster recovery targets, audit-log retention, accessibility conformance, and external hospital integrations remain stakeholder decisions and are listed explicitly as open items.

## 3. Alignment with the Phase 1 teaching objective

| Teaching objective | Evidence in the refreshed package |
|---|---|
| Identify the project problem | The customer requirements document explains fragmented hospital workflows, duplicated data entry, weak traceability, delayed financial clearance, and incomplete cross-department visibility. |
| Analyze customer requirements | Functional requirements are grouped by the ten source-aligned bounded contexts and by cross-cutting security and integration behavior. |
| Define actors and responsibilities | Nine source-defined roles are mapped to hospital responsibilities, permitted actions, and restrictions. |
| Prepare initial forms | The package defines the minimum fields and validation intent for patient, appointment, clinical, laboratory, pharmacy, billing, inpatient, surgery, notification, and reporting forms. |
| Establish an acceptance baseline | Each major requirement has a stable identifier, expected outcome, and verification approach. |

## 4. Source evidence reviewed

### 4.1 Implemented system structure

The Maven backend contains the gateway, Eureka server, shared contracts, and ten business services. Each business service owns its PostgreSQL schema and follows the repository's domain, application, and infrastructure boundaries.

| Context | Implemented responsibility observed in source |
|---|---|
| Organization | Users, staff profiles, departments, specialties, rooms, schedules, roles, and organizational authority. |
| Patient | Patient identity, demographics, contacts, insurance, appointments, and patient-facing records. |
| Clinical | Encounters, medical records, diagnoses, vital signs, prescriptions, clinical queues, and clinical workflow coordination. |
| Lab | Test catalog, laboratory orders, work queues, sample/result workflows, result publication, and billing triggers. |
| Pharmacy | Medicine catalog, stock, prescriptions, dispensing, replenishment, and inventory effects. |
| Billing | Invoices, charge items, payments, deposits, refunds, receipts, and financial-clearance decisions. |
| Notification | Delivery of operational and patient notifications from domain events. |
| Report | Operational and management projections built from service events. |
| Inpatient | Admission requests, bed and room coordination, deposits, inpatient stays, and discharge workflow. |
| Surgery | Surgery requests, readiness and financial clearance, scheduling, execution, outcomes, and related notifications. |

### 4.2 Security and role evidence

The Organization domain defines the following roles:

`ADMIN`, `DOCTOR`, `NURSE`, `PHARMACIST`, `CASHIER`, `LAB_TECH`, `MANAGER`, `PATIENT`, and `SYSTEM`.

The gateway provides the public API entry point. Backend controllers declare authorization rules with `@PreAuthorize`, and the design uses default deny, role checks, and ownership or context checks where a role alone is insufficient. The refreshed requirements preserve this distinction: hiding a feature in a client is only a usability measure; backend authorization remains authoritative.

### 4.3 REST and event evidence

The implementation uses two integration styles for different consistency needs:

- Versioned REST calls are used when a request must obtain a current decision before it can complete, such as authority, financial clearance, or another service's current status.
- RabbitMQ events propagate completed facts to other contexts without creating cross-service database access.

Observed consumers cover inpatient lifecycle, cash receipts and refunds, billing changes, lab and clinical queues, prescription and surgery clearance, payment completion, notification delivery, and report facts. Publishers use routing keys and exchange configuration rather than direct service-to-service table coupling.

Critical paths include safeguards for reliable asynchronous processing:

- aggregate state and its outgoing event are committed through an outbox pattern;
- consumers store event receipts or fingerprints to reject exact duplicates safely;
- transient failures are retried with a bounded policy;
- exhausted messages are routed to a service dead-letter queue;
- event payloads are versioned contracts rather than entity serialization.

### 4.4 Quality evidence

The latest complete Maven suite executed against the current repository completed successfully:

| Metric | Result |
|---|---:|
| Tests executed | 3,612 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

Coverage includes gateway behavior and all ten business services. The result supports the claim that the requirements baseline is tied to executable behavior, while not replacing product acceptance testing by hospital stakeholders.

## 5. Corrections made to the former Phase 1 baseline

| Former statement or assumption | Source-aligned correction |
|---|---|
| The architecture has eight implemented business services. | The repository contains ten implemented business services, including Inpatient and Surgery. |
| Inpatient and Surgery are approved future contexts. | Both contexts now contain production code, migrations, security, integration behavior, and tests. |
| Messaging is not implemented. | RabbitMQ publishers, listeners, queue declarations, event contracts, retries, DLQs, and outbox processing are implemented. |
| Endpoint role enforcement is missing. | Controllers across the services declare `@PreAuthorize` rules and the gateway enforces authenticated routing. |
| The repository is mostly a skeleton. | The source contains domain models, use cases, ports, adapters, controllers, migrations, and extensive automated tests across the bounded contexts. |
| Cross-context consistency can be documented as a shared transaction. | Each service owns its local transaction. Cross-context workflows use REST authority checks and asynchronous events or sagas. |
| Service entities can be treated as one shared hospital data model. | Identifiers cross boundaries as values; services must not read or join another service's database. |

## 6. Architecture baseline

![MediFlow system architecture](assets/mediflow-system-architecture.svg)

The diagram reflects these non-negotiable boundaries:

1. clients access backend functions through the gateway;
2. each business service owns its data and schema;
3. synchronous calls are reserved for current decisions required to complete a request;
4. events propagate completed facts;
5. outbox and idempotency controls protect event-driven consistency;
6. authorization, observability, and automated tests apply across all contexts.

## 7. Integration and saga baseline

![MediFlow REST and event integration flow](assets/mediflow-integration-and-saga-flow.svg)

Representative source-aligned workflows include:

- a published laboratory result creating the appropriate billing charge;
- payment or deposit clearance enabling dispensing, admission, or surgical readiness;
- prescription authority changes enabling or stopping pharmacy work;
- surgery readiness and financial clearance enabling scheduling and execution;
- dispense, refund, admission, discharge, and surgery outcomes updating reports and notifications;
- duplicate deliveries being acknowledged without applying a second business mutation.

## 8. Traceability method

The requirements document uses stable prefixes for traceability:

| Prefix | Area |
|---|---|
| `ORG` | Organization |
| `PAT` | Patient |
| `CLI` | Clinical |
| `LAB` | Laboratory |
| `PHA` | Pharmacy |
| `BIL` | Billing |
| `NOT` | Notification |
| `REP` | Reporting |
| `INP` | Inpatient |
| `SUR` | Surgery |
| `SEC` | Security and access control |
| `INT` | Integration and reliability |
| `NFR` | Non-functional requirements |

Each identifier states a verifiable outcome rather than an implementation detail. Later design, test, and acceptance artifacts can reference these identifiers without renumbering business rules.

## 9. Open stakeholder decisions

The following items are intentionally not invented from source code:

| Decision | Why stakeholder confirmation is required |
|---|---|
| Production availability target and maintenance windows | These depend on hospital operating policy and deployment budget. |
| Recovery time and recovery point objectives | These determine backup, replication, and disaster-recovery investment. |
| Audit and medical-record retention periods | These depend on applicable law and hospital governance. |
| Accessibility conformance target | The required standard and testing process must be approved by the customer. |
| External insurance, laboratory, pharmacy, and identity integrations | Vendors, protocols, credentials, and contractual responsibilities are not defined by the repository. |
| Clinical approval and escalation policies | Medical governance must define who can approve, override, or cancel sensitive actions. |
| Data migration and cutover plan | The source does not identify the customer's legacy systems or data quality. |

## 10. Phase 1 completion assessment

The refreshed package satisfies the documentation objective for Phase 1 when reviewed as a source-aligned baseline:

- the business problem and project goals are stated;
- the scope matches the ten implemented bounded contexts;
- actors and permissions reflect the role model in source;
- initial business forms cover the primary workflows;
- architecture and integration diagrams are editable Mermaid sources with English labels;
- requirements are suitable for traceability into design and testing;
- assumptions and unresolved customer decisions are explicit;
- no Word, PDF, application source, or canonical `docs/ai` file was modified by this refresh.
