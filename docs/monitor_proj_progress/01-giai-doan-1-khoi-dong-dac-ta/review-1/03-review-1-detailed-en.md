# MediFlow Review 1 — Detailed Report (English)

| Document control | Value |
|---|---|
| Project | MediFlow Hospital Management System |
| Review | Review 1 |
| Coverage | Part A — Problem Definition; Part B — Customer Requirements Specification |
| Version | Detailed |
| Language | English |
| Baseline | Current repository source, deployment configuration, and implemented contracts |
| Audience | Review panel, project supervisors, customer representatives, developers, testers, and presenters |
| Formatting objective | Direct conversion to Word with stable headings and tables |

## Part A — Problem Definition

### A1. Executive Context

Hospitals coordinate information that is simultaneously personal, clinical, operational, and financial. A patient may interact with reception, a doctor, nursing staff, a laboratory, a pharmacy, a cashier, an inpatient ward, and a surgery team during a single episode of care. Each department creates information needed by another department, yet each remains responsible for its own professional decisions and records.

When this information is managed by paper, spreadsheets, isolated applications, or informal messages, the organization loses a reliable end-to-end view. Staff repeat data entry, status changes are communicated late, charges may be missed or duplicated, inventory can become inaccurate, and management reports depend on manual reconciliation. Sensitive data is also harder to protect because access rules are inconsistent across tools.

MediFlow is designed as a hospital workflow platform that connects these areas while preserving clear data ownership. The current repository implements a browser-based frontend and a Spring Boot microservice backend. The backend contains ten implemented business services supported by an API gateway, Eureka service discovery, PostgreSQL databases, and RabbitMQ messaging.

The central design principle is that each business service is authoritative for its own data. Other services exchange UUID references, use REST only when immediate authoritative information is required, and use events to distribute completed business facts. This structure supports traceability and reduces hidden cross-department coupling.

### A2. Formal Problem Statement

The customer needs a secure and traceable information system that coordinates the patient journey across hospital departments without creating a single uncontrolled database or allowing one department to modify another department's authoritative records.

The solution must answer five questions for every important action:

1. Who is allowed to perform the action?
2. What information is required as input?
3. Which service owns the business decision and stored result?
4. What output is made available to the next authorized actor?
5. How is the action traced, communicated, and recovered if a dependent process fails?

### A3. Business Drivers and Stakeholders

| Driver | Customer concern | Required response |
|---|---|---|
| Patient safety | Missing or delayed information can affect care | Deliver the right clinical information to authorized staff at the correct workflow stage |
| Operational efficiency | Repeated entry and manual handoff consume staff time | Reuse trusted identifiers and automate cross-service notification of completed states |
| Financial integrity | Missed, duplicated, or unexplained charges reduce trust | Create idempotent billable events and trace all financial transitions |
| Privacy | Medical and financial records are sensitive | Apply authenticated identity, role authorization, and service ownership boundaries |
| Accountability | Disputes require evidence of actions and status | Store stable identifiers, actors, timestamps, state transitions, and delivery history |
| Scalability of change | Hospital functions evolve at different rates | Keep bounded contexts independently testable and deployable |
| Management visibility | Decisions require consolidated operational information | Build reporting read models from authoritative source events |
| Resilience | Temporary failures must not silently lose work | Use outbox publication, idempotent consumption, retries, and dead-letter handling |

| Stakeholder | Interests | Primary system contact |
|---|---|---|
| Hospital owner/board | Governance, cost control, service performance | Report and Organization contexts |
| Hospital administrator | Staff, departments, facilities, policy configuration | Organization context |
| Clinicians | Accurate patient history, documentation, orders, and outcomes | Patient and Clinical contexts |
| Diagnostic department | Complete orders, specimens, results, turnaround state | Laboratory context |
| Pharmacy department | Valid prescriptions, stock accuracy, dispensing evidence | Pharmacy context |
| Finance department | Charges, invoices, payments, deposits, receipts, refunds | Billing context |
| Ward operations | Admission, bed use, care status, discharge | Inpatient context |
| Surgical operations | Request, readiness, resources, execution, outcome | Surgery context |
| Patient | Access to permitted personal service information | Patient-facing web flows through the gateway |
| IT operations | Availability, deployment, monitoring, backup, recovery | Shared platform and all services |

### A4. Current-State Gaps

