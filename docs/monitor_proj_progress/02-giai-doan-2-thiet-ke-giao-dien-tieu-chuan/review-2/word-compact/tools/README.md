# Dựng lại bộ sơ đồ Word

Các nguồn nằm trong `../portrait/mermaid`, `../landscape/mermaid` và `../panels/mermaid`.
Không dùng lại quy tắc chọn cùng một bố cục cho cả hai khổ giấy.

1. Tại thư mục này, chạy `npm install` (Node.js 20+). Có thể đặt `CHROME_PATH` nếu dùng Chrome đã cài.
2. Chạy `npm run render`. Renderer thay URL logo bằng assets offline, dựng SVG, chờ font/ảnh, đo vùng nội dung, thêm lề 24 đơn vị và xuất PNG trực tiếp từ **SVG đã lưu** trong một trang riêng.
3. Mỗi PNG được đối chiếu với một lần mở SVG độc lập. Chỉ cho phép sai khác khử răng cưa thưa: tối đa 10/255 mỗi kênh và 0,1% số pixel; sai vị trí hoặc thiếu nội dung làm bước này thất bại.
4. Chạy `python publish.py` để cập nhật hướng dẫn, manifest, hai báo cáo Word và trang chọn hình. Bước này không chép đè một khổ sang khổ còn lại.
5. Chạy `python verify.py` (cần Pillow) rồi `node inspect.mjs` để kiểm tra file, ảnh, tính độc lập bố cục, vùng hiển thị và trang chọn hình. Mở các contact sheet trong `../qa/` để duyệt hình.

Có thể truyền tên nguồn để dựng thử, ví dụ `node render.mjs 06-vi.mmd`. Selected runs write a separate diagnostic ledger; successful results also update the matching entries in the full render-validation.json ledger.
Nếu dùng dependency đã cài ở nơi khác, đặt `MERMAID_RUNTIME` trỏ tới thư mục chứa `node_modules`.
Giữ `assets/` của bản chuẩn và các file DOCX bên ngoài bộ này nguyên vẹn.
