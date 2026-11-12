# Ngôn ngữ giao diện web và mobile

Tiếng Việt (`vi`) là mặc định, tiếng Anh (`en`) là lựa chọn thứ hai. Ngôn ngữ và theme
được lưu riêng trên từng thiết bị; đổi ngôn ngữ không đăng xuất hoặc gửi lại yêu cầu nghiệp vụ.

Nguồn bản dịch dùng chung ở `frontend/src/lib/l10n/vi.json` và `en.json`. Chuỗi tiếng Việt
là khóa ổn định. Hai file phải có cùng khóa, giữ nguyên các biến `{0}`, `{1}`. Khi thêm UI,
thêm cả hai bản dịch, rồi chạy `node scripts/sync-l10n.mjs` và
`dart format mobile/lib/core/l10n/messages_en.dart mobile/lib/core/l10n/messages_vi.dart`.
Mobile đọc các file Dart sinh sẵn, không gọi dịch vụ dịch thuật khi chạy ứng dụng.

Web: dùng `useLocale().t("Nhãn")` / `localize(label)` trong client component;
dùng `<L10n>{"Nhãn"}</L10n>` cho nội dung tĩnh ở server component. `L10nValue` hỗ trợ
nhãn, trạng thái và thông báo có sẵn, giữ nguyên React elements. Không bọc tên, địa chỉ,
chẩn đoán hay nội dung người dùng nhập bằng hàm dịch. Giữ `value`, `id`, mã trạng thái,
route và DTO đúng hợp đồng; chỉ dịch nhãn hiển thị.

Mobile: dùng `LText('Nhãn')` và `AppL10n.text(context, 'Nhãn')` cho thuộc tính chuỗi.
Dùng `Text` thông thường cho dữ liệu người dùng nhập. `AppL10n` cập nhật các widget phụ thuộc
ngôn ngữ, không tạo lại router hoặc xóa form. Flutter Material nhận locale tương ứng cho
lịch, hộp thoại và các nút hệ thống. Theme Sáng/Tối/Theo hệ thống độc lập với ngôn ngữ.

Kiểm thử: bộ `LanguageToggle.test.tsx`, `l10n.test.ts` và `mobile/test/core/l10n_test.dart`
kiểm tra mặc định, lưu lựa chọn, đồng bộ tab, giữ nội dung form và biến trong bản dịch.
Các bản dịch dài ban đầu được hỗ trợ bằng dịch máy; thuật ngữ menu và cài đặt đã được
hiệu chỉnh. Nên rà soát ngữ cảnh khi sửa nội dung nghiệp vụ hoặc thêm màn hình mới.

Xác nhận local ngày 12/11/2026: web 1.539 tests / 181 files, mobile 788 tests đều đạt;
web typecheck, lint (không lỗi), Next production build và Flutter web build đạt.
Flutter analyze không có lỗi/cảnh báo mới; còn ba thông báo `info` có sẵn ở Pharmacy data
và hai file test. Trình duyệt đã kiểm tra chuyển Việt → Anh và giữ lựa chọn sau reload.
Đây là kiểm tra giao diện/local build, không thay thế nghiệm thu luồng nghiệp vụ qua Gateway
hoặc bản cài native Android/iOS.
