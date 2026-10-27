# Tiếp tục ONB-06 và dọn checklist — 27/10/2026

Baseline: `b67bea03`. Thực hiện kế hoạch ONB đã được giao tại [07](07-outpatient-workflow-implementation.md),
theo nghiệp vụ [02](02-business-flows.md) và wire [03](03-contracts.md).

## Phạm vi thực hiện

1. Build các packaged JAR từ baseline mới; khởi động runner `rehearse_snapshot.py --scheduling --keep`
   với PostgreSQL/RabbitMQ và tám context DB demo riêng. Không dùng dữ liệu hiện có.
2. Bổ sung chế độ `--onboarding` cho `accept_scheduling.py`: NURSE tìm/tạo patient, cấp account
   qua command riêng, retry cùng key, duplicate/conflict và forbidden input. Không login patient
   cho tới khi đã hoàn tất khám/Lab/lời hẹn. Trước đổi mật khẩu kiểm trực tiếp Gateway,
   sau đổi kiểm credential cũ, quyền own results và notification history.
3. Tái khám dùng lại patient/account, liên kết registration nguồn; kiểm không có yêu cầu cấp lại
   account hoặc đổi mật khẩu do có lượt mới. Đọc kết quả không tạo một EXAM mới.
4. Chạy runner trên stack thật, sửa lỗi quan sát được với regression test trước thay production.
   Kiểm UI trên trình duyệt nếu stack/frontend sẵn sàng. Evidence ghi rõ phần API và UI.
5. Rút checklist source ONB-01–05 đã triển khai thành bảng trạng thái có dẫn chứng; giữ mục
   acceptance còn mở, không xóa handoff chỉ vì code đã có. Cập nhật chỉ mục tài liệu lỗi thời.

## Tiêu chí kết thúc

- Runner không lưu password/token hoặc request body chứa credential trong evidence.
- Ghi đúng status/error.code, candidate SHA, operation IDs và giới hạn các ca đã chạy.
- Không sửa gates mặc định, migrations đã phát hành, Surgery/Inpatient hay dữ liệu dự án.
- H-11 chỉ đóng khi đủ ONB-A01–12; API smoke không tự đóng toàn bộ UI, recovery/race hoặc WF-11.

## Tiến độ

- [x] Build candidate baseline, exit 0.
- [x] Bổ sung và chạy acceptance onboarding qua Gateway: 87 checks đạt; 20 checks bệnh nhân cũ đạt.
- [x] Kiểm UI và hồi quy phần có thay đổi: Billing 1.178, Clinical 438, frontend 1.097 tests đạt.
- [x] Cập nhật trạng thái/checklist và tổng hợp [bằng chứng](../acceptance/onboarding-2026-10-27/README.md).

## Nhật ký quyết định

- Ruling: sửa lỗi Billing phát hiện trong packaged rehearsal trước khi đóng acceptance.
  SQL authority đã nhận scheduled EXAM nhưng domain chỉ đối chiếu appointmentId V1;
  registrationId đang được producer khóa trong recordId/careEpisodeId. Giữ nguyên mọi
  ledger/quote/lease guard và wire, chỉ nhận nguồn scheduled khi OUTPATIENT_VISIT khớp
  đúng registration; không suy một medical record đã tồn tại. Nếu sai, bước bắt đầu khám
  sẽ bị từ chối; regression dùng cả positive và zero-liability trên PG thật.
- Regression domain đã chạy RED (2/3 fail) trước sửa, GREEN 3/3 sau sửa.
- Ruling: scheduled clearance không dùng quote expiry sau khi đã thu/zero-liability.
  02/03 quy định suất CONFIRMED tồn tại đến buổi đã chọn; dùng expiry HELD khiến lượt
  đã thu không bắt đầu được sau 15 phút hoặc ngày hôm sau. Hai integration cases positive/
  zero đã RED sau TTL trước sửa. Chỉ đổi grant mới, không rewrite receipt lịch sử;
  sai quyết định này có thể cho phép bắt đầu muộn, nên Clinical session/arrival và Billing
  ledger/refund/revocation/source guards vẫn phải kiểm tại thời điểm bắt đầu.
- Commit source Billing `981f8c2e`: domain 3/3 và integration quote 4/4 đạt, gồm hai
  cases paid/zero quan sát sau TTL và replay execution cùng receipt; package exit 0.
  Lượt đầu full Billing có 4 lỗi setup do test mới chưa bật clearance lookup; đã sửa
  cấu hình test, chạy lại toàn suite đạt 1.178 tests, exit 0. Không xóa hoặc bỏ qua ca lỗi.

- Ruling: Clinical nhận cả care contract 1 và 2 cho diagnosis/Lab order khi cùng lượt
  đang IN_EXAM. Guard cũ chỉ nhận 1 làm luồng scheduled mới bị chặn sau khi đã thanh toán.
  Hai regression V2 đã RED trước sửa, targeted suite 25/25 GREEN sau sửa; bổ sung chặn
  version 0 (domain đã chặn version ngoài 0–2). Giữ exact patient/doctor/department, OPEN record và assigned-doctor checks.
  Nếu quyết định sai có thể ghi vào lượt không hợp lệ, nên không bỏ các guard lifecycle
  hoặc mở quyền của NURSE sang ghi chuyên môn. Không nhận version tương lai.

## Bàn giao phần nghiệm thu còn mở

Kế hoạch triển khai tiếp trong lượt này đã thực hiện; ONB-06/H-11 chưa đóng toàn bộ.
Danh sách ca timeout/concurrent, login đầu ở lần đến thứ hai, draft/unavailable, broker replay
và đọc Lab khi lượt còn mở tại thời điểm này được giữ ở [biên bản nghiệm thu](../acceptance/onboarding-2026-10-27/README.md).
Tiến độ mới sau pull PR #546 ở [kế hoạch 14](14-onboarding-recovery-acceptance-2026-10-27.md)
và [107 + 99 observations](../acceptance/onboarding-recovery-2026-10-27/README.md); không ghi đè kết quả lịch sử.
Không xóa handoff hoặc test đang bảo vệ Surgery/Inpatient. UI copy login đã giải thích web tùy chọn
và recovery dùng tài khoản hiện có. Những bảng acceptance lịch sử giữ số test của đúng checkpoint.
