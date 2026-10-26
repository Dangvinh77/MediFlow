# HANDOFF — Clinical/Lab financial clearance

**Phân loại ngày 26/10/2026: HISTORICAL CHECKPOINT — không là backlog hiện hành.** Các trạng thái ACTIVE/OPEN, owner và test counts
bên dưới thuộc checkpoint ghi trong tài liệu, không là kết luận trên revision hiện tại.
Xem [registry hiện hành](../README.md) để biết phần cần xác minh; di chuyển không có nghĩa đã nghiệm thu.

**Status:** ACTIVE — authoritative request issuance exists; L1/L2 implementation and partial L3 broker/recovery proof (default OFF) have evidence
(see the discovery handoff §5/§7); L3 failure/ordering coverage and L4 artifact delivery remain open; G1 Gateway route and V-CLOSE-03 consumer acceptance remain open.
**Owner:** Lộc (`locgit-89`), Billing.
**Unblocks:** Vinh's Clinical EXAM gate and Lab LAB_TEST gate.

## Current implementation handoff — 2026-10-16

Source `71e2684d`: thực hiện [Lộc discovery/publication L1–L4](2026-10-18-billing-notification-checkpoints.md)
theo contract/checklist đã chốt; không cần viết kế hoạch khác. Hoàng Anh owns G1 Gateway.
Các mục lịch sử bên dưới nói "published clearance" phải được đọc với giới hạn này:
`ExamChargeRabbitIntegrationTest`/`LabTestChargeRabbitIntegrationTest` tắt dispatcher, lấy
request bằng SQL và assert grant từ BILLING_EVENT_OUTBOX. Chúng chứng minh persisted grant,
chưa chứng minh broker delivery. Live LedgerEventOutboxAdapter giữ V1 HELD và V6 còn fence.
L1 bổ sung public source discovery; L2–L3 bổ sung new-cohort publication/actual broker recovery.
Producer/consumer phải nghiệm thu cùng bytes trước deployment activation; historical HELD
không được release. **Cập nhật 2026-10-16 (cuối ngày):** L1/L2 và một phần L3 đã triển khai và chạy Docker thật
(owner báo 1007/1007 test + recovery IT), evidence ở discovery handoff §7;
L3 failure/ordering và L4 raw artifacts chưa đóng; hai flags publication/discovery
vẫn default OFF nên hành vi production không đổi. Clinical/Lab consumer bên dưới vẫn phải tự
nghiệm thu actual delivery (không dùng evidence Billing này để tự coi là paid E2E).

## Producer

Billing produces `financial.clearance.granted` version 1 after an authoritative charge request is paid or an approved emergency path is recorded. The durable payload and target rules are defined in [`CONTRACT-CARE-BILLING-01`](../care-finance/CONTRACT-CARE-BILLING-01.md).

## Consumer

- Clinical consumes only `purpose=EXAM` for the exact appointment or record and outpatient episode.
- Lab consumes only `purpose=LAB_TEST` with explicit `labTestIds` for the exact episode.

Both consumers already reject mismatched purpose, patient, episode and target IDs, and both feature paths remain disabled.

## Owner actions

1. Create Billing charge requests from accepted `appointment.status.changed`/`medicalrecord.created` and `lab.request.created` facts without double-charging the same source.
2. Reconcile payment to the exact request and publish the version-1 clearance through the outbox.
3. Preserve one clearance per business operation and idempotent replay by event ID.
4. Keep the held producer disabled until the same-byte producer/consumer fixtures pass together.

### LAB_TEST planned-request issuance (2026-10-10)

