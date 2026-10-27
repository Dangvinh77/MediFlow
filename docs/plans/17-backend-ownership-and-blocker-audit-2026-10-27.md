# Phân công và đánh giá blocker — 27/10/2026

Baseline source: `5e6f29f0`, merge PR [#547](https://github.com/Dangvinh77/MediFlow/pull/547).
Đợt này rà hợp đồng/source và chạy targeted backend tests; không chạy lại toàn bộ demo.

## Kết luận về phần việc Vinh

Phần kỹ thuật của [plan 15](15-demo-readiness-closeout-2026-10-27.md) đã hoàn tất và merge.
Không đồng nghĩa toàn bộ backend/MVP, WF-11 hoặc H-01–11 đã đạt acceptance. Không dùng số
checkbox lịch sử hoặc tổng test để suy tỷ lệ hoàn thành. Source đã có không giao viết lại.

Theo phân công trực tiếp mới: Vinh xử lý toàn bộ backend, kể cả Core/Gateway/Pharmacy/Report/
Support. Huy và Hoàng Anh không còn là dependency về quyền sửa backend. Data authority giữa
contexts không đổi; shared root/CI vẫn cần đúng phạm vi được giao. Inpatient/Surgery hoãn.

Frontend chia theo service ở [ownership](../ai/15-frontend-ownership.md):

| Người | Feature/route phụ trách | Lượt thử trực tiếp |
|---|---|---|
| Vinh | appointment, medical-record, lab, billing, notification; shared/auth | Tiếp nhận/lịch, khám/Lab, thu/hoàn, thông báo và backend toàn luồng |
| Huy | pharmacy, report | Danh mục/đơn/cấp thuốc và Report; kiểm UX bằng nguồn backend thật |
| Hoàng Anh | organization, patient | Tổ chức/phòng/scope, hồ sơ/account, lần đăng nhập đầu và UX tiếp nhận |

Billing/Notification trước đây của Lộc do Vinh quản lý. Đổi owner backend không tự cấp quyền
Vinh sửa production frontend Huy/Hoàng Anh. Vai trò thao tác trong runbook 16 không là ownership.

## Dependency thực sự còn lại

| Việc | Phân loại | Ai giải quyết / điều kiện đóng |
|---|---|---|
| H-01 directory/care-labels | Contract đã code; chưa đủ consumer acceptance | Vinh backend/shared xác minh quyền; Huy/Hoàng Anh kiểm consumer FE và đổi phiên. Không chờ Hoàng Anh xây endpoint mới |
| H-02/03/04/05 Core outbox/JWT/refund routes | Thiếu đối chiếu final artifact/race/runtime, chưa xác nhận bug hiện tại | Vinh chạy đúng nhóm acceptance; targeted JWT/route tests đợt này không đóng toàn handoff |
| H-06 Pharmacy UI V1 | Frontend regression/UX còn cần đối chiếu | Huy kiểm expired/revoked/unavailable/V0 trên candidate; backend lỗi do Vinh sửa |
| H-07 Notification byte identity | Cần final packaged/upgrade evidence | Vinh; ba result fact replay đã có evidence riêng, không thay toàn bộ upgrade/raw-NULL policy |
| H-08 Report coverage | **Chặn công bố Report cho dataset chưa duyệt**; không xác nhận thiếu API | Vinh đối soát năm nguồn và approve ngày đã kết thúc; Huy kiểm UI sau approval. Không zero-fill hoặc mở gate để giả đạt |
| H-09/10 WF/SC | Thiếu ma trận runtime đầy đủ trên cùng candidate/flags | Vinh backend; từng owner FE thử UI. SC-04/06/07/11–16 cần DB/recovery thật; không thay bằng schema tests |
| H-11 onboarding | Cần lượt UX chung, quay lại ngày khác và xác minh danh tính recovery | Vinh sửa backend nếu phát hiện lỗi; ba người thực hiện runbook 16. Không suy recovery identity thực tế từ synthetic API tests |
| FE-NOTIFICATION-03 resend/read-state | **DEFERRED / không chặn MVP hiện hành** | 02/06 chỉ yêu cầu nhận/đọc IN_APP; chưa có hai command và không đánh DONE. Thiết kế riêng nếu mở rộng |

Chưa tái hiện được blocker code backend mới trong lát kiểm tra này. Không kết luận toàn bộ
backend không lỗi từ targeted tests. Hiện Huy có thể kiểm Pharmacy và Report error/coverage UX;
Report có số liệu của dataset mới vẫn phụ thuộc Vinh chuẩn bị/đối soát nguồn. Hoàng Anh có thể
tiếp tục Patient/Organization với contract hiện có, chưa xác định API thiếu chặn hai feature.

## Report cần làm theo đúng nguồn

API preview/approval đã có, không xây lại. Theo [operator workflow](../../backend/report-service/operations/outpatient-coverage-acceptance.md):

1. Chọn một ngày đã kết thúc, đúng timezone/currency/scope của stack; dữ liệu hôm nay chưa
   đủ điều kiện duyệt trước khi kết thúc ngày. Lượt khám đầy đủ chạy trước 17:00 theo runner.
2. Lưu bằng chứng xuất/đối soát thật từ CLINICAL, LAB, PHARMACY, BILLING_CASH, BILLING_REFUND,
   gồm nguồn rỗng có xác nhận, SHA-256/cursor và thời điểm quan sát. Không lấy preview làm
   bằng chứng nguồn, không đọc chéo DB hoặc ký completeness khi chưa kiểm.
3. ADMIN preview, đối soát pending/invalid và từng source witness rồi approve bằng requestId
   ổn định. MANAGER không có quyền approve. Không sửa giờ máy hoặc backdate để lách ngày.
4. ADMIN/MANAGER đọc daily qua Gateway, kiểm thu/hoàn/support/zero và late facts; Huy kiểm UI.
   Daily coverage không cấp quyền cho monthly cả năm. Gate và dataset khác vẫn phải nghiệm thu riêng.

## Kiểm chứng thực hiện trong lượt này

Targeted Maven reactor trên source baseline: **1.407 tests**, 0 failures/errors/skipped;
exit 0. [Danh sách suite, counts và hashes XML](../acceptance/backend-ownership-2026-10-27/verification.json).

| Module | Tests | Nội dung giới hạn |
|---|---:|---|
| Core | 44 | Care labels, directory roles, account provisioning service, JWT |
| Gateway | 1.227 | Route-role contract và scheduling authorization |
| Pharmacy | 11 | JWT filter; không là stock/dispense E2E |
| Report | 107 | JWT, approval service/controller, real PostgreSQL concurrency/rollback, signed HTTP + PG |
| Notification | 18 | JWT và controller roles/own-patient reads |

Report PG/HTTP dùng evidence synthetic trong database disposable, không duyệt dataset demo.
Testcontainers đã dọn container sau chạy. Không chạy lại frontend/build vì diff chỉ thay tài liệu
và ownership instructions; 1.108 frontend tests thuộc checkpoint trước, không ghi thành lần chạy mới.

Ưu tiên tiếp theo: Vinh chuẩn bị coverage/đối soát Report và hoàn tất matrix backend còn thiếu;
ba owner frontend thử [D01–12](16-three-developer-demo-acceptance-2026-10-27.md) trên cùng candidate.
Chỉ đóng handoff sau đủ tiêu chí; chưa có chữ ký nghiệm thu chung hoặc GO demo.
