# Customer Requirements Specification and Initial Business Forms

## Document control

| Item | Value |
|---|---|
| System | MediFlow Hospital Management System |
| Phase | Phase 1 — Project initiation and requirements specification |
| Version | 2.0, source-aligned English edition |
| Review date | 10 October 2026 |
| Source baseline | Commit `3f10ea6` |
| Status | Requirements baseline for stakeholder review |

## 1. Purpose

This document establishes the Phase 1 customer requirements baseline for MediFlow. It defines the business problem, project scope, stakeholders, roles, functional requirements, initial forms, integration rules, non-functional expectations, and acceptance criteria.

The document reflects the current repository. It does not replace the canonical architecture and coding rules used by the implementation team, and it does not invent customer policies that are absent from source.

## 2. Business problem

Hospitals coordinate patient identity, appointments, clinical care, laboratory work, medication, charges, payments, admission, surgery, notifications, and management reporting across multiple departments. When those workflows rely on disconnected tools or manual handoffs, the hospital faces several recurring problems:

- staff enter the same information repeatedly and can create inconsistent records;
- clinicians lack a clear, current view of orders, results, prescriptions, and care status;
- financial clearance can be delayed or applied to the wrong operational step;
- pharmacy, laboratory, inpatient, and surgery teams receive incomplete or late instructions;
- managers cannot reliably trace operational events into consolidated reports;
- patients receive inconsistent status information;
- authorization and audit evidence are difficult to enforce uniformly.

MediFlow addresses these problems by assigning clear ownership to ten business contexts, exposing one secured API entry point, and coordinating cross-context work through current REST decisions and reliable integration events.

## 3. Project goals

MediFlow shall:

1. provide one coherent workflow from patient registration through care, payment, admission, surgery, discharge, notification, and reporting;
2. preserve one accountable owner for each category of hospital data;
3. enforce role and context authorization at every backend endpoint;
4. prevent cross-service database coupling;
5. make completed business facts available to affected departments through reliable events;
6. make duplicates, retries, and partial failures safe and observable;
7. support browser, mobile, and authorized API clients through the gateway;
8. provide traceable records suitable for operational review and later audit requirements.

## 4. Scope

### 4.1 Included scope

| Context | Phase 1 capability boundary |
|---|---|
| Organization | Users, staff, departments, specialties, rooms, schedules, roles, and organizational authority. |
| Patient | Patient profile, contacts, insurance, appointments, and patient-facing access. |
| Clinical | Encounters, medical records, diagnoses, vital signs, prescriptions, and clinical work queues. |
| Lab | Test catalog, orders, work queues, result entry and publication, and billing triggers. |
| Pharmacy | Medicine catalog, stock, prescription validation, dispensing, and inventory effects. |
| Billing | Invoices, charge items, payments, deposits, receipts, refunds, and financial clearance. |
| Notification | Event-driven messages and delivery status. |
| Report | Operational and management projections derived from service facts. |
| Inpatient | Admission requests, room or bed coordination, deposits, stays, and discharge workflow. |
| Surgery | Requests, readiness, financial clearance, scheduling, execution, and outcomes. |

### 4.2 Technical scope

- Next.js web client.
- Flutter mobile client.
- API gateway and Eureka service discovery.
- Ten Spring Boot business services.
- One PostgreSQL database per business service.
- RabbitMQ integration events.
- JWT-based authentication context and role-based endpoint authorization.
- Health, logging, correlation, retry, dead-letter, and automated-test controls.

### 4.3 Out of scope for this baseline

- selection or contracting of external insurance, payment, laboratory, pharmacy, or identity vendors;
- medical-device integration;
- production infrastructure sizing and final hosting topology;
- migration rules for an unidentified legacy hospital system;
- final legal retention periods and clinical governance policies;
- changes to generated Word or PDF submissions.

## 5. Stakeholders and users

