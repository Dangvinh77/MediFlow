# MediFlow Review 1 — Detailed Report (English)

| Document control | Value |
|---|---|
| Project | MediFlow Hospital Management System |
| Review | Review 1 |
| Coverage | Part A — Problem Definition; Part B — Customer Requirements Specification |
| Version | Detailed |
| Language | English |
| Baseline | Repository commit `1cc014e`, its deployment configuration, and implemented contracts |
| Audience | Review panel, project supervisors, customer representatives, developers, testers, and presenters |
| Formatting objective | Direct conversion to Word with stable headings and tables |

## Part A — Problem Definition

### A1. Executive Context

Hospitals coordinate information that is simultaneously personal, clinical, operational, and financial. A patient may interact with reception, a doctor, nursing staff, a laboratory, a pharmacy, a cashier, an inpatient ward, and a surgery team during a single episode of care. Each department creates information needed by another department, yet each remains responsible for its own professional decisions and records.

When this information is managed by paper, spreadsheets, isolated applications, or informal messages, the organization loses a reliable end-to-end view. Staff repeat data entry, status changes are communicated late, charges may be missed or duplicated, inventory can become inaccurate, and management reports depend on manual reconciliation. Sensitive data is also harder to protect because access rules are inconsistent across tools.

MediFlow is designed as a hospital workflow platform that connects these areas while preserving clear data ownership. The current repository implements a browser-based frontend and a Spring Boot microservice backend. The final system comprises ten business services supported by an API gateway, Eureka service discovery, PostgreSQL databases, and RabbitMQ messaging.

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
| Organization | Identity-related organization records, staff, roles, departments, facilities, and rooms | Staff/user reference, role assignment, facility hierarchy, room reference |
| Patient | Staff-operated patient registration, profile, identifiers, and contact data | Patient profile and demographic status |
| Clinical | Appointments, medical records, diagnoses, dispositions, and admission-referral references; examination requires matching EXAM clearance or audited override | Appointment, medical record, diagnosis, disposition/reference |
| Laboratory | `ADMIN`/`DOCTOR`-created test lifecycle, specimen, result, verification, and publication; protected processing requires matching LAB_TEST clearance or audited override | Lab test, specimen, result, published result event |
| Pharmacy | Medicine catalogue, Pharmacy-owned prescription creation, inventory, durable payment-proof validation, dispensing | Prescription, medicine, stock movement, dispense record |
| Billing | Charges, invoices, deposits, payments, receipts, refunds, settlement, and purpose-specific service clearance | Auditable charge, invoice, payment, refund, settlement, and clearance records |
| Notification | Template selection, recipient/channel processing, delivery attempts | Notification request and delivery status |
| Report | Event-fed read models and filtered management reports | Operational and financial projections |
| Inpatient | Admission request, financial clearance, bed ownership/allocation, active stay, treatment/transfer, medical discharge, settlement, and administrative close | Admission, bed, stay, treatment entry, discharge/close state |
| Surgery | Request, readiness, financial clearance, scheduling, execution, recovery, outcome, and charge handoff | Surgery case, readiness evidence, schedule, procedure, recovery, outcome, and charge event |

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
| `ADMIN` | Users, staff references, roles, departments, facilities, rooms, configuration, and permitted Inpatient bed records | Administrative state and reference data | Does not obtain unrestricted clinical authority merely by being an administrator |
| `DOCTOR` | Clinical records/diagnoses, Laboratory test requests, Pharmacy-owned prescription creation, admissions, and Surgery requests | Patient history, results, medication, admission, Surgery readiness, and outcomes | Cannot alter stock or settled finance; requests must use the owning service's contract |
| `NURSE` | Permitted patient/appointment creation and status, read access, and Inpatient bed/transfer/treatment observations | Appointments, clinical records, active stays | Cannot write Clinical records/diagnoses, create Lab tests, create admissions, perform medical discharge, administratively close, or approve finance |
| `PHARMACIST` | Dispensing and inventory movements | Authorized prescriptions and medicine stock | Cannot rewrite the originating clinical prescription |
| `CASHIER` | Deposits, payments, receipts, allowed refunds, financial confirmation | Charges, invoices, balances, clearance needs | Cannot alter clinical outcomes or create clinical orders |
| `LAB_TECH` | Start/cancel/status/result/publication operations for existing Lab tests | Existing Laboratory tests and permitted patient context | Cannot create a Lab test or change the originating clinical context |
| `MANAGER` | Report criteria and permitted management views | Aggregated operational/financial information | Reporting must not modify source operational records |
| `PATIENT` | Permitted self-service profile, appointment, result, prescription, invoice, receipt, payment, and notification access | Only information owned by or explicitly shared with the patient | Cannot access another patient's information or perform staff-only clinical and financial operations |
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

### A10. Limitations, Risks, and Remaining Work

