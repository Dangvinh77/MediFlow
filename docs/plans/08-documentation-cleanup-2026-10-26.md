# Biên bản chuẩn hóa tài liệu trước triển khai — 26/10/2026

## Phạm vi và baseline

Người dùng đồng ý thực hiện bước dọn tài liệu trước sửa backend/frontend. Phạm vi lần này:
docs, service README/docs/AGENTS, registry bàn giao và liên kết trong Markdown.
Không đổi production code, test source, migration, SQL snapshot, runtime flags hoặc Docker.
Các thay đổi Notification UI và Docker guide có từ trước được giữ nguyên ngoài phần liên kết docs.

Baseline Git: `dc2b8f2e5c9eb3c373a6feb0b2047ca22adf8317`; có working-tree changes chưa commit,
gồm thiết kế nghiệp vụ 25/10. Ngày 26/10 là ngày chuẩn hóa, không đổi ngày của quyết định 25/10.
Đây là kiểm tra tài liệu, không phải fresh regression hoặc release acceptance.

## Nguồn đọc và vai trò tài liệu

| Nhu cầu | Nguồn chính |
|---|---|
| Scope, topology, backend ownership | [01-system-design](01-system-design.md) và root AGENTS |
| Nghiệp vụ khám/lịch/tái khám/BHYT | [02-business-flows](02-business-flows.md) |
| Integration semantics và nguồn wire | [03-contracts](03-contracts.md); live controller/DTO/serializer/fixture khóa tại WF-01 |
| Atomicity, race, recovery | [04-data-reliability](04-data-reliability.md) |
| Baseline implementation và checkpoint | [05-implementation-plan](05-implementation-plan.md), giữ đúng ngày |
| Acceptance phải chạy | [06-acceptance-runbook](06-acceptance-runbook.md), 28 ca WF chưa nghiệm thu |
| Việc cần triển khai và dependency | [07-workflow-implementation](07-outpatient-workflow-implementation.md) |
| Producer/consumer chưa bàn giao đủ | [Backend registry](../handoffs/README.md); [frontend registry](../../frontend/docs/handoffs/README.md) chỉ composition/shared FE |
| Cách chạy và giới hạn từng module | `backend/<module>/README.md`; Core thêm `docs.md`; không tạo đặc tả cạnh tranh với 02/03 |
| Evidence theo ngày | Các acceptance bundles và [history index](../handoffs/history/README.md); không dùng làm backlog |

Các file `docs/handoffs/care-finance/CONTRACT-*.md` đã là compatibility pointers sau đợt reset
trước đây; **không có contract độc lập cần di chuyển** trong nhóm này. Giữ đường dẫn cho agent/skill
ngoài repo, sửa reading gates hiện hành đi thẳng 02/03/04 và registry. Exact wire tiếp tục ở source,
không dựng thêm thư mục spec chứa một bản sao của DTO. Không khôi phục fragment heading đã mất
trong các pointer để giả rằng mô tả lịch sử còn là schema hiện hành.

## Thay đổi đã thực hiện

- Tạo registry backend gọn: H-01 là contract gap cần source lock, H-02–09 cần đối chiếu acceptance;
  không đóng chỉ vì có test counts lịch sử và không giao code lại từ báo cáo cũ.
- Chín handoff đang giữ ở thư mục chính có status/owner điều phối 26/10, tách khỏi checkpoint gốc.
- Registry FE chỉ còn handoff hiển thị tham chiếu cần xác minh; Surgery chuyển deferred.
- Đồng bộ reading gates Clinical/Lab/Billing/Notification/Gateway, các pointer Pharmacy/Report;
  làm rõ Inpatient deferred và Surgery có trong reactor nhưng không bật runtime.
- Service README có chỉ dẫn về nghiệp vụ 25/10, contracts, handoff và owner plan; giữ lệnh/config
  và kết quả kỹ thuật theo phiên bản, không biến số test cũ thành nghiệm thu mới.
- Sửa giải thích Clinical: `ARRIVED` là đã đến, không phải bằng chứng đã khám; V1 có start riêng.
- Thay các link service README tới legacy CONTRACT fragment không còn tồn tại bằng 03.

## File di chuyển và xóa

