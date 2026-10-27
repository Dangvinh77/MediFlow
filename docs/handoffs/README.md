# Registry bàn giao hiện hành — 26/10/2026

Đọc [nghiệp vụ](../plans/02-business-flows.md), [contracts](../plans/03-contracts.md) và
[kế hoạch WF-01–11](../plans/07-outpatient-workflow-implementation.md) trước khi giao việc.
Registry này thay các bảng phân công theo checkpoint cũ; không là bằng chứng vừa chạy test.
Baseline tài liệu: `dc2b8f2e5c9eb3c373a6feb0b2047ca22adf8317` cùng working-tree docs chưa commit.

Quyết định bổ sung ngày 27/10/2026: NURSE tiếp nhận/cấp tài khoản bệnh nhân mới và việc dùng
web tùy chọn theo [nghiệp vụ mục 4.1](../plans/02-business-flows.md); bàn giao mới ở H-11.
Baseline của H-11 là `f28d25b4`, không thay mốc lịch sử của các handoff khác.

## Quy ước trạng thái

- `ACTIVE CONTRACT GAP`: đã xác định yêu cầu liên owner còn cần source lock/bàn giao.
- `NEEDS REVALIDATION`: có implementation hoặc evidence lịch sử; phải đối chiếu revision và
  acceptance còn thiếu. Không giao viết lại code chỉ từ nhãn OPEN cũ, cũng không tự đóng.
- `DEFERRED`: ngoài MVP ngoại trú; không là dependency phải làm trước WF-01–11.
- `HISTORICAL`: checkpoint/evidence giữ để tra cứu, không là danh sách việc hiện tại.

Không dùng tổng số handoff hoặc test counts cũ làm tỷ lệ hoàn thành. Lượt chuẩn hóa không chạy
regression backend, không xác nhận bug còn tồn tại trên binary đang chạy và không tự bật gate.

## Bàn giao còn cần xử lý hoặc xác minh

| ID | Trạng thái | Producer/code owner → người nhận | Việc cụ thể còn phải xác minh | Hồ sơ/acceptance |
|---|---|---|---|---|
| H-11 | NEEDS REVALIDATION | Hoàng Anh/Core/Gateway, Vinh/Clinical/Lab/Notification → consumer FE theo owner + Vinh verification | Đã triển khai command NURSE cấp PATIENT, outcome recovery, own results và in-app trước login; còn nghiệm thu ONB-A01–12 qua Gateway/UI | [Nurse onboarding](HANDOFF-NURSE-PATIENT-ONBOARDING-2026-10-27.md); [ONB plan](../plans/07-outpatient-workflow-implementation.md); [ONB acceptance](../plans/06-acceptance-runbook.md) |
| H-01 | NEEDS REVALIDATION | Hoàng Anh/Core/Gateway → các consumer FE theo owner | Contract A.13 đã triển khai cho CASHIER/LAB_TECH directory và PATIENT care-labels; đối chiếu consumer/session reset web/mobile trước khi đóng | [Reference directory](HANDOFF-HA-FRONTEND-REFERENCE-DIRECTORY-2026-10-22.md); [Core README](../../backend/core-service/README.md) |
| H-02 | NEEDS REVALIDATION | Hoàng Anh/Core → Vinh verification | Patient outbox, department-head/race: implementation có checkpoint 21/10; kiểm consumer/replay và packaged trên artifact cuối | [Core audit](HANDOFF-HA-BACKEND-AUDIT-2026-10-20.md) |
| H-03 | NEEDS REVALIDATION | Hoàng Anh/Core/Gateway → Vinh verification | JWT expiration và caller credential verifier đã có bản sửa; đối chiếu merged revision, test và runtime; race chuyển H-02, không mở task thứ hai | [JWT/Core](HANDOFF-HA-JWT-EXPIRATION-AND-CORE-AUDIT.md) |
| H-04 | NEEDS REVALIDATION | Hoàng Anh/Gateway + Vinh/Billing → Vinh verification | Hai refund routes đã có code và policy proof; còn xác nhận đúng phạm vi HTTP/Billing/refund race, không giao viết lại routes | [Refund routes](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) |
| H-05 | NEEDS REVALIDATION | Huy/Pharmacy/Report → Vinh verification | Mandatory exp có source fix và PG/Rabbit suite checkpoint; đối chiếu final artifact/Gateway/Support, không yêu cầu sửa parser lại từ báo cáo lỗi cũ | [JWT Pharmacy/Report](HANDOFF-HUY-PHARMACY-REPORT-JWT-EXPIRATION.md) |
| H-06 | NEEDS REVALIDATION | Huy/FE Pharmacy → Vinh review + Huy consumer | V1 dispense có implementation/browser proof; xác định còn thiếu expired/revoked/unavailable/V0 cases nào trên final candidate | [Dispense UI](HANDOFF-HUY-PHARMACY-V1-DISPENSE-UI.md) |
| H-07 | NEEDS REVALIDATION | Vinh/Notification → Vinh verification | Raw-byte identity có implementation/upgrade runbook; final packaged acceptance và historical raw-NULL policy cần đối chiếu evidence | [Byte identity](HANDOFF-VINH-NOTIFICATION-V1-BYTE-IDENTITY.md) |
| H-08 | NEEDS REVALIDATION | Vinh/Lab/Billing, Huy/Report, Hoàng Anh/Core → từng consumer | Review3 ghi lỗi theo môi trường 22/10; Lab paid fresh flow đã có proof 25/10, Report snapshot closed-day smoke không thay current coverage, reference permissions theo H-01 | [Runtime observations](HANDOFF-REVIEW3-RUNTIME-2026-10-22.md); [Lab proof](../../scripts/demo_25_10/FOLLOWUP-VERIFICATION.md) |
| H-09 | NEEDS REVALIDATION | Tất cả owner → Vinh verification | Khóa final revision/flags, gom evidence, chạy acceptance còn thiếu; queue 18/10 không phải kết quả mới | [Verification queue](HANDOFF-VINH-BACKEND-VERIFICATION.md); [runbook](../plans/06-acceptance-runbook.md) |
| H-10 | NEEDS REVALIDATION | Hoàng Anh/Core/Gateway, Vinh/Clinical/Billing/Lab/Notification, Huy/Pharmacy/Report → từng consumer | Đã có source revision 2 trên nhánh PR: lịch/giữ suất, quote/zero, tái khám, consumer và UI; còn acceptance packaged/API/UI và Report coverage trên commit cuối | [WF-01 cross-owner contracts](HANDOFF-WF01-OUTPATIENT-CONTRACT-GAPS-2026-10-26.md); [source audit](../plans/09-outpatient-source-lock-2026-10-26.md); [SC acceptance](../plans/10-scheduling-contract-acceptance.md) |

