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
Khi chỉ dựng bộ Word, không ghi đè `assets/` của bản chuẩn. Chỉ dùng `--original` khi chủ động cập nhật bản chuẩn; các file DOCX lịch sử bên ngoài bộ này được giữ nguyên.

## Use case theo role — hình 29–36

`role_tasks.json` chứa nhãn Việt/Anh, tác vụ chính, quan hệ và đường dẫn đối chiếu source cho 8 role. `role_usecases.py` đọc nguồn này để dựng các sơ đồ. Chạy script này khi cập nhật bộ use case. Script bổ sung hai báo cáo chuẩn và các nguồn Word; sau đó render các nguồn thay đổi, chạy `publish.py` để đồng bộ báo cáo Word/trang chọn.

Nguồn Mermaid dùng shape stadium tương thích và mũi tên `extension -.-> base`. Khi xuất, renderer dàn extension sau tác vụ gốc, khôi phục đầu mũi tên trỏ về base và chuyển đường bao thành ellipse. Chỉ hình có marker `%% Role use case:` áp dụng xử lý này. Actor, nội dung và quan hệ giữ theo nguồn. Mã điều kiện E1/E2 được giữ thống nhất giữa hình đầy đủ và các phần; mô tả điều kiện nằm ngay sau hình trong báo cáo.

`node render.mjs --original 29-vi.mmd` dựng bản chuẩn từ `../../assets/mermaid/`; ghi ledger riêng `render-original-validation.json`, không trộn bản chuẩn với ledger Word. Có thể truyền nhiều tên nguồn. Lệnh này cũng tạo PNG chuẩn bên cạnh SVG. Chỉ dùng với các nguồn cần cập nhật.

Sau khi xuất cả bản chuẩn và Word, chạy `python verify_role_usecases.py`. Bước này kiểm tra đủ 8 role, đúng một actor, node/quan hệ Việt–Anh, ellipse, đầu mũi tên include/extend và việc các phần Word giữ đủ tác vụ của hình đầy đủ. `verify.py` và `inspect.mjs` tự lấy danh sách hình hiện có, không giới hạn 28 hình.

## Bản sửa theo góp ý giảng viên — 2026-10-19

Chạy `python supervisor_revision.py` trước `python role_usecases.py` khi thay đổi mô hình. Script đầu tạo hai architecture, tám sequence và năm class diagram từ cùng một bộ nhãn song ngữ. Hai script đồng bộ báo cáo chuẩn và nguồn Mermaid; chúng dọn các panel cũ thuộc chính những hình được dựng lại, vì vậy cần render lại đầy đủ các hình/panel bị thay đổi sau mỗi lần chạy.

- Hình 04/05 nay là gia hạn/đặt lịch; use case chỉ dùng bộ theo role 29–36.
- Hình 07–09, 37–38 dùng cùng luồng V1 ngoại trú, tách trả kết quả khám và cấp thuốc.
- Hình 39/Core, 10/Clinical, 40/Lab, 11/Pharmacy + Billing, 41/Support nhóm class theo service.
- Các phần sequence giữ guard `alt/opt/loop`, đủ mọi thông điệp theo thứ tự và tiếp tục số thứ tự từ hình đầy đủ. Chú thích phần trước/sau yêu cầu đọc cùng hình đầy đủ.
- `python verify_supervisor_revision.py` kiểm tra cấu trúc Việt/Anh, lifecycle V1, trường/phương thức class trong source và thông điệp của các phần.
- Renderer đặt tiêu đề nhóm architecture vào khoảng trống đã kiểm tra để tránh đè lên nhãn request/response. Tất cả PNG vẫn được xuất và đối chiếu độc lập từ SVG đã lưu.

Sau khi render, chạy `python publish.py`, `python verify_role_usecases.py`, `python verify_supervisor_revision.py`, `python verify.py` và `node inspect.mjs`. Các file DOCX/PDF chỉnh tay trong `docs/documentation_docx/` không thuộc đầu ra của bộ công cụ này.

Nếu một batch gặp khóa file tạm thời trên Windows, chạy lại đúng danh sách nguồn với `--resume`. Chỉ các bộ source/SVG/PNG đã thành công và có đủ ba hash trùng khớp được dùng lại; những hình còn lại được dựng lại. Luôn chạy bộ kiểm tra đầy đủ sau khi hoàn tất.

Có thể truyền ID để chỉ dựng lại mô hình đã sửa, ví dụ `python supervisor_revision.py 8 9`. Một nhánh alternative được trích riêng sẽ dùng `opt` với nguyên điều kiện của nhánh đó; không hiển thị lại các nhánh trống. Bộ kiểm tra đối chiếu toàn bộ đường điều kiện bao quanh từng thông điệp giữa hình đầy đủ và các phần.
