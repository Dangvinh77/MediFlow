# 16 — Care & Finance Integration Contracts

Tài liệu này quy định cách các service triển khai thiết kế đã chốt trong
[`mediflow-care-finance-redesign.html`](../architecture/mediflow-care-finance-redesign.html) mà không
làm producer và consumer lệch contract. Nó áp dụng cho cả service hiện có và hai bounded context sẽ
bổ sung là Inpatient và Surgery.

## Thứ tự nguồn sự thật

Khi tài liệu mâu thuẫn, dùng thứ tự sau:

1. HTML kiến trúc care-finance quyết định **luồng nghiệp vụ và ranh giới service**.
2. Handoff trong [`docs/handoffs/care-finance/`](../handoffs/care-finance/README.md) quyết định
   **wire contract giữa producer và consumer**.
3. `docs/ai/services/<service>.md` quyết định **trách nhiệm và invariant cục bộ của service**.
4. `docs/ai/` còn lại quyết định **cách triển khai, bảo mật, persistence và testing**.
5. Code/fixture hiện tại cho biết **trạng thái đã triển khai**, không tự động thay thế thiết kế đích.

Nếu code chưa khớp tài liệu, ghi rõ `CURRENT`, `TARGET` và migration path. Không sửa tài liệu đích để
hợp thức hóa một implementation tạm thời.

## Contract gate bắt buộc

Mọi thay đổi REST/event được service khác dùng phải đi qua gate sau:

1. Xác định contract ID và producer owner.
2. Cập nhật handoff canonical trước hoặc trong cùng PR với code producer.
3. Cập nhật producer DTO/schema, serializer, fixture và contract test.
4. Cập nhật fixture/deserializer/contract test của mọi consumer trong cùng PR; nếu ownership không
   cho phép, ghi consumer là `BLOCKED` và tạo handoff có acceptance criteria cụ thể.
5. Chỉ merge breaking change sau khi có version mới và compatibility path.
6. Không đánh dấu `IMPLEMENTED` cho tới khi cả producer lẫn consumer có test trên cùng fixture.

Không được giải quyết thiếu contract bằng cách query database service khác, suy luận ID từ
`patientId`, dùng bản ghi gần nhất, hard-code giá, hoặc import Java event class xuyên module.

## Envelope và versioning

Mọi domain event có:

```json
{
  "eventId": "uuid",
  "eventType": "financial.clearance.granted",
  "version": 1,
  "occurredAt": "2026-09-24T03:00:00Z",
  "correlationId": "uuid",
  "producer": "billing-service",
  "payload": {}
}
```

- Thêm field optional/additive được giữ cùng version nếu consumer bỏ qua field lạ.
- Đổi tên, xóa field, đổi kiểu hoặc đổi ý nghĩa phải tăng version.
- Routing key runtime hiện có thể tiếp tục dạng không có hậu tố, ví dụ
  `financial.clearance.granted`; `version` nằm trong envelope. Không tạo hai cách versioning song
  song trong cùng một rollout.
- Producer ghi aggregate và outbox trong cùng transaction. Consumer claim `eventId` và side effect
  trong cùng transaction.
- Retry chỉ dùng cho lỗi tạm thời. Payload sai schema, thiếu target reference hoặc vi phạm invariant
  phải có lỗi ổn định và đi DLQ sau bounded retry.

## Care episode và financial clearance

Mọi charge, payment request, clearance và settlement phải gắn với:

- `careEpisodeType = OUTPATIENT_VISIT`: dùng `appointmentId` cho lượt có lịch; walk-in không có lịch
  dùng `recordId`. ID được chọn khi mở Billing account và không đổi khi record xuất hiện;
- `careEpisodeType = ADMISSION`, `careEpisodeId = admissionId`.

`financial.clearance.granted` là quyền thực hiện **một mục đích cụ thể**, không phải cờ “đã trả
tiền” toàn bệnh nhân. `purpose` tối thiểu gồm `EXAM`, `LAB_TEST`, `PRESCRIPTION`,
`ADMISSION_DEPOSIT`, `SURGERY`. Event phải mang target reference tương ứng; consumer không tự tìm
target theo patient.

## Emergency override

Clinical, Lab, Inpatient và Surgery có thể tiếp tục nghiệp vụ cấp cứu khi chưa có clearance chỉ khi
command chứa người duyệt, vai trò, lý do, thời điểm và care episode. Service chuyên môn lưu audit và
phát fact; Billing ghi khoản phải thu. Không dùng `emergency=true` như một shortcut không audit.

## Ma trận contract bắt buộc

| Contract | Producer / consumer | Phạm vi |
|---|---|---|
| [`CONTRACT-CARE-BILLING-01`](../handoffs/care-finance/CONTRACT-CARE-BILLING-01.md) | Clinical, Lab, Pharmacy, Inpatient ↔ Billing | episode, charge, clearance, settlement |
| [`CONTRACT-INPATIENT-SURGERY-01`](../handoffs/care-finance/CONTRACT-INPATIENT-SURGERY-01.md) | Clinical/Inpatient ↔ Surgery/Pharmacy | admission, surgery readiness/result, inpatient medication |
| [`CONTRACT-SURGERY-BILLING-01`](../handoffs/care-finance/CONTRACT-SURGERY-BILLING-01.md) | Surgery ↔ Billing | procedure charge, clearance, cancellation/refund |
| [`CONTRACT-IDENTITY-LOOKUP-01`](../handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md) | Organization/Patient/Gateway → all | stable identities, service auth, routes |
| [`CONTRACT-CARE-PROJECTIONS-01`](../handoffs/care-finance/CONTRACT-CARE-PROJECTIONS-01.md) | domain/Billing → Notification/Report | templates, cash/liability/revenue, operational KPI |

## Trạng thái contract

- `DESIGN_READY`: field và invariant đã chốt, nhưng một hoặc nhiều module chưa tồn tại.
- `PRODUCER_READY`: producer DTO/fixture/test đã merge; consumer chưa hoàn tất.
- `CONSUMER_READY`: consumer projection/fixture/test đã merge; producer chưa phát live.
- `IMPLEMENTED`: producer và tất cả consumer bắt buộc đã chạy contract test cùng version.
- `BLOCKED`: thiếu contract hoặc dependency cụ thể; handoff phải ghi owner và acceptance criteria.
- `DEPRECATED`: có replacement/version và thời hạn migration rõ ràng.

## Definition of Done liên service

- Handoff và service docs dùng cùng event name, version, nullability và ownership.
- Producer fixture được consumer deserialize trong CI hoặc script wire-check chung.
- Có test duplicate event, out-of-order khi có thể, broker retry và DLQ cho poison payload.
- REST lookup phân biệt confirmed absence với timeout/5xx/malformed envelope.
- Correlation ID được giữ xuyên REST, outbox và event.
- Frontend/mobile chỉ gọi Gateway; Gateway route không thay đổi payload nghiệp vụ.
- Report có thể rebuild projection bằng replay; Notification không trở thành nguồn dữ liệu nghiệp vụ.
