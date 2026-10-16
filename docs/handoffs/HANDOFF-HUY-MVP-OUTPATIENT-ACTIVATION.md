# Huy MVP outpatient — gate API/host cần phối hợp

2026-10-14. Producer/client: Huy Pharmacy/Report. Source baseline `5f40b32c` + changes batch đầu
chưa commit. Canonical scope/contract: [MVP contracts](../plans/03-contracts.md),
[plan Huy](../plans/2026-10-14-huy-backend-mvp-adjustment-plan.md),
[manifest/evidence](../plans/huy-backend-mvp-baseline.md).

Đây là acceptance gate, không giao Huy quyền sửa foreign/shared modules hoặc công bố V1 đã live.

| Owner | Cần xác nhận | Tiêu chí đóng |
|---|---|---|
| Hoàng Anh | Core discovery phục vụ Patient/Organization lookup hiện hữu, logical caller pharmacy-service; Gateway pass-through Idempotency-Key cho POST prescriptions | Packaged Core trả exact IDs/absence/unavailable đúng fixture, Gateway không strip/rewrite header, giữ ADMIN/DOCTOR policy và service-only lookup denial |
| Vinh | Clinical prescription-context với exact record/visit/prescriber/patient/department | Signed Pharmacy caller được phép; stale/mismatched context fail-closed, không infer latest record |
| Lộc | Billing outpatient Rx issuer/grants/terminal adjustment; Notification cùng lifecycle bytes | Creation fixture → one source charge → exact PRESCRIPTION grant; pending refund không completed; filled/terminal fact consumer accepted |
| Lộc + shared owner | Notification module export và Support host/root build/profile | Report/Notification giữ hai stores/tx/queues, packaged single host, no dual standalone writers, Gateway route owner phối hợp |

New Pharmacy public V1 opt-in dùng **Idempotency-Key canonical UUID**, bắt buộc riêng V1.
Không đổi JSON/URL/envelope V0. Request thiếu/sai key không sinh commandId mới; request replay
giữ actor/intent receipt fence. Huy đã thêm module tests; Gateway/client acceptance chưa chạy.

Profile `mvp-outpatient` mới chỉ thuộc hai module Huy, không là topology sáu-app.
`mediflow.pharmacy.outpatient-api.enabled` mặc định false. Không mở API/publisher V1 thực tế
trước khi public dispense/terminal dispatch và same-byte/PG/Rabbit chain đạt H6-05/06/12.
Historical HELD không release vì profile mới; broker queues/data/migrations giữ nguyên.

Current clearance source lock (2026-10-15): Pharmacy now calls existing Billing
`GET /api/v1/billing/financial-clearances/{id}/lookup` with `SYSTEM`, `type=service`, subject
`pharmacy-service`. Response DTO: exists/clearanceId/eligible/invoiceId/accountId/patientId/purpose/
careEpisodeType/careEpisodeId/admissionId/surgeryCaseId/grantedAt/expiresAt/observedAt, inside the
existing ApiResponse and matching correlation header. It has NO prescriptionId/revokedAt field;
the local immutable grant binds the prescription and Billing's eligible includes revocation.
The stock transaction checks current eligibility after local locks, compares exact source fields/times,
and denies stale/unavailable evidence. Observation is not a distributed lock/lease against later revocation.
Billing lookup gate `mediflow.billing.clearance-lookup.enabled` remains owner-controlled.
Lộc must verify actual PRESCRIPTION/OUTPATIENT_VISIT response and revocation with a real issuer/grant.
Existing Billing lookup fixtures are SURGERY/ADMISSION compatibility fixtures, not Rx permission proof.

Public creation/lifecycle now require SIX gates: global V2, identity, clinical-context,
clearance-consumer, clearance-authority and outpatient-api. API/event enrollment defaults remain OFF.
V20 adds a new outpatient delivery cohort: `mediflow.pharmacy.outpatient-events.enabled` plus
`mediflow.pharmacy.outpatient-events.acceptance-reference` and all six gates/profile are required.
The reference records the operator's acceptance decision; it is not a substitute for same-byte owner
tests. Historical HELD rows cannot join by flag/replay and are protected by an immutable-cohort trigger.
Already enrolled flows retain terminal delivery when new enrollment is disabled. Published V1 proof
is retained for retries. Billing/Clinical/Report/Notification acceptance still gates rollout.

## Billing V1 publication gate found by packaged acceptance

2026-10-15 finding, reconfirmed in batch 8 on 2026-10-16, source `e9480758` plus Huy's uncommitted worktree. Owner: Lộc/Billing; consumer: Pharmacy.
`PrescriptionChargeService.issue` appends the V1 `invoice.created` through `LedgerEventPort.appendHeld`.
The isolated packaged Billing/Pharmacy test did not receive that invoice within its 35-second bounded
probe, with ledger, Rx consumer, allowed DRUG price code and current-clearance lookup enabled.
This is an unaccepted publication boundary, not permission for Huy to release Billing rows or fake
a paymentRequestId. No Billing DB was inspected or changed. Historical HELD must remain held.

