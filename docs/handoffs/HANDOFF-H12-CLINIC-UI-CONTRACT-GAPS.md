# H-12 — Hợp đồng còn thiếu sau sửa tương tác web/mobile

Status: ACTIVE CONTRACT GAP. Ngày rà soát: 03/11/2026.
Source lock: `3e28d96e` cùng working tree sửa UI trong phiên này.
Producer/resume owner backend: Vinh (Clinical, Lab, Billing).
Consumer: web Clinical/Lab/Billing và Flutter mobile, theo ownership hiện hành.
Không đổi runtime flags; các cổng nghiệp vụ đang tắt vẫn giữ tắt.

## Khoảng trống đã đối chiếu với source

1. Chọn xét nghiệm và mã giá: chưa có public read contract cho danh mục xét nghiệm
   và giá hiệu lực để thay hai ô nhập tự do. Consumer hiện dùng chính `labType` và
   `priceCode` từ thao tác bác sĩ, không tự tạo mã hay đoán giá.
2. Tìm hàng chờ Lab theo bệnh nhân/ngày: `LabController.search` nhận department/status
   và phân trang. Endpoint theo bệnh nhân chỉ cho ADMIN/DOCTOR, không phải lối tìm
   cho LAB_TECH. Không lọc cục bộ một trang rồi coi đó là toàn bộ kết quả.
3. Nội dung khoản thu: `PaymentRequestQueueItem(request, createdAt)` và
   `PaymentRequestDiscoveryDTO` không có tên xét nghiệm/dòng thuốc. Consumer hiện
   hiển thị bệnh nhân, khoa, loại khoản thu và số còn phải thu; không dựng tên dịch vụ.
4. Khôi phục chỉ định sau tải lại ứng dụng: `ClinicalLabOrderController` chỉ có POST
   `/{id}/lab-orders` cho ADMIN/DOCTOR, chưa có read/reconciliation contract cho
   người dùng để tìm lại chỉ định nguồn theo hồ sơ. Cache trong phiên UI chỉ bảo vệ
   khi chuyển trang; không giải quyết browser reload, đóng app hoặc đổi thiết bị.

## Source và fixture hiện hành

- `backend/clinical-service/src/main/java/com/mediflow/clinical/web/ClinicalLabOrderController.java`
- `backend/lab-service/src/main/java/com/mediflow/lab/web/LabController.java`
- `backend/billing-service/src/main/java/com/mediflow/billing/web/PaymentRequestQueueController.java`
- `backend/billing-service/src/main/java/com/mediflow/billing/application/dto/response/PaymentRequestQueueItem.java`
- `backend/billing-service/src/main/java/com/mediflow/billing/application/dto/response/PaymentRequestDiscoveryDTO.java`
- `backend/clinical-service/src/test/java/com/mediflow/clinical/web/ClinicalLabOrderControllerTest.java`
- `backend/billing-service/src/test/java/com/mediflow/billing/web/PaymentRequestQueueControllerTest.java`

Fixture queue hiện có: GET `/api/v1/billing/payment-requests/queue?purpose=LAB_TEST&page=0&size=20`,
ADMIN/CASHIER, envelope `data.content[{request:{paymentRequestId,patientId,departmentId,
purpose,sourceId,careEpisodeId,requestedAmount,paidAmount,remainingAmount,currency,...},createdAt}]`.
Đây là mô tả field hiện hữu, không là schema mới. Chọn hàng phải discovery lại đúng
source/patient/episode/paymentRequestId trước thanh toán; không dùng queue làm quyền thu.

## Acceptance trước khi đóng

- Chốt DTO/roles/phân trang/catalog authority với fixture producer; không cho client
  tự ghép DB hoặc gọi service port. Giữ tương thích consumer hiện tại.
- Danh mục phải trả mã và tên có authority, hiệu lực/ngừng dùng; kiểm cả thay giá
  giữa lúc chọn và lúc tạo chỉ định. Thu tiền dựa quote/ledger hiện hành.
- LAB_TECH tìm đúng bệnh nhân/khoảng ngày, đủ phân trang; kiểm quyền khoa và dữ liệu
  ngoài phạm vi, empty/error/loading và response cũ sau đổi bộ lọc/session.
- Khoản thu có mô tả có authority và kiểm danh tính; vẫn discovery/quote mới trước thu.
- Clinical có recovery sau committed-write mất phản hồi và app restart; xác minh
  đúng record/patient/episode/actor, không lập chỉ định thay thế khi kết quả chưa rõ.
- Producer integration tests và consumer contract/widget/browser acceptance qua
  Gateway đạt trên cùng candidate, rồi chuyển lasting rules vào docs chuẩn và xóa H-12.

Lịch/đăng ký/thu phí khám V2 mobile là backlog consumer MOB-05–09 đã có, không coi
toàn bộ backlog đó là thiếu producer contract hoặc đã hoàn tất bởi đợt sửa UI này.
