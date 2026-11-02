# Tiến độ cải thiện sau review — 02/11/2026

Review gốc và ảnh trong thư mục này phản ánh phiên quan sát trước khi sửa. Không sửa
lại bằng chứng lịch sử thành kết quả nghiệm thu mới. Nhánh triển khai:
`codex/outpatient-ux-foundation`, nền `99b80114`.

## Đợt đầu đã có mã nguồn

| Nhóm review | Thay đổi | Giới hạn còn lại |
|---|---|---|
| MF-01 — phạm vi Report | Report xác minh quyền quản lý khoa hiện tại qua Core ở mỗi lần đọc; yêu cầu khoa cụ thể; từ chối phạm vi toàn viện, khoa khác, thu hồi quyền và lỗi xác minh. ADMIN giữ quyền cũ. | Bộ chọn khoa và cách trình bày lỗi trên FE Report thuộc Huy; chưa coi toàn bộ trải nghiệm Report hoàn tất. |
| MF-02 — tiếp nhận | Giữ phiếu thành công, tải lại số chỗ và danh sách từ backend; lỗi tải lại chỉ thử đọc lại; khóa đăng ký bằng dữ liệu cũ. Không cho đổi buổi khi yêu cầu tạo chưa rõ kết quả; gửi lại giữ nguyên mã chống trùng. | Chưa có hàng điều phối xuyên quầy bằng tên bệnh nhân, chưa thay thế toàn bộ việc sao chép mã. |
| MF-05/MF-08/MF-27 — ngữ cảnh Lab | Hiện bệnh nhân, ngày sinh, ngày/khoa tại phiếu và lúc nhập kết quả; xác minh hồ sơ trước khi lập chỉ định; chặn nguồn chỉ định không khớp. Ẩn phiên bản/mã kỹ thuật trong phần hỗ trợ; dịch trạng thái dễ hiểu hơn. | Chỉ định vẫn có hai bước; chưa có danh mục xét nghiệm/giá được duyệt và phục hồi bền vững khi rời trang. Không mở thêm quyền tìm nhân sự/khoa cho LAB_TECH. |

149 kiểm thử giao diện liên quan đã đạt; đã có kiểm thử tái hiện lỗi trước khi sửa.
Kiểm tra kiểu, lint và build sạch đã qua tại thời điểm kiểm tra. Rà soát độc lập theo
yêu cầu rồi theo chất lượng mã đã qua. Kết quả backend và UI thực tế cuối đợt được
ghi riêng trong [biên bản nghiệm thu](../../acceptance/outpatient-ux-foundation-2026-11-02/README.md), không suy ra từ kiểm thử giả lập.

## Việc còn lại

- Giữ backlog 30 vấn đề và thứ tự ưu tiên trong [review gốc](REVIEW_UI_UX_NGOAI_TRU.md).
- [Bàn giao theo owner](../../../frontend/docs/handoffs/HANDOFF-OUTPATIENT-PRODUCT-OWNER-UX.md): Huy xử lý FE Pharmacy/Report; Hoàng Anh xử lý FE Patient/Organization; Vinh xử lý backend và shared UI.
- Cần các đợt tiếp theo cho hàng công việc xuyên quầy, danh mục chỉ định, nội dung hồ sơ khám, tình trạng thu/nhận thuốc, tổng quan quản lý theo khoa và nghiệm thu toàn hành trình trong giờ khám thực.
- Surgery/Inpatient tiếp tục deferred; không xóa nguồn mở rộng và không coi là đã được kích hoạt.

Đây là đợt cải thiện đầu tiên, không phải tuyên bố sản phẩm đã hoàn tất mọi chức năng.