| Gap ID | Gap | Risk if unresolved | MediFlow response |
|---|---|---|---|
| GAP-01 | No consistent patient reference across departments | Misidentification or duplicate records | UUID-based patient identity and controlled references |
| GAP-02 | Appointment status is disconnected from clinical work | Delayed consultation and incomplete handoff | Appointment-to-encounter workflow |
| GAP-03 | Orders and results lack a visible lifecycle | Tests are delayed, repeated, or overlooked | Laboratory order, specimen, processing, verification, and publication states |
| GAP-04 | Prescription and inventory are not reconciled | Unsafe or unavailable dispensing | Prescription validation plus auditable inventory movement |
| GAP-05 | Charges are manually recreated | Financial leakage or double charging | Event-driven, idempotent charge creation |
| GAP-06 | Admission and bed assignment are separate | Resource conflicts and unclear occupancy | Admission clearance and controlled bed allocation |
| GAP-07 | Surgery prerequisites are communicated informally | Cancellation, resource conflict, or unsafe execution | Readiness and clearance gates before scheduling/execution |
| GAP-08 | Notifications are sent ad hoc | Important updates are missed | Template-based, event-driven delivery tracking |
| GAP-09 | Reports query or alter operational data directly | Performance and integrity problems | Event-fed reporting read models |
| GAP-10 | Failure recovery is implicit | Silent data loss or repeated side effects | Outbox, retries, idempotency, and dead-letter observation |

### A5. Objectives and Measurable Outcomes

| Objective ID | Objective | Review-level evidence |
|---|---|---|
| OBJ-01 | Provide end-to-end patient workflow traceability | Each major workflow retains patient/reference UUIDs and states |
| OBJ-02 | Enforce separation of duties | Protected endpoints declare permitted roles |
| OBJ-03 | Maintain bounded-context ownership | No service reads another service's database |
| OBJ-04 | Improve cross-department handoff | REST and event contracts carry explicit identifiers and information |
| OBJ-05 | Protect financial consistency | Money uses decimal values and repeated events do not duplicate charges |
| OBJ-06 | Support reliable asynchronous work | Outbox, retry, idempotency, and dead-letter patterns are represented |
| OBJ-07 | Support management insight | Reporting consumes trusted operational events into query models |
| OBJ-08 | Keep delivery maintainable | Services follow common clean-architecture and testing conventions |
| OBJ-09 | Provide a consistent client entry point | Browser traffic uses same-origin `/api/*` paths through the gateway |
| OBJ-10 | Make limitations explicit | Unverified production and external capabilities are documented rather than assumed |

### A6. Scope Definition

#### A6.1. In-scope system capabilities

| Bounded context | In-scope responsibility | Principal records/results |
|---|---|---|
| Organization | Identity-related organization records, staff, roles, departments, facilities, rooms, beds | Staff/user reference, role assignment, facility hierarchy, resource reference |
| Patient | Patient registration, profile, identifiers, contact data, appointments | Patient profile, appointment, patient-facing status |
| Clinical | Encounter documentation, observations, diagnoses, orders, prescriptions | Encounter, clinical note, diagnosis, lab request, prescription |
| Laboratory | Test catalogue, order lifecycle, specimen, result, verification | Lab order, specimen, result, published result event |
| Pharmacy | Medicine catalogue, inventory, prescription validation, dispensing | Medicine, stock level/movement, dispense record |
| Billing | Billable charges, invoices, deposits, payments, receipts, refunds, clearance | Charge, invoice, transaction, receipt, clearance status |
| Notification | Template selection, recipient/channel processing, delivery attempts | Notification request and delivery status |
| Report | Event-fed read models and filtered management reports | Operational and financial projections |
| Inpatient | Admission, clearance, bed allocation, active stay, care/transfer, discharge | Admission, stay, bed occupancy, discharge |
| Surgery | Request, readiness, clearance, scheduling, execution, outcome | Surgery request, schedule, procedure record, outcome |

#### A6.2. Shared platform scope

- A gateway that provides the client-facing route boundary.
- Eureka service discovery for backend service registration and lookup.
- One PostgreSQL database per business service.
- RabbitMQ for asynchronous business events.
- Shared envelope, exception, security-context, event, and interoperability conventions.
- Container-based development and demonstration topology.
- Automated tests for domain rules, service behavior, persistence, web endpoints, and integration contracts as represented in the source.

#### A6.3. Out-of-scope or not proven complete

| Area | Boundary |
|---|---|
| National systems | No certified national identity, government, or health-insurance integration is claimed |
| External clinical systems | No certified PACS, external EHR, external laboratory, or medical-device interoperability is claimed |
| Commercial payment | No completed production bank, card, or payment-gateway integration is claimed |
| Notification vendors | Internal notification processing exists; production SMS/email/push provider delivery is not established |
| Mobile delivery | The repository has a Flutter loader package, not a complete runnable MediFlow mobile application |
| Automated clinical decisions | Diagnosis, prescription recommendation, and treatment selection remain human clinical responsibilities |
| Production guarantees | Capacity, availability, SLO, RTO, RPO, and retention values require deployment-specific validation |
| Certification | Regulatory, accessibility, penetration-test, and medical-device certifications are outside current evidence |

