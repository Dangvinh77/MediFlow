# MediFlow v2.0 Đợt 3 — Nghiệm thu vòng đời lượt khám

Ngày chạy: 2026-11-11 (đồng hồ dịch vụ lùi 12 giờ, ngày nghiệp vụ 2026-11-10).
Nhánh: `develop/mediflow-v2.0`. Kế hoạch: [`2026-11-10-v2-wave-3-exam-lifecycle.md`](../superpowers/plans/2026-11-10-v2-wave-3-exam-lifecycle.md).

## Test tự động

| Bộ | Kết quả |
|---|---|
| Backend (`mvn test`, toàn bộ module) | 2992 test; 3 lỗi. `ExamClosureTest.onlyContractTwoCanClose` phụ thuộc ngày chạy, đã sửa ở `69a3d829` và chạy lại xanh. Hai lỗi còn lại là lỗi nền đã biết: `LabTestChargeConfigurationTest.intake_requiresBothExplicitGates[4]`, `DispenseApplicationServiceTest.dispense_expiredDrug_doesNotReduceStock` |
| Web (`vitest run`) | 1515/1519. Bốn test hết 5 giây khi chạy cả bộ (`ReceptionWorkspace`, `CreateAdmissionForm`); chạy riêng hai file đó 34/34 |
| Typecheck web | Sạch |
| Lint web | 0 lỗi, 3 cảnh báo có từ trước |
| Mobile (`flutter test`) | 678/678 |
| `flutter analyze` | 3 info có từ trước, không thêm vấn đề mới |

## Nghiệm thu qua Gateway trên DB sạch

Compose project riêng `mediflow-w2`, `down -v` rồi dựng lại, seed demo, cờ tiếp nhận tại khoa bật. Mọi lệnh nghiệp vụ
đi qua Gateway với tài khoản demo thật. Kết quả 25/25.

| Kịch bản | Kết quả |
|---|---|
| Bắt đầu khám tạo một hồ sơ; bấm lại không tạo hồ sơ thứ hai | 200, 1 hồ sơ |
| Hai thiết bị bắt đầu cùng lúc | 200 và 200, 1 hồ sơ |
| Có chỉ định chưa có kết quả thì chưa hoàn tất | 422 `CLINICAL_LAB_ORDER_PENDING` |
| Thu tiền Lab, Lab bắt đầu và trả kết quả; hồ sơ đọc trạng thái từ Lab | `COMPLETED` |
| Chiều có kết quả vẫn ghi vào cùng hồ sơ | 200, 1 hồ sơ |
| Sang ngày sau vẫn cùng lượt | Sửa 200, hoàn tất 200, ngày khám giữ `2026-11-10` |
| Bỏ về, không có chỉ định treo | 200; DB `CLOSED_WITHOUT_CONCLUSION`, `PATIENT_LEFT`, có `closed_at` |
| Gửi lại lệnh đóng cùng key | 200, `Idempotency-Replayed: true` |
| Lượt đã đóng: hoàn tất, đóng lại, sửa | 422 `CLINICAL_INVALID_STATUS_TRANSITION`, 422 cùng mã, 422 `CLINICAL_V0_WRITE_DISABLED` |
| Điều dưỡng đóng lượt | 403 `AUTH_FORBIDDEN` |
| Còn chỉ định treo thì không đóng được | 422 `CLINICAL_LAB_ORDER_PENDING` |
| Hủy chỉ định qua Lab rồi đóng | Hủy 200, trạng thái `CANCELLED`, đóng 200 |
| Chỉ định chưa tới Lab | `NOT_RECEIVED`, không có `testId` |
| Nguồn quay lại là hồ sơ của người khác | 422 `RETURN_SOURCE_INVALID` |
| Quay lại thiếu lý do | 422 `RETURN_SOURCE_INVALID` |
| Hoàn tất rồi quay lại | Lượt mới có `previousRecordId` trỏ về lượt cũ; lượt cũ không đổi; lượt mới không có chẩn đoán |
| Quầy và bác sĩ xem lượt đang mở | 200, đúng hồ sơ |
| Cùng khoa còn lượt mở, không có lý do | 409 `OPEN_VISIT_EXISTS` |
| Cùng khoa, có lý do | 201 |
| Khác khoa, không có lý do | 201 |
| Phí khám | Mỗi lượt có đúng 1 dòng `EXAM` trong `charge`; lượt quay lại có phí riêng |

## Giới hạn của bằng chứng

- "Sang ngày sau" được mô phỏng bằng cách lùi `session_date` của buổi khám một ngày trong DB, không phải chờ qua ngày thật.
  Test `examinationDateIsClinicDate` và các test giữ hành vi `complete`/`update` không xét ngày là bằng chứng bổ sung.
- Đồng hồ của Postgres và các dịch vụ được lùi 12 giờ bằng libfaketime để buổi khám nằm trong giờ làm việc.
  Lần chạy đầu Postgres chưa lùi giờ: outbox của Billing ghi `available_at` theo giờ thật nên sự kiện thanh toán Lab
  chưa đến hạn. Đây là lỗi của bộ chạy thử, không phải của sản phẩm.
- Lệnh sửa hồ sơ đã đóng bị chặn bởi `CLINICAL_V0_WRITE_DISABLED` ở đường dẫn v1, trước cả kiểm tra trạng thái.
  Kiểm tra trạng thái `RECORD_CLOSED` được test ở `ExamClosureTest`.
- Giao diện web và mobile chỉ được kiểm bằng test tự động, chưa chạy tay trên trình duyệt hay thiết bị.

## Ghi chú cấu hình

`docker-compose.yml:323` bật `MEDIFLOW_BILLING_EXAM_CHARGE_CONSUMER_ENABLED: "true"`, còn `application.yml` của Billing
mặc định `false`. Lượt đặt lịch không phát sinh phí khám qua consumer này (test `scheduledLifecycleIssuesNoExamCharge`),
nên giá trị cờ không đổi số dòng phí khám của lượt đặt lịch.
