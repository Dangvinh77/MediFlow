# Huy V2 — báo cáo hoàn thiện và đánh giá plan

Ngày: 2026-10-02. Baseline: `Huy` / HEAD `ea11597` + working tree chưa commit.
Phạm vi lượt này: Pharmacy, Report và tài liệu; không sửa Surgery/shared/producer khác.
Backlog chuẩn: [plan Huy](2026-09-25-huy-surgery-pharmacy-report.md).

## 1. Kết luận

**V2 chưa hoàn tất production.** Nền tảng transaction/persistence/compatibility và các internal
outpatient lifecycle paths đã có kiểm chứng dữ liệu thật. Report có operational kernel, pending
admission evidence, finite replay/reconciliation và availability-aware snapshot reads. Đây không
phải classified finance, admission medication permission hoặc full live care-finance dashboard.

Không có critical test bị skip trong lần kiểm cuối. Tuy nhiên feature flags/listeners/held delivery
vẫn OFF; G1 same-byte owner acceptance và G3 E2E chưa đạt. Không bật cờ hoặc ghi SQL thủ công
để biến phạm vi local thành production DONE.

## 2. Phần hoàn thiện trong lượt này

| Slice | Code và nghiệp vụ đã thực hiện | Bằng chứng |
|---|---|---|
| P-02.5.4 | V18 internal outpatient creation receipt/fence; trusted DOCTOR self/ADMIN intent, server price/name, expiry/availability sau lock, Rx/reservations/slip/held CREATED atomic; same intent retry, conflict, rollback | 8 creation unit; PG receipt retry/changed intent, concurrent create, injected outbox failure/retry và internal create→grant→dispense |
| P-03.5.3 | Internal cancellation/whole-order expiry; exact actor/reason/time proof, expiry sau lock waits, held terminal/whole reservation release atomic | 10 terminal unit; PG cancel/expiry success, writer rollback, nanos response retry và cancel-dispense race |
| P-03.5.4 | REQUIRES_NEW stock-failure recovery sau rollback; re-lock/re-authorize/recheck current shortage/expiry; no stale overwrite, no denied→FAILED, no V0 receipt/refund/decrement | Dispense unit 16 cases; PG stock-failure success/retry, rollback và missing clearance denial |
| R-01.7.2 / R-04.5.1 | V10 empty publication, exact accepted period/zone/metric gate, immutable VERIFIED generation reads; bounded daily English counters và snapshotOnly/as-of metadata | 8 read unit; 4 new PG read cases + V9→V10 preservation/no-invented-coverage migration |
| R-02.3.1 | Two gated operations GET endpoints; ADMIN/MANAGER/DOCTOR, aggregate-only envelope/correlation, 400 invalid period, 404 unavailable, default off | 7 web/auth cases, 2 feature-gate cases; report.http updated |
| Các VERIFY còn treo | V16 clearance, V17 held writer/time reload, V8 replay, actual Lab mapping, V9 admission pending và migration/race tests chạy lại với Docker | Full clean suite, 0 skip; đánh dấu đúng các leaf verification trong plan |

Pharma internal creation chưa kiểm chứng authority liên service của patient/episode; không có
public controller gọi nó. Admission creation/dispense bị từ chối khi thiếu eligible context contract.
Terminal facts là held proposals, không phải sự kiện hoàn tiền hay charge adjustment đã được Billing
chấp nhận. Report snapshot counters không gồm medical LOS/inpatient-days, occupancy hay full
surgery complication taxonomy; không đọc được live facts sau freeze như một báo cáo real-time.

## 3. Kiểm chứng cuối cùng

Command từ repository root:

```powershell
mvn -q -pl backend/pharmacy-service,backend/report-service -am '-Dapi.version=1.44' clean verify
```

| Module | Discovered/pass | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Pharmacy | 395 / 395 | 0 | 0 | 0 |
| Report | 244 / 244 | 0 | 0 | 0 |
| Tổng hai service | **639 / 639** | **0** | **0** | **0** |

Exit 0; clean build cùng Common trong reactor. Counts lấy từ XML Surefire sau clean, không cộng
báo cáo cũ. Docker Desktop engine 29.6.2, `postgres:16-alpine`, RabbitMQ Testcontainers theo module
(`rabbitmq:3.13-management-alpine`). PG tests chạy thật, gồm CareDispensePostgresTest 18/18,
PrescriptionCareEventWriterPostgresTest 8/8, OperationalReplayPostgresTest 11/11 và V9 pending.
Legacy Rabbit transport/consumer suites chạy; **không** có V2 live producer/binding E2E được duyệt.

Phát hiện và sửa trong quá trình kiểm: migration test chưa whitelist FK tới local clearance target;
test declaration trùng; cancellation retry response phải dùng exact lifecycle ISO proof thay vì
timestamp bị PostgreSQL làm tròn. Full clean verification trên code cuối đã pass sau sửa.
Log local ignored: `backend/report-service/target/v2-release-check.log`; XML ở target/surefire-reports
của từng module. Không chạy whole-repository reactor/Gateway hoặc Surgery suite trong lượt này.

## 4. Mức hoàn thiện của plan

Đếm **leaf task có ID**: không tính parent nếu có child, không cộng checkbox evidence/template
hoặc task chữ cái của legacy plan. Các child còn thiếu được tách rõ để parent PARTIAL không bị
hiểu là DONE chỉ vì một child local đã xong. Counts là trạng thái checklist, không phải phần trăm
production readiness hoặc trọng số effort.