| ID | Limitation/risk | Current mitigation | Required completion |
|---|---|---|---|
| LR-01 | No complete mobile app | Responsive web is the supported delivery assumption | Build and validate a gateway-only Flutter application |
| LR-01A | Patient self-service is narrow | Current `PATIENT` access is limited to owned notification reads | Add explicitly authorized profile, appointment, clinical, and financial self-service endpoints |
| LR-02 | External insurance not integrated | Financial workflow remains internal and explicit | Add insurer adapters, eligibility, claim, and reconciliation contracts |
| LR-03 | External payments not integrated | Cashier-controlled internal transaction records | Integrate compliant payment provider and settlement reconciliation |
| LR-04 | Device/external-lab interfaces not certified | Manual/internal laboratory lifecycle remains available | Define HL7/FHIR/device adapters where customer requires them |
| LR-05 | Notification provider unconfirmed | Delivery attempts/status are modeled internally | Select provider, secure credentials, measure delivery SLO |
| LR-06 | Capacity not measured for production | Docker topology supports development/demo | Execute load, soak, failure, and recovery tests |
| LR-07 | SLO/RTO/RPO not approved | Reliability mechanisms exist at application level | Agree measurable targets and redundant deployment design |
| LR-08 | Accessibility target not certified | Responsive web structure provides a base | Audit against an approved WCAG target and remediate |
| LR-09 | Clinical governance outside code | Roles and workflow gates restrict operations | Establish policy ownership, review boards, and incident process |
| LR-10 | Regulatory status not established | No unsupported certification claim is made | Conduct jurisdiction-specific legal and compliance assessment |
| LR-11 | Inpatient departmental integration is incomplete | Core admission, bed, treatment, and discharge operations can be demonstrated independently | Complete and verify the registered finance, Pharmacy, Surgery, Report, and Notification handoffs |
| LR-12 | The Surgery lifecycle is incomplete | Selected request, preparation, cancellation, read, and scheduling operations provide a partial foundation | Complete readiness, clearance, execution, recovery, outcome, charge handoff, and end-to-end acceptance |
| LR-13 | Automated Clinical-to-Lab order transfer is incomplete | `ADMIN`/`DOCTOR` currently create the Lab test directly in Laboratory | Complete the explicit test-code/order-ID handoff and verify it end to end |
| LR-14 | The financial lifecycle is incomplete | Basic invoice and payment operations are demonstrable | Complete deposits, refunds, settlement, service clearance, care-workflow handoffs, and acceptance evidence |

## Part B — Customer Requirements Specification (CRS)

### B1. Requirement Conventions and Priorities

The words **must**, **should**, and **may** indicate mandatory, recommended, and optional requirements. Requirement IDs are stable references for review and later acceptance. Priority uses `Must`, `Should`, or `Could`.

### B2. Data-Processing Cycle by User Type

The requirement summary is followed by one subsection per actor and describes the complete intended system behavior. Review 1 gaps are recorded separately in the limitations and acceptance-evidence sections.

| ID | Priority | Requirement | Owner | Main users |
|---|---|---|---|---|
| FR-ORG-01 | Must | The system must maintain staff/user references and assigned roles | Organization | `ADMIN` |
| FR-ORG-02 | Must | The system must maintain departments, facilities, and rooms | Organization | `ADMIN` |
| FR-INP-00 | Must | The system must maintain Inpatient-owned beds through permitted management routes | Inpatient | `ADMIN`, `MANAGER` |
| FR-PAT-01 | Must | The system must register a patient with a stable UUID and validated demographics | Patient | `ADMIN`, `NURSE` |
| FR-CLI-00 | Must | The system must create and track appointments associated with the correct patient | Clinical | `ADMIN`, `NURSE` |
| FR-CLI-01 | Must | Clinical record/diagnosis writes must require `ADMIN`/`DOCTOR`, a valid encounter state, and matching EXAM clearance or an audited emergency override | Clinical | `ADMIN`, `DOCTOR` |
| FR-LAB-00 | Must | `ADMIN`/`DOCTOR` must submit an explicit Clinical test request with test code and producer-generated order ID; Laboratory validates the request and creates its test | Laboratory | `ADMIN`, `DOCTOR` |
| FR-PHA-00 | Must | `ADMIN`/`DOCTOR` must create the Pharmacy-owned prescription through Pharmacy | Pharmacy | `ADMIN`, `DOCTOR` |
| FR-LAB-01 | Must | Laboratory processing must enforce lifecycle rules and require matching LAB_TEST clearance or an audited emergency override | Laboratory | `ADMIN`, `LAB_TECH` |
| FR-LAB-02 | Must | The system must retain specimen, result, verification, and publication information; doctors may read results but do not perform write/publication operations | Laboratory | `ADMIN`, `LAB_TECH` |
| FR-PHA-01 | Must | Dispensing must validate the Pharmacy-owned prescription, durable payment proof, and stock | Pharmacy | `ADMIN`, `PHARMACIST` |
| FR-PHA-02 | Must | The system must record stock and dispensing movements atomically | Pharmacy | `PHARMACIST` |
| FR-BIL-01 | Must | Charges, invoices, payments, and receipts must remain traceable and use auditable financial states | Billing | Authorized Billing staff |
| FR-BIL-02 | Must | Deposit, refund, settlement, and service-clearance operations must be authorized, idempotent, append-only where required, and tied to an exact business purpose | Billing | `ADMIN`, `CASHIER` |
| FR-NOT-01 | Must | The system must create notification requests from eligible domain events | Notification | `SYSTEM` |
| FR-NOT-02 | Should | The system should retain delivery attempts, status, and failure reason | Notification | `SYSTEM`, support staff |
| FR-REP-01 | Must | The system must update reporting projections from trusted events | Report | `SYSTEM` |
| FR-REP-02 | Must | The system must provide filtered operational/financial reports to authorized managers | Report | `MANAGER` |
| FR-INP-01 | Must | The system must create and cancel admissions through authorized operations | Inpatient | `ADMIN`, `DOCTOR` |
| FR-INP-02 | Must | The system must manage beds, active stays, treatment, transfers, and permitted discharge transitions | Inpatient | `ADMIN`, `NURSE`, `DOCTOR` as authorized per operation |
| FR-INP-03 | Must | Medical discharge and administrative close must remain separate authorized transitions | Inpatient | `ADMIN`, `DOCTOR` for medical discharge; `ADMIN`, `CASHIER` for close |
| FR-SUR-01 | Must | Surgery requests, readiness, financial clearance, scheduling, execution, recovery, outcome, and cancellation must follow authorized state transitions | Surgery | Authorized surgery staff |
| FR-SUR-02 | Must | Completed or cancelled Surgery cases must publish the required outcome and eligible charge handoff without bypassing Clinical or Billing authority | Surgery | Authorized surgery staff, `SYSTEM` |

