# Kiểm chứng khả dụng ngoại trú — 26/10/2026

Candidate: `codex/outpatient-frontend-usability`. Ảnh và runtime checkpoint ban đầu trên base
`be8c48f8`; sau đó rebase lên master `4851b196` (PR #540), giữ cả contact API mới của Hoàng Anh.
Dữ liệu tổng hợp demo, không dùng dữ liệu người bệnh thật. Không lưu token trong evidence.

## Thay đổi được kiểm chứng

- DOCTOR: lịch tuần 26/10–01/11, buổi 27/10 07:00–17:00 có tên khoa/phòng;
  phiếu ghi Nguyễn Văn An Demo/Trần Thị Bình Demo kèm ngày sinh, trạng thái thanh toán/đến riêng.
- PATIENT: tên bác sĩ Lê Bác Sĩ Demo, khoa/phòng và số thứ tự 1; phiếu xác nhận yêu cầu
  đến quầy ngày hẹn, không hiển thị hạn giữ chỗ cũ.
- CASHIER: tải hàng đợi, chọn Trần Thị Bình Demo theo tên/ngày sinh, tải lại quote;
  100.000 VND, hỗ trợ mô phỏng 50%, người bệnh trả 50.000 VND. Chọn phiếu thu gọn mã kỹ thuật.
  Thu tiền mặt demo thành công; request/quote cũ không tiếp tục hiển thị như khoản nợ.
  Cập nhật số dư và tải lại hàng đợi: phiếu đã trả đủ rời danh sách. Không có giao dịch tiền thật.
- API Gateway: [28 kiểm tra](gateway-checks.json), gồm exact minimal fields/no-store,
  CASHIER/LAB_TECH được directory nhưng bị chặn full profile, PATIENT bị chặn directory/queue,
  paid registration không xuất hiện trong queue, một phiếu PREBOOKED chưa trả có trong queue.
- [Booking/payment](booking-payment.json): đăng ký replay cùng phiếu, quote hỗ trợ mô phỏng,
  payment replay cùng transaction, CONFIRMED, chưa đến và chưa readyForExam.
  DOCTOR start trước khi đủ điều kiện bị từ chối 422.

## Kiểm thử tự động

- Frontend sau rebase: 117 file / 1.078 test qua (`vitest run --maxWorkers=1 --testTimeout=15000`);
  giới hạn một worker để không tranh tài nguyên với Java/container. 13 test thu ngân cũng qua riêng.
- Trước rebase: Core 362 / Gateway 1.584 / Billing 1.175 test. Sau rebase chạy lại toàn bộ
  Core 385 / Gateway 1.625; Billing giữ nguyên source đã kiểm thử. [XML summary](backend-tests.json)
  không failure/error/skip; lệnh Maven test thoát 0.
  Có cảnh báo Surefire shutdown của các Spring context dùng container sau test;
  không coi cảnh báo này là bằng chứng một nghiệp vụ đã được kiểm thử.
- Package JAR Core/Gateway/Billing thành công sau khi dừng JAR cũ đang khóa file trên Windows.
- TypeScript, ESLint và production build được chạy lại sau thay đổi cuối (xem kết quả trong PR).
- Có review độc lập về role matrix, directory tối thiểu, queue và các khóa thu tiền.

## Phạm vi và cách tái kiểm tra

Runtime: tám JAR/ứng dụng ở 28761,28080,28089,28082,28084–28087; PostgreSQL/RabbitMQ
trong hai container rehearsal riêng. Web 3000 gọi Gateway 28080 cùng origin qua Next proxy.
Cách chạy và các bước theo role trong
[runbook V2](../../../../scripts/demo_25_10/MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md).
Sau rebase đã build lại JAR, khởi động đủ tám ứng dụng và chạy lại booking/payment cùng
28 kiểm tra Gateway trên stack `snapshot-20261026-202020`; JSON kèm theo là kết quả lần này.
Đăng nhập lại bằng `demo.patient`, tải phiếu trên trình duyệt và chụp lại `patient.png`
trên stack sau rebase: đủ tên khoa/bác sĩ/phòng, số thứ tự và hướng dẫn đến tiếp nhận.

API mới và các quyền theo [A.13](../../../../docs/plans/03-contracts.md).

Tổng duyệt này thực hiện ngoài giờ khám: booking ngày mai, không chứng minh đã đi hết
khám/Lab/tái khám trong một ca. Phải chạy lại full examination trước 17:00 theo ca thật;
không đổi clock/loosen rule để tạo bằng chứng giả. Mobile/Inpatient/Surgery không thuộc đợt này.
Handoff reference directory còn consumer web/mobile cần đối chiếu; không đóng toàn bộ H-01.

## Ảnh trình duyệt

![Lịch bác sĩ](doctor.png)
![Phiếu người bệnh](patient.png)
![Thu ngân chọn khoản theo tên](cashier.png)