| Stakeholder | Interest in the system |
|---|---|
| Hospital leadership | Operational visibility, risk control, capacity, financial status, and service quality. |
| Hospital administrators | Identity, roles, organization structure, rooms, schedules, and configuration. |
| Doctors | Clinical decisions, diagnoses, orders, prescriptions, admissions, and surgery workflows. |
| Nurses | Care coordination, vital signs, queues, inpatient operations, and patient status. |
| Laboratory technicians | Laboratory work queues, specimen or execution status, and result publication. |
| Pharmacists | Prescription authorization, medication stock, dispensing, and replenishment. |
| Cashiers | Invoice collection, deposits, receipts, refunds, and financial status. |
| Managers | Reports and operational oversight within authorized scope. |
| Patients | Their own profile, appointments, permitted records, notifications, and payment information. |
| System processes | Trusted service-to-service actions that are not performed by a human account. |

## 6. Role model

The application role set is defined by source and must not be expanded by a client application.

| Role | Primary responsibilities | Key restrictions |
|---|---|---|
| `ADMIN` | Manage users, roles, departments, specialties, rooms, schedules, and authorized configuration. | Administrative access does not automatically grant clinical authority for medical decisions. |
| `DOCTOR` | Conduct encounters, record diagnoses, create orders and prescriptions, and initiate authorized admission or surgery work. | May act only within authenticated and authorized context. |
| `NURSE` | Record nursing observations, vital signs, and support clinical and inpatient workflows. | Cannot assume doctor, cashier, pharmacist, or lab authority. |
| `PHARMACIST` | Maintain medication operations, validate dispensable prescriptions, and record dispensing. | Cannot dispense without valid authority and required financial clearance. |
| `CASHIER` | Collect payments and deposits, issue receipts, and process authorized refunds. | Cannot change clinical facts or independently create clinical orders. |
| `LAB_TECH` | Process laboratory work and enter or publish authorized results. | Cannot create unrelated diagnoses, prescriptions, or financial decisions. |
| `MANAGER` | View authorized operational and management reports. | Reporting access does not grant mutation privileges in source services. |
| `PATIENT` | View and manage permitted self-service information and workflows. | Access is limited to the authenticated patient's own authorized data. |
| `SYSTEM` | Perform trusted internal processing such as event-driven projections and coordination. | Not an interactive human role; use is limited to approved service workflows. |

## 7. System architecture

![MediFlow system architecture](assets/mediflow-system-architecture.svg)

Architecture rules derived from the implementation:

- all client traffic enters through the API gateway;
- business URLs are versioned under the gateway API surface;
- each service owns its database and exposes contracts rather than tables;
- a cross-service identifier is a value, not a database relationship;
- synchronous REST obtains a current decision needed to complete a request;
- asynchronous events communicate completed facts;
- backend authorization is authoritative even when the client hides unavailable actions.

## 8. Functional requirements

### 8.1 Organization

| ID | Requirement | Acceptance outcome |
|---|---|---|
| ORG-01 | Authorized administrators shall create, update, activate, and deactivate user and staff records. | Changes are validated, persisted by Organization, and visible only to authorized callers. |
| ORG-02 | The system shall manage departments, specialties, rooms, and schedules as organizational authority data. | Other services use identifiers or approved contracts and never read Organization tables. |
| ORG-03 | The system shall assign only source-defined roles to authenticated identities. | Invalid role values are rejected and endpoint rules use the authoritative role set. |
| ORG-04 | Schedule changes shall preserve valid time ranges and ownership. | Overlapping or invalid schedules receive a controlled validation response. |
| ORG-05 | Relevant organization changes shall publish versioned facts for dependent projections. | Consumers can process the fact without accessing Organization storage. |

### 8.2 Patient

