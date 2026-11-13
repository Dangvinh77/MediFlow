MEDIFLOW — SLIDE DEMO THEO SERVICE
Ngày soạn: 04/11/2026. Baseline tham chiếu: 8d6c287d.

Mở index.html trực tiếp bằng Chrome/Edge; chạy offline, không cần cài gói, không gọi CDN.
← → hoặc Page Up/Down: chuyển slide. G: mục lục. N: ghi chú / nguồn. M: mã Mermaid.
F: toàn màn hình. P: in hoặc lưu PDF. Bấm sơ đồ để phóng to; Escape để đóng.
Link #7 mở trực tiếp slide 7. Giao diện tự co theo màn hình; nên dùng màn hình ngang.

23 slide: Functional Requirements, Use case, kiến trúc microservices, 3 slide Gateway chi tiết, rồi hành trình bệnh nhân. Billing quay lại ở 3 điểm thu.
Kịch bản demo theo thứ tự và account seed: scripts/demo_25_10/DEMO_THEO_THU_TU.md.
Phân công trình bày đúng yêu cầu: Hoàng Anh (Gateway/Organization/Patient), Vinh (Clinical/Lab), Huy (Pharmacy/Report), Lộc (Billing/Notification).
Phân công demo không thay đổi ownership sửa source của repo.

Sơ đồ SVG đã nhúng; mã Mermaid có trong diagrams/ và trong nút M.
build.mjs dùng thư viện beautiful-mermaid có sẵn của môi trường soạn để dựng lại; không cần để xem HTML.

Đây là kịch bản đối chiếu source, chưa xác nhận runtime đã chạy qua toàn bộ luồng.
Chốt đúng profile: scheduled EXAM, Lab/Rx clearance, Notification IN_APP và Report accepted periods.
Report có snapshot/publication: không hứa kỳ đã publish cập nhật ngay khi tạo lượt mới.
Đọc ghi chú N trên từng slide để xem contract/giới hạn và đường dẫn source.
Nguồn chính: docker-compose.yml; backend/gateway/src/main/resources/application.yml; docs/plans/01-system-design.md; 02-business-flows.md; 03-contracts.md; 06-acceptance-runbook.md; producer/consumer và UI của từng context.