Opt-in `lab.request.created` issuer added: `LabTestPlannedRequestService` opens/reuses the exact
episode account, posts one `LAB_TEST` charge from the configured price catalog and creates one
`PAYMENT_REQUEST` (target `recordId`/`labTestIds`) per lab test, mirroring the Surgery planned-request
pattern. Gated by `mediflow.billing.ledger.enabled` + `mediflow.billing.lab-test-charge-consumer.enabled`
(both false by default); disabled leaves `lab.request.created` unbound on `billing.q`, unchanged from
before this slice. Exact replay returns the recorded request without re-pricing; a same-source event
with different `sourceOrderId`/bytes conflicts. Clearance granting needs no new code: the existing
generic `LedgerPaymentService` already grants `financial.clearance.granted` for any `PaymentRequestPurpose`,
including `LAB_TEST`, once the request is paid in full.
Verified against the real lab-service producer fixture, copied byte-for-byte into
`backend/billing-service/src/test/resources/contracts/lab-request-v1/lab.request.created.v1.json`
(mirrors `backend/lab-service/src/test/resources/contracts/lab.request.created.v1.json`).
Decoder/service/configuration tests pass. **Docker E2E (2026-10-12):** real Postgres + RabbitMQ
proof now exists and passes — `LabTestChargeRabbitIntegrationTest` sends the real producer fixture
through the actual queue, verifies the charge/request/dedupe, completes payment, and asserts the
published `financial.clearance.granted` payload (`purpose=LAB_TEST`, `labTestIds`). This proves
Billing's own side of the chain (request → charge → payment → clearance published); Lab's own
"execution start" consumption of that clearance is Vinh's side, proven separately in Lab's own test
suite, not Billing's. The prior "Testcontainers cannot reach Docker here" note was a Docker
Desktop/testcontainers-version API incompatibility (HTTP 400 with an empty `/info` response over the
`docker_cli` named pipe), fixed by pinning `testcontainers.version=1.20.6` plus a
`docker-java.properties` (`api.version=1.40`) in this module's test resources, mirroring
`pharmacy-service`/`report-service`'s existing fix for the same issue.

### EXAM charge issuance (2026-10-12)

Opt-in `appointment.status.changed` issuer added: `ExamChargePlannedRequestService` opens/reuses the
exact appointment's episode account, posts one `EXAM` charge from the configured price catalog and
creates one `PAYMENT_REQUEST` (target `appointmentId`) for the appointment's own `priceCode`, mirroring
the Lab-test planned-request pattern. Gated by `mediflow.billing.ledger.enabled` +
`mediflow.billing.exam-charge-consumer.enabled` (both false by default); disabled keeps the existing
legacy V0 `onAppointmentStatusChanged` compatibility handler on `appointment.status.changed` unchanged
— that routing key is already unconditionally bound, so no new Binding bean was needed, only a dispatch
switch in `BillingEventConsumer` (exactly one writer, same principle as the discharge-approval path).

Clinical reuses this one routing key for every appointment transition, always with `sourceType=EXAM`;
only the normal check-in transition (`oldStatus=PENDING`, `newStatus=AWAITING_PAYMENT`, `recordId=null`)
is the EXAM charge trigger. `ExamChargeDecoder#decodeApplicable` returns `Optional.empty()` (an
explicit no-op ACK, not a DLQ reject) for every other well-formed transition, the same "valid but not
applicable" shape Surgery already uses for non-SURGERY `financial.clearance.granted` purposes. A
malformed envelope, or a charge-trigger-shaped payload that violates its own invariants (`oldStatus`
not `PENDING`, or a non-null `recordId`), still rejects to DLQ.