| ID | Requirement | Acceptance outcome |
|---|---|---|
| PAT-01 | Authorized staff shall register a patient with identity, demographic, contact, and optional insurance information. | A unique patient identifier is returned and required fields are validated. |
| PAT-02 | Authorized users shall search and view patients within their permitted scope. | Results exclude data the caller is not authorized to see. |
| PAT-03 | Patients shall access only their own self-service information unless another explicit policy applies. | A patient cannot retrieve another patient's protected record by changing an identifier. |
| PAT-04 | Authorized users shall create, reschedule, and cancel appointments according to current availability and status rules. | Invalid transitions and scheduling conflicts return controlled errors. |
| PAT-05 | Patient and appointment changes shall publish the facts required by downstream workflows. | Consumers receive versioned events without shared database access. |

### 8.3 Clinical

| ID | Requirement | Acceptance outcome |
|---|---|---|
| CLI-01 | Authorized clinical staff shall open and progress an encounter for an identified patient. | The encounter follows valid status transitions and retains its patient identifier. |
| CLI-02 | Doctors shall record diagnoses and clinical notes in the medical record. | Records are timestamped, attributable, and protected by clinical authorization. |
| CLI-03 | Nurses or authorized clinicians shall record vital signs with valid units and observation times. | Invalid values or incomplete observations are rejected. |
| CLI-04 | Doctors shall create prescriptions containing medication instructions and clinical authority. | A prescription exposes the information required for Pharmacy validation without sharing Clinical tables. |
| CLI-05 | Authorized clinicians shall create laboratory orders linked to the relevant patient and encounter. | Lab receives a stable, versioned instruction through the approved contract. |
| CLI-06 | Clinical queues shall reflect relevant appointment, laboratory, prescription, admission, and surgery facts. | Duplicate events do not create duplicate queue mutations. |

### 8.4 Laboratory

| ID | Requirement | Acceptance outcome |
|---|---|---|
| LAB-01 | Authorized staff shall maintain the laboratory test catalog and active availability. | Orders can reference only valid catalog items. |
| LAB-02 | Laboratory orders shall enter an authorized work queue with patient, encounter, requester, and requested-test references. | The queue rejects invalid or duplicate instructions safely. |
| LAB-03 | Lab technicians shall progress work through valid statuses and enter results. | Invalid transitions and unauthorized updates are rejected. |
| LAB-04 | Publishing a final result shall create a versioned laboratory fact. | Clinical views, billing, notifications, and reports can react through contracts. |
| LAB-05 | A billable published result shall trigger the appropriate charge exactly once. | Duplicate delivery does not create a second charge. |

### 8.5 Pharmacy

| ID | Requirement | Acceptance outcome |
|---|---|---|
| PHA-01 | Authorized pharmacy staff shall maintain medicine and stock information. | Stock quantities and medicine status remain consistent with local rules. |
| PHA-02 | Pharmacy shall accept only prescriptions with valid current clinical authority. | Cancelled, expired, or otherwise invalid authority prevents dispensing. |
| PHA-03 | Dispensing shall require the configured financial-clearance decision where applicable. | A non-cleared prescription remains pending or is rejected with a controlled response. |
| PHA-04 | A successful dispense operation shall update local stock atomically. | Stock is not decremented twice for the same authorized operation. |
| PHA-05 | Dispense and inventory facts shall be published for notification and reporting consumers. | Consumers can project outcomes without reading Pharmacy tables. |

### 8.6 Billing

| ID | Requirement | Acceptance outcome |
|---|---|---|
| BIL-01 | Billing shall create and maintain invoices and charge items from authorized requests or source events. | Each business charge is attributable and duplicate inputs do not create duplicate items. |
| BIL-02 | Cashiers shall record payments and deposits with amount, method, reference, and payer context. | Money uses exact decimal arithmetic and invalid amounts are rejected. |
| BIL-03 | The system shall issue a receipt for a successful financial transaction. | The receipt is linked to the transaction and can be retrieved by an authorized caller. |
| BIL-04 | Refunds shall require an authorized request and shall not exceed the refundable amount. | Successful refunds update local financial state and publish a refund fact. |
| BIL-05 | Billing shall expose current clearance decisions required by Pharmacy, Inpatient, and Surgery. | The requesting service receives a current decision or a controlled failure, never direct table access. |
| BIL-06 | Payment, deposit, refund, and clearance changes shall publish versioned events. | Operational services, notifications, and reports process each fact idempotently. |