Acceptance for Lộc: implement/approve a selective NEW-outpatient source publication boundary;
creation → one source charge/request → actual invoice.created → selected payment → actual
PRESCRIPTION grant, with original IDs/bytes, confirms, retry/fencing and no historical bulk release.
Then run the prepared PharmacyOutpatientRuntimeAcceptanceIT live-chain method explicitly with
`-Dmediflow.acceptance.billing-v1-published=true`. Without this switch, that one method is reported
SKIPPED (not passed); H6-06.2/.4 and V1 rollout stay open. Separate executed fixture/JVM tests
prove pending-grant persistence and no automatic dispense, not a live issuer publication chain.

Core's actual correlation filter normalizes UUID headers. Pharmacy rejects a rewritten non-UUID
echo as unavailable. Use canonical UUID correlations for the real Core lookup chain; do not loosen
response correlation verification or treat the rewritten response as authenticated intent evidence.

## Clinical durable publication regression found during lookup setup

Owner: Vinh/Clinical. Packaged Clinical, a fresh private PG store, real Core-backed patient/doctor
lookups and `MEDIFLOW_CLINICAL_DURABLE_EVENTS_ENABLED=true`: a valid compatibility
`POST /api/v1/records` returns 500 and rolls back with
`IllegalArgumentException: legacy Clinical wire payload does not match its flat event metadata`.
The producer path is ClinicalEventPublisherAdapter.publishDurably → ClinicalOutboxPort.appendLegacyEvent.
Huy did not edit Clinical or seed its DB directly. Vinh should reconcile actual Rabbit conversion
bytes with the outbox metadata validator and test API + rollback/commit + original-byte replay.

The separate H6-03.4 lookup suite uses the existing compatibility API with durable-events=false
solely to create authoritative test records, then calls the real signed prescription-context lookup.
Its result does not certify Clinical's durable publisher or full MVP exam flow. Those producer gates
remain open under H6-08/12 and must not be hidden by the successful lookup acceptance.

Surgery/Inpatient/bed-capacity/admission authority không thuộc checklist handoff MVP này.

## Batch 3 — Report intake/read và library proposal

2026-10-15 update: canonical profile is now `outpatient-mvp`; `mvp-outpatient` remains supported.
Report module export/scoped persistence/security and remaining host owner work are canonical in
[Support composition handoff](HANDOFF-HUY-SUPPORT-COMPOSITION.md). That handoff supersedes the
earlier "library artifact only" description below, not the source/coverage/activation gates.

Vinh: Clinical `medicalrecord.completed.v1.json` hiện ghi ADMISSION. Report MVP giữ nó làm
negative/deferred fixture, không đổi file producer. Cần outpatient completion fixture source thật
(disposition/episode/department/revision/occurredAt), cùng bytes được Report accept, trước bật intake.
Positive outpatient completion test hiện chỉ LOCAL VARIANT. Actual Lab fixture đã được đọc trực tiếp.

Huy đã thêm four alternative outpatient terminal/filled fixtures cạnh created.outpatient hiện có;
codec round-trip và Report đọc filled bytes trực tiếp. Các owner Billing/Clinical/Notification cần
same-byte local+runtime acceptance riêng; fixture pass không release historical HELD deliveries.

Hoàng Anh/FE: API additive proposal, profile MVP + global/read gate đều opt-in, ADMIN/MANAGER:

| Method/path | Query | Response/error |
|---|---|---|
| GET `/api/v1/reports/outpatient/daily` | fromDate/toDate ISO dates, optional departmentId UUID | ApiResponse<OutpatientReportDTO>, max 366 days |
| GET `/api/v1/reports/outpatient/monthly` | year 2000–2100, optional departmentId | Same DTO, 12 periods for complete calendar year |

DTO: fromDate/toDate/departmentId/currency(VND)/zoneId/coverageId/sourceObservedAt/reconciledAt/
covered/periods. Period: fromDate/toDate/completedVisits/labTests/dispensedPrescriptions/dispensedUnits/
cashReceived/refunds/netCash. Cash is NOT earned revenue. Invalid period => 400 REPORT_VALIDATION_ERROR;
missing/unaccepted/stale coverage => 404 REPORT_NOT_FOUND, no zero DTO. Legacy APIs unchanged.
Gateway must exact-route/deny roles and client must show unavailable distinctly; no Gateway edits here.

Lộc + Vinh + Huy: V27 accepted coverage is EMPTY. Need audited per-source export/cutover references
for CLINICAL/LAB/PHARMACY/BILLING_CASH/BILLING_REFUND, exact period/department/zone/VND and
reconciliation/owner sign-off before reader rollout. No automatic write API, no fixture historical seed.
Local witness digest binds Report evidence only, is not an external completeness proof/signature.
New facts/late refund change digest and withhold stale totals until re-acceptance. Pending/refund
conflicts stay unavailable. Do not turn quiet queue or absent source into legitimate zero.

Lộc/shared build owner: Report now attaches plain `report-service` classifier `lib`; default JAR
is still standalone Boot executable. Support must explicitly depend classifier lib and exclude
ReportServiceApplication scans. Two-store/Flyway/tx/security composition still H6-10.2–11; artifact
checks are not host readiness. New five-key intake owns report.outpatient-v1.q/.dlq; old queues/data
remain. Cutover must prevent dual writers and preserve pending evidence; no bulk HELD release.

Sau khi các gate đạt, đưa rule lasting vào canonical contracts và gỡ handoff/registry entry.
