# Nghiệm thu hợp đồng lịch khám và giữ suất — 26/10/2026

Đây là ma trận triển khai/nghiệm thu phái sinh từ [03 phụ lục A](03-contracts.md#opd-scheduling-v2),
không là schema thứ hai. Contract `OPD-SCHED-2026-10-26`, revision 1 là TARGET được mô tả;
chưa có producer implementation, Gateway route hoặc nghiệm thu liên service. Các bước dưới đây
không phải lệnh Postman chạy được trên demo hiện tại. [WF-01 source baseline](09-outpatient-source-lock-2026-10-26.md)
vẫn được giữ nguyên fingerprint, không ghi đè bằng target fixtures.

## 1. Cách đọc và kiểm bộ ví dụ

- [Schema](contracts/outpatient-scheduling-v2.schema.json): `$defs` là DTO được gọi tên trong 03;
  dùng draft 2020-12 và format checker cho UUID/date/date-time. Root chấp nhận các DTO đã định nghĩa;
  khi kiểm một endpoint phải chọn đúng `$ref: #/$defs/<DTO>`, không kiểm root rồi coi mọi DTO thay nhau được.
- [Ví dụ](contracts/outpatient-scheduling-v2.examples.json): schemaCases gồm payload cùng expected validity;
  tất cả UUID là giả lập thiết kế. Không dùng chúng như patient/doctor/session đang tồn tại trên demo.
- semanticCases chỉ ghi tình huống và kết quả **cần kiểm**, không là unit/integration test đã chạy.
  Schema không chứng minh authority, tổng sức chứa, quan hệ UUID, thời gian bắt đầu nhỏ hơn kết thúc,
  overlap, money arithmetic hoặc transaction safety.
- Mẫu `100000.00` là giao dịch thu dương chưa áp hỗ trợ, không thay bảng giá hoặc chính sách BHYT đã chốt.
  Schema slice này không nghiệm thu policy, FOLLOW_UP hay zero-liability.

Có thể kiểm độc lập bằng Python với `jsonschema` đã cài, chạy từ repo root:

```python
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

base = Path("docs/plans/contracts")
schema = json.loads((base / "outpatient-scheduling-v2.schema.json").read_text(encoding="utf-8"))
examples = json.loads((base / "outpatient-scheduling-v2.examples.json").read_text(encoding="utf-8"))
Draft202012Validator.check_schema(schema)
for case in examples["schemaCases"]:
    selected = {
        "$schema": schema["$schema"],
        "$defs": schema["$defs"],
        "$ref": "#/$defs/" + case["definition"],
    }
    validator = Draft202012Validator(selected, format_checker=FormatChecker())
    actual = validator.is_valid(case["payload"])
    assert actual == case["schemaValid"], case["id"]
print("Schema examples passed:", len(examples["schemaCases"]))
```

Kết quả kiểm lượt tài liệu: **39 định nghĩa, 69 schema cases đạt kỳ vọng**, trong đó 21 payload sai
bị từ chối; **18 semantic cases chưa chạy trên backend**. Không gọi schema examples là producer golden fixtures.

## 2. Sơ đồ xử lý payment lock

![Giữ suất, thu và đối soát](diagrams/outpatient-payment-lock-v2.svg)

[Mermaid source](diagrams/outpatient-payment-lock-v2.mmd). Khi Core/Clinical/Billing mất kết nối,
UI không tự cấp lại suất hoặc gửi một giao dịch với key mới. `reconcileAfter` đánh thức worker,
không tự giải phóng khóa. Missing operation không là bằng chứng chưa thu.

## 3. Các mốc bàn giao có thể kiểm riêng

| Mốc | Owner và nguồn sẽ thay đổi | Acceptance bắt buộc | Trạng thái |
|---|---|---|---|
| C-01 Core scheduling authority | Hoàng Anh; Organization web/application/domain/persistence trong core-service, migrations mới và tests | ADMIN cấp/revoke scope theo account; MANAGER own/other khoa; role/staff inactive; room eligibility/directory; service credential allowlist | NOT IMPLEMENTED |
| C-02 Clinical session + hold | Vinh; clinical-service domain/model, application/ports/services, web, persistence/migration mới | overlap doctor/room, breaks/giờ/zone, revision conflict, capacity counter, last-seat PG race, idempotent registration | NOT IMPLEMENTED |
| C-03 Billing operation và request binding | Vinh; billing-service ledger service/config/repository/outbox, controller V2 và migrations mới | PREPARING trước network; lock acquire; full positive patient liability; key/actor/binding guard; không bypass bằng V1 | NOT IMPLEMENTED |
| C-04 Clinical/Billing recovery | Vinh; hai context cùng own store/receipt/outbox, internal clients/workers | close-vs-commit, timeout/restart, missing giữ khóa, stale fence, late acquire, confirmation sau cuối buổi | NOT IMPLEMENTED |
| C-05 Gateway + producer/consumer fixtures | Hoàng Anh/Gateway; Vinh/Clinical/Billing/Notification; Huy/Report | V2 exact route/method/role, human/internal deny; raw bytes/revision/order replay; V1 regression | NOT IMPLEMENTED |
| C-06 Frontend schedule/reception/doctor/patient | Theo frontend ownership; không suy từ ownership backend | ADMIN/MANAGER quản lý ca; NURSE giữ suất; CASHIER pending/receipt; DOCTOR own day/week; PATIENT own phiếu | NOT IMPLEMENTED |

Tên thư mục ở bảng theo blueprint hiện hành. Trước khi viết code phải đọc nearest AGENTS và khóa
exact class/migration version trong plan của mốc; không cấp quyền Core/Gateway/Report/Pharmacy từ tài liệu này.
Producer C-01 chưa nghiệm thu thì giữ Clinical mutation gate OFF, không dùng fake authority cho demo.

## 4. Matrix API và database cần thực hiện

| Case | Bước kiểm | Kết quả cần chứng minh |
|---|---|---|
| SC-01 | ADMIN cấp khoa A cho MANAGER, MANAGER tạo ca A rồi thử B | A thành công, B 403; scope revision và actor lưu audit |
| SC-02 | Thu hồi scope hoặc khóa staff rồi gửi command mới | Từ chối theo current Core snapshot; không replay dữ liệu trái quyền |
| SC-03 | Doctor/room không active, sai khoa hoặc Core timeout | 422 eligibility hoặc 503 unavailable; không tạo session/hold |
| SC-04 | Tạo hai ca cùng doctor hoặc room có thời gian chồng | Chỉ một commit; ca liền nhau hợp lệ; STOPPED vẫn chiếm thời gian |
| SC-05 | Gửi time/break sai, capacity=0, unknown field, stale revision | Validation/conflict rõ ràng; không đổi aggregate/outbox |
| SC-06 | Hai NURSE giữ suất cuối bằng hai intent | Một registration/hold commit; count không âm/vượt capacity |
| SC-07 | NURSE double-click cùng key, rồi đổi patient cùng key | Exact replay cùng IDs/expiry; changed input 409 |
| SC-08 | AT_COUNTER và PREBOOKED | Counter đã có checkedInAt; prebook cần arrival riêng; chưa thu không vào khám |
| SC-09 | Hold hết hạn, Billing source chưa chuẩn bị hoặc amount thiếu | Không thu hold hết hạn; not-ready cho read retry; amount khác remaining bị 422 |
| SC-10 | CALL lease thành công nhưng response mất | Billing retry cùng operation; Clinical trả cùng fence, không chiếm thêm suất |
| SC-11 | Kill Billing trước/sau local cash commit rồi restart | PREPARING/CLOSED/COMPLETED được phục hồi đúng; không thu hai lần |
| SC-12 | Race close-if-unpaid với payment commit | Chỉ CLOSED hoặc COMPLETED; không có CLOSED rồi cash mới commit |
| SC-13 | Close trả 404/timeout, worker Clinical restart | Suất giữ PAYMENT_LOCKED; durable cursor retry + cảnh báo, không release từ absence |
| SC-14 | Acquire đến muộn sau CLOSED/release | Tombstone/fence từ chối; không hồi sinh operation hoặc lấy suất người khác |
| SC-15 | Event eligibility trễ nhưng close trả completed receipt/clearance | Clinical xác nhận đúng một lần; event trễ no-op |
| SC-16 | Payment trước TTL, commit sau TTL; confirmation sau cuối buổi | Lease hợp lệ vẫn cho ghi outcome cash; hết buổi đưa đối soát, không cấp số sai |
| SC-17 | Hủy/stop/sửa ca có hold, locked, confirmed hoặc consumed | Stop chặn mới; không auto-cancel/refund; không đổi doctor/time có phiếu |
| SC-18 | Doctor/patient sửa ID trên URL/query | Không đọc lịch/phiếu người khác; own calendar không nhận doctorId |
| SC-19 | Xem tuần qua Chủ nhật/thứ Hai và UTC boundary | Ngày/tuần Việt Nam chính xác; week response không chứa bệnh án |
| SC-20 | V1 payment/check-in vào registration V2; start-exam trước/sau thu và arrival | Payment/check-in bị flow-required; start-exam chọn guard V2, chỉ consume một lần khi đủ điều kiện; V1 historical rows giữ regression |

SC-04/06/07/11–16 cần PG thực; crash/replay/consumer cần RabbitMQ thực. Mock chỉ đủ cho business unit
rules, không chứng minh khóa DB hay phục hồi process. Chạy bằng isolated fixture setup, không sửa DB state
hoặc xóa inbox/outbox để dựng kết quả mong muốn.

## 5. Event và UI acceptance

Event mới khóa trong 03: outpatient.registration.changed và outpatient.financial.eligibility.granted,
đều version 1 của event type mới. Cần producer serializer xuất bytes rồi consumer xác nhận cùng bytes;
same eventId/revision khác raw bytes conflict. Cancellation trước create phải để lại tombstone;
rebuild Report không phát notification; nhận payment.completed V1 không xác nhận lịch thay Clinical.

Trên UI, kiểm tuần tự với các tài khoản demo được cấp role thật, không đổi claim ở trình duyệt:

1. ADMIN cấp scope; ADMIN/MANAGER tạo buổi; sửa/ngừng nhận có reason và xử lý conflict.
2. NURSE chọn patient/khoa/buổi, đăng ký AT_COUNTER đúng một lần; thấy hạn giữ, trạng thái chờ thu.
3. CASHIER tra đúng registration, xem số tiền Billing xác nhận; thu cùng key, thấy receipt hoặc pending.
4. NURSE/PATIENT đọc lại trạng thái; chỉ CONFIRMED mới có queueNumber. Khi event chậm hiển thị chờ xác nhận.
5. DOCTOR xem lịch của mình theo ngày/tuần và danh sách buổi; bắt đầu lượt assigned, đã đến, đủ authority.
6. Ngắt kết nối trong một lần thu; UI không đổi key và không gọi thành công khi chưa có outcome.
7. Kiểm nhận thông báo chưa thu khác xác nhận sau thu, mất quyền, khoa khác và bệnh nhân khác.

Mốc này chưa triển khai FOLLOW_UP, policy BHYT/zero-liability, vòng Lab hoàn chỉnh hoặc thay báo cáo tổng hợp;
nghiệm thu đủ SC không tự đóng toàn bộ WF-01–11. Các yêu cầu đó vẫn ở 02/06/07 và G-05–10.

## 6. Tiêu chí sẵn sàng bật gate

- C-01–05 có source commit, fixture hashes, migration/version mới, role matrix và evidence runtime;
  từng phía ký acceptance của context mình. Giữ mục blocked cụ thể nếu producer còn thiếu.
- C-06 chỉ mở theo producer acceptance; không gọi port trực tiếp hoặc trộn DTO V1/V2 để tránh lỗi.
- Kiểm migration trên fresh và persisted test DB; giữ checksum cũ; rollback không nhận write V2 bằng binary V1.
- Đọc effective configuration và artifact của cả Gateway/Clinical/Core/Billing/Support; không chỉ nhìn YAML source.
- Không coi 69 schema checks trong lượt tài liệu là 69 business tests hoặc chứng nhận sẵn sàng bảo vệ.
