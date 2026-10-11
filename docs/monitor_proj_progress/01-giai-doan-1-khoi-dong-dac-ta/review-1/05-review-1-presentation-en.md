# MediFlow Review 1 — Presentation Report (English)

| Presentation item | Value |
|---|---|
| Topic | Problem Definition and Customer Requirements Specification |
| Suggested duration | 12–18 minutes plus questions |
| Structure | 10 presentation sections |
| Source baseline | MediFlow repository commit `1cc014e` |
| Audience | Review panel and project stakeholders |

## Section 1 — Project Introduction

**Core message:** MediFlow connects the patient's journey across hospital departments while keeping each department's data ownership clear.

| Item | Summary |
|---|---|
| Project | Hospital management platform |
| Delivery channel | Next.js web application through a central API gateway |
| Backend | Ten Spring Boot business services |
| Data | Isolated PostgreSQL database per service |
| Integration | REST for immediate authority checks; RabbitMQ events for completed facts |
| Users | Nine roles covering staff, patients, managers, and automation |

MediFlow is not only a collection of forms. It models how information moves from registration to care, testing, medication, finance, admission, surgery, communication, and management reporting.

*Presenter cue: Start with a simple staff-assisted patient story: registration, appointment, consultation, laboratory test, prescription, payment, and follow-up. Explain that every implemented handoff has an owning service.*

## Section 2 — Problem Definition

| Current problem | Operational consequence | MediFlow response |
|---|---|---|
| Paper and disconnected applications | Repeated entry and inconsistent records | Stable patient identifiers and explicit service contracts |
| Manual departmental handoff | Delayed orders, results, and decisions | Controlled workflow states and events |
| Prescription separated from stock | Dispensing risk and inaccurate inventory | Prescription validation and inventory movement |
| Services separated from finance | Missed or duplicate charges | Idempotent billable events and centralized Billing ownership |
| Weak admission/surgery coordination | Resource conflict and unclear readiness | Clearance, allocation, readiness, and schedule gates |
| Manual management reporting | Slow and disputed figures | Event-fed read models |
| Inconsistent access rules | Privacy and accountability risk | Backend role authorization and default deny |

*Presenter cue: Emphasize that the problem is not a lack of data; it is fragmented data without reliable ownership, timing, or accountability.*

## Section 3 — Objectives and Scope

### Objectives

- Trace the patient journey end to end.
- Give every important record one authoritative service.
- Enforce role-based separation of duties.
- Automate safe cross-department handoff.
- Protect financial and inventory integrity.
- Support failure recovery without duplicate business effects.
- Provide management information without modifying source records.

### Implemented business scope

| Service | Main responsibility |
|---|---|
| Organization | Staff, roles, departments, facilities, rooms |
| Patient | Staff-operated profiles and identifiers |
| Clinical | Appointments, medical records, diagnoses, disposition/references; EXAM proof gates examination |
| Laboratory | Direct `ADMIN`/`DOCTOR` test creation, clearance-gated processing, results/publication |
| Pharmacy | Pharmacy-owned prescriptions, payment-proof-gated dispensing, medicines, stock |
| Billing | Active compatibility invoice read/create/pay; other ledger/deposit/refund/settlement/clearance slices are gated |
| Notification | Event-based notification processing and delivery status |
| Report | Operational and financial read models |
| Inpatient | Locally complete Core V1 for admissions, owned beds, stays, treatment, transfers, discharge; event integration remains gated |
| Surgery | Default-off feature-gated foundation and selected local routes; full lifecycle is not active |

*Presenter cue: Distinguish status carefully: Inpatient Core V1 is locally complete but cross-service event activation is gated; Surgery is a default-off foundation with selected local routes, not a complete lifecycle.*

## Section 4 — Users and Functions

| Role | Main functions |
|---|---|
| `ADMIN` | Configure Organization data and permitted Inpatient bed records |
| `DOCTOR` | Write proof-gated Clinical records; directly create Lab tests and Pharmacy prescriptions; perform permitted Inpatient actions |
| `NURSE` | Create/update appointments, read Clinical records, and perform permitted Inpatient bed/admit/treatment-observation operations |
| `LAB_TECH` | Process existing proof-authorized Lab tests; record, verify, and publish results; cannot create tests |
| `PHARMACIST` | Validate prescriptions, dispense medicines, control stock |
| `CASHIER` | Handle active compatibility invoice/pay and only explicitly enabled ledger/deposit/refund/settlement/clearance slices |
| `MANAGER` | Review operational and financial reports |
| `PATIENT` | Read owned notifications; broader profile, appointment, clinical, and financial self-service is future-facing |
| `SYSTEM` | Publish/consume events, retry failures, update reports, create notifications |

*Presenter cue: Explain that hiding a button is only user experience; the backend independently checks roles at protected endpoints.*

