# Thiết kế bộ ERD chi tiết theo luồng service

**Ngày:** 04/09/2026  
**Phạm vi:** Báo cáo Giai đoạn 2 của MediFlow  
**Trạng thái:** Đã được người dùng duyệt phương án

## 1. Mục tiêu

Mở rộng Mục 2.3 của báo cáo Giai đoạn 2 để người đọc không chỉ thấy cấu trúc từng database mà còn hiểu dữ liệu đi vào, được lưu trữ và được phát sang các service khác như thế nào.

Bộ tài liệu phải đáp ứng đồng thời hai nhu cầu:

- ERD vật lý mô tả đúng bảng, cột, khóa và quan hệ thật trong từng database.
- Sơ đồ ERD–luồng dữ liệu mô tả UUID tham chiếu xuyên service và integration event mà không tạo cảm giác có foreign key xuyên database.

## 2. Phương án được chọn

Mỗi service có hai góc nhìn độc lập nhưng liên kết với nhau:

1. **ERD vật lý chi tiết:** dùng ký pháp ERD, liệt kê bảng, cột quan trọng, PK, FK, UK và cardinality trong cùng database.
2. **ERD–luồng dữ liệu:** dùng sơ đồ luồng có ranh giới service, chỉ ra nguồn dữ liệu bên ngoài, bảng thuộc quyền sở hữu, UUID tham chiếu logic, event nhận và event phát.

Sau tám service, thêm **Mục 2.3.9. ERD tổng quan toàn hệ thống** để thể hiện các aggregate/thực thể chính và quan hệ logic xuyên bounded context.

## 3. Cấu trúc Mục 2.3

| Mục | Service | ERD vật lý | ERD–luồng dữ liệu |
|---|---|---|---|
| 2.3.1 | Organization | `02-erd-organization` | `18-erd-flow-organization` |
| 2.3.2 | Patient | `03-erd-patient` | `19-erd-flow-patient` |
| 2.3.3 | Clinical | `04-erd-clinical` | `20-erd-flow-clinical` |
| 2.3.4 | Lab | `05-erd-lab` | `21-erd-flow-lab` |
| 2.3.5 | Pharmacy | `06-erd-pharmacy` | `22-erd-flow-pharmacy` |
| 2.3.6 | Billing | `07-erd-billing` | `23-erd-flow-billing` |
| 2.3.7 | Notification | `08-erd-notification` | `24-erd-flow-notification` |
| 2.3.8 | Report | `09-erd-report` | `25-erd-flow-report` |
| 2.3.9 | Toàn hệ thống | — | `26-erd-system-overview` |

Mỗi mục service gồm:

- phạm vi dữ liệu service sở hữu;
- các luồng nghiệp vụ chính;
- ERD vật lý;
- bảng giải thích tham chiếu ngoài và event;
- ERD–luồng dữ liệu;
- liên kết tới Mermaid, SVG dùng cho Word và PNG 2400 px.

## 4. Nội dung theo service

### 4.1. Organization

- Dữ liệu sở hữu: `DEPARTMENT`, `STAFF`, `ACCOUNT`.
- Quan hệ vật lý: khoa quản lý nhân viên; nhân viên có thể có tài khoản; trưởng khoa là một nhân viên.
- Event phát: `department.created`, `staff.created`, `staff.department.changed`.
- Clinical tham chiếu logic `DEPARTMENT` và `STAFF` qua `department_id`, `doctor_id`; Report nhận `staff.department.changed` để cập nhật số liệu theo khoa.

### 4.2. Patient

- Dữ liệu sở hữu: `PATIENT`.
- Luồng chính: tạo hồ sơ hành chính, cập nhật thông tin, cung cấp `patient_id` cho các service nghiệp vụ.
- Event phát: `patient.created`, `patient.updated`.
- Clinical xác minh bệnh nhân bằng REST chịu lỗi; các service khác chỉ lưu `patient_id` trần hoặc nhận dữ liệu qua event.

### 4.3. Clinical

- Dữ liệu vật lý V1: `APPOINTMENT`, `MEDICAL_RECORD`, `DIAGNOSIS`.
- Tham chiếu logic: `patient_id`, `doctor_id`, `department_id`.
- Quan hệ vật lý: lịch hẹn có thể khởi tạo hồ sơ; hồ sơ có nhiều chẩn đoán.
- REST vào: xác minh Patient và Organization.
- Event nhận: `lab.result.created`, `prescription.filled`.
- Event phát: `appointment.created`, `appointment.status.changed`, `medicalrecord.created`, `diagnosis.added`.
- Sơ đồ luồng đánh dấu `ATTACHED_RESULT` và sổ khử trùng lặp là phần mở rộng V2 đã được nêu trong backend spec, không trình bày chúng như bảng V1 đã triển khai.

### 4.4. Lab

- Dữ liệu sở hữu: `LAB_TEST`, `LAB_RESULT`, `PROCESSED_EVENT`.
- Tham chiếu logic: `record_id`, `patient_id`, `requesting_department_id`.
- Event nhận: `medicalrecord.created`, `payment.completed`.
- Event phát: `lab.request.created`, `lab.result.created`.
- Luồng chính: tạo chỉ định, ghi kết quả, cập nhật thanh toán và phát kết quả về Clinical/Billing/Notification/Report.

### 4.5. Pharmacy