Verified against the real clinical-service producer fixture (`appointment.status.changed.v1.json`,
PR #334), copied byte-for-byte into
`backend/billing-service/src/test/resources/contracts/appointment-status-changed-v1/`. Decoder/service/
configuration/consumer tests pass, including the not-applicable and malformed-shape branches.
**Docker E2E (2026-10-12):** real Postgres + RabbitMQ proof now exists and passes —
`ExamChargeRabbitIntegrationTest` sends the real check-in fixture through the actual queue, verifies
the charge/request/dedupe, rejects a trigger-shaped payload that violates its own invariants, acks
(without effect) a different valid appointment transition, completes payment, and asserts the
published `financial.clearance.granted` payload (`purpose=EXAM`, `appointmentId`, `recordId=null`).
This proves Billing's own side of the chain (check-in → charge → payment → clearance published);
Clinical's own "examination start" consumption of that clearance is Vinh's side, proven separately
in Clinical's own test suite, not Billing's. Clearance granting needs no new code: the existing
generic `LedgerPaymentService` already grants `financial.clearance.granted` for `EXAM` once the
request is paid in full, same as LAB_TEST.

**Out of scope, deliberately not touched:** `medicalrecord.created` is NOT the EXAM charge trigger —
Clinical's own design doc (`docs/superpowers/specs/2026-10-10-vinh-loc-care-finance-contract-closure-design.md`)
states the record is only created after clearance already permits examination to start, so Billing's
legacy `onMedicalRecordCreated` V0 compatibility handler on that routing key is left exactly as-is.
Clinical's V2 `MedicalRecordCreatedV2Payload` can already be emitted (when `care-finance-v2` is on)
wrapped in the same V1 envelope shape that the legacy flat `MedicalRecordCreatedEvent` record does not
expect; whether that combination is safe today is a pre-existing question that predates this task
(the V2 payload class existed before this slice) and is not something this EXAM-issuance change
introduces, changes, or closes. Flagging it here rather than silently expanding scope to Clinical's
`medicalrecord.created` routing.

**Still open:** Billing's own Docker proof for both EXAM and LAB_TEST is done (above); the full
cross-service proof — Clinical actually starting examination and Lab actually starting execution
after consuming the real published clearance — is Vinh's side and is not run from this module.
Deleting this handoff needs both sides' acceptance together, per the lifecycle rule, not Billing's
proof alone.

### Fixture reconciliation and MVP runtime config (2026-10-15)

Per Vinh's open owner task in `docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md` ("Lộc — fixture
ownership"): the generic held fixture
`backend/billing-service/src/test/resources/contracts/ledger-v1/clearance-exam.json` previously
carried both `appointmentId` and `recordId`, which does not match the live EXAM producer —
`ExamChargeRepositoryAdapter` always publishes an appointment-only target (`recordId=null`), as
already asserted by `ExamChargeRabbitIntegrationTest`. Set `recordId` to `null` in the fixture to
match. This does not change Clinical's exactly-one-target (XOR) acceptance invariant; Surgery's
`SurgeryClearanceDecoderTest`, which also reads this generic fixture, does not assert on the exact
value of `recordId` and remains green.

Also wired the MVP runtime switches for the outpatient flows this handoff covers (Phase M6-12/
M6-07 in `docs/plans/05-implementation-plan.md`), none of which required new code — only turning
on existing opt-in flags:
- `backend/billing-service/src/main/resources/application.yml`: declared the two
  previously-undeclared flags `exam-charge-consumer.enabled`
  (`MEDIFLOW_BILLING_EXAM_CHARGE_CONSUMER_ENABLED`) and `lab-test-charge-consumer.enabled`
  (`MEDIFLOW_BILLING_LAB_TEST_CHARGE_CONSUMER_ENABLED`), default `false` (no behavior change).
- `docker-compose.yml`: set `true` for billing's `clearance-lookup`, `ledger`, `refunds`,
  `exam-charge-consumer`, `lab-test-charge-consumer`, `prescription-charge-consumer`, and for
  notification's `care-v1` + `refund-consumer`. Every Admission/Surgery-only flag
  (`settlement-preview`, `reconciliation`, `admission-charge-close`, `deposit-allocation`,
  `final-settlement`, `settlement`, `surgery-charge-consumer`, `surgery-completion-proof`,
  `surgery-consumer`, `surgery-payment-request-consumer`) is left at its default `false` —
  deliberately not enabled, per the MVP scope decision that a global care-finance flag must not
  turn Inpatient/Surgery on by accident.

Fresh verification same day (Java 21, Docker available, `testcontainers.version=1.20.6` +
`docker-java.properties` pin already in place from 2026-10-12):
`mvn -pl backend/billing-service -am test` → **940 tests, 0 failures, 0 errors**, including
`ExamChargeRabbitIntegrationTest` and `LabTestChargeRabbitIntegrationTest` against real
PostgreSQL + RabbitMQ (Docker E2E, not mocked). `mvn -f backend/surgery-service/pom.xml test
-Dtest=SurgeryClearanceDecoderTest` → 13 tests, 0 failures/errors, confirming the fixture edit
does not regress Surgery's read of this generic fixture.

## Acceptance criteria

- Billing serializes the canonical EXAM and LAB_TEST fixtures byte-for-byte with the consumers. DONE.
- Duplicate delivery has one effect; malformed or mismatched targets follow bounded retry/DLQ. DONE
  (`ExamChargeRabbitIntegrationTest`, `LabTestChargeRabbitIntegrationTest`).
- Docker proves check-in → charge → payment → EXAM clearance. DONE (Billing's side). → examination
  start remains Clinical's own proof, not run here.
- Docker proves Lab request → charge → payment → LAB_TEST clearance. DONE (Billing's side). →
  execution start remains Lab's own proof, not run here.
- Compatibility payment handling remains until migration and rollback evidence is recorded.
- Only after Clinical's and Lab's own acceptance proofs also pass may owners enable the affected
  Care-Finance flags and delete this handoff.