### 8.7 Notification

| ID | Requirement | Acceptance outcome |
|---|---|---|
| NOT-01 | Notification shall consume approved operational events and create the corresponding delivery work. | Duplicate events do not create duplicate business notifications. |
| NOT-02 | A notification shall record recipient, channel, template or content reference, status, and timestamps. | Delivery history is traceable within authorized scope. |
| NOT-03 | Delivery failures shall follow a bounded retry policy and preserve failure evidence. | Exhausted work is visible through controlled failure or dead-letter handling. |
| NOT-04 | Notification shall not become the authority for clinical, patient, or financial facts. | Messages reflect source events and do not overwrite the owning service. |

### 8.8 Reporting

| ID | Requirement | Acceptance outcome |
|---|---|---|
| REP-01 | Report shall build operational projections from approved service events. | Projections do not query another service's database. |
| REP-02 | Managers shall filter reports by authorized dimensions such as period, department, status, or service category. | Results respect caller scope and use consistent time boundaries. |
| REP-03 | Duplicate or replayed events shall not double-count report facts. | Receipt or projection keys make processing idempotent. |
| REP-04 | Reports shall expose the timestamp or period represented by the data. | Users can distinguish a current projection from an earlier reporting period. |

### 8.9 Inpatient

| ID | Requirement | Acceptance outcome |
|---|---|---|
| INP-01 | Authorized clinicians shall submit an admission request for a patient with the required care context. | The request has a stable identifier and follows valid states. |
| INP-02 | Inpatient shall coordinate room or bed allocation using current organizational authority. | Invalid or unavailable allocation is rejected without reading Organization tables. |
| INP-03 | Admission shall require the configured deposit or financial clearance. | An uncleared request cannot become an active admission. |
| INP-04 | Authorized staff shall manage the inpatient stay and discharge workflow. | Status transitions are attributable and invalid transitions are rejected. |
| INP-05 | Admission and discharge facts shall update affected clinical, notification, billing, and reporting workflows. | Consumers process each event at most once in business effect. |

### 8.10 Surgery

| ID | Requirement | Acceptance outcome |
|---|---|---|
| SUR-01 | Authorized clinicians shall create a surgery request with patient, clinical reason, procedure context, and responsible parties. | Required information is validated and the request receives a stable identifier. |
| SUR-02 | Surgery shall maintain clinical-readiness and financial-clearance state from authoritative contracts. | Scheduling cannot proceed when mandatory clearance is absent. |
| SUR-03 | Authorized staff shall schedule an eligible surgery against valid organizational resources and time. | Conflicts or invalid resources are rejected with a controlled response. |
| SUR-04 | Authorized surgical staff shall record execution status and outcome. | Outcome changes are attributable and follow valid transitions. |
| SUR-05 | Readiness, schedule, cancellation, completion, and outcome facts shall notify affected contexts. | Notifications, billing, clinical views, and reports update without shared database access. |

### 8.11 Security and integration

| ID | Requirement | Acceptance outcome |
|---|---|---|
| SEC-01 | Every business endpoint shall declare an authorization policy. | An endpoint without an allowed identity or role is denied by default. |
| SEC-02 | The backend shall enforce role and ownership or context restrictions. | Client-side visibility cannot grant access to a forbidden backend operation. |
| SEC-03 | Protected data shall not be returned in error details or logs. | Errors use controlled messages and correlation identifiers. |
| INT-01 | A service shall never read or write another service's database. | Integration occurs through REST or versioned events only. |
| INT-02 | Aggregate state and its outgoing event shall be committed reliably for critical event paths. | The outbox can dispatch an event after the local transaction commits. |
| INT-03 | Event consumers shall be idempotent. | An exact duplicate is acknowledged without a second business mutation. |
| INT-04 | Event processing shall use bounded retry and dead-letter handling. | Transient failures retry; exhausted or invalid messages are retained for controlled investigation. |
| INT-05 | Requests and events shall carry traceable identifiers. | Operators can correlate an API request with downstream processing. |

