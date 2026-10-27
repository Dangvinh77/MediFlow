# Tiếp tục nghiệm thu phục hồi onboarding — 27/10/2026

Baseline `ddbb56a2`, sau khi pull PR #546 đã merge. Đây là phần tiếp tục ONB-06
trong [07](07-outpatient-workflow-implementation.md), không thêm nghiệp vụ mới.

## Phạm vi và cách kiểm

- [x] Bổ sung helper `scripts/demo_25_10/issuance_acceptance.py`: tạo điều dưỡng thứ hai
  qua API ADMIN trên stack riêng; hai actor NURSE, hai key cùng cấp cho một patient.
  Dùng barrier để gửi đồng thời; yêu cầu chính xác một 201 và một 409
  `PATIENT_ACCOUNT_ALREADY_ISSUED`. Retry key thắng trả cùng account; key thua vẫn conflict.
- [x] Qua proxy loopback, gửi cấp account tới Gateway rồi bỏ phản hồi sau khi upstream
  trả 201. Client phải thấy mất kết nối; retry cùng actor/key/patient trả 200 cùng account.
  Không mô tả đây là timeout nội bộ service hoặc UI recovery.
- [x] Bổ sung `--login-before-lab` cho runner hiện hữu: đổi mật khẩu khi lượt đang khám,
  kiểm danh sách kết quả chưa có; Lab hoàn tất thì đọc được trước khi hoàn tất khám.
  Đối chiếu registration/record và receipt EXAM không thay đổi sau đọc.
- [x] Bổ sung `--login-after-return`: sau lượt đầu hoàn tất, NURSE tìm lại cùng patient,
  tiếp nhận NEW_VISIT tại quầy lần hai trước login; replay account vẫn cần đổi mật khẩu.
  Đây là ca giả lập hai lần tiếp nhận cùng ngày, không giả đồng hồ/ngày hôm sau.
- [x] Chạy cả luồng login muộn cũ và luồng login trước Lab trên PostgreSQL/RabbitMQ riêng;
  lưu evidence có source/JAR/runner fingerprints, không lưu credential.
- [ ] Cập nhật checklist theo bằng chứng mới; giữ ONB-A05 UI/ngày khác, UI lỗi và broker
  replay còn mở nếu chưa chạy. Review và tạo PR tiếp theo, không tự merge.

## Ranh giới

Chỉ thay runner và tài liệu được giao tiếp tục; không sửa store, migration, gate mặc định,
Surgery/Inpatient. Hai phiên của cùng nurse không thay được nghiệm thu hai actor khác nhau.
Mất phản hồi chỉ được coi đã kiểm nếu proxy quan sát 201 và client không nhận outcome.
Các JAR đang có khớp source production của PR #546; ghi SHA-256 để đối chiếu checkpoint trước.

## Kết quả

Runner `52c4c07c`: recovery/tiếp nhận lần hai 107 observations, open-Lab 99 checks, cả hai
exit 0; thêm 4 guard cases từ chối đúng exit 2. [Evidence và giới hạn](../acceptance/onboarding-recovery-2026-10-27/README.md)
ghi riêng nhánh API, không đóng toàn bộ H-11/WF-11. Review source không phát hiện blocker.