### A7. User Roles and Functional Responsibilities

| Role | Creates or changes | Reads or reviews | Prohibited/controlled boundary |
|---|---|---|---|
| `ADMIN` | Users, staff references, roles, departments, facilities, rooms, beds, configuration | Administrative state and reference data | Does not obtain unrestricted clinical authority merely by being an administrator |
| `DOCTOR` | Encounters, diagnoses, orders, prescriptions, admission/surgery requests, clinical decisions | Patient history, results, medication, inpatient/surgery status | Cannot directly alter pharmacy stock or settled financial transactions |
| `NURSE` | Observations, care activities, allowed admission/bed/transfer/discharge support data | Appointments, encounters, orders, active stays | Cannot independently issue medical diagnosis or unauthorized financial approval |
| `PHARMACIST` | Dispensing and inventory movements | Authorized prescriptions and medicine stock | Cannot rewrite the originating clinical prescription |
| `CASHIER` | Deposits, payments, receipts, allowed refunds, financial confirmation | Charges, invoices, balances, clearance needs | Cannot alter clinical outcomes or create clinical orders |
| `LAB_TECH` | Specimen, processing, result, verification/publication state | Laboratory orders and permitted patient context | Cannot change the clinical reason or originating encounter |
| `MANAGER` | Report criteria and permitted management views | Aggregated operational/financial information | Reporting must not modify source operational records |
| `PATIENT` | Own registration/contact/appointment inputs where permitted | Own permitted results, prescriptions, financial documents, notifications, care status | Cannot access another patient's records or staff-only operations |
| `SYSTEM` | Event delivery records, retries, read-model updates, automated notifications | Machine-authorized event and operational data | Must not invent business decisions or bypass owning-service validation |

### A8. Proposed Architecture and Solution

The proposed solution uses a browser client and a central gateway to avoid direct client coupling to individual service ports. The web application organizes functionality by the same business contexts as the backend and performs HTTP operations through a shared API client. Backend services use clean-architecture dependencies so domain rules remain independent of Spring and database adapters.

Synchronous REST is selected when a request cannot be completed safely without immediate confirmation from the authoritative service. For example, a business command may need to confirm a referenced record or clearance state. Asynchronous events are selected when a completed fact must update notifications, reporting, billing, or another downstream workflow without forcing the originating user request to wait for every consumer.

| Layer/component | Responsibility | Design constraint |
|---|---|---|
| Web client | Present role-aware pages and validated inputs | Calls gateway only through same-origin `/api/*` |
| Gateway | Route client requests and propagate trusted identity context | No business-domain ownership |
| Service API | Validate DTOs, authorize roles, invoke application use cases | Entities are not exposed across boundaries |
| Application layer | Coordinate use cases and ports | Does not depend on infrastructure implementations |
| Domain layer | Express entities, value objects, rules, and state transitions | No Spring, persistence, or transport dependency |
| Infrastructure layer | Implement persistence, messaging, REST clients, and configuration | Depends inward on application/domain abstractions |
| PostgreSQL | Persist service-owned state and outbox entries | No cross-service table access |
| RabbitMQ | Deliver business events | Consumers are idempotent and failures observable |
| Eureka | Register and discover backend services | Discovery supports routing; it does not own business data |

### A9. Assumptions and Dependencies

| ID | Assumption/dependency | Validation needed |
|---|---|---|
| AD-01 | Each user is authenticated before protected operations | Identity-provider and gateway deployment design |
| AD-02 | Role claims reflect approved hospital responsibilities | Administrative governance and periodic access review |
| AD-03 | Network time is synchronized | Infrastructure configuration and monitoring |
| AD-04 | UUID references are exchanged without database-level foreign keys across services | Contract and integration tests |
| AD-05 | RabbitMQ and databases are monitored and backed up | Production operations plan |
| AD-06 | Hospital departments approve workflow states and separation of duties | Customer acceptance workshops |
| AD-07 | External vendor interfaces will be designed as explicit adapters | Vendor selection and contract documentation |
| AD-08 | Production hardware is sized after representative load tests | Performance plan and workload model |
| AD-09 | Retention and deletion policies follow applicable law and hospital policy | Legal and governance approval |
| AD-10 | Clinical decisions remain under qualified human responsibility | Hospital clinical governance |

### A10. Limitations, Risks, and Future Work