#### B2.1 Administrator

**Responsibilities:** Govern Organization data and perform expressly authorized operations in other services. **Restrictions:** `ADMIN` is not an automatic substitute for clinical judgment, and Organization does not own appointments or beds.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-ADM-01 | Maintain staff, roles, departments, facilities, and rooms | Authorized organization data | Validate role, uniqueness, and hierarchy | Updated organization reference | Organization | Invalid or duplicate data is rejected without partial change |
| CRS-ADM-02 | Maintain the bed catalogue where permitted | Room/department references and bed data | Validate references and bed state | Inpatient-owned bed record | Inpatient | Invalid relationship or forbidden transition is rejected |

#### B2.2 Doctor

**Responsibilities:** Write Clinical records with required EXAM proof, submit Laboratory test requests, create Pharmacy-owned prescriptions, and perform permitted Inpatient/Surgery actions. **Restrictions:** The doctor cannot alter stock or settled finance and must use each owning service's contract.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-DOC-01 | Conduct examination and write record | Appointment, findings, matching EXAM clearance or audited override | Validate state, role, and proof | Medical record, diagnosis, disposition/reference | Clinical | Missing/mismatched proof or an invalid encounter state blocks examination |
| CRS-DOC-02 | Submit a Laboratory test request | Patient/record, department, test code/date, producer-generated order ID | Validate clinical authority; Laboratory validates the order and creates its test | Accepted Lab test in initial state | Laboratory | Invalid/duplicate order identity or unauthorized context is rejected |
| CRS-DOC-03 | Create a Pharmacy prescription | Clinical record reference, medicine lines/instructions | Pharmacy validates medicines/server prices and creates its prescription | Pharmacy-owned prescription and pending slip | Pharmacy | Empty/invalid lines are rejected |
| CRS-DOC-04 | Create or medically discharge an admission | Patient/encounter or active admission | Apply route-specific Inpatient rules | Pending admission or medically discharged state | Inpatient | Financial/event integration is not inferred while gates remain off |

#### B2.3 Nurse

**Responsibilities:** Create permitted patient/appointment records, update permitted appointment/check-in state, read Clinical records, and perform permitted Inpatient bed/admit/treatment-observation/transfer operations. **Restrictions:** The nurse cannot write Clinical records/diagnoses, create a Lab test/admission, perform medical discharge, or administratively close.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-NUR-01 | Register a patient | Demographics and identity data | Validate and create the patient | Patient UUID/profile | Patient | Invalid or duplicate identity data is rejected |
| CRS-NUR-02 | Create an appointment | Patient UUID and schedule data | Validate and create the appointment | Appointment UUID/state | Clinical | Invalid patient reference or schedule state is rejected |
| CRS-NUR-03 | Assign/transfer bed, explicitly admit, or append Inpatient treatment observation | Admission, bed, matching deposit clearance/override, treatment input | Enforce route role, proof, and transition | Updated admission, bed, or treatment history | Inpatient | Missing clearance/bed, stale state, or forbidden transition is rejected |

#### B2.4 Laboratory Technician

**Responsibilities:** Start/cancel permitted existing Lab tests, manage specimens, record status/results, verify, and publish. **Restrictions:** The technician cannot create a Lab test or change the clinical context.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-LAB-01 | Process and publish an existing Lab test | Lab test, specimen, values, matching LAB_TEST clearance or audited override | Validate lifecycle, proof, and required result data | Published laboratory result | Laboratory | Invalid state/data or missing/mismatched proof blocks processing/publication |

#### B2.5 Pharmacist

**Responsibilities:** Validate Pharmacy-owned prescriptions, durable payment proof, dispense medicines, and maintain stock movements. **Restrictions:** The pharmacist cannot rewrite the doctor-authored prescription intent.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-PHA-01 | Dispense an authorized prescription | Pharmacy prescription, durable payment proof, medicine, quantity | Validate proof/prescription/stock; persist movement | Dispense record and stock balance | Pharmacy | Missing proof, invalid prescription, or insufficient stock rejects dispensing |

