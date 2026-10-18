# Chọn sơ đồ Review 2 cho Word

Bộ bổ sung có phiên bản gốc và phiên bản dàn trang Word. Mỗi sơ đồ có bản dàn cho A4 dọc và A4 ngang, cùng nguồn Mermaid, SVG và PNG. PNG dùng nền trắng, rộng 2200 hoặc 3200 px; nên chèn PNG vào Word để giữ nguyên nhãn HTML, logo và actor.

- [Báo cáo option Word tiếng Việt](../04-review-2-word-vi.md) — giữ phần nội dung và bảng, thay bằng hình phù hợp trang.
- [English Word layout option](../03-review-2-word-en.md).
- [Trang chọn và so sánh hình](choose-diagrams.html) — mở bằng trình duyệt, chọn ngôn ngữ, hình, khổ giấy và bản gốc/bản gọn/các phần.

Hình 01 và 03 có Client side, Server side, icon và luồng request/response. Hình 01 bỏ số port triển khai; hình 03 bổ sung chi tiết xử lý và phụ thuộc. Các bản trước được lưu tại `../assets/history/architecture-before-side-icons/` và `../assets/history/architecture-before-request-response/`.

## Thiết lập Word

1. Chọn A4, lề 2,5 cm. Trang dọc: ảnh tối đa rộng 16 cm, cao 23 cm. Trang ngang: ảnh tối đa rộng 24,7 cm, cao 15 cm để còn chỗ cho caption.
2. Chọn Insert → Pictures → This Device và lấy PNG ở liên kết bên dưới; đặt Wrap Text → In Line with Text, bật Lock aspect ratio. Giới hạn đồng thời chiều rộng và chiều cao, không kéo giãn ảnh.
3. Khi đổi riêng một trang sang ngang, thêm Section Break → Next Page trước và sau trang đó, rồi chọn Orientation → Landscape cho section.
4. Với hình lớn, dùng từng phần liên tiếp theo thứ tự. Các nút lặp là điểm nối giữa các phần, không phải bảng hoặc tiến trình mới. Nhãn cạnh, cardinality và thông điệp vẫn giữ trong tập các phần. DFD và graph chia theo nhóm quan hệ, không hàm ý thứ tự chạy.
5. Trong Word bật chất lượng ảnh High fidelity và tránh nén ảnh. Kiểm tra bản in ở 100%; cỡ chữ ước tính trong manifest chỉ hỗ trợ lựa chọn bố cục.

## Chọn phiên bản

Hình 01 là tổng quan gom nhóm sáu deployment và tám store, có liệt kê ánh xạ store. Hình 02 giữ đủ 20 logo công nghệ. Hình 03 chia thành Web, Flutter, backend đồng bộ, sự kiện bất đồng bộ và phụ thuộc tĩnh. Các sơ đồ còn lại dàn lại hướng, khoảng cách và xuống dòng; các bản chia phần giữ toàn bộ quan hệ của sơ đồ tương ứng. Bản đầy đủ vẫn có thể nhỏ chữ trên một trang; cột đề xuất ưu tiên các phần khi cần.

