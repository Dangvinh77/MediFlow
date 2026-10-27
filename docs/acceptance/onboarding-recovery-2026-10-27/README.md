# Onboarding: phục hồi, hai điều dưỡng và Lab trong lượt mở

Baseline sau pull: `ddbb56a2` (PR #546 đã merge). Runner commit **`52c4c07c`**;
cả hai lần chạy cuối bên dưới ghi `sourceHead` này và runner SHA-256 khớp source đã commit.
Tám JAR có SHA-256 bằng checkpoint [Gateway/UI trước đó](../onboarding-2026-10-27/README.md):
production Clinical `9b2bba85`, Billing `981f8c2e`, các module còn lại theo baseline checkpoint.
Lượt này bổ sung runner/tài liệu, không sửa production module hoặc build lại JAR khác source.

## Kết quả đã chạy

| Bằng chứng | Kết quả | Phạm vi |
|---|---:|---|
| [Gateway recovery](gateway-recovery.json) | 107 HTTP observations, `passed=true`, exit 0 | Hai NURSE khác actor cấp cùng patient; mất phản hồi sau commit; tiếp nhận lần hai trước login; vòng khám/Lab/login/tái khám/recovery |
| [Gateway open Lab](gateway-open-lab.json) | 99 HTTP checks, `passed=true`, exit 0 | Login/đổi mật khẩu trong lượt đang khám; chưa lộ draft; đọc Lab khi lượt mở; sau đó hoàn tất khám, lời hẹn và đăng ký tái khám |
| [Runner guards](runner-guards.json) | 4 ca từ chối, exit 2 đúng dự kiến | Sai mode, hai thời điểm login, booking-only/onboarding, Gateway không thuộc rehearsal; chưa gọi login |

Hai điều dưỡng được xác thực riêng qua Gateway: `actorIds` khác nhau trong JSON; người thứ hai
được tạo staff/account NURSE qua API ADMIN trên stack riêng. Barrier mở hai POST cùng lúc,
hai idempotency key khác nhau: đúng một 201 và một 409 `PATIENT_ACCOUNT_ALREADY_ISSUED`.
Gửi lại key thắng trả 200 cùng account; key thua vẫn 409. Không dùng hai phiên cùng actor
làm bằng chứng hai NURSE, không thay tài khoản đang hoạt động.

Fault proxy chỉ lắng nghe loopback, chuyển POST tới đúng Gateway rehearsal. Sau khi nhận 201
từ Gateway, proxy đóng kết nối trước khi trả bất kỳ HTTP response nào cho client. Client quan sát
`RemoteDisconnected`; cùng actor/key/patient retry trả 200 cùng account. Đổi payload với key cũ
trả 409 `ACCOUNT_IDEMPOTENCY_KEY_REUSED`; key mới cho cùng patient vẫn conflict. Trong 107
observations có **một 201 chỉ proxy upstream quan sát**, được đánh dấu rõ ở JSON; client không
nhận 201 đó. Đây là mất phản hồi sau commit, không chứng minh timeout nội bộ hoặc UI recovery.

Ca tiếp nhận lần hai là hai lượt **cùng ngày** trên dữ liệu giả lập: lượt đầu hoàn tất, chưa login;
NURSE tìm lại đúng patient, đăng ký NEW_VISIT AT_COUNTER lần hai, có `checkedInAt`, chờ thu.
Replay cấp account vẫn cùng account và `mustChangePassword=true`. Login đầu vẫn phải đổi,
phiên/refresh/mật khẩu cũ bị từ chối; sau đó đọc đúng kết quả lượt trước. Đăng ký FOLLOW_UP
ngày sau giữ nguồn/lời hẹn, tổng ba phiếu của cùng patient; không mô tả lượt hai/ba đã khám.
Không giả đồng hồ để coi đây là bệnh nhân thực sự quay lại vào ngày khác.

Ca Lab: sau login/đổi mật khẩu, cả own-result lists trống khi record OPEN và Lab chưa có kết quả.
Lab hoàn tất thì own Lab có đúng kết quả WBC 7.0; đọc hai lần khi registration vẫn IN_EXAM,
own exam vẫn trống. Staff record xác nhận đúng appointment/record còn OPEN; registration DTO
chỉ có recordId khi COMPLETED nên không suy field đó phải có lúc khám. Discovery EXAM vẫn
cùng paymentRequestId; quote và receipt của payment operation bằng snapshot trước đọc;
own registrations vẫn đúng một. Sau đó bác sĩ hoàn tất mới có kết quả khám cho PATIENT.

## Môi trường và cách lặp lại

`rehearse_snapshot.py --scheduling --keep` tạo PostgreSQL/RabbitMQ mới và restore tám context
stores riêng; sáu business deployments cùng Gateway/Eureka đều UP. Không dùng dữ liệu dự án,
không cross-context SQL, không giả JWT, không sửa gate mặc định. Manifest local có stopFile
để dừng đúng stack do runner tạo. Evidence không lưu password/token/request body chứa credential.

```powershell
python scripts/demo_25_10/rehearse_snapshot.py --scheduling --keep
# Terminal khác, trước 17:00 Asia/Ho_Chi_Minh
python scripts/demo_25_10/accept_scheduling.py --onboarding --issuance-recovery --login-after-return --output scripts/demo_25_10/evidence/recovery.local.json
# Dùng sessionId/followUpSessionId từ JSON vừa chạy; bệnh nhân giả lập mới mỗi lần
python scripts/demo_25_10/accept_scheduling.py --onboarding --login-before-lab --session-id <sessionId> --follow-up-session-id <followUpSessionId> --output scripts/demo_25_10/evidence/open-lab.local.json
```

Số HTTP checks có thể thay đổi theo số lần poll projection. Lần chạy cuối đã reuse hai session
từ cùng rehearsal; kiểm lại ngày, bác sĩ, khoa, revision và suất trống, không tự bỏ qua conflict.
`python -m py_compile` cho cả ba runner và `git diff --check` đạt. Module/frontend suites của
checkpoint trước giữ nguyên bằng chứng lịch sử, không tuyên bố chạy lại trong lượt runner này.
Lần thử open-Lab đầu dừng ở assertion sai rằng registration IN_EXAM đã trả recordId;
đã đối chiếu live mapper, sửa runner sang staff record, chạy lại hoàn chỉnh đạt; không sửa API
để làm test đạt. Không gọi đây là regression production RED/GREEN.

## Checklist và phần còn mở

- ONB-A07: đã kiểm nhánh Gateway với PG thật, hai actor; không tự ký UI đa vai trò.
- ONB-A06: đã kiểm mất phản hồi sau commit/retry và key conflict; UI giữ hồ sơ/key và phục hồi
  sau gián đoạn còn phải nghiệm thu trực tiếp trên trình duyệt.
- ONB-A05: đã kiểm hai lần tiếp nhận cùng ngày trước login; UI và lần quay lại ngày khác còn mở.
- ONB-A10/A12, WF-T16: đã kiểm API pending-result filtering và đọc Lab cùng lượt, snapshot EXAM
  không đổi; chưa chứng minh toàn UI chờ/lỗi nguồn, qua ngày khác hoặc mọi nguồn charge khác.
- ONB-A11: broker replay/effect counts vẫn mở; in-app history không là bằng chứng đã đọc,
  không có SMS/email/push. Recovery chưa chứng minh xác minh danh tính ngoài đời.

H-11 vẫn **NEEDS REVALIDATION**, WF-11 chưa đóng. Giữ handoff và nguồn Surgery/Inpatient.
Theo dõi tại [kế hoạch tiếp tục](../../plans/14-onboarding-recovery-acceptance-2026-10-27.md)
và [ma trận nghiệm thu](../../plans/06-acceptance-runbook.md).