#### B2.6 Cashier

**Responsibilities:** Process charges, invoices, deposits, payments, receipts, authorized refunds, settlement, service clearance, and permitted Inpatient administrative close. **Restrictions:** The cashier cannot perform medical discharge or change clinical decisions.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-CAS-01 | Create and pay an invoice | Patient/source context, charge, invoice, and payment data | Validate financial state and persist payment/receipt | Invoice, payment, and receipt state | Billing | Invalid amount, authority, or state is rejected |
| CRS-CAS-02 | Process deposit/refund/settlement/clearance | Exact business purpose, original transaction when required, amount, and care/admission reference | Validate authority, idempotency, append-only rules, totals, and purpose | Deposit, refund, settlement, or clearance record | Billing | Invalid purpose, target, amount, cumulative limit, or authority is rejected |
| CRS-CAS-03 | Administratively close an admission | Medical discharge plus matching settlement or approved override | Validate the Inpatient close gate | Closed admission | Inpatient | Missing settlement/override or stale admission is rejected |

#### B2.7 Manager

**Responsibilities:** Query authorized reports and manage Inpatient beds where permitted. **Restrictions:** Reporting is read-only and does not modify source records.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-MAN-01 | Query a management report | Report type, filters, authorized scope | Query the event-fed projection | Operational or financial report | Report | Unauthorized scope is rejected; projection delay is reported operationally |
| CRS-MAN-02 | Create/update an Inpatient bed | Department/room references and bed data | Validate Manager role, references, and local bed rules | Inpatient-owned bed record | Inpatient | Invalid reference/state is rejected |

#### B2.8 Patient

**Responsibilities:** Use authorized self-service for the patient's own profile, appointments, results, prescriptions, invoices, receipts, payments, and notifications. **Restrictions:** The patient cannot access another patient's data or perform staff-only clinical and financial operations.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-PAT-01 | Use authorized self-service | Verified patient identity and ownership/relationship context | Enforce ownership and expose permitted profile, appointment, result, prescription, invoice, receipt, payment, and notification data | Personal self-service view and permitted requests | Owning service for each record | Cross-patient access and staff-only mutations are rejected |

#### B2.9 Internal System Actor

**Responsibilities:** Publish outbox records, consume messages idempotently, retry bounded failures, update projections, and create notifications. **Restrictions:** Automation cannot invent identifiers or bypass an owning service's decision.

| Requirement ID | Normal flow | Input | Process | Output | Owning service | Alternative or failure outcome |
|---|---|---|---|---|---|---|
| CRS-SYS-01 | Publish an approved domain event | Owning-service outbox record and event/correlation identity | Publish the exact committed event | Broker delivery evidence | Producing service | Retry transient transport failure without inventing business state |
| CRS-SYS-02 | Consume an approved notification event | Event/correlation identity and recipient data | Consume idempotently and attempt delivery | Notification delivery record | Notification | Retry transient failure; terminal failure is observable |
| CRS-SYS-03 | Consume an approved reporting event | Event/correlation identity and projection data | Consume idempotently and update read model | Reporting projection | Report | Retry/dead-letter without modifying the source record |

### B3. Data-Processing Cycles by Role

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
| Platform administration | `ADMIN` | Staff identity, role, department, facility/resource data | Validate authority and uniqueness; persist organization state | Governed user/resource references and audit data | Organization |
| Staff-operated patient registration | `ADMIN`, `NURSE` | Demographics, contacts, identifiers, consent-related input | Validate fields and duplicate rules; assign UUID | Patient profile and confirmation | Patient |
| Appointment management | `ADMIN`, `NURSE` | Patient UUID, service/provider, time, reason | Validate scheduling conditions; create/update status | Appointment and visible status | Clinical |
| Clinical examination | `ADMIN`, `DOCTOR` | Patient/appointment, findings, EXAM clearance or audited override | Validate role, context, and proof; write record/diagnosis | Medical record and disposition/reference | Clinical |
| Clinical request and Laboratory test creation | `ADMIN`, `DOCTOR` | Patient/record, department, test code/date, order ID | Transfer the explicit order; Laboratory validates and creates its test | Accepted Lab test in initial state | Laboratory |
| Diagnostic processing | `ADMIN`, `LAB_TECH` | Existing Lab test, specimen/values, LAB_TEST clearance or audited override | Enforce lifecycle and proof | Verified result and publication event | Laboratory |
| Prescription creation | `ADMIN`, `DOCTOR` | Clinical record reference, medicine lines, instructions | Pharmacy validates and creates its prescription | Pharmacy-owned prescription and pending slip | Pharmacy |
| Medication dispensing | `ADMIN`, `PHARMACIST` | Pharmacy prescription, durable payment proof, quantity, stock | Validate proof/prescription/stock; persist movement | Dispense record and new stock position | Pharmacy |
| Invoice and payment processing | Authorized Billing staff | Patient/source, charge, invoice, and payment data | Validate financial state; create invoice; record payment and receipt | Invoice, payment, and receipt state | Billing |
| Deposit/refund/settlement/clearance | `ADMIN`, `CASHIER` | Exact business purpose, care/admission target, original transaction where required, and amount | Apply authorization, append-only, idempotency, total, and purpose rules | Deposit, refund, settlement, or clearance record | Billing |
| Inpatient lifecycle | Authorized `ADMIN`, `DOCTOR`, `NURSE`, or `CASHIER` per operation | Admission, bed, clearance, treatment, medical-discharge, settlement, or close input | Apply the operation's role, proof, and state-transition rules | Admission, bed, treatment, discharge, and close record | Inpatient |
| Surgery lifecycle | Authorized surgery staff | Request, readiness, clearance, schedule, procedure, recovery, outcome, or cancellation input | Apply authority, prerequisite, and state-transition rules; publish outcome/charge handoff | Auditable Surgery case and outcome | Surgery |
| Management reporting | `MANAGER` | Report type, date/filter criteria, authorized scope | Query event-fed projection; aggregate and format | Operational or financial report | Report |
| Notification automation | `SYSTEM` | Approved event, message key, recipient data | Consume idempotently, select template, attempt delivery | Delivery status and operational evidence | Notification |
| Reporting automation | `SYSTEM` | Approved event, message key, projection data | Consume idempotently and update the read model | Updated reporting projection | Report |

