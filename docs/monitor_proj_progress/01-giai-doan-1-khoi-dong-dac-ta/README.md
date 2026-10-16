# Giai đoạn 1 — Khởi động và đặc tả yêu cầu

Cập nhật ngày **16/10/2026**. Hồ sơ giai đoạn 1 tập trung vào **MVP ngoại trú** của MediFlow, bám theo [thiết kế hệ thống hiện hành](../../plans/01-system-design.md).

## Mục tiêu và đầu ra

1. Nêu vấn đề phối hợp tiếp nhận, khám, xét nghiệm, dược và thu phí.
2. Xác định người dùng, ranh giới nghiệp vụ và phạm vi MVP.
3. Đặc tả chức năng theo role, yêu cầu máy khách/máy chủ và tiêu chí nghiệm thu.
4. Xác định nội dung biểu mẫu ban đầu để đối chiếu với DTO và giao diện khi triển khai.

## Review 1

Chỉ duy trì hai bản Standard có nội dung tương ứng:

- [Bản tiếng Việt](review-1/02-review-1-standard-vi.md).
- [English version](review-1/01-review-1-standard-en.md).

Kèm [Task Sheet](review-1/Tasksheet_review1.md) lập ngày 16/10/2026: kế hoạch backend, frontend và mobile từ đầu tháng 10 đến 24/10/2026, có người phụ trách, ngày thực hiện, trạng thái và kết quả cần đạt.

Mỗi bản gồm **Phần A — Xác định vấn đề** và **Phần B — CRS**. Functional Requirements nằm trong phần B, được liệt kê bằng danh sách theo từng role. Yêu cầu phần cứng dùng cấu hình tham khảo cho demo/tích hợp, không cam kết công suất production.

## Cấu trúc project và phạm vi

| Deployment đích | Ngữ cảnh nghiệp vụ | Owner |
|---|---|---|
| Core | Organization + Patient | Hoàng Anh |
| Clinical | Clinical | Vinh |
| Lab | Lab | Vinh |
| Pharmacy | Pharmacy | Huy |
| Billing | Billing | Lộc |
| Support | Report + Notification | Huy / Report; Lộc / Notification |

Gateway là điểm vào backend; Eureka là hạ tầng discovery; `common` là thư viện. Core/Support không chuyển ownership giữa các module và không hợp nhất database. Đích giữ tám kho dữ liệu ngữ cảnh hoạt động.

Source đã có `backend/core-service`, chưa có `backend/support-service`; Report và Notification còn tách module. Đây là phân biệt CURRENT/TARGET, không phải biên bản đã nghiệm thu sáu deployment. Inpatient và Surgery được giữ trong repository nhưng hoãn khỏi profile MVP.

Frontend tiếp tục tổ chức theo logical context trong `src/features/`, không đổi thành thư mục Core/Support. Các lời gọi web đi qua cùng nguồn `/api/*` tới Gateway. Mở rộng mobile không phải điều kiện đóng MVP backend.

## Biểu mẫu nghiệp vụ ban đầu

Danh sách này xác định nội dung người dùng cần làm việc với hệ thống, không định nghĩa lại tên field JSON. Tên field, trường bắt buộc và quyền thao tác phải theo DTO/controller thực tế và [hợp đồng tích hợp](../../plans/03-contracts.md).

| Biểu mẫu | Nội dung chính cần thể hiện |
|---|---|
| Hồ sơ người bệnh | Định danh, thông tin nhân khẩu, liên hệ và thông tin bảo hiểm được hỗ trợ |
| Lịch khám / tiếp nhận | Người bệnh, khoa/bác sĩ, thời gian, trạng thái lịch và check-in |
| Hồ sơ khám | Liên kết lượt khám, thông tin khám, chẩn đoán, chỉ định và hướng xử trí ngoại trú |
| Chỉ định / kết quả xét nghiệm | Hồ sơ nguồn, loại xét nghiệm, trạng thái, kết quả và ngày thực hiện |
| Đơn thuốc / cấp thuốc | Người bệnh/lượt khám nguồn, thuốc, số lượng, giá máy chủ, giữ thuốc và trạng thái cấp |
| Hóa đơn / thanh toán / hoàn tiền | Tài khoản lượt khám, khoản phí, đối tượng được thanh toán, giao dịch gốc, trạng thái và lịch sử |
| Báo cáo / thông báo | Bộ lọc được hỗ trợ, dữ liệu có nguồn, trạng thái cập nhật và người nhận hợp lệ |

Biểu mẫu nội trú, phẫu thuật, tạm ứng và quyết toán nội trú không thuộc đầu ra giai đoạn 1 của MVP này.

## Cơ sở đối chiếu và kiểm tra

- [Luồng nghiệp vụ](../../plans/02-business-flows.md) và [hợp đồng](../../plans/03-contracts.md).
- [Kế hoạch triển khai](../../plans/05-implementation-plan.md) và [tiêu chí nghiệm thu](../../plans/06-acceptance-runbook.md).
- [Frontend blueprint](../../ai/12-frontend.md), [ownership](../../ai/15-frontend-ownership.md) và [workboard](../../../frontend/docs/frontend-workboard.md).

Kiểm tra hai ngôn ngữ có cùng vai trò, phạm vi hoãn, điều kiện nghiệp vụ và tiêu chí nghiệm thu. Kết quả triển khai phải có commit, profile và bằng chứng kiểm thử; không lấy báo cáo lịch sử làm xác nhận hiện tại.

## Sơ đồ và hồ sơ lịch sử

Các file trong `assets/` là sơ đồ của lần lập hồ sơ trước, có thể còn mô tả mười nghiệp vụ. Chúng được giữ để đối chiếu, không minh họa topology MVP hiện hành. Kiến trúc dùng cho Review 1 mới được mô tả trong hai bản Standard và tài liệu thiết kế được liên kết ở trên.

Báo cáo Word/PDF ở thư mục cha là bản nộp lịch sử, chưa được tái xuất từ Markdown mới. Các bản CRS trùng, giải trình, refresh plan/design và các phiên bản Review 1 ngoài Standard đã được bỏ để chỉ còn một bộ yêu cầu song ngữ cần duy trì.
