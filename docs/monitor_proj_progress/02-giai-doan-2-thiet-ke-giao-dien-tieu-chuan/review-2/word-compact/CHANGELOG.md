# Sửa bộ Word — 2026-10-19

- Bỏ bước sao chép ứng viên có tỷ lệ tốt nhất vào cả hai khổ. Bộ cũ có 38/56 cặp nguồn dọc–ngang trùng nhau. Hai khổ hiện có cách dàn riêng; ERD Patient chỉ một bảng là ngoại lệ được ghi rõ.
- Dựng lại toàn bộ SVG và PNG từ cùng nguồn Mermaid. Không giữ SVG cũ sau khi thay cấu hình nguồn.
- Tính lại vùng nội dung thực, bao gồm chữ, logo, nhãn cạnh và actor; thêm lề an toàn. Lifeline sequence kết thúc ở vùng nội dung, có actor cuối để thấy rõ điểm kết thúc.
- Xuất PNG từ SVG độc lập đã lưu; chờ font, ảnh và trình duyệt vẽ ổn định. Bỏ CSS toàn cục và cơ chế chụp toàn trang cũ. Đối chiếu pixel với lần mở SVG độc lập để bắt ảnh thiếu hoặc lệch.
- Giữ namespace XHTML khi lưu SVG có foreignObject. Kiểm tra XML, asset offline, kích thước PNG, bốn viền trắng và hash nguồn/ảnh.
- Trang chọn hình dùng vùng ảnh có chiều cao xác định, không cắt tràn. Thêm chế độ so sánh dọc/ngang.
- Các ERD không có quan hệ được xếp bảng thành lưới theo khổ giấy sau bước dựng Mermaid; không thêm quan hệ giả để ép bố cục.

Kết quả kiểm tra cuối nằm trong `validation.json`, `render-validation.json` và `chooser-validation.json`.
Các contact sheet trong `qa/` chứa toàn bộ ảnh để duyệt. Bản chuẩn trong `../assets/` và DOCX hiện có được giữ nguyên.