- Dữ liệu sở hữu: `DRUG`, `PRESCRIPTION`, `PRESCRIPTION_LINE`, `DISPENSE_SLIP`, `STOCK_RESERVATION`, `PROCESSED_EVENT`.
- Tham chiếu logic: `record_id`, `patient_id`, `doctor_id`, `department_id`, `dispensed_by`.
- Event nhận: `payment.completed`.
- Event phát: `prescription.created`, `prescription.filled`, `prescription.dispense.failed`, `stock.low`.
- Luồng chính: kê đơn, giữ tồn, thanh toán, xuất thuốc, giảm tồn và bù trừ khi xuất thất bại.

### 4.6. Billing

- Dữ liệu sở hữu: `FEE`, `INVOICE`, `PROCESSED_EVENT`.
- Tham chiếu logic: `patient_id`, `record_id`, `department_id`, `source_ref_id`, `prescription_id`, `dispense_id`.
- Event nhận: các event tạo phí, kê đơn, xuất thuốc thành công hoặc thất bại.
- Event phát: `invoice.created`, `payment.completed`, `payment.failed`.
- Luồng chính: tích lũy khoản phí, lập hóa đơn, điều phối saga thanh toán–cấp thuốc và bù trừ.

### 4.7. Notification

- Dữ liệu sở hữu: `NOTIFICATION`, `PROCESSED_EVENT`.
- Tham chiếu logic: `patient_id`; `event_id` chỉ là khóa chống xử lý trùng của thông điệp nguồn, không phải khóa ngoại tới database khác.
- Event nhận: sáu routing key được khai báo trong đặc tả Notification.
- Event phát: `notification.sent`.
- Luồng chính: chống trùng, chọn mẫu/kênh, lưu trạng thái `PENDING`, gửi và cập nhật `SENT` hoặc `FAILED`.

### 4.8. Report

- Dữ liệu sở hữu: `DAILY_VISIT_REPORT`, `MONTHLY_REVENUE_REPORT`, `DRUG_STATISTIC`, `PROCESSED_EVENT`.
- Tham chiếu logic: `department_id`, `drug_id` và `event_id` nguồn.
- Event nhận: các event hồ sơ khám, xét nghiệm, cấp thuốc, thanh toán và điều chuyển khoa.
- Luồng chính: chống trùng và cập nhật read model theo khoa lẫn toàn viện; không gọi REST hoặc truy cập database nguồn.

## 5. ERD tổng quan toàn hệ thống

Mục 2.3.9 trình bày tám bounded context theo nhóm màu. Mỗi context chỉ hiển thị aggregate/thực thể cốt lõi và khóa nhận diện cần thiết để hình vẫn đọc được khi in ngang trong Word.

Quan hệ trong hình tổng quan là **quan hệ logic**, không phải foreign key vật lý. Chú giải bắt buộc phân biệt:

- đường liền: quan hệ vật lý nội bộ cùng database;
- đường nét đứt màu xanh: UUID tham chiếu logic hoặc REST validation;
- đường nét đứt màu cam: integration event;
- vùng bao: quyền sở hữu dữ liệu của từng service.

Các tuyến nghiệp vụ chính cần nhìn thấy:

1. Organization/Patient → Clinical → Lab/Pharmacy.
2. Clinical/Lab/Pharmacy → Billing.
3. Billing → Pharmacy trong saga thanh toán–cấp thuốc.
4. Các event nghiệp vụ → Notification và Report.

## 6. Quy chuẩn trình bày

- Tên bảng và cột kỹ thuật dùng tiếng Anh thống nhất với bộ tài liệu đã chuẩn hóa.
- Chú thích nghiệp vụ dùng tiếng Việt có dấu.
- Không vẽ FK vật lý giữa hai database.
- Các khóa ngoài logic phải ghi rõ service nguồn.
- Event dùng routing key chính xác theo backend spec.
- Hình ưu tiên bố cục ngang, nền sáng, tương phản cao và không dùng màu làm phương tiện phân biệt duy nhất.
- Mỗi hình có tiêu đề, chú giải và ghi chú phạm vi.

## 7. Tệp đầu ra

Mỗi sơ đồ mới có ba định dạng trong thư mục `assets/diagrams/`:

- `src/*.mmd`: nguồn Mermaid chỉnh sửa được;
- `word-svg/*.svg`: SVG tự chứa, phù hợp nhập vào Microsoft Word;
- `png/*.png`: bản xem trước rộng 2400 px.

Các ERD vật lý hiện có chỉ được thay thế khi nội dung chưa khớp đặc tả backend. Tám sơ đồ luồng và sơ đồ tổng quan dùng số thứ tự `18` đến `26` để không làm hỏng các liên kết hiện hữu.

## 8. Kiểm tra nghiệm thu

- Có đủ 8 ERD vật lý, 8 ERD–luồng dữ liệu và 1 ERD tổng quan.
- Mọi bảng/cột/quan hệ vật lý khớp DDL trong tám backend spec.
- Mọi UUID ngoài service được thể hiện là tham chiếu logic, không phải FK.
- Event vào/ra khớp routing key trong event catalog và backend spec.
- Tất cả Mermaid render thành công; SVG bắt đầu bằng thẻ `<svg`; PNG đọc được và rộng 2400 px.
- Kiểm tra trực quan không có chữ bị cắt, mũi tên đè lên bảng hoặc chú giải khó đọc.
- Mọi liên kết tương đối trong báo cáo và README trỏ tới tệp được Git theo dõi.

## 9. Ngoài phạm vi

- Không thay đổi migration, entity Java, DTO, REST API hoặc event contract runtime.
- Không tạo foreign key xuyên service.
- Không thiết kế lại nghiệp vụ đã được chốt trong backend spec.
- Không chỉnh sửa các tài liệu Giai đoạn 1 hoặc Giai đoạn 3.
