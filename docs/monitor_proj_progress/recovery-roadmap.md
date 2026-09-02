# Lộ trình bù tiến độ

**Mục tiêu:** nộp gói hồ sơ thiết kế có thể kiểm tra vào **08/09/2026**, đồng thời không làm mất toàn bộ tuần setup backend 01–07/09.

## 1. Nguyên tắc điều hành

- Chạy hai lane song song: **A — đóng hồ sơ tháng 8**, **B — giữ nhịp backend tháng 9**.
- Mỗi đầu ra có đúng một owner, reviewer khác owner và bằng chứng nằm trong Git.
- Không dùng “đã viết code” thay cho “đã duyệt tài liệu”, và không dùng “đã có spec” thay cho “đã chạy”.
- Mỗi ngày 17:00 cập nhật checklist; blocker quá 4 giờ phải báo Leader.

## 2. Kế hoạch phục hồi 02/09–08/09

| Ngày | Việc bắt buộc | Owner | Reviewer | Đầu ra/Gate |
|---:|---|---|---|---|
| 02/09 | Gửi gói hiện trạng này; freeze danh sách gap; chốt roster 3 hay 4 người | Phạm Đăng Vinh | Cả nhóm | Folder `monitor_proj_progress`; biên bản nhận việc |
| 03/09 | Review 38 Use Case, 9 role; chốt scope/out-of-scope | Vinh | Hoàng Anh + Huy | SRS v0.95, không còn actor mâu thuẫn |
| 03/09 | Chốt NFR có số: p95/p99, concurrency, uptime, RTO/RPO, retention, WCAG | Lê Quang Huy | Vinh | NFR có cách đo và ngưỡng đạt |
| 04/09 | Chốt GUI Standards; wireframe đủ Web/Mobile cho màn hình chính | Trần Hoàng Anh | Nguyễn Hoàng Phúc | PDF/PNG hoặc source thiết kế + mapping Use Case |
| 05/09 | Tách DDL từ spec thành Flyway migration cho 7 service còn thiếu | Huy | Vinh | 8/8 DB migrate sạch, `ddl-auto=validate` |
| 05/09 | Chốt số bảng, naming và FK/UUID xuyên service; cập nhật ERD | Huy | Phúc | ERD và DDL có cùng inventory |
| 06/09 | Chốt 18+ event, routing key, payload, queue/binding, DLQ và saga compensation | Vinh | Huy | Event contract v1 + sơ đồ 06/03 cập nhật |
| 06/09 | Đóng gói System Architecture + Business Flow | Vinh | Hoàng Anh | `giai-doan-2-design.md` draft complete |
| 07/09 | Rà soát SRS ↔ GUI ↔ API ↔ DDL ↔ event; sửa mọi mismatch P0/P1 | Nguyễn Hoàng Phúc | Cả nhóm | Traceability matrix, không còn mismatch P0/P1 |
| 07/09 | Sửa quality gate đang đỏ và chạy build tối thiểu | Vinh + chủ module | Huy | Maven test, frontend typecheck/lint/build xanh |
| 08/09 | Final review, version 1.0, export và nộp giáo viên | Vinh | 3 thành viên ký review | Manifest đủ, link/ảnh mở được, nộp có timestamp |

## 3. Lane Backend để không trễ dây chuyền tháng 9

| Tuần | Phạm vi điều chỉnh | Deliverable |
|---|---|---|
| 02–07/09 | Infra + common + sửa gateway/pharmacy quality gate; migration 8 service | Skeleton thật sự chạy, database migrate sạch, build xanh |
| 08–14/09 | organization + patient hoàn chỉnh; bắt đầu clinical contract | Domain/application/JPA/API/events/tests cho 2 service |
| 15–21/09 | clinical + lab; hoàn thiện pharmacy | Luồng khám–XN–kê đơn chạy qua gateway |
| 22–28/09 | billing saga + notification + report | Happy path + compensation + read model |
| 29–30/09 | Integration, security, event idempotency, fix | Backend stable v1.0 hoặc báo scope cắt có phê duyệt |

## 4. Ưu tiên xử lý

### P0 — chặn nộp

- Không có SRS/design/final-review/phan-cong theo tên trong kế hoạch.
- Không có GUI Standards/wireframe artifact.
- Mâu thuẫn actor, roster, số bảng, naming và event.
- Maven test và frontend lint đang fail.

### P1 — rủi ro triển khai tháng 9

- 7/8 migration còn thiếu.
- 0 business controller, publisher, listener, `@PreAuthorize`.
- Patient source vắng mặt dù README tuyên bố đã có code.
- Mobile chưa phải Flutter project chạy được.

### P2 — cần hoàn tất trước demo

- Gateway auth stub, chưa rate limit, correlation propagation chưa hoàn chỉnh.
- Chưa có measurable SLO, RTO/RPO, log retention, accessibility target.
- Chưa có integration test toàn saga và DLQ.

## 5. Cơ chế chống trễ lặp lại

1. Mỗi task có owner, reviewer, deadline giờ-phút và đường dẫn output.
2. Pull request chỉ đóng khi checklist artifact + test pass.
3. Bảng tiến độ dùng ba trạng thái bằng chứng: `SPECIFIED`, `IMPLEMENTED`, `VERIFIED`.
4. Daily checkpoint 15 phút; không báo phần trăm cảm tính, chỉ báo artifact/gate.
5. Trước mỗi mốc nộp 48 giờ: freeze scope; trước 24 giờ: dry-run gói nộp.

## 6. Điều kiện tuyên bố đã bù tiến độ

- Giáo viên nhận được gói Design v1.0 trước hoặc trong 08/09.
- SRS, GUI, ERD/DDL, event contract, architecture và business flow có version + owner + reviewer.
- Traceability không còn mismatch P0/P1.
- Link nội bộ và hình ảnh mở được trên máy sạch.
- Build/test tối thiểu xanh hoặc mọi exception được ghi rõ, có owner và ngày xử lý.