| ID | Limitation/risk | Current mitigation | Future work |
|---|---|---|---|
| LR-01 | No complete mobile app | Responsive web is the supported delivery assumption | Build and validate a gateway-only Flutter application |
| LR-02 | External insurance not integrated | Financial workflow remains internal and explicit | Add insurer adapters, eligibility, claim, and reconciliation contracts |
| LR-03 | External payments not integrated | Cashier-controlled internal transaction records | Integrate compliant payment provider and settlement reconciliation |
| LR-04 | Device/external-lab interfaces not certified | Manual/internal laboratory lifecycle remains available | Define HL7/FHIR/device adapters where customer requires them |
| LR-05 | Notification provider unconfirmed | Delivery attempts/status are modeled internally | Select provider, secure credentials, measure delivery SLO |
| LR-06 | Capacity not measured for production | Docker topology supports development/demo | Execute load, soak, failure, and recovery tests |
| LR-07 | SLO/RTO/RPO not approved | Reliability mechanisms exist at application level | Agree measurable targets and redundant deployment design |
| LR-08 | Accessibility target not certified | Responsive web structure provides a base | Audit against an approved WCAG target and remediate |
| LR-09 | Clinical governance outside code | Roles and workflow gates restrict operations | Establish policy ownership, review boards, and incident process |
| LR-10 | Regulatory status not established | No unsupported certification claim is made | Conduct jurisdiction-specific legal and compliance assessment |

## Part B — Customer Requirements Specification (CRS)

### B1. Requirement Conventions and Priorities

The words **must**, **should**, and **may** indicate mandatory, recommended, and optional requirements. Requirement IDs are stable references for review and later acceptance. Priority uses `Must`, `Should`, or `Could`.

### B2. Functional Requirements by Service

| ID | Priority | Requirement | Owner | Main users |
|---|---|---|---|---|
| FR-ORG-01 | Must | The system must maintain staff/user references and assigned roles | Organization | `ADMIN` |
| FR-ORG-02 | Must | The system must maintain departments, facilities, rooms, and beds | Organization | `ADMIN`, authorized ward staff |
| FR-PAT-01 | Must | The system must register a patient with a stable UUID and validated demographics | Patient | `PATIENT`, authorized staff |
| FR-PAT-02 | Must | The system must create and track appointments associated with the correct patient | Patient | `PATIENT`, `NURSE`, authorized staff |
| FR-CLI-01 | Must | The system must record encounters, observations, diagnoses, and notes | Clinical | `DOCTOR`, `NURSE` |
| FR-CLI-02 | Must | The system must allow authorized doctors to issue lab requests and prescriptions | Clinical | `DOCTOR` |
| FR-LAB-01 | Must | The system must progress a laboratory order through controlled lifecycle states | Laboratory | `LAB_TECH` |
| FR-LAB-02 | Must | The system must retain specimen, result, verification, and publication information | Laboratory | `LAB_TECH`, `DOCTOR` |
| FR-PHA-01 | Must | The system must validate an authorized prescription before dispensing | Pharmacy | `PHARMACIST` |
| FR-PHA-02 | Must | The system must record stock and dispensing movements atomically | Pharmacy | `PHARMACIST` |
| FR-BIL-01 | Must | The system must create traceable billable charges without duplicate effects | Billing | `SYSTEM`, `CASHIER` |
| FR-BIL-02 | Must | The system must support invoices, deposits, payments, receipts, and refunds | Billing | `CASHIER`, `PATIENT` |
| FR-NOT-01 | Must | The system must create notification requests from eligible domain events | Notification | `SYSTEM` |
| FR-NOT-02 | Should | The system should retain delivery attempts, status, and failure reason | Notification | `SYSTEM`, support staff |
| FR-REP-01 | Must | The system must update reporting projections from trusted events | Report | `SYSTEM` |
| FR-REP-02 | Must | The system must provide filtered operational/financial reports to authorized managers | Report | `MANAGER` |
| FR-INP-01 | Must | The system must control admission using clinical and financial prerequisites | Inpatient | `DOCTOR`, `NURSE`, `CASHIER` |
| FR-INP-02 | Must | The system must allocate/release beds and track active stays and transfers | Inpatient | `NURSE`, `DOCTOR` |
| FR-INP-03 | Must | The system must validate discharge prerequisites and close the stay | Inpatient | `DOCTOR`, `NURSE`, `CASHIER` |
| FR-SUR-01 | Must | The system must track surgery request, readiness, and financial clearance | Surgery | `DOCTOR`, authorized surgery staff |
| FR-SUR-02 | Must | The system must schedule and record execution and outcome | Surgery | Authorized surgery staff |