## 9. Core use-case catalog

| Use case | Primary actor | Supporting contexts | Successful result |
|---|---|---|---|
| Register patient | Administrator or authorized staff | Patient | A validated patient profile is created. |
| Book appointment | Patient or authorized staff | Patient, Organization | A valid appointment is reserved. |
| Start encounter | Doctor or nurse | Clinical, Patient | An authorized clinical encounter becomes active. |
| Record diagnosis | Doctor | Clinical | The medical record receives an attributable diagnosis. |
| Submit lab order | Doctor | Clinical, Lab | Lab receives an authorized work item. |
| Publish lab result | Lab technician | Lab, Clinical, Billing, Notification, Report | A final result is available and downstream facts are emitted. |
| Issue prescription | Doctor | Clinical, Pharmacy | Pharmacy receives valid prescription authority. |
| Dispense medicine | Pharmacist | Pharmacy, Billing, Clinical | Medication is dispensed and stock changes once. |
| Collect payment | Cashier | Billing | Payment and receipt are recorded and clearance is published. |
| Process refund | Cashier or authorized financial user | Billing, Notification, Report | A valid refundable amount is returned and published. |
| Admit patient | Doctor, nurse, or authorized inpatient staff | Inpatient, Organization, Billing | An eligible patient begins an inpatient stay. |
| Discharge patient | Authorized inpatient staff | Inpatient, Clinical, Billing, Notification, Report | The stay closes and affected contexts receive the fact. |
| Request surgery | Doctor | Surgery, Clinical | A surgery case is created for readiness review. |
| Schedule surgery | Authorized surgical staff | Surgery, Organization, Billing | An eligible case is assigned valid time and resources. |
| Record surgery outcome | Authorized surgical staff | Surgery, Clinical, Notification, Report | The outcome is stored and published. |
| View operational report | Manager | Report | An authorized filtered projection is returned. |
| Receive status notification | Patient or staff recipient | Notification | A traceable message is delivered or a controlled failure is recorded. |

## 10. Initial business forms

These forms define the minimum Phase 1 information model for user interfaces. Exact wire names and validation remain aligned with each service's DTO contracts.

### 10.1 Patient registration form

| Field group | Minimum fields | Validation intent |
|---|---|---|
| Identity | Full name, date of birth, sex or gender value used by the service, identity reference where applicable | Required identity fields; valid date; no future birth date. |
| Contact | Phone, email, address, emergency contact | Format validation; optional fields remain explicit. |
| Insurance | Provider, member or policy reference, validity period | Valid period and authorized disclosure. |
| Consent and metadata | Consent indicators where configured, registering staff context | Caller attribution and timestamp. |

### 10.2 Appointment form

| Field group | Minimum fields | Validation intent |
|---|---|---|
| Patient | Patient identifier | Existing and authorized patient reference. |
| Service | Department or specialty, practitioner where selected | Current organizational identifiers. |
| Schedule | Start time, expected duration, reason | Future or permitted time; no invalid conflict. |
| Status | Requested, confirmed, rescheduled, cancelled, or source-defined equivalent | Only valid status transitions. |

### 10.3 Encounter and medical record form

| Field group | Minimum fields | Validation intent |
|---|---|---|
| Context | Patient, appointment where applicable, encounter identifier | Stable references and caller authorization. |
| Observation | Symptoms, notes, vital signs, observation time | Valid required values and units. |
| Assessment | Diagnosis or assessment code and narrative | Doctor authority where required. |
| Plan | Orders, prescription references, follow-up plan | Contract-valid identifiers and instructions. |