### B4. Registration, Appointment, and Encounter Flow

| Step | Actor/source | Input | Process and rule | Output/consumer |
|---:|---|---|---|---|
| 1 | `ADMIN` or `NURSE` | Demographic and contact information | Patient validates mandatory data and uniqueness constraints | Patient UUID and profile |
| 2 | `ADMIN` or `NURSE` | Patient UUID, requested service/time | Clinical validates appointment data and records schedule state | Appointment UUID/status |
| 3 | Nurse/reception | Appointment and arrival information | Appointment progresses through an allowed check-in state | Ready appointment context |
| 4 | `ADMIN`/`DOCTOR` | Patient/appointment references and matching EXAM clearance or audited override | Clinical validates role, context, and proof; opens the record | Medical record UUID |
| 5 | `ADMIN`/`DOCTOR` | Symptoms, findings, notes | Clinical validates and records medical information | Updated record and diagnosis |
| 6 | `DOCTOR` | Assessment and diagnosis | Clinical records an explicit disposition/reference; later Lab and Pharmacy commands go to their owning services | Diagnosis and disposition/reference |

### B5. Laboratory Request and Result Flow

The final workflow transfers a Clinical test request to Laboratory with an explicit test code and producer-generated order ID. Review 1 currently relies on direct Laboratory creation by `ADMIN` or `DOCTOR`; completing the automated handoff remains recorded as a limitation.

| Step | Input information | Processing | Output information | Control |
|---:|---|---|---|---|
| 1 | Patient/record references, department, test code/date, producer-generated order ID | Clinical validates authority; Laboratory validates the explicit order and creates its test | Lab test UUID and initial state | Invalid/duplicate order identity or unauthorized context is rejected |
| 2 | Existing test plus matching LAB_TEST clearance or audited emergency override | Laboratory validates lifecycle and exact proof | Authorized processing context | Missing/mismatched proof blocks processing |
| 3 | Authorized test and specimen data | `ADMIN`/`LAB_TECH` records processing state | Specimen/status timestamps | State transition validation |
| 4 | Measured values, reference data, comments | `ADMIN`/`LAB_TECH` records results and verifies completeness | Draft/completed result | Required fields and units/ranges as defined |
| 5 | Completed result | Authorized publication changes final state | Published result and event | Published data changes only through controlled correction |
| 6 | Result event | Eligible consumers process the approved contract idempotently | Notification, reporting projection, and billable-result handoff | Duplicate delivery is a no-op; retry/dead-letter is observable |

### B6. Prescription-to-Dispensing Flow

| Step | Actor | Input | Processing | Output |
|---:|---|---|---|---|
| 1 | `ADMIN`, `DOCTOR` | Clinical record reference, patient, medicine UUID, dose, frequency, duration, quantity, instructions | Pharmacy validates medicines/server prices and creates its prescription | Pharmacy-owned prescription and pending dispense slip |
| 2 | `ADMIN`, `PHARMACIST` | Prescription UUID, medicine lines, durable payment proof | Pharmacy verifies proof, prescription status, and medicine references | Dispensing decision |
| 3 | `ADMIN`, `PHARMACIST` | Requested quantity and current stock | Pharmacy enforces availability and valid quantity | Approved dispense quantity |
| 4 | Pharmacy transaction | Prescription and stock records | Dispense and inventory movement are persisted consistently | Dispense record and reduced stock |
| 5 | Event consumers | Dispense event | Billing/Notification/Report process eligible facts idempotently | Financial/communication/reporting update |

### B7. Billing and Financial Flow

| Step | Actor/source | Input | Processing rule | Output |
|---:|---|---|---|---|
| 1 | Authorized charge/invoice request | Patient/source and billable-item data | Billing validates context and creates the charge/invoice | Open invoice and account state |
| 2 | `CASHIER` | Invoice and payer-provided amount/method | Validate authority, amount, and state; record payment | Paid/partially paid invoice and receipt |
| 3 | Authorized payment request | Exact business target and decimal amount | Apply append-only and idempotent payment rules | Payment, receipt, and purpose-specific clearance |
| 4 | Authorized deposit request | Exact admission target and requested amount | Record the deposit without treating held funds as earned revenue | Deposit and admission financial state |
| 5 | Authorized refund request | Original completed transaction, reason, and amount | Enforce authorization, cumulative limits, and append-only reversal | Refund record and updated balance |
| 6 | Settlement/clearance request | Exact purpose/target and persisted totals | Verify full settlement or an approved exception; issue the decision | Settlement or service-clearance state |