### B3. Data-Processing Cycles by Role

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
| Platform administration | `ADMIN` | Staff identity, role, department, facility/resource data | Validate authority and uniqueness; persist organization state | Governed user/resource references and audit data | Organization |
| Patient self/service registration | `PATIENT`, authorized staff | Demographics, contacts, identifiers, consent-related input | Validate fields and duplicate rules; assign UUID | Patient profile and confirmation | Patient |
| Appointment management | `PATIENT`, `NURSE` | Patient UUID, service/provider, time, reason | Validate scheduling conditions; create/update status | Appointment and visible status | Patient |
| Clinical care | `DOCTOR`, `NURSE` | Patient context, symptoms, observations, appointment/reference | Open/update encounter; apply clinical workflow rules | Notes, observations, diagnoses, orders | Clinical |
| Diagnostic testing | `LAB_TECH` | Order, test items, specimen, measured values | Progress lifecycle; verify result authority/completeness | Verified result and publication event | Laboratory |
| Medication dispensing | `PHARMACIST` | Prescription, medicine, requested quantity, stock | Validate prescription/stock; persist dispense and movement | Dispense record and new stock position | Pharmacy |
| Financial transaction | `CASHIER` | Charge/invoice/admission reference, amount, method, reason | Validate state and decimal amount; record transaction | Balance, receipt, refund or clearance status | Billing |
| Inpatient management | `DOCTOR`, `NURSE`, `CASHIER` | Admission need, clearance, bed need, care/discharge information | Gate transitions; allocate/release resource; retain stay history | Admission/stay/transfer/discharge records | Inpatient |
| Surgery management | `DOCTOR`, authorized surgery staff | Procedure need, priority, readiness, clearance, schedule/resources | Gate lifecycle; reserve schedule; record execution/outcome | Surgery status, schedule, procedure and outcome | Surgery |
| Management reporting | `MANAGER` | Report type, date/filter criteria, authorized scope | Query event-fed projection; aggregate and format | Operational or financial report | Report |
| Background automation | `SYSTEM` | Outbox event, message key, recipient/projection data | Publish, consume idempotently, retry or dead-letter | Delivery status, downstream state, operational evidence | Shared/Notification/Report |

### B4. Registration, Appointment, and Encounter Flow

| Step | Actor/source | Input | Process and rule | Output/consumer |
|---:|---|---|---|---|
| 1 | Patient or authorized staff | Demographic and contact information | Patient validates mandatory data and uniqueness constraints | Patient UUID and profile |
| 2 | Patient/reception | Patient UUID, requested service/time | Patient validates appointment data and records schedule state | Appointment UUID/status |
| 3 | Nurse/reception | Appointment and arrival information | Appointment progresses through an allowed check-in state | Ready appointment context |
| 4 | Doctor/nurse | Patient and appointment references | Clinical confirms required context and opens encounter | Encounter UUID |
| 5 | Doctor/nurse | Symptoms, observations, notes | Clinical validates and records care information | Updated encounter record |
| 6 | Doctor | Assessment and diagnosis | Clinical stores diagnosis and determines authorized next orders | Diagnosis, lab request, prescription, admission/surgery request |

### B5. Clinical-to-Laboratory Flow

| Step | Input information | Processing | Output information | Control |
|---:|---|---|---|---|
| 1 | Encounter UUID, patient UUID, requested test identifiers | Clinical validates the encounter and doctor's authority | Clinical laboratory request | Only authorized clinician can order |
| 2 | Request and test details | Laboratory accepts contractually complete order | Laboratory order UUID and initial state | Duplicate/correlation handling |
| 3 | Order and specimen data | Laboratory records collection/receipt and processing state | Specimen record and timestamps | State transition validation |
| 4 | Measured values, reference data, comments | `LAB_TECH` records results and verifies completeness | Draft/completed result | Required fields and units/ranges as defined |
| 5 | Completed result | Authorized verification changes publication state | Published result and event | Published data becomes immutable except controlled correction |
| 6 | Result event | Notification, Report, and eligible Billing consumer process idempotently | Patient/staff notification, projection, eligible charge | Retry and dead-letter visibility |

### B6. Prescription-to-Dispensing Flow

| Step | Actor | Input | Processing | Output |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Encounter, patient, medicine UUID, dose, frequency, duration, quantity, instructions | Clinical validates prescription content and encounter state | Authorized prescription |
| 2 | `PHARMACIST` | Prescription UUID and medicine lines | Pharmacy verifies prescription status and medicine references | Dispensing decision |
| 3 | `PHARMACIST` | Requested quantity and current stock | Pharmacy enforces availability and valid quantity | Approved dispense quantity |
| 4 | Pharmacy transaction | Prescription and stock records | Dispense and inventory movement are persisted consistently | Dispense record and reduced stock |
| 5 | Event consumers | Dispense event | Billing/Notification/Report process eligible facts idempotently | Financial/communication/reporting update |

