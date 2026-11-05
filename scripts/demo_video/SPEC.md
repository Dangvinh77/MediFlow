# MediFlow — demo nghiệp vụ ngoại trú

GOAL: Thực hiện đúng kịch bản DEMO_UI_TUNG_BUOC.md bằng UI thật; người xem theo được tài khoản, thao tác và kết quả của từng quầy.
OUTPUT: HyperFrames, 1920 × 1080, 30 fps, MP4; các chương nguồn là bản ghi tab UI thật.
TIME: Theo thời lượng thao tác đã ghi, bỏ thời gian chờ giữa các lần điều khiển; không cắt mất lỗi nghiệp vụ rồi tuyên bố bước thành công.
LOOK: UI gốc chiếm 100% chiều rộng, contain; nền #ffffff; không thêm bộ khung trang trí. Con trỏ trắng viền #111827 24 × 32px, vòng tròn click đỏ #ef4444 đường kính 46px, tồn tại 0.7s.
OBJECTS: Các hồ sơ A/B/C và màn hình thật của nurse, cashier, doctor, lab, pharmacist, patient, admin, trưởng khoa.
MOTION: Con trỏ theo pointer events thực của thao tác; khoanh tròn theo pointerdown thực. Cắt thẳng giữa các đoạn, kết quả giữ tối thiểu 2s.
SHOTS: Chương theo mục 2–10 (A/B/C); chương bổ nhiệm/miễn nhiệm; chương lưới tuần, tạo ca và buổi khám.
BANNED: UI vẽ lại, kết quả giả, thay giờ hệ thống, giả lập khám ngoài giờ, ảnh nghiệm thu cũ coi là bản ghi mới, chữ bay, glow, hạt trang trí, logo đóng góc, ba thẻ quảng cáo.
QA: Đối chiếu coverage.json với từng mục tài liệu; xem ảnh đầu/cuối mỗi đoạn; kiểm MP4 bằng ffprobe; không gọi bản thiếu chương là video hoàn chỉnh.

Kỹ thuật: FFmpeg gdigrab theo HWND đã thử nhưng trả khung đen; không dùng bản thử đó. CDP screencast qua API trình duyệt được phép đã ghi được khung UI thật; bộ đọc phải yield 100ms và chỉ chạy trong từng lời gọi đang được await để tránh treo hoặc mất khung.
