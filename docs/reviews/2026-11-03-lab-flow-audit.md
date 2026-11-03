# Rà luồng Lab → Billing và hủy chỉ định — 03/11/2026

Phạm vi Vinh: Clinical/Lab/Billing/Gateway và frontend Lab/medical-record/Billing.
Baseline `2e3fa734`; working tree ban đầu chỉ có `scripts/demo_video/` untracked,
không thuộc thay đổi này. Không có business service hoặc frontend đang listen trên
các cổng local 3000/8080/8082/8084/8086 lúc bắt đầu; chưa xác minh đơn lỗi của người dùng.

## Khóa contract trước sửa (M6-02)

- `backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java`:
  PUT `/api/v1/lab/{id}/cancel`, ADMIN/DOCTOR/LAB_TECH, ApiResponse<LabTestDTO>.
- `application/dto/request/CancelLabTestRequest.java`: reason bắt buộc, tối đa1000;
  cancelledBy UUID bắt buộc ở baseline.
- `LabApplicationService.cancel`: V1 yêu cầu cancelledBy khớp verified staffId;
  test/source locks, audit và `lab.request.cancelled` outbox cùng transaction.
- `application/event/LabRequestCancelledPayload.java` và Billing
  `infrastructure/messaging/OutpatientCancellationDecoder.java`: event audit vẫn
  bắt buộc cancelledBy UUID thực, reason, cancelledAt và exact source/episode.
  Không đổi event bytes/schema hoặc migration.
- Fixture có sẵn ở `LabApplicationServiceTest`, `LabCareFinanceV2ApplicationServiceTest`,
  `LabControllerTest`, `LabTestCancellationRabbitIntegrationTest`; frontend wire ở
  `features/lab/types.ts`, `api.ts`, `components/LabActions.tsx`.

Điều chỉnh tương thích: reason-only được phép; server điền actor từ verified
principal. Client cũ gửi cancelledBy vẫn phải khớp principal; không cấp quyền giả
mạo actor. Audit/event giữ UUID thực, không nullable. FE chỉ yêu cầu lý do.

## Kết quả khám phá

1. **Tạo Clinical order chưa tạo Lab request.** `LabOrderWorkspace` hiện có hai
   bước và chỉ bước hai gọi POST Lab. Chỉ Lab request V1 mới phát nested
   `lab.request.created` tới Billing. Rời trang sau bước một sẽ không có khoản thu.
2. **Có ba màn hình viện phí khác nhau.** `/billing` là tra hóa đơn compatibility;
   `/billing/scheduled-payments` là EXAM; Lab/Rx phải xem
   `/billing/payment-requests`. PR563 đã thêm hàng thu Lab/Rx ở màn hình cuối.
3. **Mã giá nhập tự do, tạo khoản thu bất đồng bộ.** Clinical order và Lab creation
   không xác nhận mã trong Billing catalogue. `CareFinancePriceCatalogAdapter`
   từ chối mã không biết bằng BILLING_PRICE_CODE_UNKNOWN; `LabTestChargeConsumer`
   reject fact vào DLQ. Vì vậy Lab creation thành công chưa chứng minh Billing
   đã có khoản thu. Cần actual sourceId/event/DLQ ở môi trường lỗi để kết luận.
   Policy không có mapping riêng dùng supportRate0 theo source hiện hành;
   thiếu mapping không tự nó là lỗi tạo khoản thu.
4. **Lab quoted request hết hạn15 phút.** `LabTestPlannedRequestService` tạo
   request/quote cùng expiry; `PaymentRequestDiscoveryAdapter.findQueue` loại
   expired. Không thấy public command renew/requote Lab trong các controller
   Billing đã đọc. Không bỏ expiry hoặc lập chỉ định trùng để thu tiếp; đây là
   khoảng trống phục hồi cần thiết kế riêng với quote/source/idempotency.
5. **Hủy bắt chọn actor trái với signed identity.** Staff selector cho chọn người
   khác nhưng server V1 chỉ chấp nhận đúng người đăng nhập. Sửa bằng server actor,
   giữ negative test spoof và audited cancellation event.
6. **Hủy dịch vụ khác hoàn tiền.** Domain cho hủy nonterminal, kể cả IN_PROGRESS;
   COMPLETED/CANCELLED terminal. Billing nhận cancellation để void unpaid hoặc
   ghi nghĩa vụ hoàn trên nguồn cụ thể; tiền thực hoàn vẫn do CASHIER ghi riêng.
   Late clearance không mở lại xét nghiệm đã hủy. Giữ guards và policy này.