| File cũ | Đích mới hoặc xử lý |
|---|---|
| `docs/handoffs/HANDOFF-HUY-MVP.md` | [Huy checkpoints](../handoffs/history/2026-10-22-huy-mvp-checkpoints.md) |
| `docs/handoffs/HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md` | [Billing/Notification checkpoints](../handoffs/history/2026-10-18-billing-notification-checkpoints.md) |
| `docs/handoffs/HANDOFF-VINH-OUTPATIENT-MVP.md` | [Clinical/Lab checkpoints](../handoffs/history/2026-10-18-clinical-lab-checkpoints.md) |
| `docs/handoffs/BACKEND-FINAL-ASSIGNMENTS-2026-10-18.md` | [Phân công lịch sử](../handoffs/history/2026-10-18-backend-assignments.md) |
| `docs/handoffs/HANDOFF-VINH-BACKEND-TEST-FAILURES.md` | [Test harness findings](../handoffs/history/2026-10-19-test-harness-findings.md) |
| `backend/billing-service/HANDOFF-CLINICAL-LAB-FINANCIAL-CLEARANCE.md` | [Clearance checkpoint](../handoffs/history/2026-10-16-billing-clearance-checkpoints.md) |
| `backend/billing-service/HANDOFF-INPATIENT-DEPOSIT-SETTLEMENT.md` | [Deferred Billing](../handoffs/deferred/billing-inpatient-deposit-settlement.md) |
| `frontend/docs/handoffs/HANDOFF-SHARED-HUY-IDEMPOTENCY-HEADERS.md` | [Deferred Surgery consumer](../handoffs/deferred/frontend-surgery-idempotency.md) |
| Registry backend trước gom | [Bản giữ nguyên nội dung](../handoffs/history/2026-10-26-registry-before-consolidation.md) |
| `HANDOFF-NOTIFICATION-CARE-PROJECTIONS.md`, `HANDOFF-VINH-GATEWAY-CARE-ROLES.md` | Xóa hai file chỉ dẫn đã retired, inbound links chuyển trực tiếp 03 |

Tám file được di chuyển, không đánh CLOSED. Link Markdown vào/ra được đổi theo vị trí mới;
provenance và nội dung checkpoint giữ lại. Tên đường dẫn cũ trong code block/biên bản lịch sử
có thể được giữ để mô tả source tại thời điểm đó, không là lệnh yêu cầu chỉnh file đã xóa.
Hai redirect bị xóa không có acceptance riêng đang chờ; nội dung chỉ trỏ tới plan/contracts.

## Điều kiện đi tiếp

- Hoàn tất tổ chức tài liệu không có nghĩa đã hoàn thành WF-01 source lock.
- Bước tiếp theo: khóa source manifests và xác định exact API/event/authority cho schedule,
  reservation/payment race, revisit và support policy; mỗi gap có owner/fixture/test.
- Chưa cho phép frontend tự tạo fields, nới role, đổi migration hoặc gọi service port để bù thiếu API.
- H-01–09 cần đối chiếu trên final commit/artifact; không coi mọi mục đều là bug production còn tồn tại.
- Sau source lock mới triển khai theo dependency WF; giữ ownership backend/frontend và các
  ủy quyền task cụ thể, không tự mở quyền chỉ vì đã sửa README của module khác.

## Kiểm chứng phù hợp cho lượt tài liệu

Kiểm tra liên kết vào/ra sau move, file cũ không còn link hoạt động, new headings/status thống nhất,
UTF-8, diff whitespace và Mermaid source/render đã có. Phân biệt lỗi link tồn tại từ trước với
lỗi do đợt dọn này. Không chạy suite ứng dụng chỉ để kiểm chứng các thao tác Markdown.


Kết quả kiểm tra ngày 26/10/2026:

- Quét 93 file Markdown thay đổi hoặc mới: 2.717 liên kết nội bộ tới file/thư mục đều có đích tồn tại
  (bỏ qua ví dụ trong fenced code blocks; phép kiểm này không xác minh mọi fragment anchor).
- Registry chính còn đúng 9 handoff; các checkpoint/deferred có chỉ mục riêng.
- Các entry point registry, biên bản này và hướng dẫn mobile đọc được UTF-8, không có ký tự thay thế.
- `git diff --check` đạt; hai sơ đồ Mermaid và hai bản SVG từ lượt thiết kế vẫn được giữ.
- Không chạy lại suite backend/frontend, không khởi động lại Docker và không xác nhận nghiệm thu
  nghiệp vụ mới trong lượt chỉ chuẩn hóa Markdown này.
