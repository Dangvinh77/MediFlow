# MediFlow — Spec triển khai Backend

Đặc tả đủ chi tiết để **viết code thật** cho từng service: DDL chính xác, kiểu dữ liệu chính xác,
chữ ký method chính xác, annotation validation chính xác, payload event chính xác, và test case
chính xác. Viết để cả AI lẫn người đọc đều dùng được.

## Khác gì với các tài liệu còn lại

| Ở đâu | Trả lời câu hỏi | Ví dụ |
|-------|-----------------|-------|
| [`docs/ai/`](../../docs/ai/README.md) | **Viết code thế nào** | "tầng domain không được import Spring" |
| [`docs/eproject_general_plan/*.html`](..) | **Nghiệp vụ cần gì** | "một bệnh nhân không được có 2 lịch hẹn chờ trong cùng ngày" |
| **thư mục này** | **Xây chính xác cái gì** | `boolean existsByMaBenhNhanAndNgayHenAndTrangThai(UUID, LocalDate, TrangThaiLichHen)` |

Ba nơi này **không lặp lại nhau**. Khi spec ở đây cần một quy tắc, nó *dẫn link* sang `docs/ai/`
chứ không chép lại. Nếu có mâu thuẫn, thứ tự thẩm quyền là:

1. `docs/eproject_general_plan/*.html` — cho *nghiệp vụ làm gì*
2. `docs/ai/` — cho *code tổ chức ra sao*
3. thư mục này — cho *hình hài cụ thể của phần triển khai*

## Danh sách file

| File | Service | Module |
|------|---------|--------|
| [00-overview.md](00-overview.md) | **Đọc trước tiên** — hợp đồng dùng chung cho mọi service | — |
| [01-organization.md](01-organization.md) | Khoa phòng, nhân viên, tài khoản | `organization-service` |
| [02-patient.md](02-patient.md) | Hồ sơ bệnh nhân gốc | `patient-service` |
| [03-clinical.md](03-clinical.md) | Khoa Khám bệnh — lịch hẹn, hồ sơ, chẩn đoán | `clinical-service` |
| [04-lab.md](04-lab.md) | Khoa Xét nghiệm | `lab-service` |
| [05-pharmacy.md](05-pharmacy.md) | Khoa Dược — thành phần của saga | `pharmacy-service` |
| [06-billing.md](06-billing.md) | Phòng Viện phí — điều phối saga | `billing-service` |
| [07-notification.md](07-notification.md) | Email / SMS / trong ứng dụng | `notification-service` |
| [08-report.md](08-report.md) | Read model dựng từ event | `report-service` |
| [09-gateway.md](09-gateway.md) | Định tuyến, JWT, giới hạn tần suất | `gateway` |

## Thứ tự xây dựng

Phụ thuộc chạy từ trái sang phải — chỉ xây một service sau khi các service nó gọi REST đã tồn tại:

```
common → eureka-server → organization → patient → clinical → lab → pharmacy → billing
                                                                  ↘ notification, report (chỉ nghe event, làm lúc nào cũng được)
                                                      gateway (cần organization để đăng nhập)
```

`notification` và `report` chỉ subscribe event và không gọi ai, nên có thể xây bất cứ lúc nào.
`gateway` cần `organization` để xác thực thật.

## Dùng cùng AI viết code

Chỉ đưa cho AI **ba** file, không hơn:

1. `docs/ai/04-microservice-blueprint.md` — cấu trúc package bắt buộc
2. `docs/eproject_general_plan/backend-spec/00-overview.md` — hợp đồng dùng chung
3. `docs/eproject_general_plan/backend-spec/0N-<service>.md` — service đang xây

Sau đó yêu cầu làm **từng tầng một** (domain → port → application → adapter), đừng bảo nó sinh cả
service trong một lần. Mỗi spec kết thúc bằng một Definition of Done để bạn đối chiếu kết quả.