Financial records must not use binary floating-point representation. Monetary values use `BigDecimal`. Charges, invoices, deposits, payments, receipts, refunds, settlement, and clearance must remain auditable, idempotent where required, and tied to an exact business purpose.

### B8. Inpatient Flow

| Step | Actor | Input | Process | Output |
|---:|---|---|---|---|
| 1 | `ADMIN`, `DOCTOR` | Patient/encounter, clinical reason, requested admission details | Inpatient validates references and creates the local admission | Pending admission |
| 2 | `ADMIN`, `NURSE` | Admission and ward/bed need | Inpatient validates and assigns an available bed | Updated admission and active bed assignment |
| 3 | `ADMIN`, `DOCTOR`, `NURSE` | Pending admission, active bed, matching deposit clearance or audited emergency override | Invoke the explicit `/admit` transition | `ADMITTED` state |
| 4 | `ADMIN`, `DOCTOR`, `NURSE` | Admitted stay and treatment/observation data | Append or correct authorized treatment entry | Treatment history |
| 5 | `ADMIN`, `NURSE` | Transfer need and target bed | Validate availability and perform controlled transfer | Transfer history and updated occupancy |
| 6 | `ADMIN`, `DOCTOR` | Discharge decision and approved summary | Apply medical-discharge transition | `MEDICALLY_DISCHARGED` state |
| 7 | `ADMIN`, `CASHIER` | Medical discharge plus matching `settlement.completed` or approved debt/emergency override | Validate close gate and apply administrative close | `CLOSED` admission and available bed |

### B9. Surgery Lifecycle

The final workflow covers the complete surgery journey. The limitations and acceptance-evidence sections identify which parts still require completion and end-to-end verification at Review 1.

| Step | Actor | Input | Process | Output |
|---:|---|---|---|---|
| 1 | `DOCTOR` | Patient, encounter, indication, requested procedure | Validate authority and clinical references; create Surgery request | Surgery case linked to patient and encounter |
| 2 | Authorized surgery team | Checklist, consent, assessment, equipment, and readiness evidence | Validate required evidence and record readiness decision | Ready, blocked, or cancelled case with reasons |
| 3 | Billing authority and `CASHIER` | Surgery reference, expected charges, deposit/payment context | Evaluate financial requirements and issue clearance or approved exception | Financial clearance decision |
| 4 | Authorized scheduler | Cleared case, room/time/team, equipment, and expected revision | Validate conflicts and resources; confirm the schedule | Confirmed surgery schedule and assigned team/resources |
| 5 | Authorized surgery team | Scheduled procedure and intraoperative information | Start procedure, record key events, complete or cancel through controlled transitions | Execution record, used resources, and procedure status |
| 6 | Authorized surgery team and `SYSTEM` | Findings, recovery information, outcome, and billable resources | Record recovery/outcome; publish eligible care and charge events idempotently | Final outcome, recovery status, notifications, reports, and Billing handoff |

### B10. Notification and Reporting Flow

| Consumer | Trigger input | Processing | Output | Failure behavior |
|---|---|---|---|---|
| Notification | Eligible domain event, recipient reference, template data | Select template/channel, create request, attempt delivery | Message content, status, timestamps, failure reason | Retry bounded transient failure; expose terminal failure |
| Report | Trusted domain event and correlation data | Apply event idempotently to read model | Updated projection and report measures | Retry safely; do not mutate source service |

Notification and Report are downstream consumers. They do not become the authority for clinical, financial, pharmacy, laboratory, inpatient, or surgery records.

### B10A. Ten Cross-Department Workflow Contracts

#### Workflow 1 — Staff Registration and Appointment

- **Trigger:** `ADMIN` or `NURSE` receives valid patient and scheduling data.
- **Primary authority:** Patient owns registration; Clinical separately owns appointments.
- **Input:** Demographics, identifiers, patient UUID, service/provider, and time.
- **Process:** Create the Patient record, then call the Clinical appointment boundary; no shared transaction is implied.
- **Output:** Patient UUID and appointment UUID/status.
- **Failure or exception:** Each owner rejects invalid input independently; patient self-service enforces authenticated ownership and staff-only boundaries.

#### Workflow 2 — Appointment to Clinical Encounter

- **Trigger:** An authorized staff workflow reaches the permitted consultation state.
- **Primary authority:** Clinical.
- **Input:** Clinical-owned appointment and patient UUID reference.
- **Process:** Validate appointment/actor context and create or update the encounter.
- **Output:** Encounter, observations, diagnosis, and next permitted orders.
- **Failure or exception:** Invalid state, role, or reference is rejected by Clinical.

#### Workflow 3 — Clinical Test Request and Laboratory Result