### B7. Billing and Financial Flow

| Step | Actor/source | Input | Processing rule | Output |
|---:|---|---|---|---|
| 1 | Operational service event | Event ID, patient, source record, billable item, decimal amount | Billing checks event/idempotency key | New charge or recognized duplicate |
| 2 | Billing | Open charges and patient/account context | Charges are associated with an invoice according to billing rules | Invoice and balance |
| 3 | `CASHIER`/patient | Invoice/admission reference, amount, method | Validate positive decimal amount and allowed financial state | Deposit or payment transaction |
| 4 | Billing | Successful transaction | Update balance/clearance and issue receipt data | Receipt and financial status |
| 5 | `CASHIER` | Original transaction, approved reason and amount | Validate refundable amount and authorization | Refund and adjusted balance |
| 6 | `SYSTEM` | Financial state event | Notify dependent inpatient/surgery/reporting workflows | Clearance or reporting update |

Financial records must not use binary floating-point representation. Monetary values use `BigDecimal` in backend business boundaries. Repeated delivery of the same eligible billable event must not create another charge.

### B8. Inpatient Flow

| Step | Actor | Input | Process | Output |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Patient/encounter, clinical reason, requested admission details | Inpatient records admission request after reference validation | Pending admission |
| 2 | `CASHIER`/Billing | Admission reference and required financial condition | Billing records deposit/payment and publishes clearance | Financial clearance status |
| 3 | `NURSE`/authorized staff | Cleared admission, ward/bed need | Inpatient validates an available suitable bed and allocates it | Active admission and occupied bed |
| 4 | `NURSE`, `DOCTOR` | Active stay, observations/care data | Record care activity under the active stay | Care history |
| 5 | `NURSE`/authorized staff | Transfer need and target resource | Validate target availability and perform controlled bed transition | Transfer history and updated occupancy |
| 6 | `DOCTOR` | Discharge decision and summary | Record clinical discharge readiness | Pending discharge completion |
| 7 | `NURSE`, `CASHIER`, Inpatient | Care completion and required financial state | Validate prerequisites, close stay, release bed | Discharge record and available bed |

### B9. Surgery Flow

| Step | Actor | Input | Process | Output |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Patient, encounter, proposed procedure, priority, reason | Surgery validates request information | Surgery request |
| 2 | Authorized care team | Required readiness evidence/checklist | Record and evaluate prerequisites | Readiness status |
| 3 | Billing/`CASHIER` | Surgery reference and financial requirements | Record financial transaction and clearance | Financial clearance status |
| 4 | Authorized scheduler | Ready/cleared request, theatre, time, team/resources | Validate state and resource availability; schedule | Surgery schedule |
| 5 | Authorized surgery staff | Scheduled procedure and intraoperative information | Record start, execution details, and completion according to allowed state | Procedure record |
| 6 | Authorized clinician | Findings, outcome, postoperative information | Validate and record result | Surgery outcome and downstream event |

### B10. Notification and Reporting Flow

| Consumer | Trigger input | Processing | Output | Failure behavior |
|---|---|---|---|---|
| Notification | Eligible domain event, recipient reference, template data | Select template/channel, create request, attempt delivery | Message content, status, timestamps, failure reason | Retry bounded transient failure; expose terminal failure |
| Report | Trusted domain event and correlation data | Apply event idempotently to read model | Updated projection and report measures | Retry safely; do not mutate source service |

Notification and Report are downstream consumers. They do not become the authority for clinical, financial, pharmacy, laboratory, inpatient, or surgery records.

### B11. Data Ownership and Information Requirements

| Information | Source of truth | Shared by | Key controls |
|---|---|---|---|
| Staff, role, department, facility, room, bed reference | Organization | REST/event references as defined | UUID, authorization, active status |
| Patient profile and appointment | Patient | Authorized REST/event contract | Identity matching, privacy, stable UUID |
| Encounter, diagnosis, order, prescription | Clinical | Controlled clinical contracts/events | Clinician authority, state validation |
| Specimen and lab result | Laboratory | Result contract/event | Verification, publication state, correction control |
| Medicine stock and dispense | Pharmacy | Dispense/inventory contract/event | Quantity rules, atomic movement, traceability |
| Charge, invoice, transaction, receipt, refund, clearance | Billing | Financial status contracts/events | Decimal money, idempotency, audit |
| Notification delivery | Notification | Operational query only | Recipient privacy, status, retry evidence |
| Reporting projection | Report | Authorized manager query | Read-only projection, event idempotency |
| Admission, stay, bed occupancy, discharge | Inpatient | Controlled references/events | Clearance and state-transition gates |
| Surgery request, schedule, execution, outcome | Surgery | Controlled references/events | Readiness, clearance, authorization |