## Tách việc mới khỏi việc kiểm chứng baseline

H-11 checkpoint 27/10: [87 checks Gateway và UI kết quả](../acceptance/onboarding-2026-10-27/README.md)
đã đạt theo phạm vi ghi trong biên bản. [Checkpoint tiếp theo: 107 + 99 observations](../acceptance/onboarding-recovery-2026-10-27/README.md)
đã kiểm hai NURSE concurrent, mất phản hồi sau commit/retry, tiếp nhận lần hai cùng ngày trước
login, API pending results và đọc Lab trong lượt mở. Vẫn NEEDS REVALIDATION cho UI phục hồi/
unavailable, broker replay, quay lại ngày khác và recovery xác minh danh tính.

WF-01–11 là backlog nghiệp vụ mới: schedule, reception, revisit, hỗ trợ BHYT và UI/Report.
Chúng không được coi đã hoàn thành bởi acceptance baseline 17–25/10. Source lock trước khi
sửa; nếu cần producer khác đổi field/permission thì mở handoff với exact fixture và consumer.
Không tạo một bản sao toàn bộ WF trong mỗi service.

Backend owners hiện hành: Vinh/Clinical/Lab/Billing/Notification; Huy/Pharmacy/Report;
Hoàng Anh/Core/Gateway. Support là composition, không gộp ownership. Frontend giữ riêng
[ma trận ownership](../ai/15-frontend-ownership.md); backend takeover không đổi FE ownership.
User delegation trong từng task vẫn có hiệu lực đúng phạm vi đã giao.

## Lịch sử, deferred và đường dẫn tương thích

- [Các checkpoint lịch sử](history/README.md): code/test evidence, không giao lại việc theo owner cũ.
- [Phần hoãn](deferred/README.md): Inpatient deposit/settlement và Surgery consumer, giữ gates OFF.
- [Frontend handoffs](../../frontend/docs/handoffs/README.md): chỉ composition/shared/consumer FE;
  endpoint/DTO/roles/events bị thiếu đăng ký ở registry backend này.
- [Care-finance compatibility pointers](care-finance/README.md): các file CONTRACT cũ chỉ dẫn
  về 02/03/01; không chứa schema độc lập. Giữ cho external agent/skill links, không tạo contract mới ở đây.
- [Biên bản chuẩn hóa](../plans/08-documentation-cleanup-2026-10-26.md): inventory và điều kiện bước tiếp theo.

## Lifecycle bắt buộc

Mỗi handoff mới ghi: status, ngày, source commit/flags, producer/consumer owner, exact paths,
thiếu field/behavior/permission nào, request/response hoặc event fixture, tiêu chí test,
compatibility và resume owner. Không suy quyền sửa module từ handoff.

Sau khi cả hai phía đạt acceptance: chuyển lasting rules vào 02/03/04 hoặc service runbook;
ghi evidence tương ứng, xóa handoff và registry entry cùng thay đổi. Evidence gốc được giữ.
Nếu chỉ có implementation và thiếu acceptance thì giữ NEEDS REVALIDATION; không xóa để làm sạch.

Checkpoint bổ sung H-11: [broker replay, browser recovery và frontend](../acceptance/demo-readiness-2026-10-27/README.md)
đã có bằng chứng theo phạm vi; [runbook ba developer](../plans/16-three-developer-demo-acceptance-2026-10-27.md)
gom phần thử trực tiếp còn lại. Các mô tả UI recovery/broker chưa kiểm ở checkpoint trước
là lịch sử và đã được bổ sung bằng chứng; trạng thái NEEDS REVALIDATION vẫn giữ cho ký
ma trận đầy đủ, quay lại ngày khác và quy trình xác minh danh tính recovery.