- **Trigger:** `ADMIN` or `DOCTOR` submits an authorized test request that Laboratory accepts with an explicit test code and order ID.
- **Primary authority:** Clinical owns the care decision; Laboratory owns the test lifecycle and result.
- **Input:** Patient/record references, department, test code/date, order ID, matching LAB_TEST clearance or audited override, specimen, and values.
- **Process:** Laboratory validates the request, proof, lifecycle, and data before processing, verifying, and publishing the result.
- **Output:** Published Laboratory result and eligible result event.
- **Failure or exception:** Missing/mismatched proof, duplicate or invalid order identity, incomplete result, or invalid state blocks processing/publication.

#### Workflow 4 — Laboratory Result to Billing Charge

- **Trigger:** An eligible Laboratory billable event is approved and emitted.
- **Primary authority:** Billing owns the charge; Laboratory owns the source result.
- **Input:** Event ID, source order/result reference, patient, item, decimal amount contract.
- **Process:** Billing consumes the approved charge contract idempotently and links the charge to the exact patient, source, and service purpose.
- **Output:** Traceable ledger charge, account update, and invoice state.
- **Failure or exception:** Invalid contract data is rejected; duplicate delivery is a no-op and delivery failures follow retry/dead-letter handling.

#### Workflow 5 — Prescription to Pharmacy Dispensing

- **Trigger:** `ADMIN` or `DOCTOR` creates a prescription through Pharmacy.
- **Primary authority:** Pharmacy owns prescription, dispense, and stock; Clinical supplies only record context/reference.
- **Input:** Clinical record/patient references, medicine lines/instructions, later durable payment proof, quantity, stock.
- **Process:** Pharmacy creates the prescription using server prices; dispensing separately validates payment proof and stock before atomic movement.
- **Output:** Dispense record and updated stock.
- **Failure or exception:** Invalid prescription or insufficient stock prevents dispensing.

#### Workflow 6 — Invoice and Financial Transaction

- **Trigger:** A valid billable fact or authorized cashier request is received.
- **Primary authority:** Billing.
- **Input:** Charge/invoice/payment data or exact deposit/refund/settlement/clearance purpose and care/admission target.
- **Process:** Validate authority, state, amount, original transaction where required, idempotency, append-only rules, totals, and business purpose.
- **Output:** Auditable invoice, payment, receipt, deposit, refund, settlement, or clearance record.
- **Failure or exception:** Invalid authority, purpose, target, amount, cumulative limit, or state is rejected.

#### Workflow 7 — Inpatient Admission and Discharge

- **Trigger:** An authorized Inpatient request is submitted.
- **Primary authority:** Inpatient.
- **Input:** Admission, active bed, matching deposit clearance or audited override, treatment, discharge summary, settlement/close override.
- **Process:** Apply operation roles: create/assign, explicit `/admit`, treatment, medical discharge, then settlement-controlled administrative close.
- **Output:** Requested/admitted/treatment/medical-discharge/closed state and required departmental events.
- **Failure or exception:** Missing bed/clearance/settlement, stale state, or forbidden transition is rejected.

#### Workflow 8 — Surgery Lifecycle

- **Trigger:** An authorized Surgery request is accepted from Clinical.
- **Primary authority:** Surgery owns the case and lifecycle; Clinical and Billing retain authority over clinical and financial evidence.
- **Input:** Approved request, readiness/consent evidence, financial clearance, room/time/team/equipment, procedure, recovery, and outcome data.
- **Process:** Validate prerequisites; confirm schedule; start and complete or cancel the procedure; record recovery/outcome; publish eligible notifications, reports, and charge handoff.
- **Output:** Auditable Surgery case, schedule, execution record, recovery state, outcome, and charge event.
- **Failure or exception:** Missing authority, readiness, clearance, resources, or invalid state transition blocks progress and records the reason.

#### Workflow 9 — Event-Driven Notification

- **Trigger:** An eligible approved domain event reaches Notification.
- **Primary authority:** Notification for delivery records; the producer retains business-record authority.
- **Input:** Event identity, recipient reference, channel/template data.
- **Process:** Consume idempotently, render, attempt delivery, and record status.
- **Output:** Notification content and delivery status; `PATIENT` may read owned notifications.
- **Failure or exception:** Transient failure retries; terminal failure is observable without rolling back the source transaction.

#### Workflow 10 — Event-Driven Reporting

- **Trigger:** An eligible approved domain event reaches Report.
- **Primary authority:** Report for its projection only.
- **Input:** Event/correlation identity and projection fields.
- **Process:** Consume idempotently and update the read model.
- **Output:** Authorized operational or financial report data.
- **Failure or exception:** Projection failure retries or dead-letters; Report never modifies the source record.

### B11. Data Ownership and Information Requirements