## Phần sửa trong lượt này

- Hủy bằng lý do, actor lấy từ verified session tại backend; tương thích body cũ.
- Thông báo rõ sau bước lập chỉ định còn phải gửi sang Lab; sau tạo Lab cần thu
  tại hàng khoản thu Lab/Rx và chỉ thực hiện khi có xác nhận tài chính.
- Đường dẫn rõ từ trang Viện phí tới hàng thu Lab/Rx và phí khám.
- Hiển thị ý nghĩa hủy trước/sau xác nhận tài chính, không báo đã hoàn tiền.

## Kiểm chứng trên mã đã sửa

- Backend Lab: full suite đạt 272 ca; sau khi thêm negative test không có verified
  staff, chạy lại class liên quan đạt 27 ca. Tổng report hiện tại: 273 ca, không
  fail/error/skip.
- Billing: 43 ca liên quan issuance, discovery, PostgreSQL queue, RabbitMQ charge/
  cancellation, quoted payment và zero-liability đều đạt, không skip.
- Frontend phạm vi Lab/Billing/Clinical commands: 23 file, 165 ca đều đạt.
  `pnpm typecheck`, `pnpm lint`, `pnpm build` đạt.
- Full frontend: 1258/1263 ca đạt. Một ca DoctorWorkShiftsWorkspace về focus sau
  conflict vẫn lỗi khi chạy riêng. Bốn ca CreateAdmissionForm timeout trong full
  run đều đạt khi chạy riêng. Những file này không sửa trong lượt này; không coi
  full frontend suite đã đạt.
- Build fresh JAR Core, Clinical, Lab, Billing, Gateway và Eureka thành công.
  `PackagedOutpatientLabAcceptanceIT` chạy sáu ứng dụng riêng, PostgreSQL và
  RabbitMQ thật qua Gateway: 1 scenario đạt, 0 fail/error/skip, 164.7 giây.
  Không dùng dữ liệu môi trường người dùng. Process/container được teardown sau test.
- Review độc lập không phát hiện lỗi cần sửa trong diff và acceptance mở rộng.
  `git diff --check` đạt.

### Luồng đã chạy qua Gateway

1. Đăng nhập các role, thu phí khám, bác sĩ mở hồ sơ và lập chỉ định Clinical.
   Lúc chỉ có Clinical order, hàng thu Lab của bệnh nhân chưa có khoản mới.
2. Gửi chỉ định sang Lab; CASHIER tìm thấy đúng Lab source trong payment queue,
   thanh toán 150.000 VND; Lab nhận clearance, thực hiện và trả kết quả về Clinical.
3. Đơn hoàn tất không được hủy. NURSE không được hủy; DOCTOR không được giả actor.
4. Bác sĩ hủy reason-only đơn chưa thu: CANCELLED, một event audit đúng doctor,
   Billing APPLIED, gỡ khỏi queue và không phát sinh nghĩa vụ hoàn tiền.
5. Bác sĩ hủy reason-only đơn đã thu 150.000 VND, đang IN_PROGRESS: CANCELLED,
   Billing APPLIED, nghĩa vụ hoàn đúng 150.000 VND liên kết giao dịch gốc.
   Gửi lại cùng yêu cầu không tạo event hủy trùng.

Bằng chứng phản hồi đã lưu tại
[`docs/acceptance/lab-flow-2026-11-03`](../acceptance/lab-flow-2026-11-03/README.md).
Acceptance này gọi API thật, không phải thao tác click browser. Frontend được
kiểm chứng bằng component tests và build. Packaged scenario dùng luồng V1 thường;
quoted/zero-liability được kiểm tra riêng bằng Billing integration tests.

## Giới hạn còn lại

Chưa xác minh được đơn lỗi cụ thể của người dùng. Luồng catalogue hợp lệ đã chạy
thành công; không kết luận mọi trường hợp thiếu khoản thu đều cùng một nguyên nhân.
Chưa thay cơ chế nhập mã giá tự do, cơ chế thông báo khi Billing reject async,
hoặc bổ sung renew/requote cho khoản thu hết hạn. Không sửa dữ liệu vận hành,
không tự tạo lại chỉ định và không tự ghi hoàn tiền. Thay đổi chưa commit/deploy.