| ID | Nội dung | Đề xuất | Tiếng Việt | English | Các phần chi tiết |
|---|---|---|---|---|---|
| 01 | Kiến trúc hệ thống và ranh giới dữ liệu | Dùng các phần | [Dọc](portrait/png/01-vi.png) · [Ngang](landscape/png/01-vi.png) | [Dọc](portrait/png/01-en.png) · [Ngang](landscape/png/01-en.png) | [1](panels/png/01-vi-part-01.png) · [2](panels/png/01-vi-part-02.png) |
| 02 | Kiến trúc công nghệ và công cụ | A4 dọc | [Dọc](portrait/png/02-vi.png) · [Ngang](landscape/png/02-vi.png) | [Dọc](portrait/png/02-en.png) · [Ngang](landscape/png/02-en.png) | — |
| 03 | Kiến trúc ứng dụng và chiều phụ thuộc | Dùng các phần | [Dọc](portrait/png/03-vi.png) · [Ngang](landscape/png/03-vi.png) | [Dọc](portrait/png/03-en.png) · [Ngang](landscape/png/03-en.png) | [1](panels/png/03-vi-part-01.png) · [2](panels/png/03-vi-part-02.png) · [3](panels/png/03-vi-part-03.png) · [4](panels/png/03-vi-part-04.png) · [5](panels/png/03-vi-part-05.png) |
| 04 | Use case tiếp nhận, khám và xét nghiệm | A4 dọc | [Dọc](portrait/png/04-vi.png) · [Ngang](landscape/png/04-vi.png) | [Dọc](portrait/png/04-en.png) · [Ngang](landscape/png/04-en.png) | — |
| 05 | Use case thuốc, viện phí và hỗ trợ | A4 dọc | [Dọc](portrait/png/05-vi.png) · [Ngang](landscape/png/05-vi.png) | [Dọc](portrait/png/05-en.png) · [Ngang](landscape/png/05-en.png) | — |
| 06 | Đăng nhập và truy cập có kiểm quyền — SOURCE | A4 dọc | [Dọc](portrait/png/06-vi.png) · [Ngang](landscape/png/06-vi.png) | [Dọc](portrait/png/06-en.png) · [Ngang](landscape/png/06-en.png) | — |
| 07 | Check-in → thanh toán EXAM → khám — V1 GATED | Dùng các phần | [Dọc](portrait/png/07-vi.png) · [Ngang](landscape/png/07-vi.png) | [Dọc](portrait/png/07-en.png) · [Ngang](landscape/png/07-en.png) | [1](panels/png/07-vi-part-01.png) · [2](panels/png/07-vi-part-02.png) |
| 08 | Chỉ định và trả kết quả xét nghiệm — V1 GATED | Dùng các phần | [Dọc](portrait/png/08-vi.png) · [Ngang](landscape/png/08-vi.png) | [Dọc](portrait/png/08-en.png) · [Ngang](landscape/png/08-en.png) | [1](panels/png/08-vi-part-01.png) · [2](panels/png/08-vi-part-02.png) |
| 09 | Thanh toán và cấp thuốc — V0 compatibility saga | A4 dọc | [Dọc](portrait/png/09-vi.png) · [Ngang](landscape/png/09-vi.png) | [Dọc](portrait/png/09-en.png) · [Ngang](landscape/png/09-en.png) | — |
| 10 | Class diagram Clinical và Lab | A4 ngang | [Dọc](portrait/png/10-vi.png) · [Ngang](landscape/png/10-vi.png) | [Dọc](portrait/png/10-en.png) · [Ngang](landscape/png/10-en.png) | — |
| 11 | Class diagram Pharmacy và Billing | Dùng các phần | [Dọc](portrait/png/11-vi.png) · [Ngang](landscape/png/11-vi.png) | [Dọc](portrait/png/11-en.png) · [Ngang](landscape/png/11-en.png) | [1](panels/png/11-vi-part-01.png) · [2](panels/png/11-vi-part-02.png) |
| 12 | DFD Level 0 — ranh giới hệ thống | A4 ngang | [Dọc](portrait/png/12-vi.png) · [Ngang](landscape/png/12-vi.png) | [Dọc](portrait/png/12-en.png) · [Ngang](landscape/png/12-en.png) | — |
| 13 | DFD Level 1 — sáu tiến trình và tám store | Dùng các phần | [Dọc](portrait/png/13-vi.png) · [Ngang](landscape/png/13-vi.png) | [Dọc](portrait/png/13-en.png) · [Ngang](landscape/png/13-en.png) | [1](panels/png/13-vi-part-01.png) · [2](panels/png/13-vi-part-02.png) · [3](panels/png/13-vi-part-03.png) · [4](panels/png/13-vi-part-04.png) · [5](panels/png/13-vi-part-05.png) · [6](panels/png/13-vi-part-06.png) · [7](panels/png/13-vi-part-07.png) |
| 14 | DFD Level 2 — ghi tiền và cấp quyền dịch vụ V1 | Dùng các phần | [Dọc](portrait/png/14-vi.png) · [Ngang](landscape/png/14-vi.png) | [Dọc](portrait/png/14-en.png) · [Ngang](landscape/png/14-en.png) | [1](panels/png/14-vi-part-01.png) · [2](panels/png/14-vi-part-02.png) · [3](panels/png/14-vi-part-03.png) |
| 15 | ERD — Organization | A4 dọc | [Dọc](portrait/png/15-vi.png) · [Ngang](landscape/png/15-vi.png) | [Dọc](portrait/png/15-en.png) · [Ngang](landscape/png/15-en.png) | — |
| 16 | ERD — Patient | A4 ngang | [Dọc](portrait/png/16-vi.png) · [Ngang](landscape/png/16-vi.png) | [Dọc](portrait/png/16-en.png) · [Ngang](landscape/png/16-en.png) | — |
| 17 | ERD — Clinical | A4 dọc | [Dọc](portrait/png/17-vi.png) · [Ngang](landscape/png/17-vi.png) | [Dọc](portrait/png/17-en.png) · [Ngang](landscape/png/17-en.png) | — |
| 18 | ERD — Lab | A4 ngang | [Dọc](portrait/png/18-vi.png) · [Ngang](landscape/png/18-vi.png) | [Dọc](portrait/png/18-en.png) · [Ngang](landscape/png/18-en.png) | — |
| 19 | ERD — Pharmacy | A4 dọc | [Dọc](portrait/png/19-vi.png) · [Ngang](landscape/png/19-vi.png) | [Dọc](portrait/png/19-en.png) · [Ngang](landscape/png/19-en.png) | — |
| 20 | ERD — Billing | Dùng các phần | [Dọc](portrait/png/20-vi.png) · [Ngang](landscape/png/20-vi.png) | [Dọc](portrait/png/20-en.png) · [Ngang](landscape/png/20-en.png) | [1](panels/png/20-vi-part-01.png) · [2](panels/png/20-vi-part-02.png) · [3](panels/png/20-vi-part-03.png) · [4](panels/png/20-vi-part-04.png) |
| 21 | ERD — Notification | A4 dọc | [Dọc](portrait/png/21-vi.png) · [Ngang](landscape/png/21-vi.png) | [Dọc](portrait/png/21-en.png) · [Ngang](landscape/png/21-en.png) | — |
| 22 | ERD — Report | A4 dọc | [Dọc](portrait/png/22-vi.png) · [Ngang](landscape/png/22-vi.png) | [Dọc](portrait/png/22-en.png) · [Ngang](landscape/png/22-en.png) | — |
| 23 | Triển khai database: một server phát triển, tám authority | A4 dọc | [Dọc](portrait/png/23-vi.png) · [Ngang](landscape/png/23-vi.png) | [Dọc](portrait/png/23-en.png) · [Ngang](landscape/png/23-en.png) | [1](panels/png/23-vi-part-01.png) |
| 24 | Sitemap web theo nhóm route | A4 dọc | [Dọc](portrait/png/24-vi.png) · [Ngang](landscape/png/24-vi.png) | [Dọc](portrait/png/24-en.png) · [Ngang](landscape/png/24-en.png) | [1](panels/png/24-vi-part-01.png) · [2](panels/png/24-vi-part-02.png) · [3](panels/png/24-vi-part-03.png) |
| 25 | Sitemap mobile và mức triển khai | A4 ngang | [Dọc](portrait/png/25-vi.png) · [Ngang](landscape/png/25-vi.png) | [Dọc](portrait/png/25-en.png) · [Ngang](landscape/png/25-en.png) | — |
| 26 | Thuật toán tạo đơn V1 có kiểm context | Dùng các phần | [Dọc](portrait/png/26-vi.png) · [Ngang](landscape/png/26-vi.png) | [Dọc](portrait/png/26-en.png) · [Ngang](landscape/png/26-en.png) | [1](panels/png/26-vi-part-01.png) · [2](panels/png/26-vi-part-02.png) · [3](panels/png/26-vi-part-03.png) |
| 27 | Thuật toán payment và clearance | Dùng các phần | [Dọc](portrait/png/27-vi.png) · [Ngang](landscape/png/27-vi.png) | [Dọc](portrait/png/27-en.png) · [Ngang](landscape/png/27-en.png) | [1](panels/png/27-vi-part-01.png) · [2](panels/png/27-vi-part-02.png) · [3](panels/png/27-vi-part-03.png) |
| 28 | Thuật toán outbox / inbox | A4 dọc | [Dọc](portrait/png/28-vi.png) · [Ngang](landscape/png/28-vi.png) | [Dọc](portrait/png/28-en.png) · [Ngang](landscape/png/28-en.png) | — |

## Nguồn và tính tương thích

Các thư mục `portrait/`, `landscape/`, `panels/` đều có `mermaid/`, `svg/`, `png/`. Không xoay ảnh để đổi khổ giấy. Hướng TB/LR trong Mermaid có thể khác tên khổ giấy vì bộ chọn đã so sánh tỷ lệ thực tế của hai cách dàn. SVG có nhãn HTML nên PNG là định dạng ưu tiên trong Word. Logo SVG được nhúng offline vào SVG/PNG; nguồn Mermaid dùng cùng CDN Devicon có phiên bản cố định như bộ gốc.

Bộ này chỉ bổ sung lựa chọn sơ đồ, chưa thay hoặc xuất lại các tệp DOCX/PDF lịch sử. Muốn dùng một hình cụ thể, mở PNG tương ứng hoặc dùng trang chọn bên trên.