### 10.4 Laboratory order and result forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Lab order | Patient, encounter, requested test, requester, priority, clinical note | Active test; authorized requester; complete context. |
| Work update | Order, current status, technician, processing timestamps | Valid transition and lab authorization. |
| Result | Order, result value or narrative, unit or reference where applicable, interpretation, result time | Required final data; authorized publication; immutable trace evidence after publication according to policy. |

### 10.5 Prescription and dispense forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Prescription | Patient, encounter, medicine reference, dose, route, frequency, duration, instructions, prescriber | Doctor authority; complete directions; valid medicine reference. |
| Dispense | Prescription, medicine or stock reference, quantity, pharmacist, financial-clearance reference where applicable | Valid current prescription; sufficient stock; no duplicate dispense. |

### 10.6 Invoice, payment, deposit, and refund forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Invoice or charge | Patient or account, source context, source reference, description, quantity, unit price, total | Exact decimal money; traceable source; no duplicate charge key. |
| Payment | Invoice or account, amount, method, external reference where applicable, payer, cashier | Positive amount; valid outstanding balance; attributable cashier. |
| Deposit | Admission or surgery reference, amount, method, payer, cashier | Positive amount and valid target case. |
| Refund | Original transaction, refundable amount, reason, approver or cashier context | Amount not greater than refundable balance; authorization required. |

### 10.7 Admission and discharge forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Admission request | Patient, encounter, reason, requesting clinician, care priority, requested department or room class | Authorized clinical request and complete care context. |
| Allocation | Admission request, room or bed reference, assigned staff where applicable, start time | Current resource validity and availability. |
| Discharge | Stay, discharge time, responsible clinician, summary or instructions, disposition | Active stay; required clinical authority; valid transition. |

### 10.8 Surgery forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Surgery request | Patient, encounter, procedure, indication, requester, priority | Authorized requester and complete clinical context. |
| Readiness | Surgery case, required clinical checks, current financial clearance, readiness decision | Current authority evidence; mandatory checks complete. |
| Schedule | Surgery case, room, start time, expected duration, team references | Eligible case; valid resources; no scheduling conflict. |
| Outcome | Surgery case, execution status, actual times, outcome summary, responsible staff | Authorized surgical update and valid status transition. |

### 10.9 Notification and report forms

| Form | Minimum fields | Validation intent |
|---|---|---|
| Notification view | Recipient, category, channel, subject or summary, delivery status, timestamps | Recipient scope and protected-content minimization. |
| Report filter | Report type, date range, department or service filter, status filter, output option where supported | Authorized dimensions; valid range; bounded query. |

## 11. Integration and saga behavior

![MediFlow REST and event integration flow](assets/mediflow-integration-and-saga-flow.svg)

The normal cross-context transaction boundary is local to one service. A representative operation follows this sequence:

1. the gateway validates the authenticated request and route access;
2. the owning service obtains a current REST decision only when the request cannot finish without it;
3. the owning service commits its aggregate change and outbox record atomically;
4. the API response is returned without waiting for every downstream projection;
5. the outbox dispatcher publishes the versioned event and records confirmation;
6. each consumer checks the event identifier and payload fingerprint;
7. an exact duplicate is acknowledged without another business mutation;
8. a new valid event updates only the consumer's local state and stores its receipt;
9. transient failures retry within bounds and exhausted messages move to the service DLQ.

## 12. Non-functional requirements