### B12. API and Boundary Requirements

| ID | Requirement |
|---|---|
| API-01 | Client traffic must enter through the gateway and use same-origin `/api/*` routes |
| API-02 | Every protected backend endpoint must declare role authorization |
| API-03 | Request and response DTOs must cross service boundaries; persistence entities must not |
| API-04 | UUID values must identify business records and cross-service references |
| API-05 | Validation errors must be deterministic and suitable for client display |
| API-06 | Services must not expose database implementation details as contracts |
| API-07 | Immediate cross-service decisions must use resilient REST with explicit timeout/failure handling |
| API-08 | Contract changes must preserve or explicitly version compatibility expectations |

### B13. Event and Asynchronous Requirements

| ID | Requirement |
|---|---|
| EVT-01 | A committed state change that informs another context must publish an event |
| EVT-02 | Event payloads must contain stable event, aggregate, correlation, and timestamp information as defined by shared contracts |
| EVT-03 | Database state and event intent must be coordinated through an outbox or equivalent reliable mechanism |
| EVT-04 | Consumers must record or otherwise enforce idempotent handling |
| EVT-05 | Retry must be bounded and observable |
| EVT-06 | Unrecoverable messages must reach an observable dead-letter path |
| EVT-07 | Consumers must not infer missing business identifiers or query another service's database |
| EVT-08 | Reporting and notification failures must not roll back an already completed authoritative business transaction |

### B14. Security and Privacy Requirements

| ID | Priority | Requirement |
|---|---|---|
| SEC-01 | Must | Protected requests must carry a trusted authenticated identity |
| SEC-02 | Must | Backend authorization must be enforced even when the UI hides unavailable actions |
| SEC-03 | Must | Users must receive only information permitted by role and record relationship |
| SEC-04 | Must | Production secrets must be externalized from source control |
| SEC-05 | Must | Production network traffic containing sensitive data must use HTTPS/TLS |
| SEC-06 | Must | Important state changes must retain actor and timestamp evidence where the contract requires it |
| SEC-07 | Should | Administrative and privileged access should be reviewed periodically |
| SEC-08 | Should | Logs should avoid unnecessary sensitive medical or credential data |
| SEC-09 | Should | Backup data should receive encryption and access controls equivalent to live data |
| SEC-10 | Should | Security testing should include dependency, authorization, input, and configuration checks |

### B15. Non-Functional and Operational Requirements

| Area | Requirement | Verification approach |
|---|---|---|
| Correctness | Business invariants must be represented by automated tests | Unit and integration test evidence |
| Availability | Target SLO must be agreed before production | Monitoring design and failure tests |
| Performance | User-facing operations should meet an agreed response target under representative load | Load and latency testing |
| Scalability | Stateless application instances should scale independently where practical | Deployment and workload tests |
| Recoverability | Backup/restore and message recovery must be rehearsed | Recovery exercise with recorded RTO/RPO |
| Maintainability | Services must preserve clean-architecture dependency direction | Code review and architecture checks |
| Observability | Health, logs, error rates, message backlog, and resource use must be visible | Dashboards and alert tests |
| Compatibility | API/event contracts must be tested across producer/consumer boundaries | Contract and integration tests |
| Usability | Role workflows should use consistent terminology and actionable validation feedback | Task-based user review |
| Accessibility | A target standard must be selected and audited | Accessibility evaluation |
| Localization | User-visible text should support the customer language strategy | Content and UI review |
| Data lifecycle | Retention, archival, correction, and deletion rules must be approved | Governance review and lifecycle tests |

### B16. Hardware Requirements

These values are planning baselines, not performance guarantees.

#### B16.1. Client hardware

| Profile | Minimum | Recommended |
|---|---|---|
| Staff workstation | 64-bit dual-core CPU, 4 GB RAM, 1366×768 display | Modern 4-core CPU, 8 GB RAM, Full HD display |
| Patient access device | Recent phone/tablet/computer, 2 GB available memory | Supported secure OS/browser, 4 GB or more memory |
| Shared clinical terminal | Standard keyboard/pointing device and reliable network | Full HD display, local-required barcode devices, UPS-backed power |
| Development workstation | 4-core 64-bit CPU, 16 GB RAM, 30 GB SSD free | 8-core CPU, 32 GB RAM, 60+ GB SSD free |

#### B16.2. Server hardware

