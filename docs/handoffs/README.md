# Registry bàn giao backend MVP

## Docker recheck — 20/10/2026

Audit xác nhận **5 nhóm lỗi**: JWT fallback Billing/Notification và Lab outbox starvation thuộc
Vinh; Patient event durability, department-head title và department/staff concurrency thuộc
Hoàng Anh. Race HA-AUDIT-03 đã có PostgreSQL proof, không còn chỉ là nghi vấn static.

- [Core owner handoff, 3 lỗi và tiêu chí nghiệm thu](HANDOFF-HA-BACKEND-AUDIT-2026-10-20.md).
- [Evidence và trạng thái kiểm thử lượt này](../acceptance/backend-audit-2026-10-20/README.md).

Cập nhật owner 21/10: ba lỗi Core đã có implementation trên `codex/core-audit-reliability`;
full Core local **205 tests xanh, không skip**, gồm regression PostgreSQL/RabbitMQ.
[Quy định chức danh/trưởng khoa và outbox](../../backend/core-service/docs.md).
Handoff còn mở để Vinh nghiệm thu Gateway/Notification/packaged trên commit cuối,
đặc biệt consumer dedupe khi replay welcome event.

Các số test lịch sử bên dưới không thay kết quả trên revision mới. Không kết luận hết lỗi chỉ
từ suite mặc định hoặc đóng các packaged profile chưa chạy.

## Audit API/nghiệp vụ bổ sung — 19/10/2026

PR #439 đã merge; audit tiếp theo khóa base `05c7690c3a37b602e336fac4902ced7f3a1f7d89`.
Các ghi nhận recheck bên dưới là lịch sử tại source lock của chúng. Không dùng tên tag hoặc
suite mặc định xanh để kết luận những regression mới sau đây đã đóng.

| Owner | Việc hiện hành | Evidence / handoff |
|---|---|---|
| Vinh | Đã sửa Billing credential boundary, Lab exact clearance khi ghi kết quả, Notification mandatory JWT exp | [Audit evidence](../acceptance/backend-api-audit-2026-10-19/README.md): 5.734 default tests xanh và 1 packaged runtime acceptance xanh; global closeout vẫn NO-GO |
| Hoàng Anh | Đã tái hiện thiếu expiration và Pharmacy service gọi được credential verifier; department/staff race còn cần PG proof | [JWT/Core audit](HANDOFF-HA-JWT-EXPIRATION-AND-CORE-AUDIT.md) |
| Vinh | Notification V1 đã có fix raw-byte identity và archive backfill; chờ final runtime acceptance trước đóng handoff | [Notification byte identity](HANDOFF-VINH-NOTIFICATION-V1-BYTE-IDENTITY.md), [evidence mới](../acceptance/notification-v1-byte-identity-2026-10-19/README.md) |
| Huy | FE Pharmacy dùng boolean receipt legacy để chặn command xuất thuốc V1 | [Pharmacy V1 UI](HANDOFF-HUY-PHARMACY-V1-DISPENSE-UI.md) |
| Huy | Pharmacy và route Report outpatient đều đã tái hiện thiếu mandatory exp; cần đồng bộ boundary | [Pharmacy/Report JWT](HANDOFF-HUY-PHARMACY-REPORT-JWT-EXPIRATION.md) |

Backend chưa đạt trạng thái đóng sạch khi các mục xác nhận còn mở. Production fixes ngoài
scope Vinh vẫn giao đúng owner; Vinh kiểm thử chung và review evidence trước đóng handoff.

## Lịch sử recheck trước audit bổ sung

Refresh **2026-10-19**, source remote master `4c19dd4a`, gồm Gateway fix PR #431
(`2805b07c`). Bản sửa test và tài liệu trong lượt recheck vẫn là local diff cho đến khi
được commit/merge; không nhầm với nguồn remote đã đóng mọi acceptance.
Theo phân công người dùng: phần cần code giao đúng dev; **Vinh kiểm thử toàn bộ backend**,
gồm module regression, phân quyền, integration và recovery. Phân công test không tự mở
quyền sửa production của dev khác.

## Sau kiểm thử thực tế

[Recheck 19/10](../acceptance/backend-recheck-2026-10-19/README.md) xác nhận Gateway
explicit refund acceptance **11/11 pass**, full Gateway **1.449/1.449 pass**, không skips.
Số liệu 4/11 đỏ ở [bundle 18/10](../acceptance/backend-2026-10-18/README.md) là lịch sử
trước fix, không phải kết quả trên master mới. Hai lỗi test Report/Pharmacy vẫn có trong
remote master và tag đóng backend; sửa local chưa commit không làm nguồn chung tự thay đổi.
[V-TEST-BUG-01/02](HANDOFF-VINH-BACKEND-TEST-FAILURES.md) do Vinh sửa test và retest;
không giao production fix cho Huy chỉ từ lỗi harness. Rerun/skip ghi riêng trong bundle mới.

## Code Gateway và phần xác minh còn lại

| Dev | Task đã xác nhận còn mở | Handoff |
|---|---|---|
| Hoàng Anh | HA-CODE-01/02 đã sửa production tại `2805b07c`; không còn yêu cầu viết lại hai route. Policy retest và Gateway suite đã pass; phần acceptance runtime rộng hơn ghi riêng, không suy từ 11 filter cases | [Gateway Billing refunds](HANDOFF-GATEWAY-BILLING-ADJUSTMENT-ROUTES.md) |

Billing controllers đã có, flags đang bật trong default Compose; không giao lại Billing code cho Lộc.
Trong source/handoffs đã rà soát, chưa xác nhận gap code MVP cụ thể khác ở Core/Clinical/Lab/
Pharmacy/Billing/Notification/Report/Support. Đây không là kết luận toàn backend đã pass;
Vinh bổ sung bug/code task cho đúng owner nếu kiểm thử phát hiện lỗi.

## Toàn bộ kiểm thử — Vinh

- [Queue Vinh V-TEST-01–06](HANDOFF-VINH-BACKEND-VERIFICATION.md): full module suites,
  packaged/distributed profiles, refund routes, tích hợp và recovery trên final revision chung.
- [Vinh Clinical/Lab closeout](HANDOFF-VINH-OUTPATIENT-MVP.md) và
  [Billing/Notification closeout](HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md):
  PR #427 đã hoàn tất owned implementation, publication/replay/timestamp, paid EXAM/Lab
  và crash recovery. Không giao lại LOC-01–06 từ checklist lịch sử.
- [Huy Pharmacy/Report/Support](HANDOFF-HUY-MVP.md): 48/48 cho môi trường local đã quan sát,
  actual sáu-app EXAM/Rx và persisted-volume rehearsal đã có ngày 17/10. Không giao lại code;
  fresh regression/integration trên final revision chuyển Vinh.

[Plan MVP](../plans/05-implementation-plan.md), [contracts](../plans/03-contracts.md),
[reliability](../plans/04-data-reliability.md) và [runbook](../plans/06-acceptance-runbook.md)
là canonical. Historical checklists không thay current source/evidence. Deployment activation
và global release cần evidence của chính môi trường đích; không tự bật flags từ phân công này.
Inpatient/Surgery/full V2 deferred. Đóng/xóa handoff code khi cả implementation và nghiệm thu
đạt, chuyển lasting rules vào canonical docs.
