# Bộ sơ đồ Giai đoạn 2 - MediFlow

Bộ này được dựng bằng skill **Pretty Mermaid** từ nguồn Mermaid và xuất theo ba định dạng:

- `src/*.mmd`: nguồn có thể chỉnh sửa.
- `word-svg/*.svg`: SVG đã loại bỏ font tải ngoài và CSS variable, phù hợp để chèn vào Microsoft Word.
- `png/*.png`: PNG nền trắng, rộng 2400 px, dùng khi phiên bản Word xử lý SVG không ổn định.
- `svg/*.svg`: SVG gốc do Pretty Mermaid render, giữ lại để đối chiếu kỹ thuật.

## Cách chèn vào Word

1. Chọn **Insert → Pictures → This Device**.
2. Ưu tiên file trong `word-svg/` để giữ nét vector khi phóng to.
3. Nếu Word làm thay đổi màu hoặc font, dùng file cùng tên trong `png/`.
4. Với hình rộng như kiến trúc, topology event và sequence diagram, chuyển trang sang **Landscape** trước khi chèn.
5. Không sao chép hình trực tiếp từ trình duyệt vì Word có thể chỉ lấy bản preview độ phân giải thấp.

## Danh mục sơ đồ

| STT | Sơ đồ | Nguồn | SVG dùng cho Word | PNG 2400 px |
|---:|---|---|---|---|
| 1 | Kiến trúc hệ thống | [MMD](src/01-kien-truc-he-thong.mmd) | [SVG](word-svg/01-kien-truc-he-thong.svg) | [PNG](png/01-kien-truc-he-thong.png) |
| 2 | ERD Organization | [MMD](src/02-erd-organization.mmd) | [SVG](word-svg/02-erd-organization.svg) | [PNG](png/02-erd-organization.png) |
| 3 | ERD Patient | [MMD](src/03-erd-patient.mmd) | [SVG](word-svg/03-erd-patient.svg) | [PNG](png/03-erd-patient.png) |
| 4 | ERD Clinical | [MMD](src/04-erd-clinical.mmd) | [SVG](word-svg/04-erd-clinical.svg) | [PNG](png/04-erd-clinical.png) |
| 5 | ERD Lab | [MMD](src/05-erd-lab.mmd) | [SVG](word-svg/05-erd-lab.svg) | [PNG](png/05-erd-lab.png) |
| 6 | ERD Pharmacy | [MMD](src/06-erd-pharmacy.mmd) | [SVG](word-svg/06-erd-pharmacy.svg) | [PNG](png/06-erd-pharmacy.png) |
| 7 | ERD Billing | [MMD](src/07-erd-billing.mmd) | [SVG](word-svg/07-erd-billing.svg) | [PNG](png/07-erd-billing.png) |
| 8 | ERD Notification | [MMD](src/08-erd-notification.mmd) | [SVG](word-svg/08-erd-notification.svg) | [PNG](png/08-erd-notification.png) |
| 9 | ERD Report | [MMD](src/09-erd-report.mmd) | [SVG](word-svg/09-erd-report.svg) | [PNG](png/09-erd-report.png) |
| 10 | Topology sự kiện | [MMD](src/10-topology-su-kien.mmd) | [SVG](word-svg/10-topology-su-kien.svg) | [PNG](png/10-topology-su-kien.png) |
| 11 | Quy trình DDL và migration | [MMD](src/11-quy-trinh-ddl.mmd) | [SVG](word-svg/11-quy-trinh-ddl.svg) | [PNG](png/11-quy-trinh-ddl.png) |
| 12 | Luồng nghiệp vụ tổng quát | [MMD](src/12-luong-nghiep-vu-tong-quat.mmd) | [SVG](word-svg/12-luong-nghiep-vu-tong-quat.svg) | [PNG](png/12-luong-nghiep-vu-tong-quat.png) |
| 13 | Tiếp nhận và tạo lịch hẹn | [MMD](src/13-sequence-tiep-nhan-lich-hen.mmd) | [SVG](word-svg/13-sequence-tiep-nhan-lich-hen.svg) | [PNG](png/13-sequence-tiep-nhan-lich-hen.png) |
| 14 | Khám và xét nghiệm | [MMD](src/14-sequence-kham-xet-nghiem.mmd) | [SVG](word-svg/14-sequence-kham-xet-nghiem.svg) | [PNG](png/14-sequence-kham-xet-nghiem.png) |
| 15 | Thanh toán và cấp thuốc | [MMD](src/15-sequence-thanh-toan-cap-thuoc.mmd) | [SVG](word-svg/15-sequence-thanh-toan-cap-thuoc.svg) | [PNG](png/15-sequence-thanh-toan-cap-thuoc.png) |
| 16 | Vòng đời saga | [MMD](src/16-vong-doi-saga.mmd) | [SVG](word-svg/16-vong-doi-saga.svg) | [PNG](png/16-vong-doi-saga.png) |
| 17 | Thông báo và báo cáo | [MMD](src/17-luong-thong-bao-bao-cao.mmd) | [SVG](word-svg/17-luong-thong-bao-bao-cao.svg) | [PNG](png/17-luong-thong-bao-bao-cao.png) |

## Quy chuẩn xuất hình

- Nền: `#FFFFFF`.
- Chữ: `#0F172A`.
- Đường nối: `#2563EB`.
- Màu nhấn và đầu mũi tên: `#0F766E`.
- Nền đối tượng: `#F0FDFA`.
- Font văn bản: Arial; font tên cột kỹ thuật: Consolas.
- Sơ đồ lớn được tách theo bounded context để giữ khả năng đọc trên trang A4.