| Phạm vi | Leaf hoàn tất local | Leaf tổng | Còn mở |
|---|---:|---:|---:|
| Pharmacy P-01…03 | 26 | 34 | 8 |
| Report R-01…04 | 20 | 40 | 20 |
| Pharmacy + Report | **46** | **74** | **28** |
| Full plan H/S/P/R/X | 85 | 158 | 73 |

Full plan vẫn 16 nhóm chính. Bảy nhóm Pharmacy/Report là phạm vi chốt V2; chỉ P-01 legacy không
đòi activation V2 mới. Các nhóm P-02/P-03/R-01…04 còn PARTIAL/OPEN. Full Surgery/H/X counts kế
thừa tài liệu, **không được re-certified bằng 639 tests của hai service**.

| Nhóm | Đánh giá hiện tại | Điều kiện còn lại |
|---|---|---|
| P-01 | Legacy ổn định theo local regression | Không dùng legacy paid semantics cho V1 |
| P-02 | Schema/DTO/codec/held lifecycle tốt, internal outpatient creation đã nối | Identity/episode authority, five-event owner approval, public wiring/hold-release rollout |
| P-03 | Exact clearance và atomic outpatient lifecycle đã kiểm chứng | Admission transfer freshness/cross-department policy + live bindings + actual-wire race/DLQ/E2E; medical discharge now ends eligibility |
| R-01 | Operational journal/pending/replay/reconcile/read gate tốt | Financial/correction journal, export/retention/horizon, live catch-up/controlled publication/rollback |
| R-02 | Legacy và local operations security pass | Financial controllers sau nguồn tiền đủ; Gateway DOCTOR route do Hoàng Anh |
| R-03 | Chưa có classified financial business writer/API | Lộc cung cấp đủ transaction/allocation/recognition/refund/settlement amounts/IDs/revisions/equations |
| R-04 | Lab revision-1 mapper, exact admission evidence, finite snapshot query pass | Imported/correction revisions, medical/discharge duration/rounding, Surgery facts/categories, bed/capacity, full KPI wiring; first Lab completion and admission singleton revisions are now accepted |

## 5. Còn 28 leaf Pharmacy/Report: chia đúng trách nhiệm

| Cụm | IDs còn mở | Input/gate | Việc Huy vẫn phải làm sau gate |
|---|---|---|---|
| Pharmacy identity/wire/clearance | P-02.2.4, P-02.5.5, P-03.2.3, P-03.5.5 | Lộc/Vinh/consumer owners: clearance immutable time/expiry/revoke, authority, adjustments, same bytes | Eligible/revoke persistence/policies nếu cần, public adapters/consumer/held release + compatibility rollout |
| Pharmacy admission/runtime | P-03.1.3, P-03.3.2, P-03.4.4, P-03.6 | Vinh: transfer freshness/cross-department policy; discharge fixture/eligibility and close-before-start semantics are now defined; G1 consumer acceptance remains | Admission authorizer/create/dispense/terminal wiring, real producer/consumer races/restart/DLQ/E2E |
| Report money/pending | R-01.2.3, R-01.3.2, R-01.5.3, R-01.6.2, R-03.1…7 | Lộc + producer acceptance: no guessed cash/liability/revenue/receivable equations | Classified writer, pending refund/reversal, financial replay, queries/API và totals/failure matrix |
| Report history/cutover | R-01.4.2, R-01.7.3 | All producers: source horizon/export/retention/correction | Catch-up/final fence, controlled approved publication, read switch/rollback. V10 loader không làm thay |
| Report permissions | R-02.3.2 | Hoàng Anh Gateway routes; finance projections | Direct finance role tests + Gateway acceptance với owner |
| Report metrics/full query | R-04.1.3, R-04.2.3, R-04.3, R-04.4, R-04.5.2, R-04.6 | Vinh source/correction/discharge/capacity; **Huy Surgery producer** outcomes/categories và joint G1 | Actual metric mappers, admission contributions/LOS, surgery/category/occupancy queries, full KPI/E2E verification |

Không đẩy toàn bộ việc còn lại cho team: money/metric/catch-up/controller wiring vẫn là code của
Huy sau khi authoritative inputs được chốt. Surgery producer cũng thuộc Huy, không phải blocker
của thành viên khác; current execution order chưa sửa Surgery trong lượt này. Active handoff:
[Huy consumer contracts](../../handoffs/HANDOFF-HUY-CARE-FINANCE-CONSUMERS.md).

## 6. Đánh giá chất lượng và go/no-go

- Kiến trúc: đúng inward ports/adapters, bare UUID references, domain không framework/I/O;
  module architecture suites pass. Không cross-service DB hoặc tự bịa target/source revision.
- Nghiệp vụ local: strong evidence cho atomicity, dedupe, conflict, exact time, rollback và race.
  Event bytes được giữ held; denials không trở thành refund/FAILED ngoài ý muốn.
- Báo cáo: phân biệt finite equality với complete history/live freshness, và unavailable với zero;
  giữ financial correctness gate thay vì suy earned revenue từ deposit/invoice.
- Khoảng trống release: G1/G3, actual classified money/eligible admission/full KPI/catch-up chưa đạt.
  639 module tests không chứng nhận các chức năng chưa có producer hoặc live adapter.

**NO-GO cho tuyên bố “V2 hoàn tất” hoặc bật production/cutover.** Local additive slice có thể đưa
qua owner review với flags OFF và đúng handoff; chưa tự commit/push/merge trong lượt này.
Checklist review-pr dùng ở bước cuối để giữ tách biệt kiến trúc/nghiệp vụ/test/activation và không
đóng parent bằng một leaf pass. Đây là báo cáo tình trạng thật, không thay đổi target spec để hợp
thức hóa implementation chưa đủ.