## Section 5 — Proposed Solution Architecture

| Component | Role in the solution |
|---|---|
| Browser client | Presents role-aware workflows |
| API gateway | Provides one controlled client entry point |
| Eureka | Registers and discovers backend services |
| Business services | Own rules and records by bounded context |
| PostgreSQL | Provides a separate logical database for each service |
| RabbitMQ | Distributes completed business events |
| Transactional outbox | Prevents committed changes from losing their event intent |
| Idempotent consumer | Prevents retries from repeating business effects |
| Retry/dead-letter handling | Makes temporary and terminal failures observable |

Architecture rule: `infrastructure → application → domain`. The domain does not depend on Spring, transport, or persistence. Services exchange DTOs and UUID references, never database relations.

*Presenter cue: Contrast REST and events: REST answers “what must I confirm now?”, while an event announces “what has already happened?”.*

## Section 6 — Customer Data-Processing Cycles

| Workflow | Actor | Input information | Processing | Output information | Owning service |
|---|---|---|---|---|---|
| Staff-operated registration | `ADMIN`, `NURSE` | Demographics, contact, identifiers | Validate identity data | Patient profile | Patient |
| Appointment creation | `ADMIN`, `NURSE` | Patient UUID, service/time | Validate scheduling data | Appointment | Clinical |
| Clinical examination | `ADMIN`, `DOCTOR` | Patient/appointment, findings, EXAM clearance/override | Validate proof and write record/diagnosis | Medical record and disposition | Clinical |
| Lab test creation | `ADMIN`, `DOCTOR` | Patient/record, department, test type/date | Create test directly; no Clinical order contract | Lab test | Laboratory |
| Lab processing | `ADMIN`, `LAB_TECH` | Existing test, LAB_TEST clearance/override, specimen/values | Process, verify, publish | Result and result event | Laboratory |
| Prescription creation | `ADMIN`, `DOCTOR` | Clinical reference and medicine lines | Pharmacy validates/prices and creates its prescription | Pharmacy prescription | Pharmacy |
| Dispensing | `ADMIN`, `PHARMACIST` | Pharmacy prescription, durable payment proof, stock | Validate proof and stock; dispense | Dispense and stock state | Pharmacy |
| Compatibility invoice/pay | Authorized Billing staff | Patient/source, invoice/payment data | Apply active compatibility rules | Invoice/payment state | Billing |
| Inpatient Core V1 | Route-authorized staff | Admission, bed, deposit proof/override, treatment, discharge/settlement close proof | Apply explicit route gates and states | Admission, treatment, discharge and bed state | Inpatient |
| Surgery gated foundation | Authorized staff under default-off flags | Selected case/pre-op/cancel/read/draft-schedule input | Execute only an enabled local slice | Local result, not a complete outcome lifecycle | Surgery |
| Notification delivery | `SYSTEM`, `PATIENT` read | Approved events and recipient identity | Deliver idempotently; enforce owned reads | Delivery status/owned notification | Notification |
| Reporting | `SYSTEM`, `MANAGER` | Approved events and report criteria | Update/query projection | Authorized report | Report |

*Presenter cue: Read one row using the five-part pattern: actor, input, process, output, owner. State that the same pattern is applied to every major workflow in the full reports.*

## Section 7 — Main Cross-Department Flows

1. Staff-operated Patient registration → Clinical appointment → clinical encounter.
2. `ADMIN`/`DOCTOR` directly creates a Laboratory test → clearance/override → processing/result; Clinical-to-Lab ordering is future work.
3. Doctor creates a Pharmacy-owned prescription → durable payment proof → dispense → stock movement.
4. Active compatibility invoice → payment; ledger/deposit/refund/settlement/clearance remains default-off or incomplete unless explicitly enabled.
5. Local Inpatient admission → bed → matching deposit clearance/override → explicit admit → treatment → medical discharge → settlement/override → administrative close.
6. Surgery request → readiness → clearance → schedule → execution → outcome is the target flow, not current acceptance; the source exposes only selected default-off local slices.
7. Completed domain event → notification delivery and report projection update.

Key controls:

- Stable UUID correlation across services.
- Owning-service validation before state change.
- `BigDecimal` for money.
- Outbox publication after committed changes.
- Idempotent event consumption.
- Bounded retry and visible dead-letter processing.

*Presenter cue: Use Inpatient to show local route ownership and explain that its financial event chain remains gated. Present Surgery as a target workflow, not active end-to-end behavior.*

## Section 8 — Hardware and Software Requirements

### Client

| Category | Minimum planning baseline | Recommended |
|---|---|---|
| Staff device | 64-bit dual-core CPU, 4 GB RAM, 1366×768 | Modern 4-core CPU, 8 GB RAM, Full HD |
| Browser | Current Chrome, Edge, Firefox, or Safari | Vendor-supported OS and browser with security updates |
| Network | Stable access to deployed HTTPS endpoint | Reliable wired/Wi-Fi connection |
| Development | 4-core CPU, 16 GB RAM, 30 GB free SSD | 8-core CPU, 32 GB RAM, 60+ GB free SSD |