| ID | Requirement | Verification approach |
|---|---|---|
| NFR-01 | Security: protected endpoints shall require authenticated and authorized access. | Gateway, controller, and negative authorization tests. |
| NFR-02 | Privacy: clients and logs shall expose only the data required for the authorized task. | DTO review, error review, and access tests. |
| NFR-03 | Reliability: critical outgoing events shall survive a service restart after local commit. | Outbox integration tests and restart scenarios. |
| NFR-04 | Idempotency: duplicate event delivery shall not duplicate business effects. | Replay and fingerprint-mismatch tests. |
| NFR-05 | Failure handling: transient processing failures shall retry within bounds and exhausted work shall remain inspectable. | Retry and DLQ integration tests. |
| NFR-06 | Data integrity: money shall use exact decimal representation, identifiers shall use UUIDs, and dates shall use explicit date or instant types. | Static review, persistence tests, and API contract tests. |
| NFR-07 | Maintainability: dependencies shall point inward through domain, application, and infrastructure boundaries. | Architecture tests and review. |
| NFR-08 | Interoperability: clients shall use versioned gateway contracts and services shall use versioned event payloads. | Contract and routing tests. |
| NFR-09 | Observability: requests and downstream work shall be traceable with health information, logs, and correlation identifiers. | Operational smoke tests and log correlation review. |
| NFR-10 | Testability: each business rule shall have automated verification at the appropriate unit, web, persistence, or integration level. | Build reports and traceability review. |
| NFR-11 | Usability: role-appropriate clients shall present clear validation, status, and recoverable error feedback. | Scenario-based user acceptance testing. |
| NFR-12 | Performance targets shall be measured against stakeholder-approved workloads rather than invented in this phase. | Production-like load plan after target volumes are approved. |

## 13. Data and contract rules

- Entities never cross a service boundary; DTOs and versioned events do.
- A service stores foreign context references as UUID values, never as cross-service ORM relations.
- API responses use the repository's shared response envelope and correlation behavior.
- Validation errors and business conflicts use controlled status and error contracts.
- Money is represented with exact decimal values.
- Calendar dates and instants use explicit types appropriate to their meaning.
- Events identify their version, occurrence, aggregate or source, and trace context as required by the shared contract.
- Consumers validate both event identity and payload consistency before mutation.

## 14. Assumptions and dependencies

1. Hospital stakeholders will approve role responsibilities and any required separation of duties.
2. Organization remains the authority for users, staff, departments, specialties, rooms, and schedules.
3. Patient remains the authority for patient identity and appointment data.
4. Clinical remains the authority for encounters, diagnoses, orders, and prescriptions.
5. Billing remains the authority for money and financial clearance.
6. Other services project or reference those facts through contracts; they do not become substitute authorities.
7. Deployment provides PostgreSQL, RabbitMQ, gateway, and service discovery in the required start order.
8. External integrations will be added only after their contracts and ownership are approved.

## 15. Risks and controls

| Risk | Control in the baseline |
|---|---|
| Unauthorized access to patient or clinical data | Gateway validation, endpoint authorization, ownership checks, and default deny. |
| Duplicate charges, dispenses, or projections | Stable source identifiers, outbox delivery, and consumer idempotency. |
| Partial cross-service failure | Local transactions, controlled REST failure, asynchronous events, retries, and DLQs. |
| Conflicting authority between services | Explicit bounded-context ownership and no cross-service database access. |
| Reporting divergence | Versioned source facts and idempotent report projections. |
| Documentation drifting from implementation | Source baseline metadata, stable requirement IDs, Mermaid source assets, and periodic reconciliation. |

## 16. Phase 1 acceptance checklist

- [x] The project problem is documented in English.
- [x] Ten implemented business contexts are included.
- [x] All nine source-defined roles are mapped to responsibilities and restrictions.
- [x] Functional requirements have stable traceability identifiers.
- [x] Initial forms cover the primary hospital workflows.
- [x] Architecture and integration diagrams are maintained as Mermaid source.
- [x] REST, events, outbox, idempotency, retries, and DLQs are represented accurately.
- [x] Cross-service database access is explicitly prohibited.
- [x] Non-functional expectations are testable or marked for stakeholder approval.
- [x] Open customer and operational decisions are not presented as implemented facts.

## 17. Stakeholder approval record

| Role | Name | Decision | Date | Notes |
|---|---|---|---|---|
| Customer representative |  | Pending |  |  |
| Hospital operations representative |  | Pending |  |  |
| Clinical representative |  | Pending |  |  |
| Finance representative |  | Pending |  |  |
| Project lead |  | Pending |  |  |