| Information | Source of truth | Shared by | Key controls |
|---|---|---|---|
| Staff, role, department, facility, and room reference | Organization | REST/event references as defined | UUID, authorization, active status |
| Bed and bed occupancy | Inpatient | Controlled local APIs/contracts | UUID, availability, transition authorization |
| Patient profile | Patient | Authorized REST/event contract | Identity matching, privacy, stable UUID |
| Appointment | Clinical | Authorized REST/event contract | Patient UUID reference, schedule state, role authorization |
| Medical record, diagnosis, disposition/reference | Clinical | Controlled clinical contracts/events | `ADMIN`/`DOCTOR`, EXAM proof, state validation |
| Lab test, specimen, and result | Laboratory | Clinical request contract plus result event | `ADMIN`/`DOCTOR` request; Laboratory creates; clearance/override; controlled result state |
| Prescription, medicine stock, and dispense | Pharmacy | Prescription/dispense/inventory contract/event | Doctor actor, Pharmacy ownership, payment proof, atomic movement |
| Charge, invoice, transaction, receipt, refund, clearance | Billing | Financial status contracts/events | Decimal money, idempotency, audit |
| Notification delivery | Notification | Operational query only | Recipient privacy, status, retry evidence |
| Reporting projection | Report | Authorized manager query | Read-only projection, event idempotency |
| Admission, stay, bed occupancy, discharge | Inpatient | Controlled references/events | Clearance and state-transition gates |
| Surgery case, readiness, schedule, execution, recovery, and outcome | Surgery | Authorized Surgery contracts and outcome events | Role authority, readiness, financial clearance, resources, state transitions, and audit |

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
| API-08 | Contract changes must preserve existing consumer expectations or introduce an explicit migration contract |

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
| Interoperability | API/event contracts must be tested across producer/consumer boundaries | Contract and integration tests |
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
| Development/Demonstration | 8 logical cores | 16 GB | 40 GB free SSD | Supports containerized infrastructure and selected workloads |
| Minimum Small Deployment | 16 logical cores | 32 GB | 150 GB SSD | Planning profile for concurrent services, tests, sample data, and basic monitoring |
| Recommended Scalable Deployment | 24+ logical cores across hosts | 64+ GB across hosts | 500+ GB resilient SSD plus isolated backups | Planning profile only; resize after load, retention, availability, and recovery tests |

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

The documented full Maven baseline is **3,612 tests, 0 failures, 0 errors, and 0 skips**. This is supporting engineering evidence from the recorded baseline, not proof that a customer has accepted every workflow or external integration.

| Requirement ID | Actor or workflow | Owning service | Source evidence area | Acceptance method |
|---|---|---|---|---|
| AC-01A / FR-PAT-01 | Staff registration | Patient | Patient controller/service docs | Demonstrate authorized `ADMIN`/`NURSE` registration |
| AC-01B / FR-CLI-00 | Appointment creation | Clinical | Clinical appointment controller/service docs | Demonstrate authorized `ADMIN`/`NURSE` appointment creation |
| AC-02 / FR-CLI-01 | Clinical encounter | Clinical | Clinical controller/domain and finance-contract tests | Demonstrate role-authorized writes, valid encounter state, and required EXAM clearance/override |
| AC-03 / FR-LAB-00..01 | Clinical request and Laboratory lifecycle | Laboratory | Lab controller/service and order-contract tests | Transfer explicit test code/order ID; require LAB_TEST clearance/override; publish a verified result |
| AC-04 / FR-PHA-00..01 | Pharmacy prescription and dispense | Pharmacy | Pharmacy controller/payment-proof/inventory tests | Create through Pharmacy; require durable payment proof and stock before dispense |
| AC-05 / FR-BIL-01 | Charge, invoice, payment, and receipt | Billing | Billing API/domain tests | Demonstrate traceable creation, payment, receipt, and account update |
| AC-06 / FR-BIL-02 | Deposit, refund, settlement, and clearance | Billing | Billing domain, contract, and integration tests | Verify authority, purpose, totals, idempotency, append-only history, and care-workflow handoff |
| AC-07 / FR-INP-01..03 | Inpatient lifecycle | Inpatient | Inpatient controller, domain, event, and integration tests | Demonstrate admission through administrative close with bed, deposit, settlement, and departmental handoffs |
| AC-08 / FR-SUR-01..02 | Surgery lifecycle | Surgery | Surgery controller, domain, event, and integration tests | Demonstrate request, readiness, clearance, schedule, execution, recovery, outcome, and charge handoff |
| AC-09 / FR-NOT-01..02 | Notification delivery | Notification | Notification controller/consumer tests | Generate and track an eligible notification; verify patient ownership |
| AC-10 / FR-REP-01..02 | Reporting projection | Report | Report consumer/query tests | Update/query projection without modifying source records |
| AC-11 / SEC-01..03 | Protected operations | Each owning service | `@PreAuthorize` web tests | Reject unauthorized roles, including unsupported `PATIENT` operations |
| AC-12 / API-03, API-06 | Service boundaries | Each owning service | Architecture rules and contract tests | Confirm DTO/UUID contracts and no cross-database access |
| AC-13 / EVT-04 | Duplicate delivery | Consuming service | Idempotency tests | Repeat message without repeated business effect |
| AC-14 / EVT-05..06 | Failure handling | Consuming service | Retry/DLQ configuration and tests | Observe bounded retry and terminal failure path |
| AC-15 / OBJ-08 | Engineering baseline | Multi-module repository | Recorded full Maven verification report | Confirm the recorded 3,612/0/0/0 baseline, then run the current agreed suite |

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

The detailed report describes the complete intended MediFlow system and separately records Review 1 limitations and acceptance evidence. Customer approval is still required for deployment-specific policy, external integrations, clinical governance, capacity targets, retention, and recovery objectives.