| Environment | Compute | Memory | Storage | Notes |
|---|---|---|---|---|
| Local demonstration | 8 logical cores | 16 GB | 40 GB free SSD | Supports containerized infrastructure and selected workloads |
| Shared integration | 16 logical cores | 32 GB | 150 GB SSD | Supports concurrent services, tests, sample data, and basic monitoring |
| Initial production planning baseline | 24+ logical cores across hosts | 64+ GB across hosts | 500+ GB resilient SSD plus isolated backups | Must be resized after load, retention, availability, and recovery tests |

Production design should isolate or distribute application, database, messaging, monitoring, and backup workloads according to the approved availability and compliance targets.

### B17. Software Requirements

#### B17.1. Client software

| Item | Requirement |
|---|---|
| Browser | Current Chrome, Edge, Firefox, or Safari with JavaScript, cookies, and HTTPS enabled |
| Operating system | A vendor-supported Windows, macOS, Linux, Android, or iOS release |
| Network | Stable access to the deployed HTTPS endpoint through approved firewall/proxy rules |
| Document tools | Microsoft Word, LibreOffice Writer, or compatible tool for report conversion |
| Mobile status | Responsive browser access is assumed; a complete native mobile client is not currently delivered |

#### B17.2. Server and development software

| Item | Source-aligned baseline |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.5 |
| Spring Cloud | 2023.0.3 |
| Backend build | Maven multi-module |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Database | PostgreSQL 16-compatible, isolated logical database per service |
| Messaging | RabbitMQ 3-compatible with operational management support |
| Discovery | Eureka Server |
| Containers | Docker Engine and Docker Compose compatible with repository definitions |
| Production platform services | HTTPS certificate management, secret management, centralized logs/metrics/alerts, backups, time synchronization |

### B18. Traceability and Acceptance Matrix

| Acceptance ID | Demonstration | Related requirements |
|---|---|---|
| AC-01 | Register a patient and create an appointment with valid ownership | FR-PAT-01, FR-PAT-02, SEC-03 |
| AC-02 | Open an encounter and record observations/diagnosis under allowed roles | FR-CLI-01, SEC-01, SEC-02 |
| AC-03 | Order, process, verify, and publish a laboratory result | FR-CLI-02, FR-LAB-01, FR-LAB-02 |
| AC-04 | Create a prescription and dispense it while updating stock | FR-CLI-02, FR-PHA-01, FR-PHA-02 |
| AC-05 | Consume a billable event once and produce traceable charge/invoice data | FR-BIL-01, EVT-03, EVT-04 |
| AC-06 | Record deposit/payment, issue receipt, and perform an authorized refund | FR-BIL-02, SEC-02 |
| AC-07 | Progress admission through clearance, bed allocation, care/transfer, and discharge | FR-INP-01, FR-INP-02, FR-INP-03 |
| AC-08 | Progress surgery through request, readiness, clearance, schedule, execution, and outcome | FR-SUR-01, FR-SUR-02 |
| AC-09 | Generate and track a notification from an eligible event | FR-NOT-01, FR-NOT-02, EVT-05, EVT-06 |
| AC-10 | Update and query a reporting projection without modifying source records | FR-REP-01, FR-REP-02, EVT-04 |
| AC-11 | Reject an unauthorized role at each protected operation | SEC-01, SEC-02, SEC-03 |
| AC-12 | Demonstrate that services use contracts rather than cross-database access | API-03, API-06, EVT-07 |
| AC-13 | Redeliver an event without creating duplicate business effects | EVT-04, FR-BIL-01 |
| AC-14 | Observe bounded retry and terminal dead-letter/failure information | EVT-05, EVT-06 |
| AC-15 | Execute the agreed automated verification suite successfully | OBJ-08 and all implemented business-rule requirements |

### B19. Glossary

| Term | Definition |
|---|---|
| Bounded context | A business boundary with its own model, rules, service, and data ownership |
| CRS | Customer Requirements Specification |
| Domain event | A durable statement that a meaningful business state change has completed |
| DTO | Data Transfer Object used at an application or service boundary |
| Encounter | A recorded clinical interaction with a patient |
| Idempotent consumer | A message handler that prevents repeated delivery from repeating a business effect |
| Outbox | A transactional database record used to publish events reliably |
| Read model | Query-focused data derived from authoritative source events |
| REST | Synchronous HTTP-based resource or command interaction |
| RPO | Recovery Point Objective |
| RTO | Recovery Time Objective |
| SLO | Service Level Objective |
| UUID | Universally Unique Identifier used for stable record references |

---

The detailed report separates implemented source-aligned behavior from recommendations and future production work. Customer approval is still required for deployment-specific policy, external integrations, clinical governance, capacity targets, retention, and recovery objectives.