### Server and platform

| Category | Source-aligned baseline |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Maven |
| Frontend | Next.js 16.2.11, React 19.2.4, TypeScript 5, Tailwind CSS 4, pnpm |
| Infrastructure | PostgreSQL 16-compatible, RabbitMQ 3-compatible, Eureka, Docker Compose |
| Integration environment | 16 logical cores, 32 GB RAM, 150 GB SSD planning profile |
| Initial production planning | 24+ logical cores, 64+ GB RAM, 500+ GB resilient SSD across suitable hosts |

These hardware values are planning recommendations. Production sizing requires load, availability, retention, and recovery tests.

*Presenter cue: Do not present the production numbers as guarantees. State clearly that they are starting points awaiting measured workload evidence.*

## Section 9 — Limitations and Remaining Work

| Limitation | Current status |
|---|---|
| Native mobile application | Only a Flutter loader package exists; no complete runnable mobile app |
| Patient self-service | Current `PATIENT` access is limited to owned notification reads |
| Inpatient cross-service integration | Core V1 local APIs exist; RabbitMQ producer/consumer activation defaults off |
| Surgery lifecycle | Business/API flags default off and the complete lifecycle/outcome event is not implemented |
| Clinical-to-Lab order | Not implemented; Lab tests are directly created by `ADMIN`/`DOCTOR` |
| Billing migration slices | Ledger, deposit, refund, settlement, and clearance are default-off or incomplete; compatibility invoice read/create/pay remains active |
| Mandatory proofs | EXAM clearance, LAB_TEST clearance, Pharmacy payment proof, Inpatient deposit/settlement proof must be supplied or an allowed audited override must exist |
| Insurance and national identity | No certified production integration is demonstrated |
| Bank/payment gateway | Internal finance exists; commercial provider integration is not proven complete |
| Medical devices/external labs/PACS/EHR | No certified external interoperability is claimed |
| SMS/email/push provider | Internal notification workflow exists; production vendor contract is pending |
| Production SLO, RTO, RPO, retention | Must be agreed and validated for the target hospital |
| Capacity and disaster recovery | Require load, failure, backup, and restore testing |
| Accessibility and regulation | Formal targets and certifications remain future governance work |
| Automated diagnosis/treatment | Explicitly outside current scope; clinical decisions remain human |

*Presenter cue: Frame limitations as controlled project boundaries. The report distinguishes future work from implemented capability so the review remains credible.*

## Section 10 — Acceptance and Conclusion

Review 1 can be demonstrated through the following acceptance story:

1. As `ADMIN`/`NURSE`, register a patient in Patient and schedule an appointment in Clinical.
2. Open a Clinical examination only with matching EXAM clearance/override under `ADMIN`/`DOCTOR`.
3. Directly create a Lab test as `ADMIN`/`DOCTOR`, then require LAB_TEST clearance/override before authorized processing/result publication.
4. Create the prescription through Pharmacy and require durable payment proof before dispensing/stock update.
5. Demonstrate active compatibility invoice read/create/pay; verify gated Billing migration features reject while disabled.
6. Demonstrate Inpatient bed + deposit gate, explicit admit, medical discharge, and settlement/override-gated close while event flags remain off.
7. Verify Surgery is default-off and demonstrate only an explicitly enabled, selected local slice; exclude full lifecycle acceptance.
8. Generate notification and reporting updates from committed events.
9. Reject unauthorized operations and repeated business effects.
10. Execute the agreed automated verification suite successfully.

**Conclusion:** MediFlow provides a source-aligned foundation for coordinated hospital operations. Its value is not only the number of modules, but the controlled movement of information between authorized actors and owning services.

*Presenter cue: Close with three phrases: “one clear owner for each record,” “one traceable path for each implemented workflow,” and “one explicit gate for unfinished integration.” Then invite questions.*

### Quick Questions and Answers

| Likely question | Short answer |
|---|---|
| Why microservices? | The hospital domains have distinct rules and ownership; independent services keep those boundaries explicit. |
| Why both REST and events? | REST confirms information needed now; events distribute facts that already happened. |
| Is mobile complete? | No. Only a shared Flutter loader package is present; responsive web is the current delivery assumption. |
| What is the status of Inpatient and Surgery? | Inpatient Core V1 is locally complete with event integrations off by default. Surgery is a default-off foundation with selected local routes; its full lifecycle is not implemented. |
| Is production capacity guaranteed? | No. Hardware figures are planning baselines and require representative testing. |
| How are duplicate charges prevented? | Billing consumers use event identity/idempotency so repeated delivery does not repeat the business effect. |
