# Onboarding qua Gateway — 27/10/2026

Candidate production: `9b2bba85` (Billing `981f8c2e`), base `b67bea03`.
JAR fingerprints và runner fingerprints nằm trong hai JSON bên dưới. Runner/tài liệu/UI copy
đang được hoàn thiện trên cùng nhánh; candidate SHA không đại diện cho các file chưa commit đó.

## Phạm vi đã chạy

- [87 HTTP checks onboarding](gateway-onboarding.json), `passed: true`, exit 0:
  NURSE tìm rồi tạo hồ sơ/cấp PATIENT, replay và conflict, input/role bị cấm;
  đăng ký tại quầy → EXAM quote/thu/replay → bắt đầu khám → chẩn đoán → chỉ định Lab;
  chặn hoàn tất khi Lab chưa xong/chặn thực hiện Lab chưa thu; thu Lab/replay → kết quả →
  hoàn tất khám → lời hẹn. Patient chưa login trong toàn bộ đoạn chuyên môn này.
- Ba thông báo kết quả khám/Lab/lời hẹn đã tồn tại trước lần login đầu. Sau đó Gateway
  chặn own APIs khi chưa đổi mật khẩu; đổi thành công thu hồi access/refresh cũ;
  mật khẩu cũ bị từ chối. Own results đúng một record và một Lab; truy cập chéo bị từ chối.
- NURSE đăng ký FOLLOW_UP cho ngày sau bằng cùng patient/account và đúng source/recommendation;
  retry giữ một registration. Account không bị cấp lại/bắt đổi lại do có lượt mới.
  FOLLOW_UP mới chỉ đang chờ thanh toán, chưa nghiệm thu lượt tái khám hoàn chỉnh.
- Recovery qua command ADMIN hiện hữu: NURSE bị cấm, phiên/mật khẩu cũ mất hiệu lực,
  account và kết quả vẫn giữ. Dữ liệu giả lập không chứng minh quy trình xác minh danh tính thật.
- [20 HTTP checks bệnh nhân cũ khám mới](gateway-returning-patient.json), `passed: true`, exit 0:
  demo.patient đặt NEW_VISIT ngày sau bằng tài khoản sẵn có, thu/replay và CONFIRMED;
  chưa chạy khám/Lab cho ca phụ này.

Stack dùng `rehearse_snapshot.py --scheduling --keep`, PostgreSQL/RabbitMQ riêng, tám context DB
restore vào container mới; từ chối restore lại trên target đã có dữ liệu. Sáu business deployments,
Gateway/Eureka đều UP. Scheduling/Care Finance gates bật bằng overlay thử nghiệm; không đổi default.
Mọi nghiệp vụ đi qua Gateway `127.0.0.1:28080`; không sửa snapshot/migration hoặc giả JWT.
Evidence không chứa password/token/request credential body, chỉ IDs dữ liệu tổng hợp và HTTP outcomes.

## Giao diện đã quan sát

Frontend `127.0.0.1:3005`, same-origin API chuyển qua Gateway rehearsal. NURSE tìm được hồ sơ;
PATIENT đăng nhập bằng credential đã khôi phục, đọc kết quả khám/Lab, lời hẹn, notification history
và hai phiếu cùng bệnh nhân (lượt hoàn tất + FOLLOW_UP đang chờ). Không thực hiện đổi mật khẩu
qua trình duyệt; bước đổi/thu hồi phiên đã được kiểm trực tiếp qua Gateway ở trên.

| Bằng chứng | Ý nghĩa |
|---|---|
| [Password gate](password-gate.jpg) | Quan sát UI trên lượt rehearsal trước đó cùng baseline UI: direct own route về trang buộc đổi mật khẩu; không phải ca đổi mật khẩu UI hoàn chỉnh |
| [Kết quả khám](exam-result.jpg) | Chẩn đoán, kết luận và lời hẹn của record trong JSON onboarding |
| [Kết quả Lab](lab-result.jpg) | CBC đã hoàn tất, WBC 7.0, kết luận |
| [Thông báo](notifications.jpg) | History có thông báo kết quả và lời hẹn phát trước login |
| [Phiếu tái khám](revisit.jpg) | Cùng account thấy lượt trước hoàn tất và FOLLOW_UP chưa xác nhận |

## Hồi quy

| Kiểm tra | Kết quả cuối |
|---|---:|
| Billing `mvn -q -pl backend/billing-service -am -Dspring.test.context.cache.maxSize=2 test` | 1.178 tests, 0 failures/errors/skipped, exit 0 |
| Clinical cùng command theo module | 438 tests, 0 failures/errors/skipped, exit 0 |
| Frontend `pnpm exec vitest run --maxWorkers=2` | 119 files, 1.097 tests passed |
| Frontend `pnpm typecheck`, `pnpm lint`, `pnpm build` | exit 0 cả ba |
| Login copy: targeted LoginPage | 12 tests passed |

Billing regression: authority matching scheduled registration và settled clearance sau quote TTL,
cả positive/zero liability, execution replay. Clinical regression: diagnosis/Lab order cho active
contract 1/2; V0 không được ghi. Đã quan sát RED trước sửa hai lỗi tương ứng.
Full Clinical lần đầu có một lỗi setup test yêu cầu restore version 3 (domain đã từ chối); đã sửa
test để kiểm V0 và chạy lại toàn bộ đạt. Full Billing lần đầu có lỗi gate/time setup; đã sửa và
chạy lại toàn bộ, không bỏ qua test lỗi.

## Phần còn mở

Danh sách dưới là giới hạn checkpoint này. Phần đã chạy tiếp sau merge PR #546 xem
[recovery/open Lab: 107 + 99 observations](../onboarding-recovery-2026-10-27/README.md).

H-11 vẫn **NEEDS REVALIDATION**, WF-11 chưa đóng. ONB-A01–12 chưa được coi đạt toàn bộ UI/API:
chưa có fault injection timeout sau commit/outcome recovery, hai NURSE concurrent,
lần đầu login ở lần đến bệnh viện thứ hai, draft/empty/unavailable UI, broker replay effect counts,
đọc Lab khi lượt còn mở và nghiệm thu recovery xác minh danh tính. UI lời hẹn còn hiển thị UUID
ở bác sĩ ưu tiên/lượt nguồn; không coi ảnh happy path là nghiệm thu hoàn thiện nhãn UI.
Không có bằng chứng SMS/email/push hoặc bệnh nhân đã đọc thông báo.

Theo dõi công việc tại [ONB closeout](../../plans/13-onboarding-closeout-2026-10-27.md)
và [ma trận ONB](../../plans/06-acceptance-runbook.md).
